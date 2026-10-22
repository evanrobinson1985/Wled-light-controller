package com.wledcontroller.app.house.model3d

import android.app.Activity
import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** How the scan is going. */
data class ScanStatus(
    val tracking: Boolean = false,
    val hint: String = "Starting the camera…",
    /** Views taken in so far. */
    val views: Int = 0,
    /** About how much surface has been scanned, m². */
    val areaM2: Float = 0f,
    /** Photos kept for sharpening the colours afterwards. */
    val photos: Int = 0,
)

/**
 * The phone as a 3D scanner, with ARCore: it tracks where the phone is as you walk, measures
 * how far away things are from the camera's movement, and combines every view into one
 * coloured surface ([TsdfVolume]), 5 cm cubes at a time.
 */
class ArScanner(private val context: Context, private val photoDir: java.io.File) {
    @Volatile var session: Session? = null
        private set
    val volume = TsdfVolume(voxel = 0.05f, truncation = 0.15f)
    private val _status = MutableStateFlow(ScanStatus())
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    /** Taking in views (paused: just watching). */
    @Volatile var recording = false
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var busy = false
    private var lastAt = 0L
    private var lastDepthTime = 0L
    @Volatile var preview: Pair<FloatArray, IntArray>? = null
        private set
    // The last photo kept: where from, and when.
    private var lastPhotoPose: FloatArray? = null
    private var lastPhotoAt = 0L
    private var photos = 0

    /** Sets ARCore up. Returns why it can't, or null when it's ready; "install" when it's being installed. */
    fun open(activity: Activity): String? {
        if (session != null) return null
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, true)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> return "install"
                else -> Unit
            }
            photoDir.deleteRecursively(); photoDir.mkdirs()
            val s = Session(context)
            // The sharpest camera pictures it offers (up to 1080p), for colour and photos.
            runCatching {
                val filter = com.google.ar.core.CameraConfigFilter(s).setFacingDirection(com.google.ar.core.CameraConfig.FacingDirection.BACK)
                s.getSupportedCameraConfigs(filter).filter { it.imageSize.width <= 1920 }
                    .maxByOrNull { it.imageSize.width * it.imageSize.height }?.let { s.cameraConfig = it }
            }
            val mode = when {
                s.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY) -> Config.DepthMode.RAW_DEPTH_ONLY
                s.isDepthModeSupported(Config.DepthMode.AUTOMATIC) -> Config.DepthMode.AUTOMATIC
                else -> { s.close(); return "This phone can't measure depth with ARCore, so it can't scan." }
            }
            s.configure(Config(s).apply {
                depthMode = mode
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.DISABLED
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            })
            session = s
            null
        } catch (e: Exception) {
            "Couldn't start the scanner: ${e.message ?: e.javaClass.simpleName}. It needs Google Play Services for AR."
        }
    }

    fun resume() { runCatching { session?.resume() } }
    fun pause() { runCatching { session?.pause() } }
    fun close() { runCatching { session?.close() }; session = null; worker.shutdown() }

    /** Each camera frame, on the drawing thread: takes in a view now and then. */
    fun onFrame(frame: Frame) {
        val cam = frame.camera
        if (cam.trackingState != TrackingState.TRACKING) {
            _status.value = _status.value.copy(tracking = false, hint = when (cam.trackingFailureReason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark — scan in daylight."
                TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast — slow down."
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at something with more detail."
                TrackingFailureReason.CAMERA_UNAVAILABLE -> "The camera is busy."
                else -> "Move the phone slowly from side to side to get started."
            })
            return
        }
        val now = System.currentTimeMillis()
        if (!recording) { _status.value = _status.value.copy(tracking = true, hint = if (_status.value.views == 0) "Ready: point at the house and press Start." else "Paused."); return }
        if (busy || now - lastAt < 180) return
        val depth = try { frame.acquireRawDepthImage16Bits() } catch (_: NotYetAvailableException) { return } catch (_: Exception) { return }
        if (depth.timestamp == lastDepthTime) { depth.close(); return }
        val conf = try { frame.acquireRawDepthConfidenceImage() } catch (_: Exception) { depth.close(); return }
        val image = try { frame.acquireCameraImage() } catch (_: Exception) { depth.close(); conf.close(); return }
        try {
            lastDepthTime = depth.timestamp
            lastAt = now
            val view = View.copy(depth, conf, image, cam)
            busy = true
            worker.execute {
                try { take(view) } finally { busy = false }
            }
        } finally {
            depth.close(); conf.close(); image.close()
        }
    }

    /** One view's measurements, copied out of ARCore so they can be worked on elsewhere. */
    private class View(
        val dw: Int, val dh: Int, val depth: ShortArray, val conf: ByteArray,
        val iw: Int, val ih: Int, val y: ByteArray, val yRow: Int, val u: ByteArray, val v: ByteArray, val uvRow: Int, val uvPix: Int,
        val pose: FloatArray, val fx: Float, val fy: Float, val cx: Float, val cy: Float, val imgW: Int, val imgH: Int,
    ) {
        companion object {
            fun copy(depth: android.media.Image, conf: android.media.Image, image: android.media.Image, cam: com.google.ar.core.Camera): View {
                val dw = depth.width; val dh = depth.height
                val dp = depth.planes[0]
                val db = dp.buffer.order(ByteOrder.nativeOrder())
                val d = ShortArray(dw * dh)
                for (r in 0 until dh) for (c in 0 until dw) d[r * dw + c] = db.getShort(r * dp.rowStride + c * dp.pixelStride)
                val cp = conf.planes[0]
                val cb = cp.buffer
                val cf = ByteArray(dw * dh)
                for (r in 0 until minOf(dh, conf.height)) for (c in 0 until minOf(dw, conf.width)) cf[r * dw + c] = cb.get(r * cp.rowStride + c * cp.pixelStride)
                fun bytes(b: ByteBuffer) = ByteArray(b.remaining()).also { b.get(it) }
                val pl = image.planes
                val pose = FloatArray(16).also { cam.pose.toMatrix(it, 0) }
                val intr = cam.imageIntrinsics
                val f = intr.focalLength; val pp = intr.principalPoint; val dims = intr.imageDimensions
                return View(
                    dw, dh, d, cf, image.width, image.height,
                    bytes(pl[0].buffer), pl[0].rowStride, bytes(pl[1].buffer), bytes(pl[2].buffer), pl[1].rowStride, pl[1].pixelStride,
                    pose, f[0], f[1], pp[0], pp[1], dims[0], dims[1],
                )
            }
        }
    }

    private fun take(v: View) {
        // The depth picture is a smaller copy of the camera's.
        val sx = v.dw.toFloat() / v.imgW; val sy = v.dh.toFloat() / v.imgH
        val fx = v.fx * sx; val fy = v.fy * sy; val cx = v.cx * sx; val cy = v.cy * sy
        val n = v.dw * v.dh
        val pts = FloatArray(n * 3); val cols = IntArray(n); val wts = FloatArray(n)
        var k = 0
        val m = v.pose
        for (r in 0 until v.dh) for (c in 0 until v.dw) {
            val mm = v.depth[r * v.dw + c].toInt() and 0xFFFF
            if (mm < 300 || mm > 6500) continue
            val confidence = v.conf[r * v.dw + c].toInt() and 0xFF
            if (confidence < 60) continue
            val z = mm / 1000f
            // In the camera's own space (OpenGL style: looking down -z), then in the world.
            val xc = (c + 0.5f - cx) / fx * z; val yc = -(r + 0.5f - cy) / fy * z; val zc = -z
            pts[k * 3] = m[0] * xc + m[4] * yc + m[8] * zc + m[12]
            pts[k * 3 + 1] = m[1] * xc + m[5] * yc + m[9] * zc + m[13]
            pts[k * 3 + 2] = m[2] * xc + m[6] * yc + m[10] * zc + m[14]
            cols[k] = colorAt(v, ((c + 0.5f) * v.iw / v.dw).toInt(), ((r + 0.5f) * v.ih / v.dh).toInt())
            wts[k] = confidence / 255f
            k++
        }
        volume.integrate(P3(m[12], m[13], m[14]), pts, cols, wts, k)
        if (wantPhoto(m)) runCatching { keepPhoto(v) }
        val views = _status.value.views + 1
        if (views % 4 == 1) preview = volume.surfacePoints(160_000)
        val area = (preview?.second?.size ?: 0) * volume.voxel * volume.voxel
        _status.value = ScanStatus(true, "Scanning — walk slowly along the house, sweeping up to the roof edge and down to the ground.", views, area, photos)
    }

    /** A photo every so often: when the phone has moved or turned enough since the last. */
    private fun wantPhoto(m: FloatArray): Boolean {
        if (photos >= MAX_PHOTOS || System.currentTimeMillis() - lastPhotoAt < 500) return false
        val last = lastPhotoPose ?: return true
        val moved = sqrt((m[12] - last[12]).let { it * it } + (m[13] - last[13]).let { it * it } + (m[14] - last[14]).let { it * it })
        val facing = m[8] * last[8] + m[9] * last[9] + m[10] * last[10]
        return moved > 0.35f || facing < 0.978f
    }

    private fun keepPhoto(v: View) {
        // The camera picture as a JPEG (NV21 is what Android's encoder takes).
        val w = v.iw; val h = v.ih
        val nv21 = ByteArray(w * h * 3 / 2)
        for (r in 0 until h) System.arraycopy(v.y, r * v.yRow, nv21, r * w, minOf(w, v.y.size - r * v.yRow))
        for (r in 0 until h / 2) for (c in 0 until w / 2) {
            val o = r * v.uvRow + c * v.uvPix
            nv21[w * h + r * w + c * 2] = v.v.getOrElse(o) { -128 }
            nv21[w * h + r * w + c * 2 + 1] = v.u.getOrElse(o) { -128 }
        }
        val i = photos
        java.io.File(photoDir, "photo$i.jpg").outputStream().use {
            android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, w, h, null).compressToJpeg(android.graphics.Rect(0, 0, w, h), 90, it)
        }
        // Where it was taken from, its lens (for this picture's size), and the trusted depths.
        val sx = w.toFloat() / v.imgW; val sy = h.toFloat() / v.imgH
        val depth = ShortArray(v.dw * v.dh) { k -> if ((v.conf[k].toInt() and 0xFF) >= 60) v.depth[k] else 0 }
        java.io.File(photoDir, "photo$i.bin").outputStream().use {
            ScanFrame(v.pose.copyOf(), v.fx * sx, v.fy * sy, v.cx * sx, v.cy * sy, w, h, v.dw, v.dh, depth).write(it)
        }
        photos++
        lastPhotoPose = v.pose.copyOf(); lastPhotoAt = System.currentTimeMillis()
    }

    private fun colorAt(v: View, x: Int, y: Int): Int {
        val ix = x.coerceIn(0, v.iw - 1); val iy = y.coerceIn(0, v.ih - 1)
        val yy = (v.y.getOrElse(iy * v.yRow + ix) { 0 }.toInt() and 0xFF).toFloat()
        val o = (iy / 2) * v.uvRow + (ix / 2) * v.uvPix
        val uu = (v.u.getOrElse(o) { -128 }.toInt() and 0xFF) - 128f
        val vv = (v.v.getOrElse(o) { -128 }.toInt() and 0xFF) - 128f
        val r = (yy + 1.402f * vv).toInt().coerceIn(0, 255)
        val g = (yy - 0.344f * uu - 0.714f * vv).toInt().coerceIn(0, 255)
        val b = (yy + 1.772f * uu).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/** The camera's view while scanning, with what's been scanned so far drawn over it. */
private const val MAX_PHOTOS = 400

class ArScanView(context: Context, private val scanner: ArScanner) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(ScanRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
        keepScreenOn = true
    }

    private inner class ScanRenderer : Renderer {
        private var cameraTex = 0
        private var bgProg = 0; private var ptProg = 0
        private var textureFor: Session? = null
        private var geometryFor: Session? = null
        private var w = 1; private var h = 1
        private val quad = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        private val quadTex = floatBuffer(FloatArray(8))
        private var shownPreview: Pair<FloatArray, IntArray>? = null
        private var ptPos: FloatBuffer? = null
        private var ptCol: ByteBuffer? = null
        private var ptCount = 0

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); cameraTex = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            bgProg = program(BG_VS, BG_FS); ptProg = program(PT_VS, PT_FS)
            textureFor = null; geometryFor = null
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            w = width; h = height; geometryFor = null
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            val session = scanner.session ?: return
            if (textureFor !== session) { session.setCameraTextureName(cameraTex); textureFor = session }
            if (geometryFor !== session) {
                @Suppress("DEPRECATION")
                val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
                session.setDisplayGeometry(rotation, w, h); geometryFor = session
            }
            val frame = try { session.update() } catch (_: Exception) { return }
            if (frame.hasDisplayGeometryChanged()) {
                quad.position(0); quadTex.position(0)
                frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, quadTex)
            }
            if (frame.timestamp == 0L) return
            // The camera picture.
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glUseProgram(bgProg)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTex)
            val aPos = GLES20.glGetAttribLocation(bgProg, "aPos"); val aTex = GLES20.glGetAttribLocation(bgProg, "aTex")
            quad.position(0); quadTex.position(0)
            GLES20.glEnableVertexAttribArray(aPos); GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glEnableVertexAttribArray(aTex); GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, quadTex)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos); GLES20.glDisableVertexAttribArray(aTex)

            scanner.onFrame(frame)
            val cam = frame.camera
            if (cam.trackingState != TrackingState.TRACKING) return

            // What's been scanned, where it really is.
            scanner.preview?.let { p ->
                if (p !== shownPreview) {
                    shownPreview = p
                    ptCount = p.second.size
                    ptPos = floatBuffer(p.first)
                    ptCol = ByteBuffer.allocateDirect(ptCount * 4).order(ByteOrder.nativeOrder()).also { b ->
                        p.second.forEach { c -> b.put(((c shr 16) and 0xFF).toByte()); b.put(((c shr 8) and 0xFF).toByte()); b.put((c and 0xFF).toByte()); b.put(0xFF.toByte()) }
                        b.position(0)
                    }
                }
            }
            val pos = ptPos ?: return
            val col = ptCol ?: return
            if (ptCount == 0) return
            val proj = FloatArray(16); val view = FloatArray(16); val mvp = FloatArray(16)
            cam.getProjectionMatrix(proj, 0, 0.1f, 100f); cam.getViewMatrix(view, 0)
            Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glUseProgram(ptProg)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(ptProg, "uMvp"), 1, false, mvp, 0)
            val pPos = GLES20.glGetAttribLocation(ptProg, "aPos"); val pCol = GLES20.glGetAttribLocation(ptProg, "aColor")
            pos.position(0); col.position(0)
            GLES20.glEnableVertexAttribArray(pPos); GLES20.glVertexAttribPointer(pPos, 3, GLES20.GL_FLOAT, false, 0, pos)
            GLES20.glEnableVertexAttribArray(pCol); GLES20.glVertexAttribPointer(pCol, 4, GLES20.GL_UNSIGNED_BYTE, true, 0, col)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, ptCount)
            GLES20.glDisableVertexAttribArray(pPos); GLES20.glDisableVertexAttribArray(pCol)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        private fun program(vs: String, fs: String): Int {
            fun shader(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
            return GLES20.glCreateProgram().also { p ->
                GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
                GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
                GLES20.glLinkProgram(p)
            }
        }
    }

    companion object {
        private fun floatBuffer(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(a).also { it.position(0) }

        private const val BG_VS = """
attribute vec4 aPos;
attribute vec2 aTex;
varying vec2 vTex;
void main() { gl_Position = aPos; vTex = aTex; }
"""
        private const val BG_FS = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uTex;
varying vec2 vTex;
void main() { gl_FragColor = texture2D(uTex, vTex); }
"""
        private const val PT_VS = """
uniform mat4 uMvp;
attribute vec3 aPos;
attribute vec4 aColor;
varying vec4 vColor;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
    vColor = aColor;
    gl_PointSize = clamp(18.0 / gl_Position.w, 2.0, 10.0);
}
"""
        private const val PT_FS = """
precision mediump float;
varying vec4 vColor;
void main() { gl_FragColor = vec4(mix(vColor.rgb, vec3(0.2, 1.0, 0.6), 0.35), 0.85); }
"""
    }
}

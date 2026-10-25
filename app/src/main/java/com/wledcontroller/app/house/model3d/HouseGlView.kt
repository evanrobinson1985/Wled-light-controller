package com.wledcontroller.app.house.model3d

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.hypot
import kotlin.math.max

/**
 * The house model in 3D with its lights glowing on it. Drag to turn round it, pinch to zoom,
 * drag with two fingers to move; a tap reports where it touched (for picking spots on the model).
 * With a [fixedCamera] it shows the house as the photo did, and doesn't move.
 */
class HouseGlView(context: Context) : GLSurfaceView(context) {
    private val renderer = Renderer()

    /** Tapped at (x, y) across the view, 0..1. */
    var onTap: ((Float, Float) -> Unit)? = null

    /**
     * Painting (the eraser): one finger paints, reporting each spot (x, y, 0..1) and then the
     * end of the stroke; two fingers turn the model round, pinch zooms.
     */
    var onPaint: ((Float, Float, Boolean) -> Unit)? = null
    /** A second finger came down mid-stroke: the stroke is dropped (the fingers are turning the model). */
    var onPaintCancel: (() -> Unit)? = null
    private var lastPaintAt = 0L

    /** Which handle is under the finger at (x, y, 0..1), or -1: dragging one moves it instead of the view. */
    var grab: ((Float, Float) -> Int)? = null
    /** A grabbed handle dragged to (x, y). */
    var onGrabMove: ((Int, Float, Float) -> Unit)? = null
    private var grabbed = -1

    @Volatile var fixedCamera: Camera? = null
        set(v) { field = v; requestRender() }

    /** While cropping: everything outside this box is shown red, as what would be cut off. */
    @Volatile var cropBox: Pair<P3, P3>? = null
        set(v) { field = v; requestRender() }

    /** How bright the house itself is: low for night-time previews, full for editing. */
    @Volatile var brightness = 0.35f
        set(v) { field = v; requestRender() }

    // The orbiting camera.
    @Volatile private var target = P3.ZERO
    @Volatile private var yaw = -0.5f
    @Volatile private var pitch = 0.25f
    @Volatile private var dist = 20f
    private var fitted: Mesh? = null

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** The model and its pictures. */
    fun setModel(mesh: Mesh, textures: List<File>) {
        if (fitted !== mesh) {
            fitted = mesh
            val (lo, hi) = mesh.bounds
            target = P3((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, (lo.z + hi.z) / 2)
            dist = max((hi - lo).length * 1.1f, 2f)
        }
        queueEvent { renderer.load(mesh, textures) }
        requestRender()
    }

    /** Where the lights are and what colour each one is (0 = off). Null positions aren't drawn. */
    fun setLights(positions: List<P3?>, colors: IntArray) {
        val n = positions.count { it != null }
        // Each light: position (3) and colour with brightness (4), as drawn (28 bytes apart).
        val buf = ByteBuffer.allocateDirect(n * LIGHT_FLOATS * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        positions.forEachIndexed { i, p ->
            if (p == null) return@forEachIndexed
            val c = colors.getOrElse(i) { 0 }
            buf.put(p.x); buf.put(p.y); buf.put(p.z)
            if (c == 0) { buf.put(0.16f); buf.put(0.17f); buf.put(0.2f); buf.put(0.45f) }
            else { buf.put(((c shr 16) and 0xFF) / 255f); buf.put(((c shr 8) and 0xFF) / 255f); buf.put((c and 0xFF) / 255f); buf.put(1f) }
        }
        buf.position(0)
        renderer.lights = buf to n
        requestRender()
    }

    fun camera(aspect: Float): Camera = fixedCamera ?: Camera.orbit(target, yaw, pitch, dist)

    // The first frame can be drawn before the screen shows the surface (it then stays blank until
    // something changes): draw again once it's shown, and shortly after.
    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {
        super.surfaceChanged(holder, format, w, h)
        redrawSoon()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        redrawSoon()
    }

    private fun redrawSoon() {
        requestRender()
        postDelayed({ requestRender() }, 120)
        postDelayed({ requestRender() }, 500)
    }

    // Gestures.
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            dist = (dist / d.scaleFactor).coerceIn(0.5f, 500f); requestRender(); return true
        }
    })
    private val tapSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop * 1.5f
    private var lastX = 0f; private var lastY = 0f; private var downX = 0f; private var downY = 0f
    private var downAt = 0L; private var multi = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scale.onTouchEvent(e)
        // The middle of the fingers still down (a finger being lifted doesn't count).
        val lifting = if (e.actionMasked == MotionEvent.ACTION_POINTER_UP) e.actionIndex else -1
        val down = (0 until e.pointerCount).filter { it != lifting }
        val cx = down.map { e.getX(it) }.average().toFloat()
        val cy = down.map { e.getY(it) }.average().toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; downAt = e.eventTime; multi = false; lastX = cx; lastY = cy; parent?.requestDisallowInterceptTouchEvent(true)
                grabbed = grab?.invoke(e.x / width, e.y / height) ?: -1
                if (grabbed >= 0) return true
                onPaint?.let { it(e.x / width, e.y / height, false); lastPaintAt = e.eventTime }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                // A second finger: not painting any more, but turning.
                if (!multi && e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) onPaintCancel?.invoke()
                grabbed = -1
                multi = true; lastX = cx; lastY = cy
            }
            MotionEvent.ACTION_MOVE -> {
                if (grabbed >= 0) {
                    if (e.pointerCount == 1) onGrabMove?.invoke(grabbed, e.x / width, e.y / height)
                    lastX = cx; lastY = cy
                    return true
                }
                val dx = cx - lastX; val dy = cy - lastY
                lastX = cx; lastY = cy
                if (fixedCamera != null) return true
                // Two fingers were down and one's been lifted: nothing moves until the other is too
                // (fingers never leave the screen at quite the same moment).
                if (multi && e.pointerCount < 2) return true
                // Still within a tap: don't turn the model yet.
                if (!multi && onPaint == null && e.pointerCount == 1 && hypot(e.x - downX, e.y - downY) < tapSlop) return true
                val paint = onPaint
                if (paint != null) {
                    if (e.pointerCount == 1 && !multi) {
                        if (e.eventTime - lastPaintAt >= 30) { paint(e.x / width, e.y / height, false); lastPaintAt = e.eventTime }
                    } else {
                        yaw -= dx / width * 3.5f
                        pitch = (pitch + dy / height * 2.5f).coerceIn(-0.2f, 1.45f)
                        requestRender()
                    }
                    return true
                }
                if (e.pointerCount >= 2) {
                    // Move sideways and up/down with two fingers.
                    val cam = camera(width.toFloat() / height)
                    val k = dist / height * 0.9f
                    target = target - cam.right * (dx * k) + P3.UP * (dy * k)
                } else {
                    yaw -= dx / width * 3.5f
                    pitch = (pitch + dy / height * 2.5f).coerceIn(-0.2f, 1.45f)
                }
                requestRender()
            }
            MotionEvent.ACTION_UP -> {
                if (grabbed >= 0) {
                    grabbed = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                if (onPaint != null) {
                    if (!multi) onPaint?.invoke(e.x / width, e.y / height, true)
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                // A tap: as Android judges one on this screen (a finger always moves a little).
                if (!multi && e.eventTime - downAt < 500 && hypot(e.x - downX, e.y - downY) < tapSlop) {
                    onTap?.invoke(e.x / width, e.y / height)
                    performClick()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private inner class Renderer : GLSurfaceView.Renderer {
        @Volatile var lights: Pair<FloatBuffer, Int>? = null
        private var meshProg = 0; private var ledProg = 0
        private var parts: List<GpuPart> = emptyList()
        private var textures = IntArray(0)
        private var loaded: Mesh? = null
        private var loadedFiles: List<File> = emptyList()
        private var pending: Pair<Mesh, List<File>>? = null
        private var w = 1; private var h = 1

        /** [mode]: 0 flat colour, 1 picture, 2 colour at each corner. */
        inner class GpuPart(val vbo: Int, val ibo: Int, val count: Int, val mode: Int, val tex: Int, val color: Int)

        fun load(mesh: Mesh, files: List<File>) { if (mesh !== loaded) pending = mesh to files }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            meshProg = program(MESH_VS, MESH_FS); ledProg = program(LED_VS, LED_FS)
            // A new context: everything goes up again.
            loaded?.let { m -> pending = pending ?: (m to loadedFiles) }
            parts = emptyList(); textures = IntArray(0); loaded = null
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { w = width; h = height; GLES30.glViewport(0, 0, width, height) }

        private fun upload(mesh: Mesh, files: List<File>) {
            if (parts.isNotEmpty()) {
                GLES30.glDeleteBuffers(parts.size * 2, parts.flatMap { listOf(it.vbo, it.ibo) }.toIntArray(), 0)
                GLES30.glDeleteTextures(textures.size, textures, 0)
            }
            textures = IntArray(files.size)
            if (files.isNotEmpty()) GLES30.glGenTextures(files.size, textures, 0)
            files.forEachIndexed { i, f ->
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return@forEachIndexed
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[i])
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
                bmp.recycle()
            }
            parts = mesh.parts.map { p ->
                val textured = p.uvs != null && p.texture in textures.indices
                val vc = p.vertexColors?.takeIf { it.size == p.vertexCount }
                // Position, picture coordinates, colour.
                val data = ByteBuffer.allocateDirect(p.vertexCount * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                for (i in 0 until p.vertexCount) {
                    data.put(p.positions, i * 3, 3)
                    if (textured) data.put(p.uvs!!, i * 2, 2) else { data.put(0f); data.put(0f) }
                    val c = vc?.get(i) ?: 0
                    data.put(((c shr 16) and 0xFF) / 255f); data.put(((c shr 8) and 0xFF) / 255f); data.put((c and 0xFF) / 255f)
                }
                data.position(0)
                val idx = ByteBuffer.allocateDirect(p.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(p.indices).also { it.position(0) }
                val ids = IntArray(2); GLES30.glGenBuffers(2, ids, 0)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.capacity() * 4, data, GLES30.GL_STATIC_DRAW)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1])
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.indices.size * 4, idx, GLES30.GL_STATIC_DRAW)
                GpuPart(ids[0], ids[1], p.indices.size, if (textured) 1 else if (vc != null) 2 else 0, if (textured) textures[p.texture] else 0, p.color)
            }
            loaded = mesh
            loadedFiles = files
        }

        override fun onDrawFrame(gl: GL10?) {
            // The model has just gone up: draw once more, so it's on the screen even if nothing else changes.
            pending?.let { (m, f) -> pending = null; upload(m, f); requestRender() }
            GLES30.glClearColor(0.043f, 0.05f, 0.07f, 1f)
            // Depth writing must be on for the clear to wipe the depth: the lights turn it off.
            GLES30.glDepthMask(true)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            val aspect = w.toFloat() / h
            val cam = camera(aspect)
            val size = loaded?.bounds?.let { (lo, hi) -> (hi - lo).length } ?: 20f
            val near = max(0.05f, (dist - size) * 0.5f).coerceAtMost(5f)
            val mvp = cam.viewProjection(aspect, if (fixedCamera != null) 0.3f else near, (dist + size) * 4f + 100f)

            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glUseProgram(meshProg)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProg, "uMvp"), 1, false, mvp, 0)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(meshProg, "uBright"), brightness)
            val crop = cropBox
            GLES30.glUniform1i(GLES30.glGetUniformLocation(meshProg, "uCrop"), if (crop != null) 1 else 0)
            if (crop != null) {
                GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProg, "uCropLo"), crop.first.x, crop.first.y, crop.first.z)
                GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProg, "uCropHi"), crop.second.x, crop.second.y, crop.second.z)
            }
            val uTex = GLES30.glGetUniformLocation(meshProg, "uMode")
            val uColor = GLES30.glGetUniformLocation(meshProg, "uColor")
            for (p in parts) {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, p.vbo)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.ibo)
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 32, 12)
                GLES30.glEnableVertexAttribArray(3)
                GLES30.glVertexAttribPointer(3, 3, GLES30.GL_FLOAT, false, 32, 20)
                if (p.mode == 1) {
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, p.tex)
                }
                GLES30.glUniform1i(uTex, p.mode)
                GLES30.glUniform3f(uColor, ((p.color shr 16) and 0xFF) / 255f, ((p.color shr 8) and 0xFF) / 255f, (p.color and 0xFF) / 255f)
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.count, GLES30.GL_UNSIGNED_INT, 0)
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glDisableVertexAttribArray(3)

            // The lights: glowing dots, added on top.
            val (buf, n) = lights ?: return
            if (n == 0) return
            GLES30.glDepthMask(false)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            GLES30.glUseProgram(ledProg)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(ledProg, "uMvp"), 1, false, mvp, 0)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(ledProg, "uEye"), cam.eye.x, cam.eye.y, cam.eye.z)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(ledProg, "uSize"), h * 0.09f)
            buf.position(0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, LIGHT_FLOATS * 4, buf)
            buf.position(3)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, LIGHT_FLOATS * 4, buf)
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, n)
            GLES30.glDisableVertexAttribArray(2)
            buf.position(0)
            GLES30.glDepthMask(true)
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        private fun program(vs: String, fs: String): Int {
            fun shader(type: Int, src: String) = GLES30.glCreateShader(type).also { GLES30.glShaderSource(it, src); GLES30.glCompileShader(it) }
            return GLES30.glCreateProgram().also { p ->
                GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs))
                GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
                GLES30.glLinkProgram(p)
            }
        }
    }

    companion object {
        private const val LIGHT_FLOATS = 7
        private const val MESH_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 3) in vec3 aColor;
uniform mat4 uMvp;
out vec2 vUv;
out vec3 vColor;
out vec3 vPos;
void main() { vUv = aUv; vColor = aColor; vPos = aPos; gl_Position = uMvp * vec4(aPos, 1.0); }
"""
        private const val MESH_FS = """#version 300 es
precision mediump float;
in vec2 vUv;
in vec3 vColor;
in vec3 vPos;
uniform int uCrop;
uniform vec3 uCropLo;
uniform vec3 uCropHi;
uniform sampler2D uTex;
uniform int uMode;
uniform vec3 uColor;
uniform float uBright;
out vec4 frag;
void main() {
    vec3 c = uMode == 1 ? texture(uTex, vUv).rgb : (uMode == 2 ? vColor : uColor);
    // What a crop would cut off: red.
    if (uCrop == 1 && (any(lessThan(vPos, uCropLo)) || any(greaterThan(vPos, uCropHi)))) c = mix(c, vec3(1.0, 0.08, 0.08), 0.75);
    frag = vec4(c * uBright, 1.0);
}
"""
        private const val LED_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 2) in vec4 aColor;
uniform mat4 uMvp;
uniform vec3 uEye;
uniform float uSize;
out vec4 vColor;
void main() {
    // A little toward the viewer, so lights on a wall aren't hidden in it.
    vec3 p = aPos + normalize(uEye - aPos) * 0.06;
    gl_Position = uMvp * vec4(p, 1.0);
    vColor = aColor;
    gl_PointSize = clamp(uSize / gl_Position.w, 3.0, 48.0) * (aColor.a < 0.9 ? 0.45 : 1.0);
}
"""
        private const val LED_FS = """#version 300 es
precision mediump float;
in vec4 vColor;
out vec4 frag;
void main() {
    float d = length(gl_PointCoord - vec2(0.5)) * 2.0;
    if (d > 1.0) discard;
    float core = smoothstep(0.42, 0.18, d);
    float glow = pow(1.0 - d, 2.0) * 0.55;
    vec3 c = vColor.rgb;
    frag = vec4(mix(c, vec3(1.0), core * 0.35) , (core + glow) * vColor.a);
}
"""
    }
}

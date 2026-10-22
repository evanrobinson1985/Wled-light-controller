package com.wledcontroller.app.house.model3d

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

/**
 * A photo taken while scanning: where the phone was ([pose], OpenGL style: the camera looks
 * down its -z), its lens in pixels, and the depth it measured (mm, 0 = unknown) to tell what
 * was hidden behind something.
 */
class ScanFrame(
    val pose: FloatArray,
    val fx: Float, val fy: Float, val cx: Float, val cy: Float,
    val width: Int, val height: Int,
    val depthW: Int, val depthH: Int, val depth: ShortArray,
) {
    val eye get() = P3(pose[12], pose[13], pose[14])

    /** Where [p] lands in the photo (pixels) and how far in front of the camera it is; null if behind. */
    fun project(p: P3): Triple<Float, Float, Float>? {
        val dx = p.x - pose[12]; val dy = p.y - pose[13]; val dz = p.z - pose[14]
        // Into the camera's space: the pose's turn, backwards.
        val x = pose[0] * dx + pose[1] * dy + pose[2] * dz
        val y = pose[4] * dx + pose[5] * dy + pose[6] * dz
        val z = -(pose[8] * dx + pose[9] * dy + pose[10] * dz)
        if (z < 0.1f) return null
        return Triple(cx + fx * x / z, cy - fy * y / z, z)
    }

    /** The depth measured toward photo pixel ([u], [v]), metres, or null. */
    fun depthAt(u: Float, v: Float): Float? {
        val x = (u * depthW / width).toInt(); val y = (v * depthH / height).toInt()
        if (x !in 0 until depthW || y !in 0 until depthH) return null
        val mm = depth[y * depthW + x].toInt() and 0xFFFF
        return if (mm == 0) null else mm / 1000f
    }

    fun write(out: OutputStream) {
        val d = DataOutputStream(out)
        pose.forEach(d::writeFloat)
        d.writeFloat(fx); d.writeFloat(fy); d.writeFloat(cx); d.writeFloat(cy)
        d.writeInt(width); d.writeInt(height); d.writeInt(depthW); d.writeInt(depthH)
        depth.forEach { d.writeShort(it.toInt()) }
        d.flush()
    }

    companion object {
        fun read(input: InputStream): ScanFrame {
            val d = DataInputStream(input.buffered())
            val pose = FloatArray(16) { d.readFloat() }
            val fx = d.readFloat(); val fy = d.readFloat(); val cx = d.readFloat(); val cy = d.readFloat()
            val w = d.readInt(); val h = d.readInt(); val dw = d.readInt(); val dh = d.readInt()
            return ScanFrame(pose, fx, fy, cx, cy, w, h, dw, dh, ShortArray(dw * dh) { d.readShort() })
        }
    }
}

/**
 * Paints a scanned model from the photos taken while scanning, much sharper than the colour
 * at each corner: every little square of the surface gets its own patch of picture, painted
 * from the photo that saw it most squarely and closest, where nothing was in the way. Squares
 * no photo saw keep the scan's own colours.
 */
object PhotoTexturer {
    /**
     * [part] is a scan's mesh (triangles in pairs, each pair a square). [image] gives a photo's
     * pixels (ARGB, row by row). Each finished page of picture goes to [onPage]; returns the
     * mesh that uses them.
     */
    fun texture(
        part: MeshPart,
        frames: List<ScanFrame>,
        image: (Int) -> IntArray?,
        pageSize: Int = 2048,
        maxPages: Int = 6,
        onPage: (Int, IntArray) -> Unit,
        onProgress: (Float) -> Unit = {},
    ): Mesh {
        val quads = part.triangleCount / 2
        // As big a patch per square as fits in the pages.
        var cell = 16
        while (cell > 4 && quads > maxPages * (pageSize / cell) * (pageSize / cell)) cell--
        val perRow = pageSize / cell
        val perPage = perRow * perRow
        val pages = (quads + perPage - 1) / perPage

        // The best photo for each square.
        val best = IntArray(quads) { -1 }
        val corners = Array(4) { P3.ZERO }
        for (q in 0 until quads) {
            quad(part, q, corners)
            val c = (corners[0] + corners[1] + corners[2] + corners[3]) * 0.25f
            val n = ((corners[2] - corners[0]) cross (corners[3] - corners[1])).normalized()
            var top = 0f
            for ((f, frame) in frames.withIndex()) {
                val toEye = frame.eye - c
                val dist = toEye.length
                val cos = abs(n dot toEye) / dist
                if (cos < 0.25f) continue
                val score = cos * cos / (dist * dist)
                if (score <= top) continue
                val (u, v, z) = frame.project(c) ?: continue
                if (u < 2f || v < 2f || u > frame.width - 3 || v > frame.height - 3) continue
                // Something nearer in the way.
                val seen = frame.depthAt(u, v)
                if (seen != null && z > seen + 0.12f) continue
                if (corners.any { p -> frame.project(p)?.let { (x, y, _) -> x < 0f || y < 0f || x > frame.width - 1 || y > frame.height - 1 } ?: true }) continue
                top = score; best[q] = f
            }
            if (q % 4096 == 0) onProgress(0.4f * q / quads)
        }

        // Each page: the squares on it, painted photo by photo.
        val parts = ArrayList<MeshPart>()
        for (p in 0 until pages) {
            val first = p * perPage; val last = minOf(quads, first + perPage)
            val px = IntArray(pageSize * pageSize)
            val byFrame = (first until last).groupBy { best[it] }
            var done = 0
            for ((f, list) in byFrame) {
                val img = if (f >= 0) image(f) else null
                val frame = if (f >= 0) frames[f] else null
                for (q in list) {
                    quad(part, q, corners)
                    val slot = q - first
                    val ox = (slot % perRow) * cell; val oy = (slot / perRow) * cell
                    val cols = quadColors(part, q)
                    for (j in 0 until cell) for (i in 0 until cell) {
                        val s = i / (cell - 1f); val t = j / (cell - 1f)
                        val point = pointAt(corners, s, t)
                        val sample = if (img != null && frame != null) frame.project(point)?.let { (u, v, _) -> bilinear(img, frame.width, frame.height, u, v) } else null
                        px[(oy + j) * pageSize + ox + i] = sample ?: colorAt(cols, s, t)
                    }
                }
                done += list.size
                onProgress(0.4f + 0.6f * (p + done.toFloat() / (last - first)) / pages)
            }
            onPage(p, px)
        }

        // The mesh: four corners of its own for each square, pointing into its patch.
        val pagePos = Array(pages) { FloatList() }; val pageUv = Array(pages) { FloatList() }; val pageIdx = Array(pages) { IntList() }
        for (q in 0 until quads) {
            val p = q / perPage; val slot = q % perPage
            val ox = (slot % perRow) * cell; val oy = (slot / perRow) * cell
            quad(part, q, corners)
            val base = pagePos[p].size / 3
            for ((k, st) in ST.withIndex()) {
                val c = corners[k]
                pagePos[p].add(c.x); pagePos[p].add(c.y); pagePos[p].add(c.z)
                pageUv[p].add((ox + 0.5f + st.first * (cell - 1)) / pageSize); pageUv[p].add((oy + 0.5f + st.second * (cell - 1)) / pageSize)
            }
            intArrayOf(0, 1, 2, 0, 2, 3).forEach { pageIdx[p].add(base + it) }
        }
        for (p in 0 until pages) parts += MeshPart(pagePos[p].toArray(), pageUv[p].toArray(), pageIdx[p].toArray(), p)
        onProgress(1f)
        return Mesh(parts)
    }

    /** Corners of square [q]: its triangles are (0, 1, 2) and (0, 2, 3). */
    private fun quad(part: MeshPart, q: Int, out: Array<P3>) {
        val a = part.indices; val t = q * 6
        out[0] = part.vertex(a[t]); out[1] = part.vertex(a[t + 1]); out[2] = part.vertex(a[t + 2])
        out[3] = part.vertex(if (a[t + 3] == a[t] && a[t + 4] == a[t + 2]) a[t + 5] else a[t + 2])
    }

    private fun quadColors(part: MeshPart, q: Int): IntArray {
        val vc = part.vertexColors ?: return IntArray(4) { part.color }
        val a = part.indices; val t = q * 6
        return intArrayOf(vc[a[t]], vc[a[t + 1]], vc[a[t + 2]], vc[a[t + 5]])
    }

    /** The point at (s, t) in the square, on the same triangle the picture will use. */
    private fun pointAt(c: Array<P3>, s: Float, t: Float): P3 =
        if (s >= t) c[0] + (c[1] - c[0]) * (s - t) + (c[2] - c[0]) * t
        else c[0] + (c[2] - c[0]) * s + (c[3] - c[0]) * (t - s)

    private fun colorAt(c: IntArray, s: Float, t: Float): Int {
        val (w0, w1, w2, w3) = if (s >= t) listOf(1 - s, s - t, t, 0f) else listOf(1 - t, 0f, s, t - s)
        fun ch(sh: Int) = ((((c[0] shr sh) and 0xFF) * w0 + ((c[1] shr sh) and 0xFF) * w1 + ((c[2] shr sh) and 0xFF) * w2 + ((c[3] shr sh) and 0xFF) * w3) + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun bilinear(img: IntArray, w: Int, h: Int, u: Float, v: Float): Int? {
        val x = u - 0.5f; val y = v - 0.5f
        val x0 = x.toInt(); val y0 = y.toInt()
        if (x < 0f || y < 0f || x0 >= w - 1 || y0 >= h - 1) return null
        val fx = x - x0; val fy = y - y0
        val a = img[y0 * w + x0]; val b = img[y0 * w + x0 + 1]; val c = img[(y0 + 1) * w + x0]; val d = img[(y0 + 1) * w + x0 + 1]
        fun ch(sh: Int): Int {
            val top = ((a shr sh) and 0xFF) * (1 - fx) + ((b shr sh) and 0xFF) * fx
            val bot = ((c shr sh) and 0xFF) * (1 - fx) + ((d shr sh) and 0xFF) * fx
            return (top * (1 - fy) + bot * fy + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private val ST = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)

    private class FloatList { var a = FloatArray(1024); var size = 0
        fun add(v: Float) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size) }
    private class IntList { var a = IntArray(1024); var size = 0
        fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size) }
}

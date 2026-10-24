package com.wledcontroller.app.house.model3d

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.roundToLong

/** A change made to a model (cropping, erasing, tidying), kept so it can be undone or redone on a fresh copy. */
@Serializable
sealed interface MeshEdit {
    /** Keep only what's inside the box [lo]..[hi]. */
    @Serializable @SerialName("crop")
    data class Crop(val lo: P3, val hi: P3) : MeshEdit

    /** Take away everything within [radius] of any of [spots] (one stroke of the eraser). */
    @Serializable @SerialName("erase")
    data class Erase(val spots: List<P3>, val radius: Float) : MeshEdit

    /**
     * Take away what's inside a loop drawn on the screen ([xs], [ys]: 0..1 across and down) as
     * seen from [camera] on a screen [aspect] wide: only what could be seen there, or [through]
     * the whole model.
     */
    @Serializable @SerialName("lasso")
    data class Lasso(val camera: Camera, val aspect: Float, val xs: List<Float>, val ys: List<Float>, val through: Boolean = false) : MeshEdit

    /** Take away loose bits: pieces smaller than [minTriangles], or every piece but the biggest. */
    @Serializable @SerialName("bits")
    data class DropBits(val minTriangles: Int = 200, val keepLargestOnly: Boolean = false) : MeshEdit

    /** The same edit for a model moved by [f] (a quarter turn and a shift keep boxes square to the axes). */
    fun moved(f: (P3) -> P3): MeshEdit = when (this) {
        is Crop -> { val a = f(lo); val b = f(hi); Crop(P3(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z)), P3(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))) }
        is Erase -> Erase(spots.map(f), radius)
        is Lasso -> {
            val e = f(camera.eye)
            Lasso(Camera(e, (f(camera.eye + camera.forward) - e).normalized(), (f(camera.eye + camera.up) - e).normalized(), camera.focal), aspect, xs, ys, through)
        }
        is DropBits -> this
    }
}

/** Carries out [MeshEdit]s on a model. */
object MeshEditor {
    fun apply(mesh: Mesh, edits: List<MeshEdit>): Mesh = edits.fold(mesh) { m, e -> apply(m, e) }

    fun apply(mesh: Mesh, e: MeshEdit): Mesh = when (e) {
        is MeshEdit.Crop -> keep(mesh) { c -> c.x in e.lo.x..e.hi.x && c.y in e.lo.y..e.hi.y && c.z in e.lo.z..e.hi.z }
        is MeshEdit.Erase -> erase(mesh, e)
        is MeshEdit.Lasso -> lasso(mesh, e)
        is MeshEdit.DropBits -> dropBits(mesh, e)
    }

    /** Only the triangles whose middle passes [test]; corners no longer used are left out. */
    fun keep(mesh: Mesh, test: (P3) -> Boolean): Mesh = keepBy(mesh) { part, t -> test(centre(part, t)) }

    private fun centre(p: MeshPart, t: Int): P3 {
        val a = p.indices[t * 3] * 3; val b = p.indices[t * 3 + 1] * 3; val c = p.indices[t * 3 + 2] * 3
        val q = p.positions
        return P3((q[a] + q[b] + q[c]) / 3f, (q[a + 1] + q[b + 1] + q[c + 1]) / 3f, (q[a + 2] + q[b + 2] + q[c + 2]) / 3f)
    }

    private fun keepBy(mesh: Mesh, keep: (MeshPart, Int) -> Boolean): Mesh = Mesh(mesh.parts.mapIndexedNotNull { _, p ->
        val remap = IntArray(p.vertexCount) { -1 }
        val idx = ArrayList<Int>(p.indices.size)
        var n = 0
        for (t in 0 until p.triangleCount) {
            if (!keep(p, t)) continue
            for (j in 0 until 3) {
                val v = p.indices[t * 3 + j]
                if (remap[v] < 0) remap[v] = n++
                idx += remap[v]
            }
        }
        if (idx.isEmpty()) return@mapIndexedNotNull null
        val pos = FloatArray(n * 3); val uv = p.uvs?.let { FloatArray(n * 2) }; val vc = p.vertexColors?.let { IntArray(n) }
        for (v in 0 until p.vertexCount) {
            val k = remap[v]; if (k < 0) continue
            pos[k * 3] = p.positions[v * 3]; pos[k * 3 + 1] = p.positions[v * 3 + 1]; pos[k * 3 + 2] = p.positions[v * 3 + 2]
            if (uv != null) { uv[k * 2] = p.uvs!![v * 2]; uv[k * 2 + 1] = p.uvs[v * 2 + 1] }
            if (vc != null) vc[k] = p.vertexColors!![v]
        }
        MeshPart(pos, uv, idx.toIntArray(), p.texture, p.color, vc)
    })

    private fun erase(mesh: Mesh, e: MeshEdit.Erase): Mesh {
        if (e.spots.isEmpty()) return mesh
        // The spots sorted into cells the eraser's size, so each triangle checks only those near it.
        val r = e.radius.coerceAtLeast(1e-3f)
        val cells = HashMap<Long, MutableList<P3>>()
        fun cell(x: Float) = floor(x / r).toInt()
        fun key(x: Int, y: Int, z: Int) = ((x.toLong() and 0x1FFFFF) shl 42) or ((y.toLong() and 0x1FFFFF) shl 21) or (z.toLong() and 0x1FFFFF)
        for (s in e.spots) cells.getOrPut(key(cell(s.x), cell(s.y), cell(s.z))) { ArrayList() } += s
        val r2 = r * r
        return keep(mesh) { c ->
            val cx = cell(c.x); val cy = cell(c.y); val cz = cell(c.z)
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                val list = cells[key(cx + dx, cy + dy, cz + dz)] ?: continue
                for (s in list) {
                    val ex = c.x - s.x; val ey = c.y - s.y; val ez = c.z - s.z
                    if (ex * ex + ey * ey + ez * ez <= r2) return@keep false
                }
            }
            true
        }
    }

    private fun lasso(mesh: Mesh, e: MeshEdit.Lasso): Mesh {
        val n = minOf(e.xs.size, e.ys.size)
        if (n < 3) return mesh
        fun inside(x: Float, y: Float): Boolean {
            var odd = false
            var j = n - 1
            for (i in 0 until n) {
                val yi = e.ys[i]; val yj = e.ys[j]
                if ((yi > y) != (yj > y) && x < (e.xs[j] - e.xs[i]) * (y - yi) / (yj - yi) + e.xs[i]) odd = !odd
                j = i
            }
            return odd
        }
        val cam = e.camera
        // What can be seen: the nearest surface, across a grid over the screen.
        val gw = 320; val gh = (320 / e.aspect).toInt().coerceIn(64, 640)
        val nearest = if (e.through) null else FloatArray(gw * gh) { Float.MAX_VALUE }.also { z ->
            for (p in mesh.parts) for (t in 0 until p.triangleCount) {
                val q = (0 until 3).map { j -> cam.project(p.vertex(p.indices[t * 3 + j]), e.aspect) }
                if (q.any { it == null }) continue
                val x0 = q.minOf { it!!.first }; val x1 = q.maxOf { it!!.first }
                val y0 = q.minOf { it!!.second }; val y1 = q.maxOf { it!!.second }
                val d = q.minOf { it!!.third }
                if (x1 < 0f || y1 < 0f || x0 > 1f || y0 > 1f) continue
                val cx0 = (x0 * gw).toInt().coerceIn(0, gw - 1); val cx1 = (x1 * gw).toInt().coerceIn(0, gw - 1)
                val cy0 = (y0 * gh).toInt().coerceIn(0, gh - 1); val cy1 = (y1 * gh).toInt().coerceIn(0, gh - 1)
                for (cy in cy0..cy1) for (cx in cx0..cx1) { val k = cy * gw + cx; if (d < z[k]) z[k] = d }
            }
        }
        return keep(mesh) { c ->
            val (x, y, depth) = cam.project(c, e.aspect) ?: return@keep true
            if (x !in 0f..1f || y !in 0f..1f || !inside(x, y)) return@keep true
            if (nearest == null) return@keep false
            val front = nearest[(y * gh).toInt().coerceIn(0, gh - 1) * gw + (x * gw).toInt().coerceIn(0, gw - 1)]
            // Kept when it's well behind what can be seen there.
            depth > front + 0.15f + front * 0.02f
        }
    }

    /**
     * Pieces of the model that don't touch the rest. Corners in the same place count as joined
     * (a model painted from photos has corners of its own for every square).
     */
    private fun dropBits(mesh: Mesh, e: MeshEdit.DropBits): Mesh {
        val ids = HashMap<Long, Int>()
        val vertexId = mesh.parts.map { p ->
            IntArray(p.vertexCount) { v ->
                fun q(x: Float) = (x * 1000f).roundToLong() and 0x1FFFFF
                val k = (q(p.positions[v * 3]) shl 42) or (q(p.positions[v * 3 + 1]) shl 21) or q(p.positions[v * 3 + 2])
                ids.getOrPut(k) { ids.size }
            }
        }
        val parent = IntArray(ids.size) { it }
        fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun join(a: Int, b: Int) { val ra = find(a); val rb = find(b); if (ra != rb) parent[rb] = ra }
        mesh.parts.forEachIndexed { k, p ->
            val vid = vertexId[k]
            for (t in 0 until p.triangleCount) { join(vid[p.indices[t * 3]], vid[p.indices[t * 3 + 1]]); join(vid[p.indices[t * 3]], vid[p.indices[t * 3 + 2]]) }
        }
        val size = HashMap<Int, Int>()
        mesh.parts.forEachIndexed { k, p -> for (t in 0 until p.triangleCount) size.merge(find(vertexId[k][p.indices[t * 3]]), 1, Int::plus) }
        val biggest = size.maxByOrNull { it.value }?.key
        val partIndex = mesh.parts.withIndex().associate { (i, p) -> p to i }
        return keepBy(mesh) { p, t ->
            val root = find(vertexId[partIndex.getValue(p)][p.indices[t * 3]])
            if (e.keepLargestOnly) root == biggest else (size[root] ?: 0) >= e.minTriangles
        }
    }
}

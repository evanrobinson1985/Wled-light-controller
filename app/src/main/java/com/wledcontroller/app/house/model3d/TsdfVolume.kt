package com.wledcontroller.app.house.model3d

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Builds a solid, coloured surface from many depth measurements, as 3D scanners do: space is
 * cut into [voxel]-sized cubes, and each measurement says how far each cube near it is in front
 * of or behind the surface. Averaged over many views the noise cancels out, and the surface is
 * where that distance crosses zero. Only cubes near a surface are kept, in blocks.
 */
class TsdfVolume(val voxel: Float = 0.05f, val truncation: Float = 0.15f) {
    /**
     * One 8 × 8 × 8 block of cubes, packed small so big scans fit in memory: the distance and
     * how much it's trusted as fixed-point numbers, and each colour with how many views gave it
     * in its top byte.
     */
    private class Block {
        val sdfQ = ShortArray(N3) { Short.MAX_VALUE }
        val weightQ = ShortArray(N3)
        val rgbc = IntArray(N3)
        fun sdf(k: Int) = sdfQ[k] / 32767f
        fun weight(k: Int) = (weightQ[k].toInt() and 0xFFFF) / WEIGHT_STEPS
        fun count(k: Int) = rgbc[k] ushr 24
        fun rgb(k: Int) = rgbc[k] and 0xFFFFFF
        fun set(k: Int, sdf: Float, weight: Float) {
            sdfQ[k] = (sdf.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            weightQ[k] = (weight * WEIGHT_STEPS).toInt().coerceIn(0, 0xFFFF).toShort()
        }
    }

    private val blocks = HashMap<Long, Block>()
    val blockCount get() = synchronized(this) { blocks.size }

    /**
     * The most blocks to keep (so a big scan can't run the phone out of memory). Once there are
     * this many, places not seen yet are left out; [full] says so.
     */
    @Volatile var maxBlocks = Int.MAX_VALUE
    val full get() = synchronized(this) { blocks.size >= maxBlocks }

    /** Measured points seen from [origin]: [points] x, y, z each; [colors] RGB; [weights] how much to trust each (0..1). */
    @Synchronized
    fun integrate(origin: P3, points: FloatArray, colors: IntArray, weights: FloatArray, count: Int) {
        val step = voxel * 0.7f
        for (i in 0 until count) {
            val px = points[i * 3]; val py = points[i * 3 + 1]; val pz = points[i * 3 + 2]
            val dx = px - origin.x; val dy = py - origin.y; val dz = pz - origin.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist < 1e-3f) continue
            val ux = dx / dist; val uy = dy / dist; val uz = dz / dist
            val w = weights[i] * (1f / (1f + dist * dist * 0.08f))
            val c = colors[i]
            var t = dist - truncation
            while (t <= dist + truncation) {
                val x = origin.x + ux * t; val y = origin.y + uy * t; val z = origin.z + uz * t
                val ix = floor(x / voxel).toInt(); val iy = floor(y / voxel).toInt(); val iz = floor(z / voxel).toInt()
                val b = block(ix, iy, iz, create = blocks.size < maxBlocks)
                if (b == null) { t += step; continue }
                val k = local(ix, iy, iz)
                val s = ((dist - t) / truncation).coerceIn(-1f, 1f)
                val W = b.weight(k)
                b.set(k, (b.sdf(k) * W + s * w) / (W + w), minOf(W + w, MAX_WEIGHT))
                if (abs(dist - t) < voxel * 1.5f) blendColor(b, k, c)
                t += step
            }
        }
    }

    private fun blendColor(b: Block, k: Int, c: Int) {
        val n = b.count(k)
        if (n == 0) { b.rgbc[k] = (1 shl 24) or (c and 0xFFFFFF); return }
        val m = minOf(n, 30)
        val o = b.rgb(k)
        fun mix(sh: Int) = ((((o shr sh) and 0xFF) * m + ((c shr sh) and 0xFF)) / (m + 1)) shl sh
        b.rgbc[k] = (minOf(n + 1, 255) shl 24) or mix(16) or mix(8) or mix(0)
    }

    // The last block looked up: most lookups are next to the one before.
    private var lastKey = Long.MIN_VALUE
    private var lastBlock: Block? = null

    private fun block(ix: Int, iy: Int, iz: Int, create: Boolean): Block? {
        val key = key(ix shr SHIFT, iy shr SHIFT, iz shr SHIFT)
        if (key == lastKey && lastBlock != null) return lastBlock
        val b = blocks[key] ?: if (create) Block().also { blocks[key] = it } else null
        if (b != null) { lastKey = key; lastBlock = b }
        return b
    }

    private fun local(ix: Int, iy: Int, iz: Int) = ((iz and MASK) * N + (iy and MASK)) * N + (ix and MASK)

    /** The averaged distance at a cube, or NaN if it's never been seen well enough. */
    private fun sdfAt(ix: Int, iy: Int, iz: Int): Float {
        val b = block(ix, iy, iz, false) ?: return Float.NaN
        val k = local(ix, iy, iz)
        return if (b.weight(k) >= MIN_WEIGHT) b.sdf(k) else Float.NaN
    }

    private fun colorAt(ix: Int, iy: Int, iz: Int): Int? {
        val b = block(ix, iy, iz, false) ?: return null
        val k = local(ix, iy, iz)
        return if (b.count(k) != 0) b.rgb(k) else null
    }

    /** How many cubes are on the surface (as of the last [surfacePoints]). */
    @Volatile var surfaceCount = 0
        private set

    /**
     * Points on the surface so far, for showing progress: x, y, z each, and their colours — or
     * with [quality], how well each is scanned: red thin, yellow fair, green solid. A big scan
     * has more than [max]: every so many is shown, spread over all of it (not just the first).
     */
    @Synchronized
    fun surfacePoints(max: Int, quality: Boolean = false): Pair<FloatArray, IntArray> {
        val pts = FloatArray(max * 3); val cols = IntArray(max)
        // Every stride-th, from how many there were last time (it grows slowly between calls).
        val stride = maxOf(1, (surfaceCount + max - 1) / max)
        var n = 0; var seen = 0
        for ((key, b) in blocks) {
            val bx = unpack(key, 42) shl SHIFT; val by = unpack(key, 21) shl SHIFT; val bz = unpack(key, 0) shl SHIFT
            for (k in 0 until N3) {
                if (b.weight(k) < MIN_WEIGHT || abs(b.sdf(k)) > 0.25f || b.count(k) == 0) continue
                seen++
                if (seen % stride != 0 || n >= max) continue
                val x = k % N; val y = (k / N) % N; val z = k / (N * N)
                pts[n * 3] = (bx + x + 0.5f) * voxel; pts[n * 3 + 1] = (by + y + 0.5f) * voxel; pts[n * 3 + 2] = (bz + z + 0.5f) * voxel
                cols[n] = if (!quality) b.rgb(k) else when {
                    b.weight(k) < 5f -> 0xFFFF3B30.toInt()
                    b.weight(k) < 20f -> 0xFFFFCC00.toInt()
                    else -> 0xFF34C759.toInt()
                }
                n++
            }
        }
        surfaceCount = seen
        return pts.copyOf(n * 3) to cols.copyOf(n)
    }

    /**
     * The surface as a coloured mesh ("surface nets": a corner inside each cube the surface passes
     * through, joined across every edge it crosses). Bits smaller than [minPiece] triangles —
     * stray noise — are left out.
     */
    @Synchronized
    fun extract(minPiece: Int = 40): Mesh {
        val vertexOf = LongIntMap(blocks.size * 16)
        val pos = FloatList(); val cols = IntList()
        val corner = FloatArray(8)
        // A corner for each cube the surface passes through.
        for ((key, _) in blocks) {
            val bx = unpack(key, 42) shl SHIFT; val by = unpack(key, 21) shl SHIFT; val bz = unpack(key, 0) shl SHIFT
            for (z in 0 until N) for (y in 0 until N) for (x in 0 until N) {
                val ix = bx + x; val iy = by + y; val iz = bz + z
                var inside = 0; var ok = true
                for (c in 0 until 8) {
                    val v = sdfAt(ix + (c and 1), iy + ((c shr 1) and 1), iz + ((c shr 2) and 1))
                    if (v.isNaN()) { ok = false; break }
                    corner[c] = v
                    if (v < 0f) inside++
                }
                if (!ok || inside == 0 || inside == 8) continue
                // The average of where the surface crosses the cube's edges.
                var sx = 0f; var sy = 0f; var sz = 0f; var m = 0
                for ((a, b) in EDGES) {
                    val va = corner[a]; val vb = corner[b]
                    if ((va < 0f) == (vb < 0f)) continue
                    val f = va / (va - vb)
                    sx += (a and 1) + (((b and 1) - (a and 1)) * f)
                    sy += ((a shr 1) and 1) + ((((b shr 1) and 1) - ((a shr 1) and 1)) * f)
                    sz += ((a shr 2) and 1) + ((((b shr 2) and 1) - ((a shr 2) and 1)) * f)
                    m++
                }
                vertexOf[key(ix, iy, iz)] = pos.size / 3
                pos.add((ix + 0.5f + sx / m) * voxel); pos.add((iy + 0.5f + sy / m) * voxel); pos.add((iz + 0.5f + sz / m) * voxel)
                cols.add(cubeColor(ix, iy, iz))
            }
        }
        // Join them across each edge the surface crosses.
        val idx = IntList()
        for ((key, _) in blocks) {
            val bx = unpack(key, 42) shl SHIFT; val by = unpack(key, 21) shl SHIFT; val bz = unpack(key, 0) shl SHIFT
            for (z in 0 until N) for (y in 0 until N) for (x in 0 until N) {
                val ix = bx + x; val iy = by + y; val iz = bz + z
                val v0 = sdfAt(ix, iy, iz)
                if (v0.isNaN()) continue
                for (axis in 0 until 3) {
                    val v1 = when (axis) { 0 -> sdfAt(ix + 1, iy, iz); 1 -> sdfAt(ix, iy + 1, iz); else -> sdfAt(ix, iy, iz + 1) }
                    if (v1.isNaN() || (v0 < 0f) == (v1 < 0f)) continue
                    // The four cubes round this edge.
                    val q = when (axis) {
                        0 -> listOf(key(ix, iy - 1, iz - 1), key(ix, iy, iz - 1), key(ix, iy, iz), key(ix, iy - 1, iz))
                        1 -> listOf(key(ix - 1, iy, iz - 1), key(ix, iy, iz - 1), key(ix, iy, iz), key(ix - 1, iy, iz))
                        else -> listOf(key(ix - 1, iy - 1, iz), key(ix, iy - 1, iz), key(ix, iy, iz), key(ix - 1, iy, iz))
                    }.map { vertexOf[it] }
                    if (q.any { it < 0 }) continue
                    idx.add(q[0]); idx.add(q[1]); idx.add(q[2])
                    idx.add(q[0]); idx.add(q[2]); idx.add(q[3])
                }
            }
        }
        return Mesh(listOf(dropSpecks(pos.toArray(), cols.toArray(), idx.toArray(), minPiece)))
    }

    private fun cubeColor(ix: Int, iy: Int, iz: Int): Int {
        var r = 0; var g = 0; var b = 0; var n = 0
        for (c in 0 until 8) {
            val col = colorAt(ix + (c and 1), iy + ((c shr 1) and 1), iz + ((c shr 2) and 1)) ?: continue
            r += (col shr 16) and 0xFF; g += (col shr 8) and 0xFF; b += col and 0xFF; n++
        }
        return if (n == 0) 0xFF808080.toInt() else (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /** Leaves out loose bits of fewer than [min] triangles. */
    private fun dropSpecks(pos: FloatArray, cols: IntArray, idx: IntArray, min: Int): MeshPart {
        val nv = pos.size / 3
        val parent = IntArray(nv) { it }
        fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        for (t in 0 until idx.size / 3) {
            val a = find(idx[t * 3]); val b = find(idx[t * 3 + 1]); val c = find(idx[t * 3 + 2])
            parent[b] = a; parent[find(c)] = find(a)
        }
        val size = HashMap<Int, Int>()
        for (t in 0 until idx.size / 3) size.merge(find(idx[t * 3]), 1, Int::plus)
        val keepTri = BooleanArray(idx.size / 3) { (size[find(idx[it * 3])] ?: 0) >= min }
        val remap = IntArray(nv) { -1 }
        val p = FloatList(); val c = IntList(); val out = IntList()
        for (t in keepTri.indices) {
            if (!keepTri[t]) continue
            for (j in 0 until 3) {
                val v = idx[t * 3 + j]
                if (remap[v] < 0) { remap[v] = p.size / 3; p.add(pos[v * 3]); p.add(pos[v * 3 + 1]); p.add(pos[v * 3 + 2]); c.add(cols[v]) }
                out.add(remap[v])
            }
        }
        return MeshPart(p.toArray(), null, out.toArray(), -1, 0xFF808080.toInt(), c.toArray())
    }

    private class FloatList { var a = FloatArray(1024); var size = 0
        fun add(v: Float) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size) }
    private class IntList { var a = IntArray(1024); var size = 0
        fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size) }

    companion object {
        private const val SHIFT = 3
        private const val N = 1 shl SHIFT
        private const val N3 = N * N * N
        private const val MASK = N - 1
        private const val MAX_WEIGHT = 60f
        private const val MIN_WEIGHT = 0.4f
        /** Trust is kept to a thousandth (up to [MAX_WEIGHT], within 16 bits). */
        private const val WEIGHT_STEPS = 1000f
        /** About how many bytes each block takes in memory. */
        const val BLOCK_BYTES = N3 * 8 + 200
        private val EDGES = listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 0 to 2, 1 to 3, 4 to 6, 5 to 7, 0 to 4, 1 to 5, 2 to 6, 3 to 7)

        private fun key(x: Int, y: Int, z: Int): Long =
            ((x.toLong() and 0x1FFFFF) shl 42) or ((y.toLong() and 0x1FFFFF) shl 21) or (z.toLong() and 0x1FFFFF)

        /** One coordinate back out of a key (sign restored). */
        private fun unpack(key: Long, shift: Int): Int = (((key shr shift) and 0x1FFFFF).toInt() shl 11) shr 11
    }
}

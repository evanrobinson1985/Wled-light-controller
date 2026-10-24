package com.wledcontroller.app.house.model3d

/**
 * Tidying a scan after it's made: small holes filled and the surface gently smoothed, keeping
 * its shape (and its triangles in pairs, as painting from photos needs).
 */
object MeshTidy {
    fun tidy(part: MeshPart, maxHoleEdges: Int = 60, smoothing: Int = 2): MeshPart = smooth(fillHoles(part, maxHoleEdges), smoothing)

    /** Holes whose edge is at most [maxEdges] long are closed with a fan from their middle. */
    fun fillHoles(p: MeshPart, maxEdges: Int): MeshPart {
        // Edges used by only one triangle are a hole's (or the scan's outer) edge.
        val count = HashMap<Long, Int>()
        fun key(a: Int, b: Int) = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()
        val tris = p.triangleCount
        for (t in 0 until tris) for (j in 0 until 3) {
            val a = p.indices[t * 3 + j]; val b = p.indices[t * 3 + (j + 1) % 3]
            if (a != b) count.merge(key(a, b), 1, Int::plus)
        }
        // Each edge on a boundary, from one corner to the next.
        val next = HashMap<Int, Int>()
        for (t in 0 until tris) for (j in 0 until 3) {
            val a = p.indices[t * 3 + j]; val b = p.indices[t * 3 + (j + 1) % 3]
            if (a != b && count[key(a, b)] == 1) next[b] = a
        }
        val pos = p.positions.toMutableList()
        val cols = p.vertexColors?.toMutableList()
        val idx = p.indices.toMutableList()
        val used = HashSet<Int>()
        for (start in next.keys.toList()) {
            if (start in used) continue
            val loop = ArrayList<Int>()
            var v = start
            while (loop.size <= maxEdges) {
                loop += v
                v = next[v] ?: break
                if (v == start) break
            }
            loop.forEach { used += it }
            if (v != start || loop.size < 3 || loop.size > maxEdges) continue
            // A new corner in the middle, coloured like the edge round it.
            var cx = 0f; var cy = 0f; var cz = 0f; var r = 0; var g = 0; var b = 0
            for (i in loop) {
                cx += p.positions[i * 3]; cy += p.positions[i * 3 + 1]; cz += p.positions[i * 3 + 2]
                p.vertexColors?.get(i)?.let { c -> r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF }
            }
            val m = pos.size / 3
            pos += cx / loop.size; pos += cy / loop.size; pos += cz / loop.size
            cols?.add((0xFF shl 24) or ((r / loop.size) shl 16) or ((g / loop.size) shl 8) or (b / loop.size))
            for (i in loop.indices) {
                val a = loop[i]; val c = loop[(i + 1) % loop.size]
                // In pairs: the second of each pair has no area.
                idx += listOf(m, a, c, m, c, c)
            }
        }
        if (pos.size == p.positions.size) return p
        return MeshPart(pos.toFloatArray(), null, idx.toIntArray(), p.texture, p.color, cols?.toIntArray())
    }

    /** Smooths bumps without shrinking the whole (Taubin: a step in, then a step back out). */
    fun smooth(p: MeshPart, iterations: Int): MeshPart {
        if (iterations <= 0) return p
        val nv = p.vertexCount
        val neighbours = Array(nv) { HashSet<Int>(8) }
        for (t in 0 until p.triangleCount) for (j in 0 until 3) {
            val a = p.indices[t * 3 + j]; val b = p.indices[t * 3 + (j + 1) % 3]
            if (a != b) { neighbours[a] += b; neighbours[b] += a }
        }
        var pos = p.positions.copyOf()
        fun step(f: Float) {
            val out = pos.copyOf()
            for (v in 0 until nv) {
                val nb = neighbours[v]
                if (nb.isEmpty()) continue
                var x = 0f; var y = 0f; var z = 0f
                for (u in nb) { x += pos[u * 3]; y += pos[u * 3 + 1]; z += pos[u * 3 + 2] }
                x /= nb.size; y /= nb.size; z /= nb.size
                out[v * 3] = pos[v * 3] + f * (x - pos[v * 3])
                out[v * 3 + 1] = pos[v * 3 + 1] + f * (y - pos[v * 3 + 1])
                out[v * 3 + 2] = pos[v * 3 + 2] + f * (z - pos[v * 3 + 2])
            }
            pos = out
        }
        repeat(iterations) { step(0.5f); step(-0.53f) }
        return MeshPart(pos, p.uvs, p.indices, p.texture, p.color, p.vertexColors)
    }
}

package com.wledcontroller.app.house.model3d

/**
 * Tidying a scan after it's made: small holes filled and the surface gently smoothed, keeping
 * its shape (and its triangles in pairs, as painting from photos needs). Kept to plain arrays:
 * a big scan's mesh has millions of corners.
 */
object MeshTidy {
    fun tidy(part: MeshPart, maxHoleEdges: Int = 60, smoothing: Int = 2): MeshPart = smooth(fillHoles(part, maxHoleEdges), smoothing)

    /** Holes whose edge is at most [maxEdges] long are closed with a fan from their middle. */
    fun fillHoles(p: MeshPart, maxEdges: Int): MeshPart {
        // Edges used by only one triangle are a hole's (or the scan's outer) edge: each edge is
        // toggled in and out, and those used once are left.
        val open = LongToggleSet(4096)
        fun key(a: Int, b: Int) = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()
        val tris = p.triangleCount
        for (t in 0 until tris) for (j in 0 until 3) {
            val a = p.indices[t * 3 + j]; val b = p.indices[t * 3 + (j + 1) % 3]
            if (a != b) open.toggle(key(a, b))
        }
        if (open.size == 0) return p
        // Each corner on an edge and the (usually two) corners it joins there.
        val links = HashMap<Int, IntArray>(open.size)
        fun link(a: Int, b: Int) {
            val l = links.getOrPut(a) { intArrayOf(-1, -1) }
            if (l[0] < 0) l[0] = b else if (l[1] < 0) l[1] = b
        }
        open.forEach { k -> val a = (k ushr 32).toInt(); val b = k.toInt(); link(a, b); link(b, a) }

        val pos = Floats(p.positions)
        val cols = p.vertexColors?.let { Ints(it) }
        val idx = Ints(p.indices)
        val used = HashSet<Int>()
        val loop = ArrayList<Int>()
        for (start in links.keys.toList()) {
            if (start in used) continue
            // Round the hole: on to whichever neighbour we didn't just come from.
            loop.clear()
            var prev = -1; var v = start
            var closed = false
            while (loop.size <= maxEdges) {
                loop += v
                val l = links[v] ?: break
                val nxt = if (l[0] != prev) l[0] else l[1]
                if (nxt < 0) break
                prev = v; v = nxt
                if (v == start) { closed = true; break }
            }
            loop.forEach { used += it }
            if (!closed || loop.size < 3 || loop.size > maxEdges) continue
            // A new corner in the middle, coloured like the edge round it.
            var cx = 0f; var cy = 0f; var cz = 0f; var r = 0; var g = 0; var b = 0
            for (i in loop) {
                cx += p.positions[i * 3]; cy += p.positions[i * 3 + 1]; cz += p.positions[i * 3 + 2]
                p.vertexColors?.get(i)?.let { c -> r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF }
            }
            val m = pos.size / 3
            pos.add(cx / loop.size); pos.add(cy / loop.size); pos.add(cz / loop.size)
            cols?.add((0xFF shl 24) or ((r / loop.size) shl 16) or ((g / loop.size) shl 8) or (b / loop.size))
            for (i in loop.indices) {
                val a = loop[i]; val c = loop[(i + 1) % loop.size]
                // In pairs: the second of each pair has no area.
                idx.add(m); idx.add(a); idx.add(c); idx.add(m); idx.add(c); idx.add(c)
            }
        }
        if (pos.size == p.positions.size) return p
        return MeshPart(pos.toArray(), null, idx.toArray(), p.texture, p.color, cols?.toArray())
    }

    /** Smooths bumps without shrinking the whole (Taubin: a step in, then a step back out). */
    fun smooth(p: MeshPart, iterations: Int): MeshPart {
        if (iterations <= 0) return p
        val nv = p.vertexCount
        var pos = p.positions.copyOf()
        val sum = FloatArray(nv * 3)
        val count = IntArray(nv)
        // Each corner moves toward the middle of the corners it shares an edge with (counted once
        // for each triangle it's in, which weighs them alike).
        fun step(f: Float) {
            sum.fill(0f); count.fill(0)
            for (t in 0 until p.triangleCount) for (j in 0 until 3) {
                val a = p.indices[t * 3 + j]; val b = p.indices[t * 3 + (j + 1) % 3]
                if (a == b) continue
                sum[a * 3] += pos[b * 3]; sum[a * 3 + 1] += pos[b * 3 + 1]; sum[a * 3 + 2] += pos[b * 3 + 2]; count[a]++
                sum[b * 3] += pos[a * 3]; sum[b * 3 + 1] += pos[a * 3 + 1]; sum[b * 3 + 2] += pos[a * 3 + 2]; count[b]++
            }
            val out = pos.copyOf()
            for (v in 0 until nv) {
                val n = count[v]
                if (n == 0) continue
                for (d in 0 until 3) out[v * 3 + d] = pos[v * 3 + d] + f * (sum[v * 3 + d] / n - pos[v * 3 + d])
            }
            pos = out
        }
        repeat(iterations) { step(0.5f); step(-0.53f) }
        return MeshPart(pos, p.uvs, p.indices, p.texture, p.color, p.vertexColors)
    }

    private class Floats(start: FloatArray) {
        var a = start.copyOf(start.size + 1024); var size = start.size
        fun add(v: Float) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size)
    }
    private class Ints(start: IntArray) {
        var a = start.copyOf(start.size + 1024); var size = start.size
        fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size)
    }
}

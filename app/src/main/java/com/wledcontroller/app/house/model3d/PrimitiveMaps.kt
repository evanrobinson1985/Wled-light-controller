package com.wledcontroller.app.house.model3d

/**
 * Lookup tables keyed by a Long, kept in plain arrays: a scan's mesh has millions of corners
 * and edges, and a HashMap's boxed entries for each would run the phone out of memory.
 */
internal class LongIntMap(expected: Int = 1024) {
    private var keys = LongArray(capacityFor(expected)) { EMPTY }
    private var values = IntArray(keys.size)
    var size = 0
        private set

    operator fun get(key: Long): Int {
        var i = slot(key, keys.size)
        while (true) {
            val k = keys[i]
            if (k == key) return values[i]
            if (k == EMPTY) return -1
            i = (i + 1) and (keys.size - 1)
        }
    }

    operator fun set(key: Long, value: Int) {
        if ((size + 1) * 2 > keys.size) grow()
        var i = slot(key, keys.size)
        while (true) {
            val k = keys[i]
            if (k == key) { values[i] = value; return }
            if (k == EMPTY) { keys[i] = key; values[i] = value; size++; return }
            i = (i + 1) and (keys.size - 1)
        }
    }

    private fun grow() {
        val oldK = keys; val oldV = values
        keys = LongArray(oldK.size * 2) { EMPTY }; values = IntArray(keys.size); size = 0
        for (j in oldK.indices) if (oldK[j] != EMPTY) set(oldK[j], oldV[j])
    }
}

/**
 * A set of Longs where adding one that's already there takes it out again: whatever's left
 * was added an odd number of times (e.g. the edges used by only one triangle).
 */
internal class LongToggleSet(expected: Int = 1024) {
    private var keys = LongArray(capacityFor(expected)) { EMPTY }
    var size = 0
        private set

    fun toggle(key: Long) {
        if ((size + 1) * 2 > keys.size) grow()
        var i = slot(key, keys.size)
        while (true) {
            val k = keys[i]
            if (k == key) { removeAt(i); return }
            if (k == EMPTY) { keys[i] = key; size++; return }
            i = (i + 1) and (keys.size - 1)
        }
    }

    fun forEach(f: (Long) -> Unit) { for (k in keys) if (k != EMPTY) f(k) }

    // Linear probing: close the gap by moving later entries of the same run back.
    private fun removeAt(at: Int) {
        val mask = keys.size - 1
        var gap = at
        var i = (at + 1) and mask
        while (keys[i] != EMPTY) {
            val home = slot(keys[i], keys.size)
            // Move it back if its home isn't between the gap and where it is now.
            val between = if (gap <= i) home in (gap + 1)..i else home > gap || home <= i
            if (!between) { keys[gap] = keys[i]; gap = i }
            i = (i + 1) and mask
        }
        keys[gap] = EMPTY
        size--
    }

    private fun grow() {
        val old = keys
        keys = LongArray(old.size * 2) { EMPTY }; size = 0
        for (k in old) if (k != EMPTY) toggle(k)
    }
}

private const val EMPTY = Long.MIN_VALUE

private fun capacityFor(n: Int): Int {
    var c = 16
    while (c < n * 2) c = c shl 1
    return c
}

private fun slot(key: Long, size: Int): Int {
    val h = key * -0x61c8864680b583ebL
    return (h ushr 32).toInt() xor h.toInt() and (size - 1)
}

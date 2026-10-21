package com.wledcontroller.app.show

import kotlinx.serialization.Serializable

/** Something timed on the song: a lyric line, a word or a mouth shape. */
@Serializable
data class TimedLabel(val startMs: Long, val endMs: Long, val label: String)

/**
 * Mouth shapes for singing faces (the Preston Blair set xLights uses), and which of a face
 * prop's three mouths shows each.
 */
enum class Phoneme(val mouth: Mouth) {
    AI(Mouth.WIDE), E(Mouth.OPEN), O(Mouth.WIDE), U(Mouth.OPEN), WQ(Mouth.OPEN),
    MBP(Mouth.CLOSED), FV(Mouth.OPEN), L(Mouth.OPEN), ETC(Mouth.OPEN), REST(Mouth.CLOSED);

    enum class Mouth { CLOSED, OPEN, WIDE }

    companion object {
        fun parse(s: String): Phoneme? = when (s.trim().uppercase()) {
            "AI" -> AI; "E" -> E; "O" -> O; "U" -> U; "WQ" -> WQ; "MBP" -> MBP; "FV" -> FV; "L" -> L
            "ETC" -> ETC; "REST", "" -> REST
            else -> null
        }
    }
}

/**
 * A song's words timed like xLights' lyric track: lines (phrases), the words in them and the
 * mouth shapes (phonemes) in each word. Singing faces follow the phonemes.
 */
@Serializable
data class Lyrics(
    val phrases: List<TimedLabel> = emptyList(),
    val words: List<TimedLabel> = emptyList(),
    val phonemes: List<TimedLabel> = emptyList(),
) {
    /** The mouth shape at [ms], or null between words. */
    fun phonemeAt(ms: Long): Phoneme? {
        // Binary search: phonemes are in time order.
        var lo = 0; var hi = phonemes.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val p = phonemes[mid]
            when {
                ms < p.startMs -> hi = mid - 1
                ms >= p.endMs -> lo = mid + 1
                else -> return Phoneme.parse(p.label)
            }
        }
        return null
    }

    fun wordAt(ms: Long): TimedLabel? = words.firstOrNull { ms >= it.startMs && ms < it.endMs }

    val isEmpty get() = phrases.isEmpty() && words.isEmpty()
}

/** Turns lyrics into timed lines, words and mouth shapes. */
object LyricSync {

    private val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')

    /** Rough syllable count, for sharing out time: vowel groups, less a silent final e. */
    fun syllables(word: String): Int {
        val w = word.lowercase().filter { it.isLetter() }
        if (w.isEmpty()) return 1
        var n = 0; var inVowel = false
        for (c in w) {
            val v = c in vowels
            if (v && !inVowel) n++
            inVowel = v
        }
        if (w.length > 2 && w.endsWith("e") && !w.endsWith("le") && n > 1) n--
        return n.coerceAtLeast(1)
    }

    /**
     * The mouth shapes of [word] in order, each with a weight for its share of the word's time
     * (vowels take longer than consonants). From spelling, so close enough for lights.
     */
    fun phonemes(word: String): List<Pair<Phoneme, Int>> {
        val w = word.lowercase().filter { it.isLetter() || it == '\'' }.replace("'", "")
        val out = ArrayList<Pair<Phoneme, Int>>()
        var i = 0
        fun add(p: Phoneme, weight: Int) { if (out.lastOrNull()?.first == p) return; out += p to weight }
        while (i < w.length) {
            val c = w[i]
            val next = w.getOrNull(i + 1)
            when {
                c == 'o' && next == 'o' -> { add(Phoneme.U, 2); i += 2; continue }
                c == 'q' && next == 'u' -> { add(Phoneme.WQ, 1); i += 2; continue }
                c == 'w' && next == 'h' -> { add(Phoneme.WQ, 1); i += 2; continue }
                c == 'p' && next == 'h' -> { add(Phoneme.FV, 1); i += 2; continue }
                (c == 't' || c == 's' || c == 'c') && next == 'h' -> { add(Phoneme.ETC, 1); i += 2; continue }
                c in "ai" -> add(Phoneme.AI, 2)
                c == 'e' -> if (i == w.lastIndex && w.length > 2) Unit else add(Phoneme.E, 2)
                c == 'o' -> add(Phoneme.O, 2)
                c == 'u' -> add(Phoneme.U, 2)
                c == 'y' -> if (i == 0) add(Phoneme.ETC, 1) else add(Phoneme.E, 2)
                c == 'w' -> add(Phoneme.WQ, 1)
                c in "mbp" -> add(Phoneme.MBP, 1)
                c in "fv" -> add(Phoneme.FV, 1)
                c == 'l' -> add(Phoneme.L, 1)
                else -> add(Phoneme.ETC, 1)
            }
            i++
        }
        return out.ifEmpty { listOf(Phoneme.ETC to 1) }
    }

    /** Words of a line: letters, digits and apostrophes. */
    fun words(line: String): List<String> = line.split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotBlank() }

    /** Shares [startMs]..[endMs] between [items] by [weight], in order. */
    private fun <T> share(items: List<T>, startMs: Long, endMs: Long, weight: (T) -> Int): List<Pair<T, LongRange>> {
        val total = items.sumOf(weight).coerceAtLeast(1)
        var at = startMs.toDouble()
        val per = (endMs - startMs).toDouble() / total
        return items.map { item ->
            val s = at; at += weight(item) * per
            item to (s.toLong() until at.toLong())
        }
    }

    /** Times one line sung from [startMs] to [endMs]: its words and their mouth shapes. */
    fun line(text: String, startMs: Long, endMs: Long): Lyrics {
        val ws = words(text)
        if (ws.isEmpty() || endMs <= startMs) return Lyrics()
        val words = ArrayList<TimedLabel>(); val phon = ArrayList<TimedLabel>()
        for ((w, r) in share(ws, startMs, endMs) { syllables(it) + 1 }) {
            // A little gap after each word so the mouth closes between them.
            val wordEnd = r.first + ((r.last + 1 - r.first) * 0.9).toLong().coerceAtLeast(1)
            words += TimedLabel(r.first, wordEnd, w)
            for ((p, pr) in share(phonemes(w), r.first, wordEnd) { it.second }) {
                phon += TimedLabel(pr.first, pr.last + 1, p.first.name)
            }
        }
        return Lyrics(listOf(TimedLabel(startMs, endMs, text.trim())), words, phon)
    }

    /**
     * Times every line of [text] (blank lines skipped): with [lineStarts] (ms, one per line, from
     * tapping along) each line runs to the next one's start; without them, the lines share
     * [startMs]..[endMs] by how many syllables they have.
     */
    fun breakdown(text: String, startMs: Long, endMs: Long, lineStarts: List<Long>? = null): Lyrics {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() && words(it).isNotEmpty() }
        if (lines.isEmpty()) return Lyrics()
        val spans: List<Pair<String, LongRange>> = if (lineStarts != null && lineStarts.isNotEmpty()) {
            lines.take(lineStarts.size).mapIndexed { i, l ->
                val s = lineStarts[i]
                val e = lineStarts.getOrNull(i + 1) ?: endMs
                // A line sung at a natural pace, not stretched across a long instrumental gap.
                val natural = words(l).sumOf { syllables(it) } * 350L + 400L
                l to (s until minOf(e, s + natural).coerceAtLeast(s + 1))
            }
        } else {
            share(lines, startMs, endMs) { l -> words(l).sumOf { syllables(it) } + 2 }
                .map { (l, r) -> l to (r.first until (r.first + ((r.last + 1 - r.first) * 0.92).toLong()).coerceAtLeast(r.first + 1)) }
        }
        val parts = spans.map { (l, r) -> line(l, r.first, r.last + 1) }
        return Lyrics(parts.flatMap { it.phrases }, parts.flatMap { it.words }, parts.flatMap { it.phonemes })
    }
}

package com.vknxworks.truectrl.data.system

/**
 * The lines of a followed container log, at most [capacity] of them: when more arrive, the oldest are
 * dropped, so a chatty container can't use up the phone's memory. Very long lines are cut at
 * [maxLineLength]. Not thread-safe; used from the main thread.
 */
class LogBuffer(val capacity: Int = DEFAULT_CAPACITY, private val maxLineLength: Int = MAX_LINE_LENGTH) {
    private val lines = ArrayDeque<String>()

    /** Lines dropped from the start because the buffer was full. */
    var dropped: Long = 0
        private set

    val size: Int get() = lines.size

    operator fun get(index: Int): String = lines[index]

    /** Adds one log event: its text may hold several lines (and `\r\n` endings), each kept as a line. */
    fun add(data: String) {
        for (raw in split(data)) {
            lines.addLast(if (raw.length > maxLineLength) raw.take(maxLineLength) + "…" else raw)
            if (lines.size > capacity) {
                lines.removeFirst()
                dropped++
            }
        }
    }

    fun clear() {
        lines.clear()
        dropped = 0
    }

    fun snapshot(): List<String> = lines.toList()

    /** Indices of the lines containing [query] (ignoring case); every index for a blank query. */
    fun matching(query: String): List<Int> {
        val q = query.trim()
        if (q.isEmpty()) return lines.indices.toList()
        return lines.indices.filter { lines[it].contains(q, ignoreCase = true) }
    }

    /** The lines at [indices] as one text, for copying. */
    fun text(indices: List<Int> = lines.indices.toList()): String =
        indices.filter { it in lines.indices }.joinToString("\n") { lines[it] }

    companion object {
        const val DEFAULT_CAPACITY = 5_000
        const val MAX_LINE_LENGTH = 4_000

        /** One event's text as lines: split on line breaks, without the trailing empty line. */
        fun split(data: String): List<String> {
            val parts = data.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            return if (parts.size > 1 && parts.last().isEmpty()) parts.dropLast(1) else parts
        }

        /** Where [query] appears in [line] (ignoring case), as ranges, for highlighting. */
        fun ranges(line: String, query: String): List<IntRange> {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            val out = mutableListOf<IntRange>()
            var from = 0
            while (true) {
                val i = line.indexOf(q, from, ignoreCase = true)
                if (i < 0) break
                out += i until i + q.length
                from = i + q.length
            }
            return out
        }
    }
}

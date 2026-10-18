package com.vknxworks.truectrl.data.api

/**
 * A TrueNAS version, compared number by number: 25.04 < 25.04.2.6 < 25.10 < 26.0.
 * [label] is what's shown, without the "TrueNAS-" or "v" prefix.
 */
data class TnVersion(val parts: List<Int>, val label: String) : Comparable<TnVersion> {
    val major: Int get() = parts.getOrElse(0) { 0 }
    val minor: Int get() = parts.getOrElse(1) { 0 }

    fun atLeast(vararg p: Int): Boolean = compare(parts, p.toList()) >= 0

    override fun compareTo(other: TnVersion): Int = compare(parts, other.parts)

    override fun toString(): String = label

    companion object {
        private val NUMBERS = Regex("""(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        /** The oldest version TrueCtrl supports: the first with the versioned JSON-RPC API it needs. */
        val MINIMUM = TnVersion(listOf(25, 4), "25.04")

        /**
         * Reads "TrueNAS-25.10.1", "TrueNAS-SCALE-25.04.2", "v25.10.5" or "27.0.0-RC.1".
         * Null when there's no version number in [text].
         */
        fun parse(text: String?): TnVersion? {
            if (text.isNullOrBlank()) return null
            val m = NUMBERS.find(text) ?: return null
            val parts = m.groupValues.drop(1).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
            if (parts.isEmpty()) return null
            val label = text.trim().removePrefix("TrueNAS-SCALE-").removePrefix("TrueNAS-").removePrefix("v")
            return TnVersion(parts, label)
        }

        private fun compare(a: List<Int>, b: List<Int>): Int {
            for (i in 0 until maxOf(a.size, b.size)) {
                val d = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}

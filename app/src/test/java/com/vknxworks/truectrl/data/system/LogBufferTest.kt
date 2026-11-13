package com.vknxworks.truectrl.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBufferTest {
    @Test
    fun keepsOnlyTheNewestLines() {
        val b = LogBuffer(capacity = 3)
        (1..5).forEach { b.add("line $it") }
        assertEquals(listOf("line 3", "line 4", "line 5"), b.snapshot())
        assertEquals(2L, b.dropped)
    }

    @Test
    fun oneEventCanHoldSeveralLines() {
        val b = LogBuffer(capacity = 10)
        b.add("first\r\nsecond\nthird\n")
        b.add("fourth\rfifth")
        assertEquals(listOf("first", "second", "third", "fourth", "fifth"), b.snapshot())
        b.add("")
        assertEquals("", b[b.size - 1])
    }

    @Test
    fun cutsVeryLongLines() {
        val b = LogBuffer(capacity = 2, maxLineLength = 5)
        b.add("abcdefghij")
        assertEquals("abcde…", b[0])
    }

    @Test
    fun searchIgnoresCaseAndBlankShowsAll() {
        val b = LogBuffer()
        listOf("INFO starting", "error: disk full", "info ready", "Error again").forEach(b::add)
        assertEquals(listOf(1, 3), b.matching("ERROR"))
        assertEquals(listOf(0, 1, 2, 3), b.matching("  "))
        assertTrue(b.matching("nothing").isEmpty())
        assertEquals("error: disk full\nError again", b.text(b.matching("error")))
    }

    @Test
    fun highlightRanges() {
        assertEquals(listOf(0..2, 8..10), LogBuffer.ranges("ERR and err", "err"))
        assertTrue(LogBuffer.ranges("anything", "").isEmpty())
    }

    @Test
    fun clearResets() {
        val b = LogBuffer(capacity = 1)
        b.add("a"); b.add("b")
        b.clear()
        assertEquals(0, b.size)
        assertEquals(0L, b.dropped)
    }
}

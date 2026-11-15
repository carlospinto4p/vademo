package com.cpinto.vademo.ui.note

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TableColumnsTest {

    @Test
    fun `columns that fit unwrapped keep their natural width`() {
        assertEquals(listOf(40, 120), columnWidths(min = listOf(40, 60), max = listOf(40, 120), available = 300))
    }

    @Test
    fun `a long column takes the spare width and a short one stays narrow`() {
        // A scorecard: criterion, score, reasons. Only the reasons need to wrap.
        val widths = columnWidths(min = listOf(90, 20, 80), max = listOf(110, 20, 2000), available = 400)
        assertEquals(400, widths.sum())
        assertEquals(20, widths[1])
        assertTrue(widths[0] in 90..110, "criterion column was ${widths[0]}")
        assertTrue(widths[2] > 270, "reasons column was ${widths[2]}")
    }

    @Test
    fun `spare width is shared in proportion to what each column still wants`() {
        assertEquals(listOf(150, 250), columnWidths(min = listOf(50, 50), max = listOf(250, 450), available = 400))
    }

    @Test
    fun `columns too wide even when wrapped keep their minimum and overflow`() {
        assertEquals(listOf(200, 300), columnWidths(min = listOf(200, 300), max = listOf(400, 900), available = 350))
    }

    @Test
    fun `rounding never leaves the table short of the screen`() {
        val widths = columnWidths(min = listOf(10, 10, 10), max = listOf(100, 100, 100), available = 100)
        assertEquals(100, widths.sum())
    }

    @Test
    fun `a max narrower than the min counts as the min`() {
        assertEquals(listOf(30, 20), columnWidths(min = listOf(30, 20), max = listOf(10, 20), available = 300))
    }

    @Test
    fun `mismatched lists are refused`() {
        assertThrows<IllegalArgumentException> { columnWidths(listOf(1), listOf(1, 2), 10) }
    }
}

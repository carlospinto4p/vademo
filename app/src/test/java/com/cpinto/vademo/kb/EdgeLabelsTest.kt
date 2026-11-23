package com.cpinto.vademo.kb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class EdgeLabelsTest {

    /** A horizontal arrow from x 0 to x 400 at y 100, its label 60 by 20 at the middle. */
    private val across = EdgeLabels.Label(
        path = listOf(Position(0.0, 100.0), Position(400.0, 100.0)),
        preferred = Position(200.0, 100.0),
        width = 60.0,
        height = 20.0,
    )

    private fun covers(at: Position, label: EdgeLabels.Label, box: Placed): Boolean =
        abs(at.x - box.centreX) < (label.width + box.width) / 2 && abs(at.y - box.centreY) < (label.height + box.height) / 2

    private fun onThePath(at: Position) = at.y == 100.0 && at.x in 0.0..400.0

    @Test
    fun aLabelWithNothingUnderItStaysAtTheMiddle() {
        val far = Placed(0.0, 300.0, 100.0, 40.0)
        assertEquals(listOf(Position(200.0, 100.0)), EdgeLabels.place(listOf(across), listOf(far)))
    }

    @Test
    fun aLabelOverABoxSlidesAlongItsArrowOffTheBox() {
        val inTheWay = Placed(180.0, 80.0, 40.0, 40.0)
        val at = EdgeLabels.place(listOf(across), listOf(inTheWay)).single()!!
        assertTrue(onThePath(at), "still on its arrow: $at")
        assertTrue(!covers(at, across, inTheWay), "off the box: $at")
        // The nearest free spot, not the far end: half the label, half the box and the margin away.
        assertTrue(abs(at.x - 200.0) <= 60.0, "close to the middle: $at")
    }

    @Test
    fun twoLabelsAtTheSameSpotDoNotCoverEachOther() {
        val crossing = EdgeLabels.Label(
            path = listOf(Position(200.0, 0.0), Position(200.0, 200.0)),
            preferred = Position(200.0, 100.0),
            width = 60.0,
            height = 20.0,
        )
        val (first, second) = EdgeLabels.place(listOf(across, crossing), emptyList())
        assertEquals(Position(200.0, 100.0), first, "the first keeps its place")
        assertNotEquals(first, second)
        assertEquals(200.0, second!!.x, "the second stays on its own arrow")
        assertTrue(abs(second.y - 100.0) >= 20.0, "and clear of the first: $second")
    }

    @Test
    fun aLabelSlidesPastALongBoxToTheFirstFreeSpot() {
        val long = Placed(0.0, 80.0, 300.0, 40.0)
        val at = EdgeLabels.place(listOf(across), listOf(long)).single()!!
        assertTrue(onThePath(at))
        assertTrue(!covers(at, across, long), "past the box's right edge: $at")
    }

    @Test
    fun withNoFreeSpotALabelGoesWhereItCoversLeast() {
        // A box over the whole arrow: only the ends, half off the box, cover less of it.
        val whole = Placed(0.0, 80.0, 400.0, 40.0)
        val at = EdgeLabels.place(listOf(across), listOf(whole)).single()!!
        assertTrue(at.x == 0.0 || at.x == 400.0, "at an end: $at")
    }

    @Test
    fun anArrowWithoutALabelGetsNoPlace() {
        assertNull(EdgeLabels.place(listOf(null), emptyList()).single())
    }

    @Test
    fun aCurveIsSampledFromWhereThePathIs() {
        val points = EdgeLabels.points(
            Position(0.0, 0.0),
            listOf(EdgeRoute.Piece.Curve(Position(0.0, 50.0), Position(100.0, 50.0), Position(100.0, 100.0))),
        )
        assertEquals(Position(0.0, 0.0), points.first())
        assertEquals(Position(100.0, 100.0), points.last())
        assertTrue(points.size > 3)
    }
}

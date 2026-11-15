package com.cpinto.vademo.kb

import com.cpinto.vademo.kb.Diagram.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EdgeRouteTest {

    private val source = Placed(0.0, 0.0, 100.0, 40.0)
    private val target = Placed(400.0, 200.0, 100.0, 40.0)

    /** On the straight line from [source] to [target], and away from both lines out of their sides. */
    private val inTheWay = Placed(200.0, 80.0, 60.0, 60.0)

    /** The picture the boxes are in: room enough around them. */
    private val picture = DiagramPlaces(emptyMap(), 600.0, 400.0)

    /** The drawn path as short straight pieces, each curve sampled along its length. */
    private fun runs(d: EdgeRoute.Detour): List<Pair<Position, Position>> {
        val points = mutableListOf(d.start)
        for (piece in d.pieces) {
            when (piece) {
                is EdgeRoute.Piece.Line -> points += piece.to
                is EdgeRoute.Piece.Curve -> {
                    val curve = listOf(points.last(), piece.control1, piece.control2, piece.to)
                    for (i in 1..48) {
                        val t = i / 48.0
                        val u = 1 - t
                        val k = listOf(u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t)
                        points += Position(curve.indices.sumOf { k[it] * curve[it].x }, curve.indices.sumOf { k[it] * curve[it].y })
                    }
                }
            }
        }
        return points.zipWithNext()
    }

    private fun EdgeRoute.Detour.misses(box: Placed) = runs(this).none { (a, b) -> EdgeRoute.crosses(a, b, box) }

    @Test
    fun `a segment crosses a box only through its inside`() {
        val box = Placed(10.0, 10.0, 20.0, 20.0)
        assertTrue(EdgeRoute.crosses(Position(0.0, 20.0), Position(40.0, 20.0), box))
        assertFalse(EdgeRoute.crosses(Position(0.0, 0.0), Position(40.0, 0.0), box))
        assertFalse(EdgeRoute.crosses(Position(0.0, 10.0), Position(40.0, 10.0), box), "running along its edge")
        assertFalse(EdgeRoute.crosses(Position(0.0, 20.0), Position(5.0, 20.0), box), "stopping short of it")
    }

    @Test
    fun `an arrow with nothing in its way stays straight`() {
        assertNull(EdgeRoute.detour(source, target, Direction.LR, listOf(Placed(200.0, 300.0, 60.0, 60.0)), picture))
    }

    @Test
    fun `an arrow over a box goes round it, with one bend in the gap nearest its middle`() {
        assertTrue(EdgeRoute.crosses(EdgeRoute.exit(source, target), EdgeRoute.exit(target, source), inTheWay))
        val d = EdgeRoute.detour(source, target, Direction.LR, listOf(inTheWay), picture)!!
        assertTrue(d.misses(inTheWay))
        // Out of the source's right side, into the target's left side, at their middles.
        assertEquals(Position(100.0, 20.0), d.start)
        assertEquals(Position(400.0, 220.0), d.end)
        // The middle (250) is beside the box, so the bend takes the middle of the nearer gap: 292 to 376.
        assertEquals(Position(334.0, 120.0), d.label)
        assertEquals(Position(358.0, 220.0), d.beforeEnd)
    }

    @Test
    fun `top to bottom, the arrow runs down out of the source and bends across`() {
        val flip = { p: Placed -> Placed(p.y, p.x, p.height, p.width) }
        val d = EdgeRoute.detour(flip(source), flip(target), Direction.TB, listOf(flip(inTheWay)), picture)!!
        assertTrue(d.misses(flip(inTheWay)))
        assertEquals(Position(20.0, 100.0), d.start)
        assertEquals(Position(120.0, 334.0), d.label)
    }

    @Test
    fun `an arrow with no gap for the bend stays straight`() {
        // A box on the source's own line and one on the target's leave nowhere between them to change lines.
        val onSource = Placed(150.0, 0.0, 60.0, 40.0)
        val onTarget = Placed(300.0, 200.0, 60.0, 40.0)
        assertNull(EdgeRoute.detour(source, target, Direction.LR, listOf(onSource, onTarget, inTheWay), picture))
    }

    /** Top to bottom: [above] and [below] in one column with [between] them, so an arrow from below to above runs backwards over it. */
    private val above = Placed(0.0, 0.0, 200.0, 64.0)
    private val between = Placed(0.0, 124.0, 200.0, 64.0)
    private val below = Placed(0.0, 248.0, 200.0, 64.0)

    @Test
    fun `an arrow running backwards over a box goes round it, beside the column`() {
        val d = EdgeRoute.detour(below, above, Direction.TB, listOf(between), picture)!!
        assertTrue(d.misses(between))
        // Out of the right side of the source and into the right side of the target, 16 to the right of the column.
        assertEquals(Position(200.0, 280.0), d.start)
        assertEquals(listOf(Position(216.0, 280.0), Position(216.0, 32.0), Position(200.0, 32.0)), d.pieces.map { it.to })
        assertEquals(Position(216.0, 156.0), d.label)
        assertEquals(Position(216.0, 32.0), d.beforeEnd)
    }

    @Test
    fun `a backward arrow takes the lane between two columns when that is nearer`() {
        // A second column to the right, 28 apart, and the column's right edge also at the picture's: only the gap is free.
        val other = Placed(228.0, 124.0, 200.0, 64.0)
        val d = EdgeRoute.detour(below, above, Direction.TB, listOf(between, other), DiagramPlaces(emptyMap(), 428.0, 312.0))!!
        assertTrue(d.misses(between) && d.misses(other))
        assertEquals(Position(214.0, 156.0), d.label)
    }

    @Test
    fun `a backward arrow with no lane inside the picture stays straight`() {
        assertNull(EdgeRoute.detour(below, above, Direction.TB, listOf(between), DiagramPlaces(emptyMap(), 200.0, 312.0)))
    }

    @Test
    fun `a backward arrow whose way out of the side is taken leaves by the back of its source`() {
        // A box right beside the source, on its right, too close for a lane between: the way out to the side runs through it.
        val beside = Placed(208.0, 248.0, 200.0, 64.0)
        val d = EdgeRoute.detour(below, above, Direction.TB, listOf(between, beside), DiagramPlaces(emptyMap(), 600.0, 312.0))!!
        assertTrue(d.misses(between) && d.misses(beside))
        // Up out of the source's top into the gap between the rows, across to the lane, up, into the target's side.
        assertEquals(Position(100.0, 248.0), d.start)
        assertEquals(
            listOf(Position(100.0, 224.0), Position(216.0, 224.0), Position(216.0, 32.0), Position(200.0, 32.0)),
            d.pieces.map { it.to },
        )
    }

    @Test
    fun `a backward arrow enters by the front of its target when its side is taken`() {
        // A box right beside the target, on its right: the way in from the side runs through it.
        val beside = Placed(208.0, 0.0, 200.0, 64.0)
        val d = EdgeRoute.detour(below, above, Direction.TB, listOf(between, beside), DiagramPlaces(emptyMap(), 600.0, 312.0))!!
        assertTrue(d.misses(between) && d.misses(beside))
        assertEquals(Position(100.0, 64.0), d.end)
        assertEquals(Position(100.0, 88.0), d.beforeEnd)
    }

    @Test
    fun `a backward arrow with every way round taken stays straight`() {
        // Boxes beside the source and the target, and no room past them inside the picture.
        val boxes = listOf(between, Placed(208.0, 248.0, 200.0, 64.0), Placed(208.0, 0.0, 200.0, 64.0), Placed(208.0, 124.0, 200.0, 64.0))
        assertNull(EdgeRoute.detour(below, above, Direction.TB, boxes, DiagramPlaces(emptyMap(), 408.0, 312.0)))
    }

    @Test
    fun `an arrow's own ends and groups are not in its way`() {
        val d = Diagram.parse(
            """{ "nodes": [{ "id": "g", "kind": "group" }, { "id": "a", "parent": "g" }, { "id": "b" }, { "id": "c" }],
                 "edges": [{ "source": "a", "target": "b" }] }""",
        )
        val places = DiagramLayout.place(d, mapOf("a" to DiagramLayout.Box(200.0, 64.0), "b" to DiagramLayout.Box(200.0, 64.0), "c" to DiagramLayout.Box(200.0, 64.0)))
        assertEquals(listOf(places.boxes.getValue("c")), EdgeRoute.obstacles(d, places, "a", "b"))
    }
}

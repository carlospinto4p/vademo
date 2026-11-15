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

    /** The drawn path as short straight pieces, the bend sampled along its curve. */
    private fun pieces(d: EdgeRoute.Detour): List<Pair<Position, Position>> {
        val curve = listOf(d.bendStart, d.control1, d.control2, d.bendEnd)
        val points = mutableListOf(d.start, d.bendStart)
        for (i in 1..48) {
            val t = i / 48.0
            val u = 1 - t
            val k = listOf(u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t)
            points += Position(curve.indices.sumOf { k[it] * curve[it].x }, curve.indices.sumOf { k[it] * curve[it].y })
        }
        points += d.end
        return points.zipWithNext()
    }

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
        assertNull(EdgeRoute.detour(source, target, Direction.LR, listOf(Placed(200.0, 300.0, 60.0, 60.0))))
    }

    @Test
    fun `an arrow over a box goes round it, with one bend in the gap nearest its middle`() {
        assertTrue(EdgeRoute.crosses(EdgeRoute.exit(source, target), EdgeRoute.exit(target, source), inTheWay))
        val d = EdgeRoute.detour(source, target, Direction.LR, listOf(inTheWay))!!
        assertTrue(pieces(d).none { (a, b) -> EdgeRoute.crosses(a, b, inTheWay) })
        // Out of the source's right side, into the target's left side, at their middles.
        assertEquals(Position(100.0, 20.0), d.start)
        assertEquals(Position(400.0, 220.0), d.end)
        // The middle (250) is beside the box, so the bend takes the middle of the nearer gap: 292 to 376.
        assertEquals(Position(334.0, 120.0), d.label)
    }

    @Test
    fun `top to bottom, the arrow runs down out of the source and bends across`() {
        val flip = { p: Placed -> Placed(p.y, p.x, p.height, p.width) }
        val d = EdgeRoute.detour(flip(source), flip(target), Direction.TB, listOf(flip(inTheWay)))!!
        assertTrue(pieces(d).none { (a, b) -> EdgeRoute.crosses(a, b, flip(inTheWay)) })
        assertEquals(Position(20.0, 100.0), d.start)
        assertEquals(Position(120.0, 334.0), d.label)
    }

    @Test
    fun `an arrow that runs backwards stays straight`() {
        assertNull(EdgeRoute.detour(target, source, Direction.LR, listOf(inTheWay)))
    }

    @Test
    fun `an arrow with no gap for the bend stays straight`() {
        // A box on the source's own line and one on the target's leave nowhere between them to change lines.
        val onSource = Placed(150.0, 0.0, 60.0, 40.0)
        val onTarget = Placed(300.0, 200.0, 60.0, 40.0)
        assertNull(EdgeRoute.detour(source, target, Direction.LR, listOf(onSource, onTarget, inTheWay)))
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

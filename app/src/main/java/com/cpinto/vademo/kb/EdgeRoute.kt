package com.cpinto.vademo.kb

import com.cpinto.vademo.kb.Diagram.Direction

/**
 * An arrow's path when its straight line would be drawn over a box, following
 * tyfold's `src/lib/graph/canvas/edgeRoute.ts` ("An arrow goes round a box its
 * curve would cross" in its `docs/GRAPH-PANE.md`).
 *
 * vademo draws an arrow as a straight line between its boxes. Where that line
 * crosses a plain box that is not one of its ends, the arrow is drawn through
 * way points instead: straight out of its source, one short bend in the gap
 * nearest its middle that no box is in, and straight into its target, with its
 * label on the bend. It is worked out each time the arrow is drawn, from where
 * the boxes are, so it holds after dragging and nothing about it is in the
 * file. An arrow with no such gap, or one that runs backwards, stays straight.
 * It does not look at other arrows, so two arrows can still cross.
 */
object EdgeRoute {

    /** How far a way-point path stays from a box. */
    private const val CLEARANCE = 8.0

    /** Half the length the bend takes along the arrow's direction. */
    const val BEND = 24.0

    /**
     * The way points of a bent arrow, in dp: out of the source at [start],
     * straight to [bendStart], a curve with controls [control1] and [control2]
     * to [bendEnd], and straight into the target at [end]. [label] is the
     * middle of the bend.
     */
    data class Detour(
        val start: Position,
        val bendStart: Position,
        val control1: Position,
        val control2: Position,
        val bendEnd: Position,
        val end: Position,
        val label: Position,
    )

    /**
     * The boxes an arrow from [source] to [target] must not be drawn over:
     * every box that is not a group, except its two ends.
     */
    fun obstacles(diagram: Diagram, places: DiagramPlaces, source: String, target: String): List<Placed> =
        diagram.nodes.filter { !it.isGroup && it.id != source && it.id != target }.mapNotNull { places.boxes[it.id] }

    /** Where the line from [from]'s centre towards [to]'s centre leaves [from]'s box. */
    fun exit(from: Placed, to: Placed): Position {
        val dx = to.centreX - from.centreX
        val dy = to.centreY - from.centreY
        if (dx == 0.0 && dy == 0.0) return Position(from.centreX, from.centreY)
        val t = minOf(
            if (dx != 0.0) from.width / 2 / kotlin.math.abs(dx) else Double.MAX_VALUE,
            if (dy != 0.0) from.height / 2 / kotlin.math.abs(dy) else Double.MAX_VALUE,
        )
        return Position(from.centreX + dx * t, from.centreY + dy * t)
    }

    /** Whether the segment from [a] to [b] passes through the inside of [box]. */
    fun crosses(a: Position, b: Position, box: Placed): Boolean {
        // Liang–Barsky: clip the segment to the box; it crosses when some of it is left strictly inside.
        var enter = 0.0
        var leave = 1.0
        val dx = b.x - a.x
        val dy = b.y - a.y
        for ((p, q) in listOf(-dx to a.x - box.x, dx to box.x + box.width - a.x, -dy to a.y - box.y, dy to box.y + box.height - a.y)) {
            if (p == 0.0) {
                if (q <= 0.0) return false
            } else {
                val r = q / p
                if (p < 0) enter = maxOf(enter, r) else leave = minOf(leave, r)
            }
        }
        return enter < leave
    }

    /**
     * The bent path for an arrow from [from] to [to] when its straight line
     * crosses one of [boxes], or null to keep the line: when the line is clear,
     * when the target is not ahead of the source in the diagram's [direction],
     * or when no gap takes the bend.
     */
    fun detour(from: Placed, to: Placed, direction: Direction, boxes: List<Placed>): Detour? {
        val a = exit(from, to)
        val b = exit(to, from)
        if (boxes.none { crosses(a, b, it) }) return null

        val horizontal = direction == Direction.LR
        // Along the arrow (`main`) and across it (`cross`), for a box and for a point.
        fun main(p: Placed) = if (horizontal) p.x else p.y
        fun cross(p: Placed) = if (horizontal) p.y else p.x
        fun length(p: Placed) = if (horizontal) p.width else p.height
        fun breadth(p: Placed) = if (horizontal) p.height else p.width

        // Out of the source's leading side and into the target's trailing side, at their middles.
        val start = main(from) + length(from)
        val finish = main(to)
        val sourceCross = cross(from) + breadth(from) / 2
        val targetCross = cross(to) + breadth(to) / 2
        val low = minOf(sourceCross, targetCross)
        val high = maxOf(sourceCross, targetCross)

        // Where along the arrow the middle of the bend may be. The straight run out
        // of the source must end before the first box on its line, the run into the
        // target must start after the last box on its line, and the bend itself
        // fits beside no box that reaches between the two lines.
        var least = start + BEND
        var most = finish - BEND
        val shut = mutableListOf<Pair<Double, Double>>()
        for (r in boxes) {
            if (main(r) + length(r) <= start || main(r) >= finish) continue
            val top = cross(r) - CLEARANCE
            val bottom = cross(r) + breadth(r) + CLEARANCE
            if (bottom <= low || top >= high) continue
            val open = main(r) - CLEARANCE - BEND
            val close = main(r) + length(r) + CLEARANCE + BEND
            shut += open to close
            if (sourceCross > top && sourceCross < bottom) most = minOf(most, open)
            if (targetCross > top && targetCross < bottom) least = maxOf(least, close)
        }
        if (least > most) return null

        // The gap nearest the middle of the arrow, so it changes least. The bend
        // goes at the arrow's middle when the gap holds it, else in the middle of
        // the gap, so it does not run along the edge of a box.
        shut.sortBy { it.first }
        val wanted = (start + finish) / 2
        var best: Double? = null
        var miss = Double.MAX_VALUE
        var at = least
        fun offer(open: Double, close: Double) {
            if (open > close) return
            val off = maxOf(open - wanted, wanted - close, 0.0)
            if (off >= miss) return
            miss = off
            best = if (off == 0.0) wanted else (open + close) / 2
        }
        for ((open, close) in shut) {
            offer(at, minOf(open, most))
            at = maxOf(at, close)
        }
        offer(at, most)
        val mid = best ?: return null

        fun point(m: Double, c: Double) = if (horizontal) Position(m, c) else Position(c, m)
        return Detour(
            start = point(start, sourceCross),
            bendStart = point(mid - BEND, sourceCross),
            control1 = point(mid, sourceCross),
            control2 = point(mid, targetCross),
            bendEnd = point(mid + BEND, targetCross),
            end = point(finish, targetCross),
            label = point(mid, (sourceCross + targetCross) / 2),
        )
    }
}

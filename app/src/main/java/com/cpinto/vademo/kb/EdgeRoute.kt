package com.cpinto.vademo.kb

import com.cpinto.vademo.kb.Diagram.Direction

/**
 * An arrow's path when its straight line would be drawn over a box.
 *
 * vademo draws an arrow as a straight line between its boxes. Where that line
 * crosses a plain box that is not one of its ends, the arrow is drawn through
 * way points instead, in one of two ways:
 *
 * - **An arrow running the diagram's way** follows tyfold's
 *   `src/lib/graph/canvas/edgeRoute.ts` ("An arrow goes round a box its curve
 *   would cross" in its `docs/GRAPH-PANE.md`): straight out of its source, one
 *   short bend in the gap nearest its middle that no box is in, and straight
 *   into its target, with its label on the bend. With no such gap it stays
 *   straight.
 * - **An arrow running backwards**, its target wholly behind its source, goes
 *   out of the side of its source, back along a lane no box stands in between
 *   its two ends, and into the same side of its target, with its label on the
 *   lane. The lane is the free one nearest the boxes, inside the drawing. With
 *   none it stays straight. tyfold leaves such an arrow straight; this part is
 *   vademo's own, until tyfold does the same.
 *
 * It is worked out each time the arrow is drawn, from where the boxes are, so
 * it holds after dragging and nothing about it is in the file. It does not
 * look at other arrows, so two arrows can still cross, as in tyfold.
 */
object EdgeRoute {

    /** How far a way-point path stays from a box. */
    private const val CLEARANCE = 8.0

    /** Half the length the bend takes along the arrow's direction. */
    const val BEND = 24.0

    /** How far a backward arrow's lane runs from the boxes beside it, when it has the room. */
    private const val LANE = 16.0

    /** One piece of a route: a straight run, or a curve with two control points, to [to]. */
    sealed interface Piece {
        val to: Position
        data class Line(override val to: Position) : Piece
        data class Curve(val control1: Position, val control2: Position, override val to: Position) : Piece
    }

    /** A route round boxes, in dp: from [start] along [pieces] into the target, with its label at [label]. */
    data class Detour(val start: Position, val pieces: List<Piece>, val label: Position) {
        val end: Position get() = pieces.last().to

        /** Where the last straight run into the target starts, which the arrowhead points along. */
        val beforeEnd: Position get() = pieces.getOrNull(pieces.size - 2)?.to ?: start
    }

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
     * The route for an arrow from [from] to [to] when its straight line crosses
     * one of [boxes], or null to keep the line: when the line is clear, or when
     * neither way round fits. [places] bounds a backward arrow's lane, so it is
     * not drawn outside the picture.
     */
    fun detour(from: Placed, to: Placed, direction: Direction, boxes: List<Placed>, places: DiagramPlaces): Detour? {
        val a = exit(from, to)
        val b = exit(to, from)
        if (boxes.none { crosses(a, b, it) }) return null
        val axes = Axes(direction == Direction.LR)
        return if (axes.main(to) + axes.length(to) <= axes.main(from)) {
            backward(from, to, axes, boxes, if (axes.horizontal) places.height else places.width)
        } else {
            bend(from, to, axes, boxes)
        }
    }

    /** Along the arrows (`main`) and across them (`cross`), in the diagram's direction. */
    private class Axes(val horizontal: Boolean) {
        fun main(p: Placed) = if (horizontal) p.x else p.y
        fun cross(p: Placed) = if (horizontal) p.y else p.x
        fun length(p: Placed) = if (horizontal) p.width else p.height
        fun breadth(p: Placed) = if (horizontal) p.height else p.width
        fun point(m: Double, c: Double) = if (horizontal) Position(m, c) else Position(c, m)
    }

    /** tyfold's `detour`: one bend in the gap nearest the arrow's middle. */
    private fun bend(from: Placed, to: Placed, axes: Axes, boxes: List<Placed>): Detour? = with(axes) {
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

        return Detour(
            start = point(start, sourceCross),
            pieces = listOf(
                Piece.Line(point(mid - BEND, sourceCross)),
                Piece.Curve(point(mid, sourceCross), point(mid, targetCross), point(mid + BEND, targetCross)),
                Piece.Line(point(finish, targetCross)),
            ),
            label = point(mid, (sourceCross + targetCross) / 2),
        )
    }

    /**
     * One way out of a backward arrow's source, or into its target: where along
     * the flow it meets the lane ([main]), and the points between the box and
     * that meeting, given the lane's place across the flow.
     */
    private class End(val main: Double, val points: (lane: Double) -> List<Position>)

    /**
     * A backward arrow: out of its source, back along a lane that runs with the
     * flow, and into its target. It leaves the source by the side facing the
     * lane or by its back, and enters the target by the side facing the lane or
     * by its front; leaving by the back or entering by the front first runs
     * [BEND] into the gap between the rows. The lane is in a gap across the
     * flow that no box beside it reaches into, within 0 to [extent]. Of every
     * such way that crosses no box, the shortest wins.
     */
    private fun backward(from: Placed, to: Placed, axes: Axes, boxes: List<Placed>, extent: Double): Detour? = with(axes) {
        val sourceMiddle = main(from) + length(from) / 2
        val targetMiddle = main(to) + length(to) / 2
        val sourceCentre = cross(from) + breadth(from) / 2
        val targetCentre = cross(to) + breadth(to) / 2
        fun side(box: Placed, lane: Double) = if (lane > cross(box)) cross(box) + breadth(box) else cross(box)
        val back = main(from) - BEND
        val front = main(to) + length(to) + BEND
        val outs = listOf(
            End(sourceMiddle) { lane -> listOf(point(sourceMiddle, side(from, lane))) },
            End(back) { listOf(point(main(from), sourceCentre), point(back, sourceCentre)) },
        )
        val ins = listOf(
            End(targetMiddle) { lane -> listOf(point(targetMiddle, side(to, lane))) },
            End(front) { listOf(point(front, targetCentre), point(main(to) + length(to), targetCentre)) },
        )
        val walls = boxes + from + to

        val routes = outs.flatMap { out ->
            ins.filter { it.main < out.main }.flatMap { into ->
                lanes(walls.filter { main(it) < out.main && main(it) + length(it) > into.main }, axes, extent).mapNotNull { lane ->
                    val points = (out.points(lane) + point(out.main, lane) + point(into.main, lane) + into.points(lane))
                        .fold(listOf<Position>()) { kept, p -> if (kept.lastOrNull() == p) kept else kept + p }
                    val runs = points.zipWithNext()
                    if (runs.any { (a, b) -> walls.any { crosses(a, b, it) } }) return@mapNotNull null
                    val detour = Detour(points.first(), points.drop(1).map { Piece.Line(it) }, point((out.main + into.main) / 2, lane))
                    detour to runs.sumOf { (a, b) -> kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y) }
                }
            }
        }
        return routes.minByOrNull { it.second }?.first
    }

    /**
     * The places across the flow a lane can run, past [beside], within 0 to
     * [extent]: one in each gap between them, in its middle, and one past each
     * outermost box, [LANE] from it where the drawing has the room.
     */
    private fun lanes(beside: List<Placed>, axes: Axes, extent: Double): List<Double> = with(axes) {
        val blocked = beside.map { (cross(it) - CLEARANCE) to (cross(it) + breadth(it) + CLEARANCE) }.sortedBy { it.first }
        val lanes = mutableListOf<Double>()
        var at = 0.0
        for ((open, close) in blocked + (extent to extent)) {
            if (open > at) {
                lanes += when {
                    at == 0.0 && open < extent -> maxOf(at, open - LANE + CLEARANCE)
                    open >= extent && at > 0.0 -> minOf(open, at + LANE - CLEARANCE)
                    else -> (at + open) / 2
                }
            }
            at = maxOf(at, close)
        }
        return lanes
    }
}

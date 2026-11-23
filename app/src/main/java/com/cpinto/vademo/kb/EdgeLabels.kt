package com.cpinto.vademo.kb

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Where arrow labels sit, so one does not cover a box or another label.
 *
 * A label starts where [EdgeRoute] puts it: an arrow's middle, or its bend or
 * lane. When a box or a label already placed is under it there, it slides along
 * its own arrow to the nearest free spot, so it still reads as that arrow's. With
 * no free spot anywhere along the arrow it goes where it covers least.
 *
 * Labels are placed in the file's order, so an earlier arrow's label keeps its
 * place and a later one moves. tyfold does not move labels; this is vademo's own,
 * for a phone's small screen.
 */
object EdgeLabels {

    /** How far apart the spots a label may slide to are, in dp. */
    private const val STEP = 4.0

    /** The room kept round a label, so it does not touch what it slid away from. */
    private const val MARGIN = 2.0

    /** One label to place: the arrow it is on, as points, where it would go, and its size. */
    data class Label(val path: List<Position>, val preferred: Position, val width: Double, val height: Double)

    /** The arrow from [start] along [pieces] as points, each curve sampled into short straight runs. */
    fun points(start: Position, pieces: List<EdgeRoute.Piece>): List<Position> {
        val points = mutableListOf(start)
        for (piece in pieces) {
            when (piece) {
                is EdgeRoute.Piece.Line -> points += piece.to
                is EdgeRoute.Piece.Curve -> {
                    val from = points.last()
                    for (i in 1..CURVE_STEPS) {
                        val t = i.toDouble() / CURVE_STEPS
                        val u = 1 - t
                        val a = u * u * u
                        val b = 3 * u * u * t
                        val c = 3 * u * t * t
                        val d = t * t * t
                        points += Position(
                            a * from.x + b * piece.control1.x + c * piece.control2.x + d * piece.to.x,
                            a * from.y + b * piece.control1.y + c * piece.control2.y + d * piece.to.y,
                        )
                    }
                }
            }
        }
        return points
    }

    private const val CURVE_STEPS = 12

    /**
     * The centre of each label in [labels], in the same order. A null label (an
     * arrow without one) gets a null place and covers nothing.
     */
    fun place(labels: List<Label?>, boxes: List<Placed>): List<Position?> {
        val taken = mutableListOf<Placed>()
        return labels.map { label ->
            label ?: return@map null
            val spots = spots(label.path)
            if (spots.isEmpty()) return@map label.preferred.also { taken += rect(it, label) }
            val home = spots.minBy { (_, at) -> distance(at, label.preferred) }.first
            // The preferred spot first, then outwards along the arrow; the first spot nothing covers wins.
            val ordered = listOf(label.preferred) + spots.sortedBy { (along, _) -> abs(along - home) }.map { it.second }
            var best = label.preferred
            var least = Double.MAX_VALUE
            for (at in ordered) {
                val area = rect(at, label).let { r -> boxes.sumOf { overlap(r, it) } + taken.sumOf { overlap(r, it) } }
                if (area < least) {
                    least = area
                    best = at
                }
                if (area == 0.0) break
            }
            taken += rect(best, label)
            best
        }
    }

    /** Points every [STEP] along [path], each with how far along the path it is. */
    private fun spots(path: List<Position>): List<Pair<Double, Position>> {
        val spots = mutableListOf<Pair<Double, Position>>()
        var walked = 0.0
        var next = 0.0
        for ((a, b) in path.zipWithNext()) {
            val length = distance(a, b)
            while (length > 0 && next <= walked + length) {
                val t = (next - walked) / length
                spots += next to Position(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                next += STEP
            }
            walked += length
        }
        return spots
    }

    private fun rect(at: Position, label: Label) = Placed(
        at.x - label.width / 2 - MARGIN,
        at.y - label.height / 2 - MARGIN,
        label.width + MARGIN * 2,
        label.height + MARGIN * 2,
    )

    private fun overlap(a: Placed, b: Placed): Double {
        val w = min(a.x + a.width, b.x + b.width) - max(a.x, b.x)
        val h = min(a.y + a.height, b.y + b.height) - max(a.y, b.y)
        return if (w > 0 && h > 0) w * h else 0.0
    }

    private fun distance(a: Position, b: Position) = hypot(a.x - b.x, a.y - b.y)
}

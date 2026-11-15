package com.cpinto.vademo.kb

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Point(val x: Double, val y: Double)

/** Box centres, all boxes inside `0..width` × `0..height`. */
data class Layout(val centres: List<Point>, val width: Double, val height: Double)

/**
 * Places the boxes of a [NoteGraph]: a force layout (Fruchterman–Reingold)
 * where every box pushes the others away, every link pulls its two ends
 * together and a weak pull to the middle keeps unlinked notes close, then a
 * pass that moves apart any boxes still overlapping.
 *
 * Distances are in units of the preferred gap between two linked notes; the
 * screen decides how many dp a unit is. There is no randomness: boxes start
 * on a spiral in [groups] order, so notes of one folder start side by side
 * and the same graph always gives the same picture.
 */
object GraphLayout {

    private const val ITERATIONS = 300
    private const val GRAVITY = 0.6
    private const val MIN_DISTANCE = 0.01

    fun layout(
        count: Int,
        links: List<Pair<Int, Int>>,
        groups: List<String>,
        boxWidth: Double,
        boxHeight: Double,
        /** Height ÷ width of the screen; the picture is stretched toward that shape. */
        aspect: Double = 1.0,
    ): Layout {
        val gravityX = GRAVITY * sqrt(aspect)
        val gravityY = GRAVITY / sqrt(aspect)
        if (count == 0) return Layout(emptyList(), 0.0, 0.0)
        val x = DoubleArray(count)
        val y = DoubleArray(count)
        val order = (0 until count).sortedWith(compareBy({ groups[it] }, { it }))
        order.forEachIndexed { slot, node ->
            // Golden-angle spiral: evenly spread and never two points on one spot.
            val r = sqrt(slot + 0.5)
            x[node] = r * cos(slot * GOLDEN_ANGLE)
            y[node] = r * sin(slot * GOLDEN_ANGLE)
        }

        val dx = DoubleArray(count)
        val dy = DoubleArray(count)
        val startTemperature = sqrt(count.toDouble())
        repeat(ITERATIONS) { step ->
            dx.fill(0.0)
            dy.fill(0.0)
            for (i in 0 until count) {
                for (j in i + 1 until count) {
                    val ux = x[i] - x[j]
                    val uy = y[i] - y[j]
                    val d = max(sqrt(ux * ux + uy * uy), MIN_DISTANCE)
                    val push = 1.0 / (d * d) // k² / d, divided by d to scale the unit vector
                    dx[i] += ux * push; dy[i] += uy * push
                    dx[j] -= ux * push; dy[j] -= uy * push
                }
            }
            for ((a, b) in links) {
                val ux = x[a] - x[b]
                val uy = y[a] - y[b]
                val d = sqrt(ux * ux + uy * uy)
                val pull = d // d² / k, divided by d
                dx[a] -= ux * pull; dy[a] -= uy * pull
                dx[b] += ux * pull; dy[b] += uy * pull
            }
            val temperature = startTemperature * (1.0 - step.toDouble() / ITERATIONS) + 0.01
            for (i in 0 until count) {
                dx[i] -= gravityX * x[i]
                dy[i] -= gravityY * y[i]
                val length = max(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), MIN_DISTANCE)
                val move = min(length, temperature) / length
                x[i] += dx[i] * move
                y[i] += dy[i] * move
            }
        }

        separate(x, y, boxWidth, boxHeight)

        val left = x.min() - boxWidth / 2
        val top = y.min() - boxHeight / 2
        val centres = (0 until count).map { Point(x[it] - left, y[it] - top) }
        return Layout(centres, x.max() - left + boxWidth / 2, y.max() - top + boxHeight / 2)
    }

    /** Pushes overlapping boxes apart along the axis where they overlap least. */
    private fun separate(x: DoubleArray, y: DoubleArray, w: Double, h: Double) {
        repeat(200) {
            var moved = false
            for (i in x.indices) {
                for (j in i + 1 until x.size) {
                    val ux = x[j] - x[i]
                    val uy = y[j] - y[i]
                    val overlapX = w - abs(ux)
                    val overlapY = h - abs(uy)
                    if (overlapX <= 0 || overlapY <= 0) continue
                    moved = true
                    if (overlapX / w < overlapY / h) {
                        val shift = (overlapX / 2 + EPSILON) * (if (ux < 0) -1 else 1)
                        x[i] -= shift; x[j] += shift
                    } else {
                        val shift = (overlapY / 2 + EPSILON) * (if (uy < 0) -1 else 1)
                        y[i] -= shift; y[j] += shift
                    }
                }
            }
            if (!moved) return
        }
    }

    private const val EPSILON = 1e-6
    private val GOLDEN_ANGLE = Math.PI * (3 - sqrt(5.0))
}

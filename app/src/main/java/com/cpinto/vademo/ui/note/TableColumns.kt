package com.cpinto.vademo.ui.note

/**
 * Widths for a table's columns, sized by their content.
 *
 * [min] is each column's narrowest width (its longest word) and [max] its
 * width with no wrapping at all. When every column fits unwrapped, each gets
 * its [max]. Otherwise each starts at its [min] and the space left over is
 * shared out in proportion to how much more each column would like, so a
 * long column of reasons takes most of the width and a short column of
 * scores stays narrow. When even the [min] widths do not fit, the result is
 * wider than [available] and the table scrolls sideways.
 */
fun columnWidths(min: List<Int>, max: List<Int>, available: Int): List<Int> {
    require(min.size == max.size) { "min and max must have one entry per column" }
    val wanted = max.zip(min) { m, n -> maxOf(m, n) }
    if (wanted.sum() <= available) return wanted
    if (min.sum() >= available) return min

    val spare = available - min.sum()
    val slack = wanted.zip(min) { w, n -> w - n }
    val totalSlack = slack.sum()
    val widths = min.indices.map { min[it] + (spare.toLong() * slack[it] / totalSlack).toInt() }.toMutableList()
    // Rounding down leaves a few pixels over; the column wanting most takes them.
    widths[slack.indices.maxBy { slack[it] }] += available - widths.sum()
    return widths
}

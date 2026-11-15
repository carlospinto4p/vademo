package com.cpinto.vademo.kb

import com.cpinto.vademo.kb.Diagram.Direction

/** A box's place: top-left corner and size, in dp, in the whole diagram. */
data class Placed(val x: Double, val y: Double, val width: Double, val height: Double) {
    val centreX: Double get() = x + width / 2
    val centreY: Double get() = y + height / 2
}

data class DiagramPlaces(val boxes: Map<String, Placed>, val width: Double, val height: Double)

/**
 * Where every box of a [Diagram] goes, in dp.
 *
 * When every node has a `position` the file's places are used, as tyfold does.
 * Otherwise everything is laid out from the diagram's shape, following tyfold's
 * `src/lib/graph/canvas/layout.ts`: a layered layout in the diagram's direction,
 * run once per group, innermost groups first, so each group is sized around
 * what it holds and then placed as one box in the group around it. An edge
 * reaching into another group orders the two boxes it runs between at each
 * level. tyfold runs dagre for each layer; [rank] is a small layered layout of
 * the same kind (layers by longest path, order by barycentre), since dagre is
 * a JavaScript library.
 *
 * After each run, `settle` (tyfold's `settle` and `clearRays`) moves a box
 * with an arrow leaving its group sideways until the arrow's line misses every
 * box ahead of it, and brings the box at the arrow's other end level with that
 * line where it is free to move. An arrow still over a box when drawn goes
 * round it ([EdgeRoute]).
 *
 * [nodeSizes] gives the size of every box that is not a group; the screen
 * measures its text to know it.
 */
object DiagramLayout {

    /** tyfold's `GROUP_PADDING`, `DEFAULT_GROUP_SIZE` and spacings, read as dp. */
    const val PAD_TOP = 48.0
    const val PAD_SIDE = 20.0
    const val PAD_BOTTOM = 20.0
    private val EMPTY_GROUP = Box(320.0, 200.0)
    private val OUTER = Spacing(rank = 90.0, node = 48.0)
    private val INNER = Spacing(rank = 60.0, node = 28.0)

    data class Box(val width: Double, val height: Double)
    private data class Spacing(val rank: Double, val node: Double)

    /**
     * [moves] are where boxes were dragged on this phone, as offsets in dp
     * from where the layout puts them; they are never in the file. A moved
     * group takes what it holds along, and every group grows to keep holding
     * a box moved past its edge.
     */
    fun place(diagram: Diagram, nodeSizes: Map<String, Box>, moves: Map<String, Position> = emptyMap()): DiagramPlaces {
        val nodes = diagram.nodes.distinctBy { it.id }
        if (nodes.isEmpty()) return DiagramPlaces(emptyMap(), 0.0, 0.0)
        val byId = nodes.associateBy { it.id }
        val parentOf = parents(nodes, byId)
        val relative = mutableMapOf<String, Placed>()
        val fromFile = nodes.all { it.position != null }
        if (fromFile) fileLayout(nodes, parentOf, nodeSizes, relative) else autoLayout(diagram, nodes, parentOf, nodeSizes, relative)
        val placed = absolute(nodes, parentOf, relative)
        return if (moves.isEmpty()) normalized(placed) else normalized(moved(nodes, parentOf, placed, moves))
    }

    private fun moved(
        nodes: List<DiagramNode>,
        parentOf: Map<String, String>,
        placed: Map<String, Placed>,
        moves: Map<String, Position>,
    ): Map<String, Placed> {
        val out = placed.mapValues { (id, p) ->
            var dx = 0.0
            var dy = 0.0
            for (step in chain(id, parentOf)) moves[step]?.let { dx += it.x; dy += it.y }
            p.copy(x = p.x + dx, y = p.y + dy)
        }.toMutableMap()
        // Innermost groups first, so a grown group is held by the group around it.
        for (group in nodes.filter { it.isGroup }.sortedByDescending { chain(it.id, parentOf).size }) {
            val held = nodes.filter { parentOf[it.id] == group.id }.mapNotNull { out[it.id] }
            if (held.isEmpty()) continue
            val g = out.getValue(group.id)
            val left = minOf(g.x, held.minOf { it.x } - PAD_SIDE)
            val top = minOf(g.y, held.minOf { it.y } - PAD_TOP)
            val right = maxOf(g.x + g.width, held.maxOf { it.x + it.width } + PAD_SIDE)
            val bottom = maxOf(g.y + g.height, held.maxOf { it.y + it.height } + PAD_BOTTOM)
            out[group.id] = Placed(left, top, right - left, bottom - top)
        }
        return out
    }

    /**
     * The group each node is directly inside, or no entry at the top level. A
     * parent that is missing, is not a group, or leads back round to the node
     * is ignored, so a mistaken file still draws.
     */
    private fun parents(nodes: List<DiagramNode>, byId: Map<String, DiagramNode>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (node in nodes) {
            val parent = node.parent?.let(byId::get)?.takeIf { it.isGroup && it.id != node.id } ?: continue
            out[node.id] = parent.id
        }
        for (node in nodes) {
            val seen = mutableSetOf(node.id)
            var at = out[node.id]
            while (at != null) {
                if (!seen.add(at)) { out.remove(node.id); break }
                at = out[at]
            }
        }
        return out
    }

    private fun chain(id: String, parentOf: Map<String, String>): List<String> =
        generateSequence(id) { parentOf[it] }.toList()

    private fun fileLayout(
        nodes: List<DiagramNode>,
        parentOf: Map<String, String>,
        nodeSizes: Map<String, Box>,
        out: MutableMap<String, Placed>,
    ) {
        // Deepest first, so a group without a stored size can be sized around what it holds.
        val deepestFirst = nodes.sortedByDescending { chain(it.id, parentOf).size }
        for (node in deepestFirst) {
            val at = node.position!!
            val size = when {
                node.size != null -> Box(node.size.width, node.size.height)
                !node.isGroup -> nodeSizes[node.id] ?: DEFAULT_NODE
                else -> {
                    val held = nodes.filter { parentOf[it.id] == node.id }.mapNotNull { out[it.id] }
                    if (held.isEmpty()) EMPTY_GROUP
                    else Box(held.maxOf { it.x + it.width } + PAD_SIDE, held.maxOf { it.y + it.height } + PAD_BOTTOM)
                }
            }
            out[node.id] = Placed(at.x, at.y, size.width, size.height)
        }
    }

    private fun autoLayout(
        diagram: Diagram,
        nodes: List<DiagramNode>,
        parentOf: Map<String, String>,
        nodeSizes: Map<String, Box>,
        out: MutableMap<String, Placed>,
    ) {
        val chains = nodes.associate { it.id to chain(it.id, parentOf) }

        // Each edge lifted to the two boxes directly inside `container` that hold its ends.
        fun linksIn(container: String?, members: Set<String>): List<Pair<String, String>> {
            fun lift(id: String): String? {
                val path = chains[id] ?: return null
                val i = if (container == null) path.size else path.indexOf(container)
                return if (i > 0) path[i - 1] else null
            }
            return diagram.edges.mapNotNull { edge ->
                val source = lift(edge.source) ?: return@mapNotNull null
                val target = lift(edge.target) ?: return@mapNotNull null
                if (source == target || source !in members || target !in members) null else source to target
            }.distinct()
        }

        val sizes = mutableMapOf<String, Box>()
        fun sizeOf(node: DiagramNode) = sizes[node.id] ?: nodeSizes[node.id] ?: DEFAULT_NODE
        val direction = diagram.direction
        val byId = nodes.associateBy { it.id }
        val edges =diagram.edges.filter { it.source in chains && it.target in chains }

        // Where an arrow's end `id` is in `container`: the box directly inside it
        // that holds the end, and how far across that box the arrow's line is. The
        // end may be deep in a group; that group is already laid out, so the end's
        // place in it is known. Null when the end is not inside `container`.
        fun endIn(id: String, container: String?): End? {
            val path = chains.getValue(id)
            val i = if (container == null) path.size else path.indexOf(container)
            if (i <= 0) return null
            val steps = path.subList(0, i - 1)
            val half = breadth(sizeOf(byId.getValue(id)), direction) / 2
            return End(path[i - 1], half + steps.sumOf { step -> out[step]?.let { cross(it, direction) } ?: 0.0 }, steps.isNotEmpty())
        }

        // What `rank` cannot see, put right in place, in two steps. An arrow from
        // deep in one box to a plain box that has no other arrow here is made
        // straight by moving the plain box level with it, unless that would land
        // it on another box. Then an arrow with one end outside `container` is
        // kept off the boxes ahead of the end inside ([clearRays]). That step is
        // last, so nothing moves a box back into such an arrow's way.
        fun settle(container: String?, placed: Map<String, Spot>, boxes: Map<String, Box>, gap: Double) {
            if (boxes.isEmpty()) return
            val ends = edges.map { endIn(it.source, container) to endIn(it.target, container) }
            val arrows = mutableMapOf<String, Int>()
            for ((source, target) in ends) {
                source?.let { arrows.merge(it.member, 1, Int::plus) }
                target?.let { arrows.merge(it.member, 1, Int::plus) }
            }
            for ((source, target) in ends) {
                if (source == null || target == null || source.member == target.member || source.deep == target.deep) continue
                val (fixed, free) = if (source.deep) source to target else target to source
                val at = placed[free.member] ?: continue
                val box = boxes[free.member] ?: continue
                val anchor = placed[fixed.member] ?: continue
                if (arrows[free.member] != 1) continue
                val to = anchor.cross + fixed.offset - free.offset
                val taken = boxes.any { (other, size) ->
                    val o = placed.getValue(other)
                    other != free.member && beside(o, size, at, box, direction) &&
                        o.cross < to + breadth(box, direction) + gap && to < o.cross + breadth(size, direction) + gap
                }
                if (!taken) at.cross = to
            }
            val least = minOf(0.0, placed.values.minOf { it.cross })
            for (p in placed.values) p.cross -= least

            val rays = ends.flatMap { (source, target) ->
                listOfNotNull(
                    source?.takeIf { target == null && it.member in boxes }?.let { Ray(it.member, it.offset, ahead = true) },
                    target?.takeIf { source == null && it.member in boxes }?.let { Ray(it.member, it.offset, ahead = false) },
                )
            }
            clearRays(placed, boxes, rays, direction, gap)
        }

        val groups = nodes.filter { it.isGroup }.sortedByDescending { chains.getValue(it.id).size }
        for (group in groups) {
            val members = nodes.filter { parentOf[it.id] == group.id }
            val boxes = members.associate { it.id to sizeOf(it) }
            val spots = spots(rank(members.map { it.id }, boxes, linksIn(group.id, boxes.keys), INNER, direction), direction)
            settle(group.id, spots, boxes, INNER.node)
            val placed = spots.mapValues { (_, s) -> s.position(direction) }
            var width = 0.0
            var height = 0.0
            for ((id, box) in boxes) {
                val corner = placed.getValue(id)
                out[id] = Placed(corner.x + PAD_SIDE, corner.y + PAD_TOP, box.width, box.height)
                width = maxOf(width, corner.x + box.width)
                height = maxOf(height, corner.y + box.height)
            }
            sizes[group.id] = if (members.isEmpty()) EMPTY_GROUP
            else Box(maxOf(EMPTY_GROUP.width, width + 2 * PAD_SIDE), height + PAD_TOP + PAD_BOTTOM)
        }

        val top = nodes.filter { it.id !in parentOf }
        val boxes = top.associate { it.id to sizeOf(it) }
        val spots = spots(rank(top.map { it.id }, boxes, linksIn(null, boxes.keys), OUTER, direction), direction)
        settle(null, spots, boxes, OUTER.node)
        for ((id, box) in boxes) {
            val corner = spots.getValue(id).position(direction)
            out[id] = Placed(corner.x, corner.y, box.width, box.height)
        }
    }

    /** A box's corner along the arrows ([main]) and across them ([cross]), moved while settling. */
    private class Spot(var main: Double, var cross: Double) {
        fun position(direction: Direction) = if (direction == Direction.LR) Position(main, cross) else Position(cross, main)
    }

    private fun spots(placed: Map<String, Position>, direction: Direction): Map<String, Spot> =
        placed.mapValues { (_, p) -> if (direction == Direction.LR) Spot(p.x, p.y) else Spot(p.y, p.x) }

    /** One end of an arrow: the box it is in at this level, and how far across that box its line runs. */
    private class End(val member: String, val offset: Double, val deep: Boolean)

    /**
     * One end of an arrow whose other end is outside the container being laid
     * out. The arrow runs straight out of [member], [offset] across it, towards
     * the far side of the container ([ahead], a source) or the near side (a target).
     */
    private class Ray(val member: String, val offset: Double, val ahead: Boolean)

    /** How far an arrow's line stays from a box it passes. */
    private const val RAY_MARGIN = 16.0

    private fun length(box: Box, direction: Direction) = if (direction == Direction.LR) box.width else box.height
    private fun breadth(box: Box, direction: Direction) = if (direction == Direction.LR) box.height else box.width
    private fun cross(place: Placed, direction: Direction) = if (direction == Direction.LR) place.y else place.x

    /** Whether two boxes share some of the run along the arrows, so one is beside the other. */
    private fun beside(a: Spot, aBox: Box, b: Spot, bBox: Box, direction: Direction) =
        a.main < b.main + length(bBox, direction) && b.main < a.main + length(aBox, direction)

    /**
     * Moves each ray's box across the flow, in place, until the ray misses every
     * box in its way. The box goes past every box beside it that lay further
     * across: left between two of them, its ray would cross an arrow from a box
     * the ray passes to one of those. Any other box it would then sit on is
     * pushed along. Boxes only ever move one way, so it settles; the pass limit
     * is for a shape that would not. tyfold's `clearRays`.
     */
    private fun clearRays(placed: Map<String, Spot>, boxes: Map<String, Box>, rays: List<Ray>, direction: Direction, gap: Double) {
        if (rays.isEmpty()) return

        // The boxes beside `id` along the flow that sat at `was` or further across.
        fun beyond(id: String, was: Double): List<String> {
            val at = placed.getValue(id)
            val box = boxes.getValue(id)
            return boxes.filter { (other, size) ->
                val o = placed.getValue(other)
                other != id && o.cross >= was && beside(o, size, at, box, direction)
            }.keys.toList()
        }

        // `id` moved from `was`: every box beside it that it now reaches goes past it.
        fun push(id: String, was: Double) {
            val at = placed.getValue(id)
            val box = boxes.getValue(id)
            for (other in beyond(id, was)) {
                val o = placed.getValue(other)
                val size = boxes.getValue(other)
                val reached = o.cross < at.cross + breadth(box, direction) + gap && at.cross < o.cross + breadth(size, direction) + gap
                if (!reached) continue
                val from = o.cross
                o.cross = at.cross + breadth(box, direction) + gap
                push(other, from)
            }
        }

        for (pass in 0..boxes.size * 2) {
            var moved = false
            for (ray in rays) {
                val at = placed.getValue(ray.member)
                val box = boxes.getValue(ray.member)
                val inWay = boxes.filter { (id, size) ->
                    val o = placed.getValue(id)
                    id != ray.member &&
                        if (ray.ahead) o.main >= at.main + length(box, direction) else o.main + length(size, direction) <= at.main
                }.map { (id, size) ->
                    val o = placed.getValue(id)
                    (o.cross - RAY_MARGIN) to (o.cross + breadth(size, direction) + RAY_MARGIN)
                }.sortedBy { it.first }
                val line = at.cross + ray.offset
                var clear = line
                for ((from, to) in inWay) if (clear > from && clear < to) clear = to
                if (clear == line) continue
                val was = at.cross
                at.cross += clear - line
                for (other in beyond(ray.member, was)) {
                    at.cross = maxOf(at.cross, placed.getValue(other).cross + breadth(boxes.getValue(other), direction) + gap)
                }
                push(ray.member, was)
                moved = true
            }
            if (!moved) break
        }

        val least = placed.values.minOf { it.cross }
        for (p in placed.values) p.cross -= least
    }

    /** From places relative to each parent group to places in the whole diagram. */
    private fun absolute(nodes: List<DiagramNode>, parentOf: Map<String, String>, relative: Map<String, Placed>): Map<String, Placed> =
        nodes.associate { node ->
            val own = relative.getValue(node.id)
            var x = own.x
            var y = own.y
            var at = parentOf[node.id]
            while (at != null) {
                val parent = relative.getValue(at)
                x += parent.x
                y += parent.y
                at = parentOf[at]
            }
            node.id to own.copy(x = x, y = y)
        }

    /** Shifted so the diagram starts at 0, 0, with its size. */
    private fun normalized(boxes: Map<String, Placed>): DiagramPlaces {
        val left = boxes.values.minOf { it.x }
        val top = boxes.values.minOf { it.y }
        val shifted = boxes.mapValues { (_, p) -> p.copy(x = p.x - left, y = p.y - top) }
        return DiagramPlaces(
            shifted,
            shifted.values.maxOf { it.x + it.width },
            shifted.values.maxOf { it.y + it.height },
        )
    }

    /**
     * One layered layout of [ids]: top-left corners, the result's own top-left
     * at 0, 0. Cycles are broken by turning round the edges that close them;
     * a node's layer is the longest path to it; long edges get placeholders in
     * the layers they cross, so they take part in ordering; each layer is then
     * ordered by the mean position of its neighbours, sweeping down and up and
     * keeping the order with fewest crossings; each layer is centred across.
     */
    private fun rank(
        ids: List<String>,
        boxes: Map<String, Box>,
        links: List<Pair<String, String>>,
        spacing: Spacing,
        direction: Direction,
    ): Map<String, Position> {
        val n = ids.size
        if (n == 0) return emptyMap()
        val index = ids.withIndex().associate { (i, id) -> id to i }
        val out = List(n) { mutableListOf<Int>() }
        for ((a, b) in links) out[index.getValue(a)] += index.getValue(b)

        // Break cycles: an edge to a node still on the depth-first path is turned round.
        val state = IntArray(n)
        val dag = mutableListOf<Pair<Int, Int>>()
        fun visit(v: Int) {
            state[v] = 1
            for (w in out[v]) {
                when (state[w]) {
                    0 -> { dag += v to w; visit(w) }
                    1 -> dag += w to v
                    else -> dag += v to w
                }
            }
            state[v] = 2
        }
        for (v in 0 until n) if (state[v] == 0) visit(v)
        val edges = dag.distinct()

        // Layers by longest path, in topological order.
        val layer = IntArray(n)
        val indegree = IntArray(n)
        val next = List(n) { mutableListOf<Int>() }
        for ((a, b) in edges) { next[a] += b; indegree[b]++ }
        val ready = java.util.PriorityQueue<Int>()
        for (v in 0 until n) if (indegree[v] == 0) ready += v
        while (ready.isNotEmpty()) {
            val v = ready.poll()
            for (w in next[v]) {
                layer[w] = maxOf(layer[w], layer[v] + 1)
                if (--indegree[w] == 0) ready += w
            }
        }

        // Placeholders for edges crossing more than one layer: ids n, n+1, …
        val layerOf = layer.toMutableList()
        val up = MutableList(n) { mutableListOf<Int>() }
        for ((a, b) in edges) {
            var from = a
            for (l in layer[a] + 1 until layer[b]) {
                val dummy = layerOf.size
                layerOf += l
                up += mutableListOf(from)
                from = dummy
            }
            up[b] += from
        }
        val down = List(layerOf.size) { mutableListOf<Int>() }
        for (v in up.indices) for (u in up[v]) down[u] += v

        val layers = (0..layerOf.max()).map { l -> layerOf.indices.filter { layerOf[it] == l }.toMutableList() }
        fun positions(): IntArray {
            val pos = IntArray(layerOf.size)
            for (row in layers) row.forEachIndexed { i, v -> pos[v] = i }
            return pos
        }
        fun crossings(): Int {
            val pos = positions()
            var count = 0
            for (row in layers.drop(1)) {
                val pairs = row.flatMap { v -> up[v].map { pos[it] to pos[v] } }
                for (i in pairs.indices) for (j in i + 1 until pairs.size) {
                    val (a1, b1) = pairs[i]
                    val (a2, b2) = pairs[j]
                    if ((a1 - a2).toLong() * (b1 - b2) < 0) count++
                }
            }
            return count
        }
        fun sweep(rows: List<MutableList<Int>>, neighbours: List<List<Int>>) {
            for (row in rows) {
                val pos = positions()
                val centre = row.associateWith { v ->
                    val around = neighbours[v]
                    if (around.isEmpty()) pos[v].toDouble() else around.map { pos[it] }.average()
                }
                row.sortBy { centre.getValue(it) }
            }
        }
        var best = layers.map { it.toList() }
        var fewest = crossings()
        repeat(12) { pass ->
            if (pass % 2 == 0) sweep(layers.drop(1), up) else sweep(layers.dropLast(1).reversed(), down)
            val now = crossings()
            if (now < fewest) { fewest = now; best = layers.map { it.toList() } }
        }

        val main = { box: Box -> if (direction == Direction.LR) box.width else box.height }
        val cross = { box: Box -> if (direction == Direction.LR) box.height else box.width }
        val sizeOf = { v: Int -> if (v < n) boxes.getValue(ids[v]) else Box(0.0, 0.0) }
        val rowBreadth = best.map { row -> row.sumOf { cross(sizeOf(it)) } + spacing.node * (row.size - 1).coerceAtLeast(0) }
        val widest = rowBreadth.max()
        val result = mutableMapOf<String, Position>()
        var along = 0.0
        best.forEachIndexed { l, row ->
            val length = row.filter { it < n }.maxOfOrNull { main(sizeOf(it)) } ?: 0.0
            var across = (widest - rowBreadth[l]) / 2
            for (v in row) {
                val box = sizeOf(v)
                if (v < n) {
                    val a = along + (length - main(box)) / 2
                    result[ids[v]] = if (direction == Direction.LR) Position(a, across) else Position(across, a)
                }
                across += cross(box) + spacing.node
            }
            along += length + spacing.rank
        }
        return result
    }

    private val DEFAULT_NODE = Box(200.0, 64.0)
}

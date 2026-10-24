package arena.engine

/**
 * Points of the board (foods, heads) sorted into square buckets, for exact "nearest" questions without looking at every
 * point. The bucket side divides both board sides, so every bucket is whole, and a bucket first reached r rings away
 * from the one holding (x, y) is at least (r - 1) * side + 1 cells away along one axis (the shorter way round while
 * toroidal). Each search walks the rings outward and stops at the first one that cannot hold anything as near as what
 * it already found, so its answer is the full scan's, ties included (the lowest id wins, as the scans in list order).
 * Boards whose sides share no divisor from 4 up keep the full scans ([enabled] false).
 */
class PointGrid(private val w: Int, private val h: Int, expected: Int) {
    private val side = bucketSide(w, h, expected)
    val enabled = side > 0
    private val nbx = if (enabled) w / side else 1
    private val nby = if (enabled) h / side else 1
    private val start = IntArray(nbx * nby + 1)
    private var px = IntArray(0)
    private var py = IntArray(0)
    private var ids = IntArray(0)
    private var bucketOf = IntArray(0)

    /** Per-thread memory of the buckets a search already looked at. */
    inner class Visits {
        private val marks = IntArray(nbx * nby)
        private var stamp = 0

        fun next() {
            if (++stamp == Int.MAX_VALUE) {
                marks.fill(0)
                stamp = 1
            }
        }

        fun first(b: Int): Boolean {
            if (marks[b] == stamp) return false
            marks[b] = stamp
            return true
        }
    }

    /** The [n] points (x[k], y[k]) inside the board, each with its [id] (a food's list index, a snake's id). */
    fun build(n: Int, x: IntArray, y: IntArray, id: IntArray) {
        if (!enabled) return
        if (px.size < n) {
            px = IntArray(n)
            py = IntArray(n)
            ids = IntArray(n)
            bucketOf = IntArray(n)
        }
        start.fill(0)
        for (k in 0 until n) {
            val b = (y[k] / side) * nbx + x[k] / side
            bucketOf[k] = b
            start[b + 1]++
        }
        for (b in 0 until nbx * nby) start[b + 1] += start[b]
        val fill = start.copyOf(nbx * nby)
        for (k in 0 until n) {
            val at = fill[bucketOf[k]]++
            px[at] = x[k]
            py[at] = y[k]
            ids[at] = id[k]
        }
    }

    /** The id of the point nearest (x, y) by [Arena.dist2], the lowest id on ties; -1 when there is none. */
    fun nearest(a: Arena, x: Int, y: Int, tor: Boolean, v: Visits): Int {
        var bestD = Long.MAX_VALUE
        var best = -1
        search(x, y, tor, v, { lb -> best >= 0 && lb.toLong() * lb > bestD }) { from, to ->
            for (k in from until to) {
                val d = a.dist2(x, y, px[k], py[k], tor)
                if (d < bestD || (d == bestD && ids[k] < best)) {
                    bestD = d
                    best = ids[k]
                }
            }
        }
        return best
    }

    /** Whether a point other than [exclude]'s lies at [Arena.dist2] at most [d] from (x, y). */
    fun anyWithin(a: Arena, x: Int, y: Int, d: Long, tor: Boolean, exclude: Int, v: Visits): Boolean {
        var found = false
        search(x, y, tor, v, { lb -> found || lb.toLong() * lb > d }) { from, to ->
            if (found) return@search
            for (k in from until to) {
                if (ids[k] != exclude && a.dist2(x, y, px[k], py[k], tor) <= d) {
                    found = true
                    return@search
                }
            }
        }
        return found
    }

    /**
     * FoodRace's question for one food at (x, y): the nearest point in Manhattan moves (the shorter way round while
     * toroidal), its id (the lowest on ties) and the second nearest distance (equal to the first when two tie), into
     * out[0], out[1], out[2]; Int.MAX_VALUE, -1, Int.MAX_VALUE when there are no points.
     */
    fun race(x: Int, y: Int, tor: Boolean, v: Visits, out: IntArray) {
        var b = Int.MAX_VALUE
        var s = Int.MAX_VALUE
        var o = -1
        search(x, y, tor, v, { lb -> s != Int.MAX_VALUE && lb > s }) { from, to ->
            for (k in from until to) {
                var dx = if (px[k] > x) px[k] - x else x - px[k]
                var dy = if (py[k] > y) py[k] - y else y - py[k]
                if (tor) {
                    if (w - dx < dx) dx = w - dx
                    if (h - dy < dy) dy = h - dy
                }
                val d = dx + dy
                val i = ids[k]
                if (d < b || (d == b && i < o)) {
                    s = b
                    b = d
                    o = i
                } else if (d < s) {
                    s = d
                }
            }
        }
        out[0] = b
        out[1] = o
        out[2] = s
    }

    /**
     * Calls [visit] with the point range of every bucket, ring by ring outward from the one holding (x, y), each bucket
     * once; before each ring [stop] gets the least distance along one axis of anything in it and may end the search.
     */
    private inline fun search(x: Int, y: Int, tor: Boolean, v: Visits, stop: (Int) -> Boolean, visit: (Int, Int) -> Unit) {
        val bx = x / side
        val by = y / side
        val rings = maxOf(nbx, nby)
        v.next()
        for (r in 0..rings) {
            if (stop(if (r == 0) 0 else (r - 1) * side + 1)) return
            for (oy in -r..r) {
                var cy = by + oy
                if (tor) cy = Math.floorMod(cy, nby) else if (cy < 0 || cy >= nby) continue
                val edge = oy == -r || oy == r
                var ox = -r
                while (ox <= r) {
                    var cx = bx + ox
                    val inside = if (tor) {
                        cx = Math.floorMod(cx, nbx)
                        true
                    } else {
                        cx in 0 until nbx
                    }
                    if (inside) {
                        val b = cy * nbx + cx
                        if (v.first(b)) visit(start[b], start[b + 1])
                    }
                    // inner rows of a ring hold only its two ends
                    ox = if (edge || ox == r) ox + 1 else r
                }
            }
        }
    }

    private companion object {
        const val MIN_SIDE = 4

        /** A divisor of both board sides (4 or more) near sqrt(cells / points): about one point per bucket; 0: none. */
        fun bucketSide(w: Int, h: Int, points: Int): Int {
            var g = w
            var b = h
            while (b != 0) {
                val t = g % b
                g = b
                b = t
            }
            val target = Math.sqrt(w.toDouble() * h / maxOf(1, points))
            var best = 0
            for (d in MIN_SIDE..g) {
                if (g % d == 0 && (best == 0 || Math.abs(d - target) < Math.abs(best - target))) best = d
            }
            return best
        }
    }
}

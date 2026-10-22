package arena.engine

/**
 * SNAKES.py's per-snake views of the board: the target food (get_target_food) and the 23-value state (get_state +
 * planning_features), from the arena's collision grid. One instance per thread (it keeps scratch arrays).
 */
class Features(private val a: Arena) {
    private val ownStamp = IntArray(a.cells)
    private val ownCnt = IntArray(a.cells)
    private var stamp = 0
    private val seen = IntArray(a.cells)
    private var seenStamp = 0
    private val stack = IntArray(a.cells + 8)
    private var grownTail = NONE

    /** Perception.marks / queue / newMark() of the tactics deciding on this thread. */
    val marks = IntArray(a.cells)
    val queue = IntArray(a.cells + 1)
    val markCounter = IntArray(1)
    private var head = NONE
    private lateinit var s: Snake

    var targetX = 0
        private set
    var targetY = 0
        private set

    /**
     * Binds [snake]: counts its cells. [ate]: it ate after the grid was built, so its last cell (the tail put
     * back) is not in the grid, as in SNAKES.py where the grid is rebuilt after the move and before eating.
     */
    fun bind(snake: Snake, ate: Boolean) {
        s = snake
        if (++stamp == Int.MAX_VALUE) {
            ownStamp.fill(0)
            stamp = 1
        }
        val b = snake.body
        for (i in 0 until b.size) {
            val c = b[i]
            if (c < 0) continue
            if (ownStamp[c] != stamp) {
                ownStamp[c] = stamp
                ownCnt[c] = 0
            }
            ownCnt[c]++
        }
        head = b.head
        grownTail = if (ate) b.tail else NONE
    }

    private fun own(c: Int) = if (ownStamp[c] == stamp) ownCnt[c] else 0

    /** c in segmentos[1:] */
    private fun inNeckToTail(c: Int) = own(c) - (if (c == head) 1 else 0) > 0

    /** the snake's own name is in collision_grid[c] */
    private fun meInGrid(c: Int) = own(c) - (if (c == grownTail) 1 else 0) > 0

    /** another snake's name is in collision_grid[c] */
    private fun othersInGrid(c: Int) = a.distinct[c] - (if (meInGrid(c)) 1 else 0) > 0

    /** get_target_food: the nearest food (first in list order on ties); DQN snakes prefer foods no rival is nearer. */
    fun target() {
        val w = a.width
        val tor = s.score < Rules.WALL_COLLISION
        val foods = a.foods
        val n = a.foodCount
        var best = -1
        if (s.kind == Kind.DQN && n > 1) {
            var bestD = Long.MAX_VALUE
            for (i in 0 until n) {
                val f = foods[i]
                val fx = f % w
                val fy = f / w
                val d = a.dist2(s.hx, s.hy, fx, fy, tor)
                if (d >= bestD) continue
                var rival = Long.MAX_VALUE
                for (o in a.snakes) {
                    if (o === s || !o.alive) continue
                    val r = a.dist2(o.hx, o.hy, fx, fy, tor)
                    if (r < rival) {
                        rival = r
                        if (r <= d) break
                    }
                }
                if (d < rival) {
                    bestD = d
                    best = f
                }
            }
        }
        if (best < 0) {
            var bestD = Long.MAX_VALUE
            for (i in 0 until n) {
                val f = foods[i]
                val d = a.dist2(s.hx, s.hy, f % w, f / w, tor)
                if (d < bestD) {
                    bestD = d
                    best = f
                }
            }
        }
        targetX = best % w
        targetY = best / w
    }

    /** get_state toward (targetX, targetY) into [out]: the 16 classic values, then (if [full]) the planning ones. */
    fun state(out: FloatArray, full: Boolean) {
        val w = a.width
        val h = a.height
        val tor = s.score < Rules.WALL_COLLISION
        val dx = Arena.shortestDelta(s.hx, targetX, w, tor)
        val dy = Arena.shortestDelta(s.hy, targetY, h, tor)
        out[0] = (dx / maxOf(1.0, w / 2.0)).coerceIn(-1.0, 1.0).toFloat()
        out[1] = (dy / maxOf(1.0, h / 2.0)).coerceIn(-1.0, 1.0).toFloat()
        out[2] = if (dy < 0) 1f else 0f
        out[3] = if (dy > 0) 1f else 0f
        out[4] = if (dx < 0) 1f else 0f
        out[5] = if (dx > 0) 1f else 0f
        for (d in 0 until 4) out[6 + d] = danger(s.hx + Rules.DX[d], s.hy + Rules.DY[d]).toFloat()
        for (d in 0 until 4) out[10 + d] = if (s.dir == d) 1f else 0f
        out[14] = if (s.selfOn) 1f else if (s.warning) 0.5f else 0f
        out[15] = if (s.score >= Rules.WALL_COLLISION) 1f else 0f
        if (!full) return
        val cap = if (s.kind == Kind.DQN) Rules.FLOOD_CAP_DQN else Rules.FLOOD_CAP
        val need = maxOf(1, minOf(s.body.size + 5, cap))
        for (d in 0 until 4) {
            out[16 + d] = if (out[6 + d] >= 1f) 0f
            else minOf(1.0, freeSpace(s.hx + Rules.DX[d], s.hy + Rules.DY[d], need).toDouble() / need).toFloat()
        }
        out[20] = minOf(1.0, s.body.size / 300.0).toFloat()
        out[21] = if (s.score >= Rules.ENEMY_COLLISION) 1f else 0f
        out[22] = minOf(1.0, s.turnsWithoutFood.toDouble() / Rules.STARVATION_TURNS).toFloat()
    }

    /** get_danger_level of the cell (x, y) (unwrapped). */
    private fun danger(x0: Int, y0: Int): Double {
        var x = x0
        var y = y0
        if (x < 0 || x >= a.width || y < 0 || y >= a.height) {
            if (s.score >= Rules.WALL_COLLISION) return 1.0
            x = Math.floorMod(x, a.width)
            y = Math.floorMod(y, a.height)
        }
        val c = y * a.width + x
        if (inNeckToTail(c)) {
            if (s.selfOn) return 1.0
            if (s.warning || s.score >= 60) return 0.5
        }
        if (s.score >= Rules.ENEMY_COLLISION && othersInGrid(c)) return 1.0
        return 0.0
    }

    private fun blocked(c: Int, avoidSelf: Boolean, avoidEnemy: Boolean, tail: Int): Boolean {
        if (a.distinct[c] == 0) return false
        if (avoidSelf && c != tail && meInGrid(c)) return true
        return avoidEnemy && othersInGrid(c)
    }

    /** free_space: cells reachable from (x, y), a depth-first search that stops once [cap] cells are seen. */
    private fun freeSpace(x0: Int, y0: Int, cap: Int): Int {
        val w = a.width
        val h = a.height
        val tor = s.score < Rules.WALL_COLLISION
        val avoidSelf = s.selfOn || s.warning || s.score >= 90
        val avoidEnemy = s.score >= Rules.ENEMY_COLLISION - 10
        val tail = s.body.tail
        var x = x0
        var y = y0
        if (tor) {
            x = Math.floorMod(x, w)
            y = Math.floorMod(y, h)
        } else if (x < 0 || x >= w || y < 0 || y >= h) {
            return 0
        }
        val start = y * w + x
        if (blocked(start, avoidSelf, avoidEnemy, tail)) return 0
        if (++seenStamp == Int.MAX_VALUE) {
            seen.fill(0)
            seenStamp = 1
        }
        seen[start] = seenStamp
        var count = 1
        var sp = 0
        stack[sp++] = start
        while (sp > 0 && count < cap) {
            val c = stack[--sp]
            val cx = c % w
            val cy = c / w
            for (d in 0 until 4) {
                var nx = cx + Rules.DX[d]
                var ny = cy + Rules.DY[d]
                if (tor) {
                    if (nx < 0) nx += w else if (nx >= w) nx -= w
                    if (ny < 0) ny += h else if (ny >= h) ny -= h
                } else if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                    continue
                }
                val n = ny * w + nx
                if (seen[n] != seenStamp && !blocked(n, avoidSelf, avoidEnemy, tail)) {
                    seen[n] = seenStamp
                    count++
                    stack[sp++] = n
                }
            }
        }
        return count
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
    }
}

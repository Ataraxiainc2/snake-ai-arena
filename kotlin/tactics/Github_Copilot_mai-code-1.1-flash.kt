class Tactic : SnakeTactic {
    private var w = 0
    private var h = 0
    private var tor = false
    private var blocked = IntArray(0)
    private var seen = IntArray(0)
    private var queue = IntArray(0)
    private var bg = 0
    private var sg = 0

    override fun decide(p: Perception): Int {
        val moves = p.validMoves
        if (moves.size == 1) return moves[0]
        w = p.width
        h = p.height
        tor = p.toroidal
        val n = w * h
        if (blocked.size != n) {
            blocked = IntArray(n)
            seen = IntArray(n)
            queue = IntArray(n)
            bg = 0
            sg = 0
        }
        if (++bg == Int.MAX_VALUE) {
            blocked.fill(0)
            bg = 1
        }
        val body = p.myBody
        val head = body[0]
        val tail = body[body.size - 1]
        val score = p.myScore
        val selfDanger = score > 98 || p.selfCollision || p.selfCollisionWarning
        val enemyDanger = score >= 298 || p.enemyCollision
        if (selfDanger) {
            val end = if (p.previousReward > 10f) body.size else body.size - 1
            for (i in 0 until end) blocked[body[i]] = bg
        }
        if (enemyDanger) for (e in p.enemies) {
            for (c in e.body) blocked[c] = bg
            val eh = e.body[0]
            val ex = eh % w
            val ey = eh / w
            for (d in 0..3) {
                val x = ex + Move.DX[d]
                val y = ey + Move.DY[d]
                val c = if (tor) ((y + h) % h) * w + (x + w) % w
                else if (x in 0 until w && y in 0 until h) y * w + x else -1
                if (c >= 0) blocked[c] = bg
            }
        }

        val hungry = p.turnsWithoutFood > 800
        var target = tail
        var targetCost = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val d = distance(head, p.foods[i])
            val rival = p.foodRivalDistance(i)
            val cost = if (hungry) d else d +
                (if (rival < d) 8 + 2 * (d - rival) else if (rival == d) 2 else 0)
            if (cost < targetCost) {
                targetCost = cost
                target = p.foods[i]
            }
        }

        val cap = minOf(body.size + 8, if (p.nanosLeft() < 300_000L) 80 else 500)
        var best = moves[0]
        var bestValue = Long.MIN_VALUE
        for (m in moves) {
            if (p.nanosLeft() < 40_000L) break
            val c = next(head, m)
            if (c < 0) continue
            val room = room(p, c, cap, tail, c in p.foods)
            val food = c in p.foods
            val value = if (hungry) {
                room.toLong() * 1_000_000_000L - distance(c, target) * 1_000L +
                    (if (m == p.myDirection) 1 else 0)
            } else {
                room.toLong() * 1_000_000_000L + (if (food) 1_000_000L else 0L) -
                    distance(c, target) * 1_000L - distance(c, tail) * 10L +
                    (if (m == p.myDirection) 1 else 0)
            }
            if (value > bestValue) {
                bestValue = value
                best = m
            }
        }
        return best
    }

    private fun next(c: Int, d: Int): Int {
        val x = c % w + Move.DX[d]
        val y = c / w + Move.DY[d]
        if (tor) return ((y + h) % h) * w + (x + w) % w
        return if (x in 0 until w && y in 0 until h) y * w + x else -1
    }

    private fun distance(a: Int, b: Int): Int {
        val dx = kotlin.math.abs(a % w - b % w)
        val dy = kotlin.math.abs(a / w - b / w)
        return if (tor) minOf(dx, w - dx) + minOf(dy, h - dy) else dx + dy
    }

    private fun room(p: Perception, start: Int, cap: Int, tail: Int, grows: Boolean): Int {
        if (blocked[start] == bg) return -1
        if (++sg == Int.MAX_VALUE) {
            seen.fill(0)
            sg = 1
        }
        seen[start] = sg
        queue[0] = start
        var read = 0
        var size = 1
        var reachesTail = start == tail && !grows
        while (read < size && size <= cap && !reachesTail) {
            if ((read and 15) == 0 && p.nanosLeft() < 40_000L) break
            val c = queue[read++]
            for (d in 0..3) {
                val x = c % w + Move.DX[d]
                val y = c / w + Move.DY[d]
                val k = if (tor) ((y + h) % h) * w + (x + w) % w
                else if (x in 0 until w && y in 0 until h) y * w + x else -1
                if (k >= 0 && seen[k] != sg) {
                    val atTail = k == tail && !grows
                    if (blocked[k] != bg || atTail) {
                        seen[k] = sg
                        queue[size++] = k
                        if (atTail) {
                            reachesTail = true
                            break
                        }
                    }
                }
            }
        }
        return if (reachesTail) 2 else if (size > cap) 1 else 0
    }
}
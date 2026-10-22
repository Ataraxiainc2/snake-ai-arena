class Tactic : SnakeTactic {
    private var w = 1
    private var h = 1
    private var tor = false

    override fun decide(p: Perception): Int {
        w = p.width
        h = p.height
        tor = p.toroidal
        val body = p.myBody
        val head = body[0]
        val tail = body[body.size - 1]
        val moves = p.validMoves
        if (moves.size == 1) return moves[0]

        val blocking = p.newMark()
        val selfDanger = p.selfCollision || p.selfCollisionWarning
        val enemyDanger = p.myScore >= 299 || p.enemyCollision
        if (selfDanger) {
            val end = if (p.previousReward > 10f) body.size else body.size - 1
            for (i in 0 until end) p.marks[body[i]] = blocking
        }
        if (enemyDanger) for (e in p.enemies) {
            for (c in e.body) p.marks[c] = blocking
            if (e.score >= 300) {
                val eh = e.body[0]
                for (d in 0..3) {
                    val x = eh % w + Move.DX[d]
                    val y = eh / w + Move.DY[d]
                    val c = if (tor) ((y + h) % h) * w + (x + w) % w
                    else if (x in 0 until w && y in 0 until h) y * w + x else -1
                    if (c >= 0) p.marks[c] = blocking
                }
            }
        }

        val hungry = p.turnsWithoutFood > 700
        var target = if (p.foods.isEmpty()) tail else p.foods[0]
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

        if (!selfDanger && !enemyDanger && p.myScore < 100) {
            var best = moves[0]
            var bestValue = Long.MIN_VALUE
            for (m in moves) {
                val c = next(head, m)
                if (c < 0) continue
                val value = (if (hasFood(p, c)) 1_000_000L else 0L) -
                    distance(c, target) * 100L + (if (m == p.myDirection) 1 else 0)
                if (value > bestValue) {
                    bestValue = value
                    best = m
                }
            }
            return best
        }

        val left = p.nanosLeft()
        val cap = minOf(body.size + 8, if (left < 300_000L) 48 else if (left < 1_000_000L) 120 else 400)
        var best = moves[0]
        var bestValue = Long.MIN_VALUE
        for (m in moves) {
            val c = next(head, m)
            if (c >= 0 && p.marks[c] != blocking) {
                best = m
                break
            }
        }
        var evaluated = false
        for (m in moves) {
            if (evaluated && p.nanosLeft() < 50_000L) break
            val c = next(head, m)
            if (c < 0 || p.marks[c] == blocking) continue
            evaluated = true
            val food = hasFood(p, c)
            val room = room(p, c, cap, tail, food, blocking)
            if (room < 0) continue
            val value = if (hungry) {
                room.toLong() * 1_000_000L - distance(c, target) * 100L +
                    (if (m == p.myDirection) 1 else 0)
            } else {
                room.toLong() * 1_000_000L + (if (food) 10_000L else 0L) -
                    distance(c, target) * 100L - distance(c, tail) +
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

    private fun hasFood(p: Perception, c: Int): Boolean {
        for (f in p.foods) if (f == c) return true
        return false
    }

    private fun room(p: Perception, start: Int, cap: Int, tail: Int, grows: Boolean, blocking: Int): Int {
        if (p.marks[start] == blocking) return -1
        val mark = p.newMark()
        val marks = p.marks
        val queue = p.queue
        marks[start] = mark
        queue[0] = start
        var read = 0
        var size = 1
        var reachesTail = start == tail && !grows
        while (read < size && size <= cap && !reachesTail) {
            if ((read and 15) == 0 && p.nanosLeft() < 50_000L) break
            val c = queue[read++]
            for (d in 0..3) {
                val x = c % w + Move.DX[d]
                val y = c / w + Move.DY[d]
                val k = if (tor) ((y + h) % h) * w + (x + w) % w
                else if (x in 0 until w && y in 0 until h) y * w + x else -1
                if (k >= 0 && marks[k] != mark) {
                    val atTail = k == tail && !grows
                    if (marks[k] != blocking) {
                        marks[k] = mark
                        queue[size++] = k
                        if (atTail) {
                            reachesTail = true
                            break
                        }
                    }
                }
            }
        }
        return if (reachesTail) cap + 1 else if (size > cap) cap else size
    }
}

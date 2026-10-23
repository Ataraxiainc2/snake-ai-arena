class Tactic : SnakeTactic {
    override fun decide(p: Perception): Int {
        val moves = p.validMoves
        if (moves.isEmpty()) return Move.UP
        if (moves.size == 1) return moves[0]
        val w = p.width
        val h = p.height
        val score = p.myScore
        val wrap = score < 200
        val enemyKills = score >= 300
        val body = p.myBody
        val len = body.size
        val head = body[0]
        val mk = p.marks
        val own = p.newMark()
        if (score >= 100) {
            for (c in body) mk[c] = own
        }

        val cells = IntArray(4)
        val eats = BooleanArray(4)
        val dead = BooleanArray(4)
        for (j in moves.indices) {
            val m = moves[j]
            var nx = head % w + Move.DX[m]
            var ny = head / w + Move.DY[m]
            if (wrap) {
                nx = (nx + w) % w
                ny = (ny + h) % h
            }
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                dead[j] = true
                continue
            }
            val c = ny * w + nx
            cells[j] = c
            eats[j] = p.foods.contains(c)
            dead[j] = blocked(p, c, own, eats[j], score)
        }

        var target = -1
        var targetKey = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val f = p.foods[i]
            val d = dist(head, f, w, h, wrap)
            val r = p.foodRivalDistance(i)
            val k = if (r < d) d + 2 * (d - r) + 6 else d
            if (k < targetKey) {
                targetKey = k
                target = f
            }
        }

        val hungry = p.turnsWithoutFood * 2 > p.starvationTurns
        val dWeight = if (hungry) 600L else 300L
        val cap = len + 6
        val useFlood = score >= 100 && p.nanosLeft() > 20_000L

        var best = moves[0]
        var bestKey = Long.MIN_VALUE
        for (j in moves.indices) {
            if (dead[j]) continue
            val c = cells[j]
            val eat = eats[j]
            val room = if (useFlood) flood(p, c, eat, own, cap) else len
            var key = 0L
            if (room >= len) key += 10_000_000L
            if (eat) key += 1_000_000L
            val dt = if (target >= 0) dist(c, target, w, h, wrap) else 0
            key += 200_000L - dWeight * dt
            key += 10L * minOf(room, cap)
            if (enemyKills) {
                for (e in p.enemies) {
                    if (e.body.isNotEmpty() && e.body.size >= len && dist(c, e.body[0], w, h, wrap) <= 1) {
                        key -= 500_000L
                    }
                }
            }
            if (key > bestKey) {
                bestKey = key
                best = moves[j]
            }
        }
        return best
    }

    // A cell a snake of ours cannot enter this turn. Below 100 nothing kills; from 100 our own body
    // (the tail excepted when we do not eat); from 300 every other body too.
    private fun blocked(p: Perception, c: Int, own: Int, eat: Boolean, score: Int): Boolean {
        var k = p.bodies[c].toInt()
        if (k == 0) return false
        if (p.marks[c] == own) {
            if (score >= 100 && (eat || c != p.myBody[p.myBody.size - 1])) return true
            k--
        }
        return score >= 300 && k > 0
    }

    // Free cells reachable from start (capped): a move that leaves too little room is a trap.
    private fun flood(p: Perception, start: Int, eat: Boolean, own: Int, cap: Int): Int {
        val w = p.width
        val h = p.height
        val wrap = p.myScore < 200
        val mk = p.marks
        val q = p.queue
        val vis = p.newMark()
        mk[start] = vis
        q[0] = start
        var n = 1
        var qi = 0
        while (qi < n) {
            val cur = q[qi++]
            val cx = cur % w
            val cy = cur / w
            for (m in 0..3) {
                var nx = cx + Move.DX[m]
                var ny = cy + Move.DY[m]
                if (wrap) {
                    nx = (nx + w) % w
                    ny = (ny + h) % h
                } else if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue
                }
                val c = ny * w + nx
                if (mk[c] == vis || blocked(p, c, own, eat, p.myScore)) continue
                mk[c] = vis
                q[n++] = c
                if (n >= cap) return n
            }
        }
        return n
    }

    private fun dist(a: Int, b: Int, w: Int, h: Int, wrap: Boolean): Int {
        var dx = maxOf(a % w - b % w, b % w - a % w)
        var dy = maxOf(a / w - b / w, b / w - a / w)
        if (wrap) {
            dx = minOf(dx, w - dx)
            dy = minOf(dy, h - dy)
        }
        return dx + dy
    }
}

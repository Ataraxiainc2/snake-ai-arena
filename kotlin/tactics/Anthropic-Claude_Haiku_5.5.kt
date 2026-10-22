class Tactic : SnakeTactic {
    override fun decide(p: Perception): Int {
        if (p.validMoves.isEmpty()) return Move.UP
        val w = p.width
        val h = p.height
        val head = p.myBody[0]
        val wrap = p.myScore < 200
        val len = p.myBody.size
        val hungry = p.turnsWithoutFood * 2 > p.starvationTurns
        val cap = if (p.nanosLeft() > 400_000L) minOf(maxOf(48, len + 8), 200) else 48

        var food = -1
        var foodDist = Int.MAX_VALUE
        for (f in p.foods) {
            val d = dist(head, f, w, h, wrap)
            if (d < foodDist) {
                foodDist = d
                food = f
            }
        }

        var best = -1
        var bestScore = Int.MIN_VALUE
        var fallback = -1
        var fallbackRoom = -1
        for (m in p.validMoves) {
            var nx = head % w + Move.DX[m]
            var ny = head / w + Move.DY[m]
            if (wrap) {
                nx = (nx + w) % w
                ny = (ny + h) % h
            } else if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                continue
            }
            val c = ny * w + nx
            val room = if (p.nanosLeft() > 50_000L) flood(c, p, wrap, cap) else 24
            if (p.bodies[c].toInt() != 0) {
                if (room > fallbackRoom) {
                    fallbackRoom = room
                    fallback = m
                }
                continue
            }

            var s = room * 2
            if (room < minOf(len, cap)) s -= 60
            if (food >= 0) {
                if (c == food) {
                    s += if (hungry) 300 else 180
                } else {
                    s += if (dist(c, food, w, h, wrap) < foodDist) 35 else -20
                }
            }
            for (e in p.enemies) {
                if (e.body.isEmpty()) continue
                if (dist(c, e.body[0], w, h, wrap) <= 1) {
                    s -= if (e.body.size >= len) 120 else 20
                }
            }
            if (s > bestScore) {
                bestScore = s
                best = m
            }
        }

        if (best >= 0) return best
        if (fallback >= 0) return fallback
        return p.validMoves[0]
    }

    private fun dist(a: Int, b: Int, w: Int, h: Int, wrap: Boolean): Int {
        val ex = a % w - b % w
        var dx = maxOf(ex, -ex)
        val ey = a / w - b / w
        var dy = maxOf(ey, -ey)
        if (wrap) {
            dx = minOf(dx, w - dx)
            dy = minOf(dy, h - dy)
        }
        return dx + dy
    }

    private fun flood(start: Int, p: Perception, wrap: Boolean, cap: Int): Int {
        val w = p.width
        val h = p.height
        val q = IntArray(cap)
        q[0] = start
        var n = 1
        var qh = 0
        while (qh < n) {
            val cur = q[qh++]
            val cx = cur % w
            val cy = cur / w
            for (m in 0 until 4) {
                var nx = cx + Move.DX[m]
                var ny = cy + Move.DY[m]
                if (wrap) {
                    nx = (nx + w) % w
                    ny = (ny + h) % h
                } else if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue
                }
                val c = ny * w + nx
                if (p.bodies[c].toInt() != 0) continue
                var known = false
                for (i in 0 until n) {
                    if (q[i] == c) {
                        known = true
                        break
                    }
                }
                if (known) continue
                if (n >= cap) return cap
                q[n++] = c
            }
        }
        return n
    }
}

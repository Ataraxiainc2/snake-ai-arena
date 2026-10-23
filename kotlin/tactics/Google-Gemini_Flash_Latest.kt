class Tactic : SnakeTactic {
    private var w = 0; private var h = 0; private var t = true; private var hd = 0
    private var bs = 0; private var ln = 0; private var gr = 0

    private fun nx(c: Int, d: Int): Int {
        val x = c % w + Move.DX[d]; val y = c / w + Move.DY[d]
        return if (t) (y + h) % h * w + (x + w) % w else if (x in 0 until w && y in 0 until h) y * w + x else -1
    }

    private fun ds(a: Int, c: Int): Int {
        val i = kotlin.math.abs(a % w - c % w); val j = kotlin.math.abs(a / w - c / w)
        return if (t) kotlin.math.min(i, w - i) + kotlin.math.min(j, h - j) else i + j
    }

    private fun ok(c: Int, d: Int, m: IntArray, g: Int) =
        c >= 0 && m[c] != g && (m[c] <= bs || m[c] > bs + ln || d >= ln + bs + 1 - m[c] + gr)

    private fun room(b: IntArray, c: Int, cap: Int, m: IntArray, q: IntArray, g: Int, sg: Int): Int {
        for (i in 0 until ln) if (m[b[i]] != g) m[b[i]] = bs + 1 + i
        m[hd] = g
        if (!ok(c, 1, m, g)) return 0
        m[c] = sg; q[0] = c; var n = 1; var k = 0; var d = 1; var e = 1
        while (k < n) {
            if (k == e) { d++; e = n }
            val a = q[k++]
            for (i in 0..3) {
                val x = nx(a, i)
                if (ok(x, d + 1, m, g) && m[x] != sg) { m[x] = sg; q[n++] = x; if (n > cap) return cap }
            }
        }
        return n
    }

    override fun decide(p: Perception): Int {
        w = p.width; h = p.height; t = p.toroidal && p.myScore < 200
        val m = p.marks; val q = p.queue; val g = p.newMark()
        val b = p.myBody; hd = b[0]; val tl = b[b.size - 1]; val v = p.myScore
        val ate = p.previousReward == 15f; bs = p.newMark(); ln = 0
        if (v > 98 || p.selfCollision || p.selfCollisionWarning) {
            ln = b.size
            for (i in 0 until ln) { m[b[i]] = bs + 1 + i; p.newMark() }
        }
        if (p.newMark() < bs) return decide(p)
        if (v > 298 || p.enemyCollision) for (e in p.enemies) {
            for (c in e.body) m[c] = g
            if (e.score >= 298) for (i in 0..3) { val x = nx(e.body[0], i); if (x >= 0) m[x] = g }
        }
        m[hd] = g
        var tg = tl; var bk = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val f = p.foods[i]; val d = ds(hd, f); val r = p.foodRivalDistance(i)
            val k = d + (if (r < d) 8 + 2 * (d - r) else if (r == d) 2 else 0)
            if (k < bk) { bk = k; tg = f }
        }
        val cap = b.size + (if (v > 298) 40 else 12)
        val hTurns = p.turnsWithoutFood; val panic = hTurns > p.starvationTurns - 150
        val hungry = hTurns > 750; val md = p.myDirection
        var best = p.validMoves[0]; var k0 = -1; var k1 = 0; var k2 = 0; var k3 = 0; var k4 = 2
        for (mv in p.validMoves) {
            if (k0 >= 0 && p.nanosLeft() <= 0) break
            val c = nx(hd, mv); if (c < 0) continue
            val f = c in p.foods; gr = (if (ate) 1 else 0) + (if (f) 1 else 0)
            val r = room(b, c, cap, m, q, g, p.newMark())
            val a0 = if (hungry) (if (r >= (if (panic) 1 else b.size / 2)) 1 else 0) else r
            val a1 = if (hungry || !f) 0 else 1
            val a2 = -ds(c, tg); val a3 = if (hungry) 0 else -ds(c, tl)
            val a4 = if (mv == md) 0 else 1
            if (a0 > k0 || a0 == k0 && (a1 > k1 || a1 == k1 && (a2 > k2 || a2 == k2 && (a3 > k3 || a3 == k3 && a4 < k4)))) {
                best = mv; k0 = a0; k1 = a1; k2 = a2; k3 = a3; k4 = a4
            }
        }
        return best
    }
}
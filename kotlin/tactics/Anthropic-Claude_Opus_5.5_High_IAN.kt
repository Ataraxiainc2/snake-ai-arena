import kotlin.math.abs
import kotlin.math.min

class Tactic : SnakeTactic {
    private var o = IntArray(0); private var q = IntArray(0); private var g = 0
    private var w = 0; private var h = 0; private var t = true; private var hd = 0
    private var bs = 0; private var ln = 0; private var gr = 0

    private fun nx(c: Int, d: Int): Int {
        val x = c % w + Move.DX[d]; val y = c / w + Move.DY[d]
        return if (t) (y + h) % h * w + (x + w) % w else if (x in 0 until w && y in 0 until h) y * w + x else hd
    }

    private fun ds(a: Int, c: Int): Int {
        val i = abs(a % w - c % w); val j = abs(a / w - c / w)
        return if (t) min(i, w - i) + min(j, h - j) else i + j
    }

    // my body cell k holds bs+1+k: it is free from step ln-k (+1 per meal still to digest)
    private fun ok(c: Int, d: Int): Boolean {
        val m = o[c]
        return m != g && (m <= bs || m > bs + ln || d >= ln + bs + 1 - m + gr)
    }

    private fun room(b: IntArray, c: Int, cap: Int, sg: Int): Int {
        for (i in 0 until ln) if (o[b[i]] != g) o[b[i]] = bs + 1 + i
        o[hd] = g
        if (!ok(c, 1)) return 0
        o[c] = sg; q[0] = c; var n = 1; var k = 0; var d = 1; var e = 1
        while (k < n) {
            if (k == e) { d++; e = n }
            val a = q[k++]
            for (m in 0..3) {
                val x = nx(a, m)
                if (o[x] != sg && ok(x, d + 1)) { o[x] = sg; q[n++] = x; if (n > cap) return cap }
            }
        }
        return n
    }

    override fun decide(p: Perception): Int {
        w = p.width; h = p.height; t = p.toroidal
        o = p.marks; q = p.queue; g = p.newMark()
        val b = p.myBody; hd = b[0]; val v = p.myScore; val tl = b[b.size - 1]
        bs = p.newMark(); ln = 0; val g0 = if (p.previousReward == 15f) 1 else 0
        if (v > 98) { ln = b.size; repeat(ln) { p.newMark() } }
        // the shared mark counter wrapped (marks cleared) while reserving: start again on clean marks
        if (p.newMark() < bs) return decide(p)
        if (v > 298) for (e in p.enemies) {
            for (c in e.body) o[c] = g
            if (!t) for (d in 0..3) o[nx(e.body[0], d)] = g
        }
        var tg = tl; var bk = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val f = p.foods[i]; val d = ds(hd, f); val e = p.foodRivalDistance(i)
            val k = d + (if (e < d) 8 + 2 * (d - e) else 0) + (if (e == d) 2 else 0)
            if (k < bk) { bk = k; tg = f }
        }
        val cap = b.size + (if (v > 298) 40 else 8); val hungry = p.turnsWithoutFood > 800
        var best = p.validMoves[0]; var k0 = -1; var k1 = 0; var k2 = 0; var k3 = 0
        for (m in p.validMoves) {
            if (k0 >= 0 && p.nanosLeft() <= 0) break
            val c = nx(hd, m); val f = c in p.foods; gr = g0 + (if (f) 1 else 0); val r = room(b, c, cap, p.newMark())
            val a0 = if (hungry) (if (r > 0) 1 else 0) else r
            val a1 = if (hungry || !f) 0 else 1
            val a2 = -ds(c, tg); val a3 = if (hungry) 0 else -ds(c, tl)
            if (a0 > k0 || a0 == k0 && (a1 > k1 || a1 == k1 && (a2 > k2 || a2 == k2 && a3 > k3))) {
                best = m; k0 = a0; k1 = a1; k2 = a2; k3 = a3
            }
        }
        return best
    }
}

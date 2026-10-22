import kotlin.math.abs
import kotlin.math.min

class Tactic : SnakeTactic {
    private var o = IntArray(0); private var s = IntArray(0); private var q = IntArray(0); private var g = 0
    private var w = 0; private var h = 0; private var t = true; private var hd = 0; private var sg = 0

    private fun nx(c: Int, d: Int): Int {
        val x = c % w + Move.DX[d]; val y = c / w + Move.DY[d]
        return if (t) (y + h) % h * w + (x + w) % w else if (x in 0 until w && y in 0 until h) y * w + x else hd
    }

    private fun ds(a: Int, c: Int): Int {
        val i = abs(a % w - c % w); val j = abs(a / w - c / w)
        return if (t) min(i, w - i) + min(j, h - j) else i + j
    }

    private fun room(c: Int, cap: Int): Int {
        if (o[c] == g) return 0
        s[c] = ++sg; q[0] = c; var n = 1; var k = 0
        while (k < n) {
            val a = q[k++]
            for (d in 0..3) {
                val b = nx(a, d)
                if (s[b] != sg && o[b] != g) { s[b] = sg; q[n++] = b; if (n >= cap) return cap }
            }
        }
        return n
    }

    override fun decide(p: Perception): Int {
        w = p.width; h = p.height; t = p.toroidal
        if (o.size != w * h) { o = IntArray(w * h); s = IntArray(w * h); q = IntArray(w * h + 1); g = 0; sg = 0 }
        g++
        val b = p.myBody; hd = b[0]; val v = p.myScore; val tl = b[b.size - 1]
        if (v > 90) { for (c in b) o[c] = g; if (b.size > 1 && p.previousReward < 14f) o[tl] = 0 }
        if (v > 296) for (e in p.enemies) {
            for (c in e.body) o[c] = g
            if (e.score >= 296) for (d in 0..3) o[nx(e.body[0], d)] = g
        }
        o[hd] = g
        var tg = tl; var bk = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val f = p.foods[i]; val d = ds(hd, f); val e = p.foodRivalDistance(i)
            val k = d + (if (e < d) 8 + 2 * (d - e) else 0) + (if (e == d) 2 else 0)
            if (k < bk) { bk = k; tg = f }
        }
        val cap = b.size + 12; val hungry = p.turnsWithoutFood > 900
        var best = p.validMoves[0]; var k0 = -1; var k1 = 0; var k2 = -1000000; var k3 = -1000000
        for (m in p.validMoves) {
            val c = nx(hd, m); val r = room(c, cap)
            val a0 = if (hungry) (if (r > 0) 1 else 0) else r
            val a1 = if (c in p.foods) 1 else 0
            val a2 = -ds(c, tg); val a3 = if (hungry) 0 else -ds(c, tl)
            if (a0 > k0 || a0 == k0 && (a1 > k1 || a1 == k1 && (a2 > k2 || a2 == k2 && a3 > k3))) {
                best = m; k0 = a0; k1 = a1; k2 = a2; k3 = a3
            }
        }
        return best
    }
}

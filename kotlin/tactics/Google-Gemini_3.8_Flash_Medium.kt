import kotlin.math.abs
import kotlin.math.min

class Tactic : SnakeTactic {
    private var o = IntArray(0); private var s = IntArray(0); private var d = IntArray(0); private var q = IntArray(0)
    private var g = 0; private var sg = 0; private var w = 0; private var h = 0; private var tr = true
    private var hd = 0; private var tl = 0; private var p_nanos = 10000000L

    private fun nx(c: Int, k: Int): Int {
        val x = c % w + Move.DX[k]; val y = c / w + Move.DY[k]
        return if (tr) (y + h) % h * w + (x + w) % w else if (x in 0 until w && y in 0 until h) y * w + x else -1
    }

    private fun ds(a: Int, c: Int): Int {
        val i = abs(a % w - c % w); val j = abs(a / w - c / w)
        return if (tr) min(i, w - i) + min(j, h - j) else i + j
    }

    private fun rm(st: Int, ef: Int, sl: Boolean): Int {
        if (st < 0 || (sl && o[st] == g) || (sl && ef > 0 && st == tl)) return 0
        if (!sl || st == tl) return 99999
        sg++; s[st] = sg; q[0] = st; var n = 1; var i = 0
        val cap = min(q.size - 8, if (p_nanos < 1000000L) 150 else 400)
        while (i < n && n <= cap) {
            val c = q[i++]
            for (k in 0..3) {
                val b = nx(c, k)
                if (b >= 0 && s[b] != sg && o[b] != g && !(ef > 0 && b == tl)) {
                    if (b == tl) return 99999
                    s[b] = sg; q[n++] = b
                }
            }
        }
        return if (n > cap) 50000 else n
    }

    override fun decide(p: Perception): Int {
        val vm = p.validMoves
        w = p.width; h = p.height; tr = p.toroidal; p_nanos = p.nanosLeft()
        val total = w * h
        if (o.size != total) { o = IntArray(total); s = IntArray(total); d = IntArray(total); q = IntArray(min(total, 4000)); g = 0; sg = 0 }
        g++
        val b = p.myBody; val n = b.size; hd = b[0]; tl = b[n - 1]; val v = p.myScore
        val sl = v > 98 || p.selfCollision || p.selfCollisionWarning
        val el = v > 298 || p.enemyCollision
        val ja = p.previousReward > 10f
        if (sl) {
            val u = if (ja) n else n - 1
            for (i in 0 until u) o[b[i]] = g
        }
        if (el) for (e in p.enemies) {
            for (c in e.body) o[c] = g
            val e0 = e.body[0]; val ex = e0 % w; val ey = e0 / w
            for (k in 0..3) {
                val x = ex + Move.DX[k]; val y = ey + Move.DY[k]
                val c = if (tr) (y + h) % h * w + (x + w) % w else if (x in 0 until w && y in 0 until h) y * w + x else -1
                if (c >= 0) o[c] = g
            }
        }
        val hu = p.turnsWithoutFood > 600
        var tg = tl; var bk = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val f = p.foods[i]; val dd = ds(hd, f); val e = p.foodRivalDistance(i)
            val k = if (hu) dd else dd + (if (el && (e <= dd || e < 4)) 999999 else if (e < dd) 8 + 2 * (dd - e) else if (e == dd) 2 else 0)
            if (k < bk) { bk = k; tg = f }
        }
        sg++; val ps = sg; var sp = 0; var i = 0
        if (tg >= 0 && o[tg] != g) { s[tg] = sg; d[tg] = 0; q[sp++] = tg }
        val cp = min(q.size - 8, if (p_nanos > 2000000L) 3000 else 800)
        while (i < sp && i < cp && s[hd] != sg) {
            val c = q[i++]
            for (k in 0..3) {
                val x = nx(c, k)
                if (x >= 0 && s[x] != sg && o[x] != g) {
                    s[x] = sg; d[x] = d[c] + 1
                    if (sp < q.size) q[sp++] = x
                }
            }
        }
        var be = vm[0]; var k0 = -1; var k1 = 0; var k2 = -20000; var k3 = 0; var k4 = 0
        for (m in vm) {
            val c = nx(hd, m)
            val ef = if (c >= 0 && c in p.foods) 1 else 0
            val r = rm(c, ef, sl)
            val a0 = if (hu) (if (r > 0) 1 else 0) else r
            val a1 = if (hu) 0 else ef
            val a2 = if (c >= 0 && s[c] == ps) -d[c] else -10000 - ds(c, tg)
            val a3 = if (hu) 0 else -ds(c, tl)
            val a4 = if (m == p.myDirection) 1 else 0
            if (a0 > k0 || a0 == k0 && (a1 > k1 || a1 == k1 && (a2 > k2 || a2 == k2 && (a3 > k3 || a3 == k3 && a4 > k4)))) {
                be = m; k0 = a0; k1 = a1; k2 = a2; k3 = a3; k4 = a4
            }
        }
        return be
    }
}

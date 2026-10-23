import kotlin.math.abs
import kotlin.math.min

class Tactic : SnakeTactic {
    var w = 0; var h = 0; var t = true
    var bs = 0; var ln = 0; var gr = 0

    fun nx(c: Int, d: Int): Int {
        val x = c % w + Move.DX[d]; val y = c / w + Move.DY[d]
        return if (t) (y + h) % h * w + (x + w) % w else if (x in 0 until w && y in 0 until h) y * w + x else -1
    }

    fun ds(a: Int, c: Int): Int {
        if (a < 0 || c < 0) return 9999
        val i = abs(a % w - c % w); val j = abs(a / w - c / w)
        return if (t) min(i, w - i) + min(j, h - j) else i + j
    }

    fun ok(c: Int, d: Int, m: IntArray, g: Int): Boolean {
        if (c < 0) return false
        val v = m[c]
        return v != g && (v <= bs || v > bs + ln || d >= ln + bs + 1 - v + gr)
    }

    fun rm(c: Int, cp: Int, m: IntArray, q: IntArray, sg: Int, g: Int): Int {
        if (!ok(c, 1, m, g)) return 0
        m[c] = sg; q[0] = c; var n = 1; var k = 0; var d = 1; var e = 1
        while (k < n) {
            if (k == e) { d++; e = n }
            val a = q[k++]
            for (x in 0..3) {
                val b = nx(a, x)
                if (b >= 0 && m[b] != sg && ok(b, d + 1, m, g)) {
                    m[b] = sg; q[n++] = b; if (n > cp) return cp
                }
            }
        }
        return n
    }

    override fun decide(p: Perception): Int {
        w = p.width; h = p.height; t = p.toroidal
        val m = p.marks; val q = p.queue; val g = p.newMark(); val b = p.myBody
        val hd = b[0]; val v = p.myScore; val tl = b[b.size - 1]
        
        bs = p.newMark(); ln = 0; val g0 = if (p.previousReward == 15f) 1 else 0
        if (v > 98 || p.selfCollision || p.selfCollisionWarning) {
            ln = b.size
            for (i in b.indices) { m[b[i]] = bs + 1 + i; p.newMark() }
        }
        if (p.newMark() < bs) return decide(p)
        
        if (v > 298 || p.enemyCollision) {
            for (e in p.enemies) {
                if (e.body.isEmpty()) continue
                for (c in e.body) m[c] = g
                for (d in 0..3) { val nb = nx(e.body[0], d); if (nb >= 0 && nb != hd) m[nb] = g }
            }
        }
        m[hd] = g
        
        var tg = tl; var bk = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val f = p.foods[i]; val d = ds(hd, f); val e = p.foodRivalDistance(i)
            val k = d + (if (e < d) 8 + 2 * (d - e) else 0) + (if (e == d) 2 else 0)
            if (k < bk) { bk = k; tg = f }
        }
        
        val cp = b.size + (if (v > 298) 40 else 8); val hu = p.turnsWithoutFood > 800; val md = p.myDirection
        var be = p.validMoves[0]; var k0 = -1; var k1 = 0; var k2 = -9999; var k3 = -9999; var k4 = 2
        
        for (x in p.validMoves) {
            if (k0 >= 0 && p.nanosLeft() <= 0) break
            val c = nx(hd, x); if (c < 0) continue
            val f = c in p.foods; gr = g0 + (if (f) 1 else 0)
            val r = rm(c, cp, m, q, p.newMark(), g)
            
            val a0 = if (hu) if (r > 0) 1 else 0 else r
            val a1 = if (hu || !f) 0 else 1
            val a2 = -ds(c, tg)
            val a3 = if (hu) 0 else -ds(c, tl)
            val a4 = if (x == md) 0 else 1
            
            if (a0 > k0 || a0 == k0 && (a1 > k1 || a1 == k1 && (a2 > k2 || a2 == k2 && (a3 > k3 || a3 == k3 && a4 < k4)))) {
                be = x; k0 = a0; k1 = a1; k2 = a2; k3 = a3; k4 = a4
            }
        }
        return be
    }
}
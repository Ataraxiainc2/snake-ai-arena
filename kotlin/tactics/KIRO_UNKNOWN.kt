import kotlin.math.abs
import kotlin.math.min

class Tactic : SnakeTactic {
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

    // Time-aware cell check: body cell i frees at step (ln - i + meals_to_digest).
    // ok(c, d, mk, g): can we enter cell c if we arrive at step d?
    private fun ok(c: Int, d: Int, mk: IntArray, g: Int): Boolean {
        val m = mk[c]
        return m != g && (m <= bs || m > bs + ln || d >= ln + bs + 1 - m + gr)
    }

    // BFS flood with time-aware body traversal; counts reachable cells up to cap
    private fun room(c: Int, cap: Int, mk: IntArray, q: IntArray, sg: Int, g: Int): Int {
        if (!ok(c, 1, mk, g)) return 0
        mk[c] = sg; q[0] = c; var n = 1; var k = 0; var d = 1; var e = 1
        while (k < n) {
            if (k == e) { d++; e = n }
            val a = q[k++]
            for (m in 0..3) {
                val nb = nx(a, m)
                if (mk[nb] != sg && ok(nb, d + 1, mk, g)) { mk[nb] = sg; q[n++] = nb; if (n > cap) return cap }
            }
        }
        return n
    }

    override fun decide(p: Perception): Int {
        w = p.width; h = p.height; t = p.toroidal
        val mk = p.marks; val q = p.queue; val g = p.newMark()
        val b = p.myBody; hd = b[0]; val v = p.myScore; val tl = b[b.size - 1]
        val ate = p.previousReward == 15f

        // Encode own body: cell b[i] frees at step (ln - i + gr), stored as bs+1+i
        bs = p.newMark(); ln = 0; gr = if (ate) 1 else 0
        if (p.selfCollision || p.selfCollisionWarning) {
            ln = b.size
            for (i in b.indices) { mk[b[i]] = bs + 1 + i; p.newMark() }
        }
        // Guard: if newMark wrapped past g, marks were cleared — recurse with fresh state
        if (p.newMark() < bs) return decide(p)

        // Block enemy bodies and head zones when lethal
        if (p.enemyCollision) {
            for (e in p.enemies) {
                for (c in e.body) mk[c] = g
                // Block cells adjacent to each enemy head (head-on kill zone at >=300)
                for (d in 0..3) { val nb = nx(e.body[0], d); if (nb != hd) mk[nb] = g }
            }
        }
        mk[hd] = g

        // Best food: favour foods we reach before rivals (arena pre-computes foodRivalDistance)
        var tg = tl; var bestK = Int.MAX_VALUE
        for (i in p.foods.indices) {
            val f = p.foods[i]; val d = ds(hd, f); val e = p.foodRivalDistance(i)
            val k = d + (if (e < d) 8 + 2 * (d - e) else 0) + (if (e == d) 2 else 0)
            if (k < bestK) { bestK = k; tg = f }
        }

        // Larger cap at >=300: snakes are long and need more lookahead to avoid self-trapping
        val cap = b.size + (if (v >= 298) 40 else 12)
        val hungry = p.turnsWithoutFood > 700

        var best = p.validMoves[0]; var k0 = -1; var k1 = 0; var k2 = 0; var k3 = 0; var k4 = 2
        for (m in p.validMoves) {
            // Respect time budget: stop evaluating once we have a candidate and time is tight
            if (k0 >= 0 && p.nanosLeft() <= 0) break
            val c = nx(hd, m); val f = c in p.foods
            // gr accounts for meals already in body + eating this food
            gr = (if (ate) 1 else 0) + (if (f) 1 else 0)
            val r = room(c, cap, mk, q, p.newMark(), g)
            val a0 = if (hungry) (if (r > 0) 1 else 0) else r
            val a1 = if (hungry || !f) 0 else 1
            val a2 = -ds(c, tg)
            val a3 = if (hungry) 0 else -ds(c, tl)
            val a4 = if (m == p.myDirection) 0 else 1  // prefer continuing direction (tiebreak)
            if (a0 > k0 || a0 == k0 && (a1 > k1 || a1 == k1 && (a2 > k2 || a2 == k2 && (a3 > k3 || a3 == k3 && a4 < k4)))) {
                best = m; k0 = a0; k1 = a1; k2 = a2; k3 = a3; k4 = a4
            }
        }
        return best
    }
}

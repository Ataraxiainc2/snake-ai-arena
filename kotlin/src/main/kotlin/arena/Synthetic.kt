package arena

import arena.api.Enemy
import arena.api.Perception
import arena.engine.Arena
import arena.engine.FoodRace
import arena.engine.Rules
import kotlin.random.Random

/** Random positions for the tactic check and the crowd benchmark (as SNAKES.py's check_tactics builds them). */
object Synthetic {
    private fun walk(w: Int, h: Int, length: Int, wrap: Boolean, rnd: Random): IntArray {
        var x = rnd.nextInt(w)
        var y = rnd.nextInt(h)
        val out = IntArray(length)
        out[0] = y * w + x
        for (i in 1 until length) {
            val d = rnd.nextInt(4)
            x += Rules.DX[d]
            y += Rules.DY[d]
            if (wrap) {
                x = Math.floorMod(x, w); y = Math.floorMod(y, h)
            } else {
                x = x.coerceIn(0, w - 1); y = y.coerceIn(0, h - 1)
            }
            out[i] = y * w + x
        }
        return out
    }

    /**
     * One position: this snake with [score] (body score + 1 cells, at most 900) and [enemies] rivals whose scores
     * are drawn from [enemyScores] (bodies up to [maxEnemyLen] cells), [foods] foods.
     */
    fun perception(
        w: Int, h: Int, score: Int, rnd: Random, enemies: Int = 14, foods: Int = 12,
        enemyScores: IntArray = intArrayOf(0, 5, 50, 150, 250, 310, 400), maxEnemyLen: Int = 900,
        turn: Long = 1, budgetNanos: Long = 15_000_000,
    ): Perception {
        val p = Perception()
        val me = walk(w, h, minOf(score + 1, 900), score < Rules.WALL_COLLISION, rnd)
        val bodies = ByteArray(w * h)
        fun add(b: IntArray) { for (c in b) if (bodies[c] < 127) bodies[c]++ }
        add(me)
        p.enemies = Array(enemies) { i ->
            val s = enemyScores[rnd.nextInt(enemyScores.size)]
            val len = if (maxEnemyLen < 900) 1 + rnd.nextInt(maxEnemyLen) else minOf(s + 1, 900)
            val b = walk(w, h, len, s < Rules.WALL_COLLISION, rnd)
            add(b)
            Enemy(i + 1, s, rnd.nextInt(4), b)
        }
        p.width = w
        p.height = h
        p.turn = turn
        p.myId = 0
        p.myScore = score
        val dir = rnd.nextInt(4)
        p.myDirection = dir
        p.myBody = me
        p.foods = IntArray(foods) { rnd.nextInt(w * h) }
        p.bodies = bodies
        p.selfCollision = score >= Rules.SELF_COLLISION + 10
        p.selfCollisionWarning = score in Rules.SELF_COLLISION until Rules.SELF_COLLISION + 10
        p.wallCollision = score >= Rules.WALL_COLLISION
        p.enemyCollision = score >= Rules.ENEMY_COLLISION
        p.toroidal = score < Rules.WALL_COLLISION
        p.validMoves = if (me.size >= 2) Arena.VALID[dir].copyOf() else Arena.VALID[4].copyOf()
        p.turnsWithoutFood = intArrayOf(0, 500, 1200)[rnd.nextInt(3)]
        p.starvationTurns = Rules.STARVATION_TURNS
        p.previousReward = floatArrayOf(0f, 15f, -1.2f)[rnd.nextInt(3)]
        p.diedLastStep = false
        p.deadline = Long.MAX_VALUE
        p.marks = IntArray(w * h)
        p.queue = IntArray(w * h + 1)
        val heads = IntArray(enemies) { p.enemies[it].body[0] }
        FoodRace().also { it.compute(w, h, p.toroidal, p.foods, heads, IntArray(enemies) { k -> k + 1 }, enemies) }.attach(p)
        return p
    }

    /** Arms the time budget of [p] for a call starting now. */
    fun arm(p: Perception, budgetNanos: Long) {
        p.deadline = System.nanoTime() + budgetNanos
    }
}

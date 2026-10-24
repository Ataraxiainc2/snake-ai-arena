package arena.engine

import arena.api.Perception
import arena.api.SnakeTactic

/** A loaded tactic (one file): how to make one instance per snake per game, and its size for the ranking. */
class TacticEntry(
    val name: String,
    val file: String,
    var chars: Int,
    @Volatile var factory: () -> SnakeTactic,
    /** An organizer's port (arena.reference): plays, but takes no part in the code-size factor. */
    val reference: Boolean = false,
) {
    var codeFactor = 1.0
    var reloads = 0
}

enum class Kind { TACTIC, DQN, HUMAN }

/** One life, as SNAKES.py's Snake.lives rows. */
class Life(
    val startTurn: Long, val endTurn: Long, val score: Int, val length: Int, val foods: Int, val cause: String,
    val killer: String?, val deathX: Int, val deathY: Int, val turnsToScore: Map<Int, Long>,
)

class Snake(val id: Int, val name: String, val kind: Kind, val entry: TacticEntry?) {
    val body = Body()

    /** The head's coordinates; outside the board only on the turn the snake dies against a wall. */
    var hx = 0
    var hy = 0
    var dir = 0
    var score = 0
    var maxScore = 0

    /** The best score in this episode, over every life in it: the order of the in-game standings. */
    var episodeBest = 0

    /** The best score in this game (bestScore also counts earlier games, from the saved records). */
    var gameBest = 0
    var alive = true
    var lastTail = 0
    var ateThisTick = false
    var deathCode: String? = null
    var killer: String? = null
    var warning = false
    var warningEnd = 0L
    var selfOn = false
    var turnsWithoutFood = 0
    var lastReward = 0.0
    var diedLastStep = false

    /** Until this turn the viewer flashes the head (after reaching 200 or 300; the 100 warning flashes on its own). */
    var flashUntil = 0L

    // learning (the shared replay buffer gets every snake's experience, as in SNAKES.py)
    var epsilon = 1.0
    var steps = 0L
    val prevState = FloatArray(Rules.STATE_SIZE)
    val nextState = FloatArray(Rules.STATE_SIZE)
    var prevHead = 0
    var prevHx = 0
    var prevHy = 0
    var prevAction = 0
    var targetX = 0
    var targetY = 0

    // tactic
    var tactic: SnakeTactic? = null
    var tacticFactory: (() -> SnakeTactic)? = null
    val perception = Perception()
    var enemiesBuf: Array<arena.api.Enemy>? = null
    var bodyArr = IntArray(0)
    var chosen = 0
    var decisionNanos = 0L
    var failure: Throwable? = null

    // telemetry
    var decCount = 0L
    var decSumMs = 0.0
    var decMaxMs = 0.0
    var decOver = 0L
    val recentMs = FloatArray(RECENT)
    var recentN = 0L
    var timeouts = 0L
    var exceptions = 0L
    var invalid = 0L
    var firstError: String? = null
    val lives = ArrayList<Life>()
    var lifeStartTurn = 0L
    var lifeFoods = 0
    val milestones = LinkedHashMap<Int, Long>()
    var foodsEaten = 0L

    val bestScore: Int get() = maxOf(maxScore, score)
    val codeFactor: Double get() = entry?.codeFactor ?: 1.0
    val rankingScore: Double get() = bestScore * codeFactor

    fun recordDecision(ms: Double, overBudget: Boolean) {
        decCount++
        decSumMs += ms
        if (ms > decMaxMs) decMaxMs = ms
        if (overBudget) decOver++
        recentMs[(recentN % RECENT).toInt()] = ms.toFloat()
        recentN++
    }

    fun p99Ms(): Double? {
        val n = minOf(recentN, RECENT.toLong()).toInt()
        if (n == 0) return null
        val v = recentMs.copyOf(n)
        v.sort()
        return v[(0.99 * (n - 1)).toInt()].toDouble()
    }

    companion object {
        const val RECENT = 8192
    }
}

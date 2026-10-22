package arena.api

/*
 * The API a Kotlin tactic sees (kotlin/tactics/rules_kotlin.txt section 3). Tactic files have no package line: the arena
 * adds `package arena.tactics.<id>` and `import arena.api.*`. This file is the contract with the phone arena too
 * (IssaArcade in EmuGaMind): keep both copies identical.
 */

/** A tactic: one instance per snake per game. decide() returns Move.UP, DOWN, LEFT or RIGHT, one of p.validMoves. */
interface SnakeTactic {
    fun decide(p: Perception): Int
}

object Move {
    const val UP = 0
    const val DOWN = 1
    const val LEFT = 2
    const val RIGHT = 3

    /** Cell offsets per move; y grows downwards. Read-only by the rules (the engine never relies on them). */
    val DX = intArrayOf(0, 0, -1, 1)
    val DY = intArrayOf(-1, 1, 0, 0)
}

/** Another living snake this turn. body: cells, head first (cell = y * width + x). */
class Enemy internal constructor(val id: Int, score: Int, direction: Int, body: IntArray) {
    var score = score; internal set
    var direction = direction; internal set
    var body = body; internal set
}

/**
 * What a snake knows when it decides. Everything is read-only and valid only during decide(): the engine reuses
 * these arrays, and bodies is shared by every snake of the turn.
 */
class Perception internal constructor() {
    /** The board in cells. */
    var width = 0; internal set
    var height = 0; internal set
    var turn = 0L; internal set
    var myId = 0; internal set
    var myScore = 0; internal set

    /** A Move: the direction of the last move (at birth the random one the snake starts with). */
    var myDirection = 0; internal set
    var myBody = EMPTY; internal set
    var foods = EMPTY; internal set
    var enemies: Array<Enemy> = NO_ENEMIES; internal set

    /** Per cell, how many body cells of all snakes (this one included) lie there; capped at 127. */
    var bodies = ByteArray(0); internal set
    var selfCollision = false; internal set
    var selfCollisionWarning = false; internal set
    var wallCollision = false; internal set
    var enemyCollision = false; internal set
    var toroidal = true; internal set

    /** The moves that do not reverse into the neck (all 4 at length 1), in the order UP, DOWN, LEFT, RIGHT. */
    var validMoves = EMPTY; internal set
    var turnsWithoutFood = 0; internal set
    var starvationTurns = 0; internal set

    /** +15 ate, -15 died, +1 moved closer to the target food, -1.2 farther (-3 more inside its own body). */
    var previousReward = 0f; internal set
    var diedLastStep = false; internal set

    /** The same 16 values as SNAKES.py's state_vector_16. */
    var stateVector16 = FloatArray(16); internal set

    /**
     * Scratch for your searches, so 400 snakes do not each keep board-sized arrays: one Int per cell and a queue of
     * width * height + 1. Shared with the other snakes deciding on the same thread, valid only during decide().
     * newMark() returns a value no cell of marks holds yet: mark the cells of one search with it.
     */
    var marks = EMPTY; internal set
    var queue = EMPTY; internal set
    internal var markCounter = IntArray(1)

    fun newMark(): Int {
        val c = markCounter
        if (c[0] == Int.MAX_VALUE) {
            marks.fill(0)
            c[0] = 0
        }
        return ++c[0]
    }

    internal var deadline = 0L
    internal var clock: () -> Long = WALL
    internal var rivalBest = EMPTY
    internal var rivalOwner = EMPTY
    internal var rivalSecond = EMPTY

    /** Nanoseconds left of this call's time budget (negative once it is spent). */
    fun nanosLeft(): Long = deadline - clock()

    /**
     * The food race, computed once per turn by the arena for everyone: the distance from foods[i] to the nearest
     * enemy head, in moves on an empty board (Manhattan; the shorter way round while toroidal). Int.MAX_VALUE when
     * there are no enemies. Compare it with your own distance to see which foods you reach first.
     */
    fun foodRivalDistance(i: Int): Int = if (rivalOwner[i] != myId) rivalBest[i] else rivalSecond[i]

    private companion object {
        val EMPTY = IntArray(0)
        val WALL: () -> Long = { System.nanoTime() }
        val NO_ENEMIES = emptyArray<Enemy>()
    }
}

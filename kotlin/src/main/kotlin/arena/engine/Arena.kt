package arena.engine

import arena.api.Enemy
import arena.api.Move
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray

/** A moment worth showing: [kind] is rule100, warn100, walls, rivals, ten (detail = turns) or died (detail = cause). */
class GameEvent(val snake: Int, val kind: String, val detail: String?, val turn: Long)

class ArenaConfig(
    val width: Int,
    val height: Int,
    val snakes: Int,
    val foods: Int,
    val seed: Long,
    val human: Boolean = false,
    /** Human players (0 to Rules.MAX_HUMANS), the first snakes; [human] alone means one, as in SNAKES.py. */
    val humans: Int = if (human) 1 else 0,
    /** Per-call time budget of the tactics; null = Rules.budgetNanos(snakes). */
    val budgetNanos: Long? = null,
    /** Threads that run the tactics' decide() calls (the engine itself is single-threaded and deterministic). */
    val threads: Int = 1,
    /** false: timeouts never turn a move random (parity runs, where the reference is Python with no limit). */
    val enforceTime: Boolean = true,
    /**
     * The clock that times each decide() call (and nanosLeft()). Wall time by default; with several threads on a
     * phone a call can lose the CPU to the scheduler and look slow, so the time each thread really ran is fairer:
     * on Android `{ android.os.Debug.threadCpuTimeNanos() }`, on the JVM arena.cpuClock (the engine itself uses
     * nothing outside Android's Java library, so the app compiles these sources as they are).
     */
    val callClock: () -> Long = { System.nanoTime() },
    /** Compute the 7 planning values of the state for tactics too even without learners (parity runs compare them). */
    val fullState: Boolean = false,
)

/**
 * SNAKES.py's game (Game.step_simulation and what it calls), in cells. One tick: every snake chooses, all move (wrap
 * while the score before the move is below 200), the collision grid is rebuilt, then in list order each snake eats
 * (first food in list order on its head; ties go to the first snake) and is checked for death with its score after
 * eating; rewards; finally the dead drop food on every 2nd cell and respawn. Food and respawn positions come from
 * Python's Mersenne Twister with the same calls in the same order, so a game can be replayed against SNAKES.py.
 */
class Arena(val cfg: ArenaConfig, tactics: List<TacticEntry>, val brain: Brain? = null) {
    val width = cfg.width
    val height = cfg.height
    val cells = width * height
    val rng = PyRandom(cfg.seed)
    val budget = cfg.budgetNanos ?: Rules.budgetNanos(cfg.snakes)
    val snakes: Array<Snake>
    var foods = IntArray(cfg.foods + Rules.EXTRA_FOOD_ON_DEATH + 8)
        private set
    var foodCount = 0
        private set
    private val foodOnCell = IntArray(cells)

    /** Per cell: how many different snakes have a cell there (SNAKES.py's collision_grid as counts of names). */
    val distinct = IntArray(cells)
    private val mark = IntArray(cells)

    /** Per cell: body cells of all snakes, capped at 127 (Perception.bodies). */
    val bodies = ByteArray(cells)
    private var foodsSnapshot = IntArray(0)
    private val raceTor = FoodRace()
    private val raceWall = FoodRace()
    private val heads = IntArray(cfg.snakes)
    private val headIds = IntArray(cfg.snakes)
    private val enemyOf = arrayOfNulls<Enemy>(cfg.snakes)

    var frame = 0L
        private set
    var episode = 0
        private set
    private val fallen = BooleanArray(cfg.snakes)
    private val died = BooleanArray(cfg.snakes)
    private var fallenCount = 0
    var deathsTotal = 0L
        private set

    /** Each human player's last requested move (-1: none yet), set from the UI thread. */
    private val humanDirs = AtomicIntegerArray(Rules.MAX_HUMANS).also { for (i in 0 until it.length()) it.set(i, -1) }

    /** The first (or only) human player's move, as SNAKES.py's arrow keys. */
    var humanDir: Int
        get() = humanDirs.get(0)
        set(value) = humanDirs.set(0, value)

    /** A move for human player [player] (0-based; its snake has that id). */
    fun steer(player: Int, move: Int) {
        if (player in 0 until humanDirs.length()) humanDirs.set(player, move)
    }

    /** Wall time of the last tick and of its decide() phase (all tactics, on every thread), in ns. */
    var lastTickNanos = 0L
        private set
    var lastDecideNanos = 0L
        private set

    /** Called after an episode ends (SNAKES.py saves q_tables, report and telemetry there). */
    var onEpisodeEnd: (() -> Unit)? = null

    /** Called (on the game thread) when every snake has died once, before all respawn: a viewer can wait there. */
    var beforeNewEpisode: (() -> Unit)? = null

    /** What a viewer shows (rule warnings, new phases, deaths); collected only while someone set this queue. */
    @Volatile
    var events: java.util.concurrent.ConcurrentLinkedQueue<GameEvent>? = null

    private fun emit(s: Snake, kind: String, detail: String? = null) {
        events?.add(GameEvent(s.id, kind, detail, frame))
    }

    private val mainFeatures = Features(this)
    private val pool: ExecutorService?
    private val workerFeatures: List<Features>
    private val tacticIdx: IntArray
    private val next = AtomicInteger()

    init {
        val n = cfg.snakes
        val positions = IntArray(n) { i ->
            val xPx = ((i + 1).toDouble() * (width * PX)) / (n + 1)
            val x = xPx.toInt() / PX
            (height / 2) * width + x
        }
        val list = ArrayList<Snake>(n)
        val start = cfg.humans.coerceIn(0, minOf(n, Rules.MAX_HUMANS))
        for (h in 0 until start) list.add(Snake(h, Rules.humanName(h, start), Kind.HUMAN, null))
        val used = HashSet(list.map { it.name })
        for (i in 0 until n - start) {
            val t = tactics.getOrNull(i)
            val name = t?.name ?: (Rules.PHILOSOPHERS.firstOrNull { it !in used } ?: "Philosopher_$i")
            used.add(name)
            list.add(Snake(start + i, name, if (t != null) Kind.TACTIC else Kind.DQN, t))
        }
        for (s in list) {
            s.body.reset(positions[s.id])
            s.hx = positions[s.id] % width
            s.hy = positions[s.id] / width
            s.lastTail = positions[s.id]
            s.dir = rng.choiceIndex(4)
            s.prevAction = s.dir
            s.prevHead = positions[s.id]
            s.entry?.let { e -> s.tacticFactory = e.factory; s.tactic = e.factory() }
        }
        snakes = list.toTypedArray()
        repeat(cfg.foods) { addFood(randomCell()) }
        tacticIdx = snakes.filter { it.kind == Kind.TACTIC }.map { it.id }.toIntArray()
        val threads = cfg.threads.coerceIn(1, maxOf(1, snakes.size))
        pool = if (threads > 1) Executors.newFixedThreadPool(threads - 1) { r -> Thread(r, "tactics").apply { isDaemon = true } } else null
        workerFeatures = List(threads) { if (it == 0) mainFeatures else Features(this) }
        updateCodeFactors()
    }

    fun close() {
        pool?.shutdownNow()
    }

    fun updateCodeFactors() {
        val entries = snakes.mapNotNull { it.entry }.distinct().filter { !it.reference }
        if (entries.isEmpty()) return
        val shortest = maxOf(1, entries.minOf { it.chars })
        for (e in entries) {
            val ratio = maxOf(1.0, e.chars.toDouble() / shortest)
            e.codeFactor = maxOf(0.0, 1.0 - Rules.CODE_SIZE_PENALTY * Math.log(ratio) / Math.log(2.0))
        }
    }

    /** A new instance for every snake whose tactic file changed (hot reload): the snake keeps its body and score. */
    fun refreshTactics() {
        for (s in snakes) {
            val e = s.entry ?: continue
            if (s.tacticFactory !== e.factory) {
                s.tacticFactory = e.factory
                s.tactic = runCatching { e.factory() }.getOrNull()
            }
        }
        updateCodeFactors()
    }

    private fun randomCell(): Int {
        val x = rng.randint(0, width - 1)
        val y = rng.randint(0, height - 1)
        return y * width + x
    }

    private fun addFood(cell: Int) {
        if (foodCount == foods.size) foods = foods.copyOf(foods.size * 2)
        foods[foodCount++] = cell
        foodOnCell[cell]++
    }

    private fun removeFood(index: Int) {
        foodOnCell[foods[index]]--
        System.arraycopy(foods, index + 1, foods, index, foodCount - index - 1)
        foodCount--
    }

    fun foodList(): IntArray = foods.copyOf(foodCount)

    fun dist2(x0: Int, y0: Int, x1: Int, y1: Int, toroidal: Boolean): Long {
        var dx = Math.abs(x1 - x0)
        var dy = Math.abs(y1 - y0)
        if (toroidal) {
            dx = minOf(dx, Math.abs(x1 - x0 + width), Math.abs(x1 - x0 - width))
            dy = minOf(dy, Math.abs(y1 - y0 + height), Math.abs(y1 - y0 - height))
        }
        return dx.toLong() * dx + dy.toLong() * dy
    }

    private fun buildGrid(withBodies: Boolean) {
        distinct.fill(0)
        mark.fill(-1)
        if (withBodies) bodies.fill(0)
        for (s in snakes) {
            if (!s.alive) continue
            val b = s.body
            val id = s.id
            for (i in 0 until b.size) {
                val c = b[i]
                if (c < 0) continue
                if (mark[c] != id) {
                    mark[c] = id
                    distinct[c]++
                }
                if (withBodies && bodies[c] < 127) bodies[c]++
            }
        }
    }

    /** One turn of the game. */
    fun tick() {
        val tickStart = System.nanoTime()
        frame++
        buildGrid(withBodies = true)
        for (s in snakes) {
            if (s.warning && frame >= s.warningEnd) {
                s.warning = false
                s.selfOn = true
                emit(s, "rule100")
            }
        }
        // 1. observe and choose (everyone is alive here: the dead respawned at the end of the last tick)
        val needFull = brain != null || cfg.fullState
        if (tacticIdx.isNotEmpty()) {
            if (foodsSnapshot.size != foodCount) foodsSnapshot = IntArray(foodCount)
            System.arraycopy(foods, 0, foodsSnapshot, 0, foodCount)
            var anyTor = false
            var anyWall = false
            for (s in snakes) {
                // reused arrays and Enemy objects: nothing is allocated per snake unless its length changed
                val b = s.body
                if (s.bodyArr.size != b.size) s.bodyArr = IntArray(b.size)
                for (i in 0 until b.size) s.bodyArr[i] = b[i]
                val e = enemyOf[s.id]
                if (e == null) enemyOf[s.id] = Enemy(s.id, s.score, s.dir, s.bodyArr)
                else { e.score = s.score; e.direction = s.dir; e.body = s.bodyArr }
                heads[s.id] = s.body.head
                headIds[s.id] = s.id
                if (s.kind == Kind.TACTIC) if (s.score < Rules.WALL_COLLISION) anyTor = true else anyWall = true
            }
            if (anyTor) raceTor.compute(width, height, true, foodsSnapshot, heads, headIds, snakes.size)
            if (anyWall) raceWall.compute(width, height, false, foodsSnapshot, heads, headIds, snakes.size)
            val d0 = System.nanoTime()
            decideTactics(needFull)
            lastDecideNanos = System.nanoTime() - d0
        }
        for (s in snakes) {
            when (s.kind) {
                Kind.TACTIC -> applyTacticChoice(s)
                Kind.DQN -> {
                    observe(s, mainFeatures, true)
                    s.dir = brain?.choose(s, validMoves(s)) ?: validMoves(s)[0]
                }
                Kind.HUMAN -> {
                    observe(s, mainFeatures, needFull)
                    val d = humanDirs.get(s.id)
                    if (d >= 0 && !(s.body.size >= 2 && d == Rules.OPPOSITE[s.dir])) s.dir = d
                }
            }
            s.prevAction = s.dir
        }
        // 2. move
        for (s in snakes) if (s.alive) move(s)
        buildGrid(withBodies = false)
        // 3. eat, then die under the rules active with the score after eating
        for (s in snakes) s.ateThisTick = false
        var anyDeath = false
        for (s in snakes) {
            if (!s.alive) continue
            val head = s.body.head
            if (head >= 0 && foodOnCell[head] > 0) eat(s, head)
            if (!s.ateThisTick) s.turnsWithoutFood++
            if (s.selfOn && head >= 0 && s.body.contains(head, 1)) {
                die(s, "self")
            } else if (s.score >= Rules.WALL_COLLISION && head < 0) {
                die(s, "wall")
            } else if (s.score >= Rules.ENEMY_COLLISION && distinct[head] >= 2) {
                s.killer = killerAt(s, head)
                die(s, "enemy")
            }
            if (s.alive && s.turnsWithoutFood >= Rules.STARVATION_TURNS) die(s, "starvation")
            if (!s.alive) anyDeath = true
        }
        // 4. rewards and learning (before respawn)
        for (s in snakes) {
            val died = !s.alive
            val reward = when {
                died -> Rules.REWARD_DIE
                s.ateThisTick -> Rules.REWARD_EAT
                else -> {
                    val tor = s.score < Rules.WALL_COLLISION
                    val old = dist2(s.prevHx, s.prevHy, s.targetX, s.targetY, tor)
                    val new = dist2(s.hx, s.hy, s.targetX, s.targetY, tor)
                    var r = if (new < old) 1.0 else -1.2
                    if ((s.warning || s.score >= 60) && s.body.contains(s.body.head, 1)) r -= 3.0
                    r
                }
            }
            s.lastReward = reward
            s.diedLastStep = died
        }
        if (brain != null) {
            // next states are independent per snake: computed on all threads, then remembered in list order
            parallel(snakes.size) { f, i ->
                val s = snakes[i]
                if (s.kind != Kind.HUMAN && learns(s)) {
                    f.bind(s, s.ateThisTick)
                    f.target()
                    f.state(s.nextState, true)
                }
            }
            for (s in snakes) if (s.kind != Kind.HUMAN && learns(s)) brain.remember(s, s.lastReward, s.diedLastStep)
            brain.trainStep()
        }
        // 5. deaths and respawn
        if (anyDeath) {
            // who died is fixed now: a snake that died this tick still drops food and respawns even if the episode
            // ended (and everyone respawned) earlier in this loop, as SNAKES.py's died_map does
            for (s in snakes) died[s.id] = !s.alive
            for (s in snakes) {
                if (!died[s.id]) continue
                deathsTotal++
                for (i in 0 until s.body.size step 2) {
                    val c = s.body[i]
                    if (foodCount < cfg.foods + Rules.EXTRA_FOOD_ON_DEATH && c >= 0) addFood(c)
                }
                if (!fallen[s.id]) {
                    fallen[s.id] = true
                    fallenCount++
                }
                respawn(s)
                if (fallenCount >= snakes.size) {
                    onEpisodeEnd?.invoke()
                    beforeNewEpisode?.invoke()
                    newEpisode()
                }
            }
        }
        lastTickNanos = System.nanoTime() - tickStart
    }

    /**
     * The snakes by their place in this episode: the best score in it (any of their lives), then the score now, then
     * the all-time ranking. What the screens list while the game plays; the all-time ranking is [Snake.rankingScore].
     */
    fun episodeStandings(): List<Snake> = snakes.sortedWith(
        compareByDescending<Snake> { it.episodeBest }.thenByDescending { it.score }.thenByDescending { it.rankingScore },
    )

    private fun newEpisode() {
        episode++
        fallen.fill(false)
        fallenCount = 0
        for (s in snakes) {
            s.episodeBest = 0
            respawn(s)
        }
    }

    private fun eat(s: Snake, head: Int) {
        var i = 0
        while (foods[i] != head) i++
        s.ateThisTick = true
        s.score++
        if (s.score > s.maxScore) s.maxScore = s.score
        if (s.score > s.episodeBest) s.episodeBest = s.score
        s.lifeFoods++
        s.foodsEaten++
        s.turnsWithoutFood = 0
        for (m in MILESTONES) if (s.score >= m && m !in s.milestones) s.milestones[m] = frame - s.lifeStartTurn
        if (s.score >= Rules.SELF_COLLISION && !s.warning && !s.selfOn) {
            s.warning = true
            s.warningEnd = frame + Rules.SELF_WARNING_TURNS
            emit(s, "warn100")
        }
        if (s.score == Rules.WALL_COLLISION || s.score == Rules.ENEMY_COLLISION) {
            s.flashUntil = frame + FLASH_TURNS
            emit(s, if (s.score == Rules.WALL_COLLISION) "walls" else "rivals")
        }
        if (s.score == 10) emit(s, "ten", (frame - s.lifeStartTurn).toString())
        s.body.pushBack(s.lastTail)
        removeFood(i)
        if (foodCount < cfg.foods) addFood(randomCell())
    }

    private fun die(s: Snake, code: String) {
        s.alive = false
        s.deathCode = code
        if (events != null) emit(s, "died", if (code == "enemy") "collision with ${s.killer}" else code)
    }

    /** sorted(names of the other snakes in collision_grid[head])[0]; the grid had every body before anyone ate. */
    private fun killerAt(s: Snake, head: Int): String? {
        var best: String? = null
        for (o in snakes) {
            if (o === s) continue
            val n = o.body.size - (if (o.ateThisTick) 1 else 0)
            var found = false
            for (i in 0 until n) if (o.body[i] == head) { found = true; break }
            if (found && (best == null || o.name < best)) best = o.name
        }
        return best
    }

    private fun move(s: Snake) {
        var nx = s.hx + Rules.DX[s.dir]
        var ny = s.hy + Rules.DY[s.dir]
        if (s.score < Rules.WALL_COLLISION) {
            nx = Math.floorMod(nx, width)
            ny = Math.floorMod(ny, height)
        }
        s.hx = nx
        s.hy = ny
        val cell = if (nx in 0 until width && ny in 0 until height) ny * width + nx else OUT
        s.lastTail = s.body.tail
        s.body.pushFront(cell)
        s.body.popBack()
    }

    private fun respawn(s: Snake) {
        val dead = s.deathCode != null
        s.lives.add(
            Life(
                s.lifeStartTurn, frame, s.score, s.body.size, s.lifeFoods, s.deathCode ?: "episode_reset", s.killer,
                if (dead) s.hx else Int.MIN_VALUE, if (dead) s.hy else Int.MIN_VALUE, LinkedHashMap(s.milestones),
            ),
        )
        s.deathCode = null
        s.killer = null
        s.lifeStartTurn = frame
        s.lifeFoods = 0
        s.milestones.clear()
        if (s.score > s.maxScore) s.maxScore = s.score
        val cell = randomCell()
        s.body.reset(cell)
        s.hx = cell % width
        s.hy = cell / width
        s.dir = rng.choiceIndex(4)
        s.alive = true
        s.score = 0
        s.warning = false
        s.selfOn = false
        s.turnsWithoutFood = 0
    }

    // ---- choosing -------------------------------------------------------------------------------------------------

    private fun validMoves(s: Snake): IntArray = if (s.body.size >= 2) VALID[s.dir] else VALID[4]

    /** prev_state, prev_head and the target, as SNAKES.py's step 1 for every snake. */
    private fun observe(s: Snake, f: Features, full: Boolean) {
        f.bind(s, false)
        f.target()
        s.targetX = f.targetX
        s.targetY = f.targetY
        f.state(s.prevState, full)
        s.prevHead = s.body.head
        s.prevHx = s.hx
        s.prevHy = s.hy
    }

    /** body(features, k) for k in 0 until count, spread over the tactic threads (each with its own Features). */
    private fun parallel(count: Int, body: (Features, Int) -> Unit) {
        next.set(0)
        val loop = { f: Features ->
            while (true) {
                val k = next.getAndIncrement()
                if (k >= count) break
                body(f, k)
            }
        }
        if (pool == null) {
            loop(mainFeatures)
            return
        }
        val futures = (1 until workerFeatures.size).map { w -> pool.submit(Callable { loop(workerFeatures[w]) }) }
        loop(mainFeatures)
        for (fu in futures) fu.get()
    }

    private fun decideTactics(learning: Boolean) = parallel(tacticIdx.size) { f, k -> decide(snakes[tacticIdx[k]], f, learning) }

    /**
     * Whether this turn's step of [s] goes to the replay buffer. Up to 50 snakes (SNAKES.py's maximum) every snake's
     * does, as there; with more, the DQN snakes' and 32 others' in rotation: the buffer takes 64 samples a turn, and
     * the full state (four flood fills) of 400 snakes twice a turn was most of the engine's time on the phone.
     */
    private fun learns(s: Snake): Boolean {
        if (cfg.fullState || snakes.size <= 50 || s.kind == Kind.DQN) return true
        val stride = (snakes.size + 31) / 32
        return (s.id + frame) % stride == 0L
    }

    private fun decide(s: Snake, f: Features, learning: Boolean) {
        observe(s, f, learning && learns(s))
        s.perception.marks = f.marks
        s.perception.queue = f.queue
        s.perception.markCounter = f.markCounter
        fillPerception(s)
        val t = s.tactic
        s.failure = null
        val clock = cfg.callClock
        s.perception.clock = clock
        val t0 = clock()
        s.perception.deadline = t0 + budget
        s.chosen = try {
            t?.decide(s.perception) ?: throw IllegalStateException("tactic failed to load")
        } catch (e: Throwable) {
            if (e is VirtualMachineError && e !is StackOverflowError && e !is OutOfMemoryError) throw e
            s.failure = e
            -1
        }
        s.decisionNanos = clock() - t0
    }

    private fun fillPerception(s: Snake) {
        val p = s.perception
        p.width = width
        p.height = height
        p.turn = frame
        p.myId = s.id
        p.myScore = s.score
        p.myDirection = s.dir
        p.myBody = s.bodyArr
        p.foods = foodsSnapshot
        (if (s.score < Rules.WALL_COLLISION) raceTor else raceWall).attach(p)
        val n = snakes.size
        var enemies = s.enemiesBuf
        if (enemies == null || enemies.size != n - 1) {
            enemies = Array(n - 1) { PLACEHOLDER }
            s.enemiesBuf = enemies
        }
        var k = 0
        for (i in 0 until n) if (i != s.id) enemies[k++] = enemyOf[i]!!
        p.enemies = enemies
        p.bodies = bodies
        p.selfCollision = s.selfOn
        p.selfCollisionWarning = s.warning
        p.wallCollision = s.score >= Rules.WALL_COLLISION
        p.enemyCollision = s.score >= Rules.ENEMY_COLLISION
        p.toroidal = s.score < Rules.WALL_COLLISION
        val vm = validMoves(s)
        if (p.validMoves.size != vm.size) p.validMoves = vm.copyOf() else System.arraycopy(vm, 0, p.validMoves, 0, vm.size)
        p.turnsWithoutFood = s.turnsWithoutFood
        p.starvationTurns = Rules.STARVATION_TURNS
        p.previousReward = s.lastReward.toFloat()
        p.diedLastStep = s.diedLastStep
        System.arraycopy(s.prevState, 0, p.stateVector16, 0, 16)
    }

    /** SNAKES.py's choose_action bookkeeping, in list order (random fallbacks use the game's Python RNG). */
    private fun applyTacticChoice(s: Snake) {
        val valid = validMoves(s)
        val ms = s.decisionNanos / 1e6
        val over = s.decisionNanos > budget
        s.recordDecision(ms, over)
        var chosen = s.chosen
        val failure = s.failure
        if (failure != null) {
            s.exceptions++
            if (s.firstError == null) s.firstError = failure.toString().take(200)
            chosen = valid[rng.choiceIndex(valid.size)]
        }
        if (chosen !in valid) s.invalid++
        if (cfg.enforceTime && s.decisionNanos > 2 * budget) {
            s.timeouts++
            chosen = valid[rng.choiceIndex(valid.size)]
        }
        if (chosen !in valid) chosen = valid[rng.choiceIndex(valid.size)]
        s.dir = chosen
    }

    companion object {
        const val PX = 20

        /** Turns a head flashes after its snake reaches 200 (walls kill) or 300 (rivals kill): 3 s at 15 turns/s. */
        const val FLASH_TURNS = 45
        const val OUT = -1
        private val MILESTONES = intArrayOf(10, 100, 200, 300)
        private val PLACEHOLDER = Enemy(-1, 0, 0, IntArray(0))

        /** Valid moves without reversing (index = current direction), and all four (index 4), in ACTIONS order. */
        val VALID = Array(5) { d -> (0 until 4).filter { d == 4 || it != Rules.OPPOSITE[d] }.toIntArray() }

        /** Snake.shortest_delta: direct, +size or -size, whichever is shortest (the first on ties). */
        fun shortestDelta(from: Int, to: Int, size: Int, toroidal: Boolean): Int {
            val direct = to - from
            if (!toroidal) return direct
            var best = direct
            if (Math.abs(direct + size) < Math.abs(best)) best = direct + size
            if (Math.abs(direct - size) < Math.abs(best)) best = direct - size
            return best
        }

        init {
            check(Move.UP == 0 && Move.DOWN == 1 && Move.LEFT == 2 && Move.RIGHT == 3)
        }
    }
}

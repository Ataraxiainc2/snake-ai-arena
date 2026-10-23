package arena

import arena.engine.Arena
import arena.engine.ArenaConfig
import arena.engine.Brain
import arena.engine.Kind
import arena.engine.Rules
import arena.engine.TacticEntry
import arena.engine.Telemetry
import arena.loader.TacticLoader
import arena.reference.References
import java.io.File
import java.util.Locale

/** Command-line options as `--name value` pairs (and bare `--flag`s). */
class Options(args: List<String>) {
    private val map = HashMap<String, String>()
    val positional = ArrayList<String>()

    init {
        var i = 0
        while (i < args.size) {
            val a = args[i]
            if (a.startsWith("--")) {
                val v = args.getOrNull(i + 1)
                if (v != null && !v.startsWith("--")) { map[a.drop(2)] = v; i++ } else map[a.drop(2)] = "true"
            } else positional.add(a)
            i++
        }
    }

    fun str(name: String, default: String) = map[name] ?: default
    fun int(name: String, default: Int) = map[name]?.toInt() ?: default
    fun long(name: String, default: Long) = map[name]?.toLong() ?: default
    fun double(name: String, default: Double) = map[name]?.toDouble() ?: default
    fun flag(name: String) = map[name] == "true"

    /** Board in cells from --cells WxH, or --size WxH in pixels (SNAKES.py's flag, 20 px per cell). */
    fun board(default: Pair<Int, Int>): Pair<Int, Int> {
        map["cells"]?.let { c -> val (w, h) = c.lowercase().split("x").map { it.toInt() }; return w to h }
        map["size"]?.let { c -> val (w, h) = c.lowercase().split("x").map { it.toInt() }; return w / Arena.PX to h / Arena.PX }
        return default
    }
}

/**
 * One game of a run: the tactic files loaded fresh (new class loaders), the spare slots filled with learning DQN
 * snakes, and what is saved in kotlin/: telemetry_kotlin.json and q_tables_kotlin.bin. With [screen] (the viewer full
 * screen) the board keeps the phone's columns and takes as many rows as the screen's shape needs for square cells
 * (195 x 110 on 16:9), so the snakes move over the whole screen; without it (run, a window) it is the phone's board.
 */
class Session(
    o: Options, private val root: File, round: Int, val events: MutableList<String>, human: Boolean = false,
    screen: Pair<Int, Int>? = null,
) {
    private val loader = TacticLoader(File(root, o.str("folder", TACTICS)), File(WORK, "tactics"))
    private val entries = ArrayList(loader.loadAll { log(it) })
    private val telemetryFile = File(root, o.str("telemetry", "kotlin/telemetry_kotlin.json"))
    private val qFile = File(root, o.str("weights", "kotlin/q_tables_kotlin.bin"))
    val arena: Arena
    val brain: Brain?
    val info: Map<String, Any?>

    init {
        if (o.flag("refs")) entries += Runner.referenceEntries(o.int("ref-copies", 1))
        val players = entries.size + (if (human) 1 else 0)
        // as SNAKES.py's default_arena: tactics + 3 (min 10); the human, when playing, takes one of those 3 slots
        var snakes = o.int("snakes", 0).takeIf { it > 0 } ?: maxOf(players, Rules.defaultSnakes(entries.size))
        if (o.flag("no-dqn")) snakes = minOf(snakes, players) // no spare slots without learners
        // the phone's board (its columns, the screen's shape full screen) unless --cells or --size says otherwise
        val phone = Rules.board(snakes)
        val (w, h) = o.board(screen?.let { (sw, sh) -> phone.first to Math.round(phone.first.toDouble() * sh / sw).toInt() } ?: phone)
        val foods = o.int("foods", 0).takeIf { it > 0 } ?: Rules.defaultFoods(snakes)
        val seed = o.long("seed", System.nanoTime() and 0x7fffffffL) + round - 1
        brain = if (snakes > players && !o.flag("no-dqn")) Brain(seed) else null
        val cfg = ArenaConfig(w, h, snakes, foods, seed, human = human,
            budgetNanos = o.long("budget-us", 0L).takeIf { it > 0 }?.times(1000),
            threads = o.int("threads", Runtime.getRuntime().availableProcessors().coerceAtMost(8)),
            // each call timed by the CPU time of its thread, as on the phone, where that clock is fine (Linux, macOS);
            // by the wall clock with --wall-clock or where it is coarse (Windows: see Clock.kt)
            callClock = if (o.flag("wall-clock")) ({ System.nanoTime() }) else cpuClock ?: { System.nanoTime() })
        arena = Arena(cfg, entries.take(snakes - (if (human) 1 else 0)), brain)
        Brain.load(qFile, brain, arena.snakes)
        info = linkedMapOf(
            "platform" to "pc", "clock" to if (o.flag("wall-clock")) "wall" else clockName, "number" to round, "started" to Telemetry.now(), "mode" to if (human) "view" else "unattended",
            "fps" to o.int("fps", 15), "restart_every_min" to o.double("round-minutes", 0.0).takeIf { it > 0 },
        )
        arena.onEpisodeEnd = { save() }
        println("[KOTLIN ARENA] game $round: ${w}x$h cells, $snakes snakes (${entries.size} tactics, " +
            "${arena.snakes.count { it.kind == Kind.DQN }} DQN${if (human) ", you" else ""}), $foods foods, seed $seed, " +
            "budget ${"%.3f".format(Locale.ROOT, arena.budget / 1e6)} ms (${info["clock"]} clock), ${cfg.threads} threads")
    }

    private fun log(line: String) {
        println(line)
        events.add("${Telemetry.now()} $line")
    }

    fun save() {
        runCatching { Brain.save(qFile, brain, arena.snakes, arena.episode) }.onFailure { println("[SAVE] scores and weights: $it") }
        runCatching { Telemetry.write(telemetryFile, arena, brain, info, events) }.onFailure { println("[SAVE] telemetry: $it") }
        // the phone's games too, for the models, when a phone is connected
        PhoneTelemetry.copyIfConnected(root)
    }

    /**
     * Saves, then reloads the tactic files that changed. Returns true when a new tactic file (or a placeholder that
     * was filled) loads fine: it can only play in a new game, so the caller starts one.
     */
    fun periodic(): Boolean {
        save()
        for (ev in loader.hotReload(entries)) log("[RELOAD] $ev")
        arena.refreshTactics()
        val joining = loader.newEntries(entries.map { it.file }.toSet()) { log(it) }
        if (joining.isNotEmpty()) log("[JOIN] ${joining.joinToString()} ready: a new game starts")
        return joining.isNotEmpty()
    }

    fun top(n: Int) = arena.snakes.sortedByDescending { it.rankingScore }.take(n)
        .joinToString(", ") { "${it.name} ${"%.1f".format(Locale.ROOT, it.rankingScore)}" }
}

/**
 * `run`: SNAKES.py --unattended for the Kotlin tactics. Games follow each other until --hours is up (a new game every
 * --round-minutes, or one game); every --save-minutes the telemetry and weights are saved, changed files reloaded,
 * and a new game starts if a new tactic file is ready (best scores carry over in q_tables_kotlin.bin).
 */
object Runner {
    @Volatile
    private var stopping = false

    /** The run ended by itself: the shutdown hook has nothing to do. */
    @Volatile private var finished = false

    fun run(o: Options, root: File): Int {
        val hours = o.double("hours", 0.0)
        val roundMinutes = o.double("round-minutes", 0.0)
        val fps = o.int("fps", 15)
        val saveMs = (o.double("save-minutes", 5.0) * 60_000).toLong()
        val end = if (hours > 0) System.currentTimeMillis() + (hours * 3_600_000).toLong() else Long.MAX_VALUE
        val main = Thread.currentThread()
        Runtime.getRuntime().addShutdownHook(Thread {
            if (finished) return@Thread
            stopping = true
            Launch.stoppedFromOutside("run")
            main.join(15_000)
        })
        var round = 0
        val events = ArrayList<String>(listOfNotNull(Launch.startLine))
        while (!stopping && System.currentTimeMillis() < end - 5_000) {
            round++
            val game = Session(o, root, round, events)
            val arena = game.arena
            val roundEnd = if (roundMinutes > 0) minOf(end, System.currentTimeMillis() + (roundMinutes * 60_000).toLong()) else end
            var lastSave = System.currentTimeMillis()
            var lastPrint = lastSave
            var lastFrame = 0L
            val pace = Pacer(fps)
            try {
                while (!stopping && System.currentTimeMillis() < roundEnd) {
                    pace.await()
                    arena.tick()
                    val now = System.currentTimeMillis()
                    if (now - lastPrint >= 30_000) {
                        val rate = (arena.frame - lastFrame) * 1000.0 / (now - lastPrint)
                        println("[KOTLIN ARENA] turn ${arena.frame}, ${"%.0f".format(rate)} turns/s, episode ${arena.episode}: ${game.top(3)}")
                        lastPrint = now
                        lastFrame = arena.frame
                    }
                    if (now - lastSave >= saveMs) {
                        lastSave = now
                        if (game.periodic()) break
                    }
                }
            } finally {
                game.save()
                arena.close()
            }
            println("[KOTLIN ARENA] game $round done after ${arena.frame} turns: ${game.top(3)}")
        }
        finished = true
        println("[KOTLIN ARENA] finished.")
        return 0
    }

    /** The organizer's ports of Python tactics, named ref-<file>, [copies] snakes each. */
    fun referenceEntries(copies: Int): List<TacticEntry> =
        (1..copies).flatMap { k ->
            References.all.map { (n, f) -> TacticEntry(if (k == 1) "ref-$n" else "ref-$n#$k", "(reference)", 0, f, reference = true) }
        }
}

/** Keeps a loop at [fps] turns per second (0 = as fast as possible). */
class Pacer(@Volatile var fps: Int) {
    private var next = System.nanoTime()

    fun await() {
        val f = fps
        if (f <= 0) {
            next = System.nanoTime()
            return
        }
        val step = 1_000_000_000L / f
        val wait = next - System.nanoTime()
        if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
        next = maxOf(next + step, System.nanoTime() - step)
    }
}

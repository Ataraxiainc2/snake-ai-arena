package arena

import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Snake Battle Royale AI Arena - Kotlin engine (same game as SNAKES.py; rules: kotlin/tactics/rules_kotlin.txt)

  run     [--hours H] [--round-minutes M] [--snakes N] [--foods N] [--cells WxH | --size WxH(px)] [--fps F (0 = max)]
          [--threads T] [--budget-us U] [--seed S] [--refs] [--ref-copies K] [--no-dqn] [--save-minutes M]
          unattended tournament of kotlin/tactics/*.kt with learning DQN snakes; writes kotlin/telemetry_kotlin.json and
          kotlin/q_tables_kotlin.bin (--refs adds the organizer's ports of Python tactics)
  view    [same options as run] [--human] [--cell PX]   live window; arrow keys steer the human snake
  check   [kotlin/tactics/X.kt ...] audit, compile and time tactic files (all of them when none is given)
  bench   [--snakes 400] [--cells 390x180] [--threads T] [--dqn N] [--warmup W] [--ticks T]   engine + tactics speed
  pack                          compile kotlin/tactics/*.kt for the phone (then ./gradlew phoneDex), see kotlin/README.md
  parity  trace.txt ...         replay games recorded from SNAKES.py by kotlin/parity/python_trace.py
  phone-telemetry               copy the phone's telemetry (EmuGaMind's Snake game, same format) to
                                kotlin/telemetry_phone.json with adb (the phone connected by USB, debugging on)
  sync                          create an empty kotlin/tactics/<Provider-Model_Name>.kt for every known model that has
                                none (models.dev, as SNAKES.py does); existing files are never touched. run and view
                                do it at start unless --no-sync
"""

fun main(args: Array<String>) {
    val cmd = args.firstOrNull() ?: "help"
    val o = Options(args.drop(1))
    val root = arenaRoot()
    if (cmd == "organizer" && args.getOrNull(1) == "init") exitProcess(Organizer.init(root))
    if (cmd in setOf("run", "view", "check", "bench", "sync", "pack", "phone-telemetry")) {
        Organizer.check(root)?.let {
            Launch.start(root, "REFUSED $cmd", args.drop(1), game = false)
            println("[KOTLIN ARENA] not started: $it")
            exitProcess(4)
        }
        Launch.start(root, cmd, args.drop(1), game = cmd == "run" || cmd == "view")?.let {
            println("[KOTLIN ARENA] not started: $it")
            exitProcess(3)
        }
    }
    val code = when (cmd) {
        "run" -> { syncCatalog(o, root); Runner.run(o, root) }
        "view" -> { syncCatalog(o, root); Viewer.run(o, root) }
        "sync" -> { Catalog.sync(File(root, TACTICS), File(root, "tacticas")) { println(it) }; 0 }
        "check" -> Check.run(o.positional, File(root, TACTICS), File(WORK, "tactics"))
        "bench" -> Bench.run(o, root)
        "pack" -> Bench.pack(root)
        "phone-telemetry" -> PhoneTelemetry.pull(root)
        "parity" -> if (o.positional.all { Parity.run(File(it)) }) 0 else 1
        else -> {
            print(USAGE)
            if (cmd == "help" || cmd == "--help") 0 else 2
        }
    }
    System.out.flush()
    exitProcess(code)
}

private fun syncCatalog(o: Options, root: File) {
    if (!o.flag("no-sync")) Catalog.sync(File(root, o.str("folder", TACTICS)), File(root, "tacticas")) { println(it) }
}

/**
 * What the arena keeps for itself, outside the arena folder: compiled tactics (cache), launches.log, the game lock
 * and the organizer key. The arena folder only gets kotlin/telemetry_kotlin.json and kotlin/q_tables_kotlin.bin.
 */
val WORK = File(System.getProperty("user.home"), ".snake-arena")

/** Where the Kotlin tactic files (and rules_kotlin.txt) live, relative to the arena folder. */
const val TACTICS = "kotlin/tactics"

/** The arena folder (the one holding SNAKES.py and tacticas/): the working directory or one of its parents. */
fun arenaRoot(): File {
    var d: File? = File("").absoluteFile
    while (d != null) {
        if (File(d, "SNAKES.py").exists() && File(d, "tacticas").isDirectory) return d
        d = d.parentFile
    }
    return File("").absoluteFile
}

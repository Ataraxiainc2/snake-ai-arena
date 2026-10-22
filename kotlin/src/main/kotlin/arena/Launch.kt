package arena

import arena.engine.Telemetry
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock

/**
 * Who started this arena, and only one game at a time. Every start of `run` or `view` is logged (time, command,
 * the chain of programs that launched it: a terminal, an IDE, an agent's sandbox...) in launches.log
 * (in the user's .snake-arena folder) and in the telemetry's integrity list; a second `run`/`view` while one is playing refuses to start, so nothing
 * overwrites the official telemetry and scores.
 */
object Launch {
    private var lock: FileLock? = null

    /** "pwsh.exe < trae-sandbox.exe < Trae.exe < explorer.exe": the programs above this process, nearest first. */
    fun launchers(): String = runCatching { chain() }.getOrElse { "unknown" } // Android has no ProcessHandle

    private fun chain(): String {
        val names = ArrayList<String>()
        var p = ProcessHandle.current().parent().orElse(null)
        while (p != null && names.size < 8) {
            val cmd = p.info().command().orElse("?")
            names.add(cmd.substringAfterLast('\\').substringAfterLast('/'))
            p = p.parent().orElse(null)
        }
        return names.joinToString(" < ")
    }

    /** Logs the start; for a game, takes the single-game lock or returns why it cannot start. */
    fun start(root: File, cmd: String, args: List<String>, game: Boolean): String? {
        val line = "${Telemetry.now()} [START] $cmd ${args.joinToString(" ")} | launched by ${launchers()}"
        val log = File(WORK, "launches.log")
        runCatching { log.parentFile.mkdirs(); log.appendText(line + "\n") }
        if (!game) return null
        val lockFile = File(WORK, "arena.lock")
        val channel = RandomAccessFile(lockFile, "rw").channel
        val l = runCatching { channel.tryLock() }.getOrNull()
        if (l == null) {
            channel.close()
            return "another Kotlin arena game is already running (see ${File(WORK, "launches.log")}); only one at a time"
        }
        lock = l
        startLine = line
        return null
    }

    /**
     * The game was ended from outside (Ctrl+C, the terminal or the Gradle run closed, the process terminated: exit code
     * 130 or 143): logged, so an unexpected end can be traced to the time it happened. The game saves before exiting.
     */
    fun stoppedFromOutside(cmd: String) {
        runCatching { File(WORK, "launches.log").appendText("${Telemetry.now()} [STOP] $cmd ended from outside the game; saving\n") }
    }

    /** The [START] line of this game, for the telemetry's integrity list. */
    var startLine: String? = null
        private set
}

package arena

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The telemetry EmuGaMind's Snake game writes on the phone (arena.engine.Telemetry, the same format as
 * telemetry_kotlin.json, "platform": "android") copied next to the PC's, as kotlin/telemetry_phone.json, for the models
 * to read. It uses adb with the phone connected by USB (debugging on); the file lives in the app's folder of the shared
 * storage, which adb reads without root. `phone-telemetry` copies it on demand; `run` and `view` copy it by themselves
 * whenever they save (each episode, every few minutes and at the end), if a phone is connected.
 */
object PhoneTelemetry {
    private const val ON_PHONE = "/sdcard/Android/data/com.issasoda.emugamind/files/issaarcade/telemetry_phone.json"
    private const val ADB_TIMEOUT_S = 15L

    /** `phone-telemetry`: copies the file and says what happened. */
    fun pull(root: File): Int = when (copy(root)) {
        Result.COPIED -> 0
        Result.NO_ADB -> {
            println("[PHONE] adb not found: install Android's platform-tools or set ANDROID_HOME")
            2
        }
        else -> {
            println("[PHONE] nothing copied: is the phone connected (USB debugging), and has the game been played and left once?")
            1
        }
    }

    /** At each save of a PC game: copies the phone's file when a phone is connected; silent otherwise. */
    fun copyIfConnected(root: File) {
        copy(root)
    }

    private enum class Result { COPIED, NO_PHONE, NO_FILE, NO_ADB }

    private fun copy(root: File): Result {
        val adb = adb()
        val state = runCatching { run(adb, "get-state") }.getOrElse { return Result.NO_ADB }
        if (state.trim() != "device") return Result.NO_PHONE
        val target = File(root, "kotlin/telemetry_phone.json")
        val tmp = File(target.path + ".tmp")
        runCatching { run(adb, "pull", ON_PHONE, tmp.path) }
        if (!tmp.isFile) return Result.NO_FILE
        renameEntries(tmp)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        println("[PHONE] the phone's telemetry copied to ${target.path} (${target.length()} bytes)")
        return Result.COPIED
    }

    /**
     * A phone with an app built before a rename writes the old names: the copy uses today's (Names.RENAMED), so each
     * model finds its entry as llm.<its file name>. Names are JSON strings, so the quoted name is replaced whole.
     */
    private fun renameEntries(file: File) {
        val text = file.readText()
        val renamed = arena.engine.Names.RENAMED.entries.fold(text) { t, (old, new) -> t.replace("\"$old\"", "\"$new\"") }
        if (renamed != text) file.writeText(renamed)
    }

    /** Runs adb and returns what it printed; a phone that does not answer is given up after a few seconds. */
    private fun run(vararg command: String): String {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        if (!p.waitFor(ADB_TIMEOUT_S, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return ""
        }
        return p.inputStream.bufferedReader().readText()
    }

    /** adb from the Android SDK (ANDROID_HOME, or Windows' default SDK folder), else the one on the PATH. */
    private fun adb(): String {
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val sdks = listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" })
        return sdks.map { File(it, "platform-tools/$exe") }.firstOrNull { it.isFile }?.path ?: "adb"
    }
}

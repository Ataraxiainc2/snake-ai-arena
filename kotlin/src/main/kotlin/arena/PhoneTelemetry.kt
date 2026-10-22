package arena

import java.io.File

/**
 * `phone-telemetry`: the telemetry EmuGaMind's Snake game writes on the phone (arena.engine.Telemetry, the same
 * format as telemetry_kotlin.json, "platform": "android") copied next to the PC's, as kotlin/telemetry_phone.json, for
 * the models to read. It uses adb with the phone connected by USB; the file lives in the app's folder of the shared
 * storage, which adb can read without root.
 */
object PhoneTelemetry {
    private const val ON_PHONE = "/sdcard/Android/data/com.issasoda.emugamind/files/issaarcade/telemetry_phone.json"

    fun pull(root: File): Int {
        val target = File(root, "kotlin/telemetry_phone.json")
        val tmp = File(target.path + ".tmp")
        val code = runCatching {
            ProcessBuilder(adb(), "pull", ON_PHONE, tmp.path).inheritIO().start().waitFor()
        }.getOrElse {
            println("[PHONE] adb not found ($it): install Android's platform-tools or set ANDROID_HOME")
            return 2
        }
        if (code != 0 || !tmp.isFile) {
            println("[PHONE] nothing copied (adb exit $code): is the phone connected, and has the game been played and left once?")
            tmp.delete()
            return 1
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        println("[PHONE] ${target.path} (${target.length()} bytes)")
        return 0
    }

    /** adb from the Android SDK (ANDROID_HOME, or Windows' default SDK folder), else the one on the PATH. */
    private fun adb(): String {
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val sdks = listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" })
        return sdks.map { File(it, "platform-tools/$exe") }.firstOrNull { it.isFile }?.path ?: "adb"
    }
}

package arena

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * On the organizer's PC only the organizer starts the arena. `organizer init` writes a random key to
 * ~/.snake-arena/organizer.key (outside the arena folder, where no agent may read) and its SHA-256 to
 * ~/.snake-arena/organizer.sha256. While that file exists, every command that plays or runs tactics needs the key;
 * agents' attempts are refused and logged. Without it (another PC, GitHub's check) nothing is required.
 */
object Organizer {
    private fun keyFile() = File(WORK, "organizer.key")
    private fun hashFile() = File(WORK, "organizer.sha256")

    private fun sha(s: String) =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** null when this start is allowed, else why not. */
    fun check(root: File): String? {
        val expected = hashFile().takeIf { it.exists() }?.readText()?.trim() ?: return null
        val key = keyFile().takeIf { it.exists() }?.readText()?.trim()
            ?: return "only the organizer may run the arena on this PC (no organizer key)"
        return if (sha(key) == expected) null else "only the organizer may run the arena on this PC (wrong organizer key)"
    }

    fun init(root: File): Int {
        val key = keyFile()
        if (!key.exists()) {
            key.parentFile.mkdirs()
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            key.writeText(bytes.joinToString("") { "%02x".format(it) })
        }
        hashFile().writeText(sha(key.readText().trim()) + "\n")
        println("organizer key: ${key.path} (keep it private); arena locked to it: ${hashFile().path}")
        return 0
    }
}

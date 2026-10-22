package arena.loader

import arena.api.SnakeTactic
import arena.engine.TacticEntry
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.security.MessageDigest

/**
 * Loads tacticas/<Provider-Model_Name>.kt as SNAKES.py loads .py files: empty files are skipped, the rest is audited
 * (Audit.source), compiled with the Kotlin compiler inside this program into its own package
 * (arena.tactics.<id>, with `import arena.api.*`), audited again on the bytecode (Audit.bytecode) and loaded through
 * its own class loader. Compiled classes are cached by content under the build folder, so a restart with unchanged
 * files compiles nothing; every call of load() still gives a fresh class loader, so a new game starts with fresh
 * statics. A file that changes during a game is reloaded (hotReload), keeping the old version if the new one fails.
 */
class TacticLoader(private val folder: File, private val cacheRoot: File) {
    class Loaded(
        val entry: TacticEntry?, val reason: String?, val empty: Boolean = false,
        val classes: File? = null, val className: String? = null,
    )

    private val stamps = HashMap<String, Pair<Long, Long>>()

    fun files(): List<File> =
        (folder.listFiles { f -> f.isFile && f.name.endsWith(".kt") && !f.name.startsWith("_") } ?: emptyArray())
            .sortedBy { it.name }

    /** Every non-empty tactic file that passes; the rejected ones with their reason. */
    fun loadAll(log: (String) -> Unit = {}): List<TacticEntry> {
        val out = ArrayList<TacticEntry>()
        for (f in files()) {
            val r = load(f)
            when {
                r.empty -> {}
                r.entry != null -> out.add(r.entry)
                else -> log("[ANTI-CHEAT] '${f.name}' not loaded: ${r.reason}")
            }
        }
        return out
    }

    fun load(file: File): Loaded {
        stamps[file.path] = file.lastModified() to file.length()
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { return Loaded(null, "read error: $it") }
        if (raw.isBlank()) return Loaded(null, "empty", empty = true)
        // Python counts characters with universal newlines (CRLF = 1): the same here, for the ranking's code factor
        val src = raw.replace("\r\n", "\n").replace('\r', '\n')
        Audit.source(src)?.let { return Loaded(null, "DISQUALIFIED by the audit: $it") }
        val stem = file.name.removeSuffix(".kt")
        val id = packageId(stem)
        val classes = compileCached(id, src).getOrElse { return Loaded(null, it.message) }
        val pkg = "arena/tactics/$id/"
        for (cf in classes.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }) {
            val bad = runCatching { Audit.bytecode(cf.readBytes(), pkg) }.getOrElse { "unreadable class file ($it)" }
            if (bad != null) return Loaded(null, "DISQUALIFIED by the audit: uses $bad")
        }
        val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), SnakeTactic::class.java.classLoader)
        val cls = runCatching { loader.loadClass("arena.tactics.$id.Tactic") }
            .getOrElse { return Loaded(null, "no class Tactic") }
        if (!SnakeTactic::class.java.isAssignableFrom(cls)) return Loaded(null, "class Tactic is not a SnakeTactic")
        val ctor = runCatching { cls.getDeclaredConstructor() }.getOrElse { return Loaded(null, "Tactic() needs no arguments") }
        val factory = { ctor.newInstance() as SnakeTactic }
        runCatching { factory() }.onFailure { return Loaded(null, "Tactic() failed: ${it.cause ?: it}") }
        markPassed(src)
        return Loaded(TacticEntry(stem.take(36), file.name, src.length, factory), null, classes = classes, className = "arena.tactics.$id.Tactic")
    }

    /** Files of [entries] that changed on disk are reloaded; returns one log line per change. */
    fun hotReload(entries: List<TacticEntry>): List<String> {
        val events = ArrayList<String>()
        for (e in entries) {
            val f = File(folder, e.file)
            val stamp = if (f.exists()) f.lastModified() to f.length() else null
            if (stamp == stamps[f.path]) continue
            if (stamp == null) {
                stamps[f.path] = 0L to -1L
                events.add("tactic_missing_kept_old ${e.file}")
                continue
            }
            val r = load(f)
            val new = r.entry
            if (new == null) {
                events.add("tactic_reload_rejected ${e.file} ${r.reason}")
                continue
            }
            e.factory = new.factory
            e.chars = new.chars
            e.reloads++
            events.add("tactic_reloaded ${e.file} ${new.chars} chars")
        }
        return events
    }

    private val tried = HashMap<String, Pair<Long, Long>>()

    /**
     * Non-empty tactic files that are not among [playing] (file names) and load fine: they join at the next game.
     * A file that fails is reported once per version.
     */
    fun newEntries(playing: Set<String>, log: (String) -> Unit = {}): List<String> {
        val out = ArrayList<String>()
        for (f in files()) {
            if (f.name in playing || f.length() == 0L) continue
            val stamp = f.lastModified() to f.length()
            if (tried[f.path] == stamp) continue
            tried[f.path] = stamp
            val r = load(f)
            when {
                r.entry != null -> out.add(f.name)
                !r.empty -> log("[ANTI-CHEAT] '${f.name}' not loaded: ${r.reason}")
            }
        }
        return out
    }

    /**
     * Records that this exact content passed (audit, compile, bytecode audit, a Tactic() made): an empty file named
     * by the SHA-256 of the source with LF line ends, in passed/ next to the compile cache (~/.snake-arena/passed).
     * EmuGaMind's build takes a tactic from this PC's arena folder only when its content is recorded here, so a file
     * a model is still writing never reaches the app.
     */
    private fun markPassed(src: String) {
        runCatching { File(cacheRoot.parentFile, "passed").apply { mkdirs() }.resolve(sha(src)).createNewFile() }
    }

    private fun packageId(stem: String): String {
        val clean = stem.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        return "t_" + clean + "_" + sha(stem).take(6)
    }

    private fun compileCached(id: String, src: String): Result<File> {
        val dir = File(cacheRoot, "$id-${sha(COMPILE_VERSION + src).take(12)}")
        val classes = File(dir, "classes")
        val done = File(dir, "ok")
        if (done.exists()) return Result.success(classes)
        dir.deleteRecursively()
        classes.mkdirs()
        val file = File(dir, "src/$id.kt").apply { parentFile.mkdirs() }
        file.writeText("package arena.tactics.$id\nimport arena.api.*\n$src", Charsets.UTF_8)
        val messages = compile(file, classes, id)
        if (messages != null) {
            dir.deleteRecursively()
            return Result.failure(IllegalArgumentException("compile error: $messages"))
        }
        done.writeText("ok")
        return Result.success(classes)
    }

    /** null when it compiled, else the compiler's errors (line numbers of the original file). */
    private fun compile(src: File, out: File, module: String): String? = synchronized(TacticLoader) {
        setIdeaIoUseFallback()
        val err = ByteArrayOutputStream()
        val args = arrayOf(
            src.path, "-d", out.path, "-classpath", compileClasspath(), "-no-stdlib", "-no-reflect",
            "-jvm-target", JVM_TARGET, "-module-name", module, "-nowarn",
            "-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions",
            // the standard JAR reader: the "fast" one calls sun.misc.Unsafe, deprecated for removal since Java 23
            "-Xuse-fast-jar-file-system=false",
        )
        val code = K2JVMCompiler().exec(PrintStream(err, true, "UTF-8"), *args)
        if (code.code == 0) return null
        err.toString(Charsets.UTF_8).lines().filter { "error" in it }.joinToString(" / ") { line ->
            LINE.replace(line.substringAfter(src.name)) { m -> ":${m.groupValues[1].toInt() - 2}:" }.trim()
        }.take(600).ifEmpty { "exit code $code" }
    }

    companion object {
        /** The bytecode of compiled tactics: the engine's (build.gradle.kts), which d8 turns into DEX for the phone. */
        const val JVM_TARGET = "26"
        private val COMPILE_VERSION = "arena-api-1 kotlin-${KotlinVersion.CURRENT} jvm-$JVM_TARGET"
        private val LINE = Regex("^:(\\d+):")

        fun compileClasspath(): String = listOf(SnakeTactic::class.java, KotlinVersion::class.java)
            .map { File(it.protectionDomain.codeSource.location.toURI()).path }.distinct()
            .joinToString(File.pathSeparator)

        private fun sha(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

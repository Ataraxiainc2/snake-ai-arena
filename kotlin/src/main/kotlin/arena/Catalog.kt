package arena

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.time.Duration

/**
 * SNAKES.py's sync_model_catalog_files for the Kotlin arena: every text model of https://models.dev/api.json (same
 * filters and file names as SNAKES.py), plus the name of every non-empty Python tactic in tacticas/, gets an empty
 * kotlin/tactics/Provider-Model_Name.kt ready to be filled. Existing files are never touched: each one is created only
 * if it does not exist (an atomic create, so a file a model is writing at that moment is safe). Empty files do not play.
 */
object Catalog {
    private const val URL = "https://models.dev/api.json"
    private val EXCLUDED = Regex(
        "embed|whisper|tts|audio|speech|moderation|rerank|image|dall-?e|imagen|veo|sora|" +
            "realtime|transcribe|guard|aqa|search|batch|lyria|banana|asr|ocr",
        RegexOption.IGNORE_CASE,
    )

    /** Creates the missing placeholders in [folder]; returns how many (0 when offline). */
    fun sync(folder: File, pythonFolder: File, log: (String) -> Unit = {}): Int {
        folder.mkdirs()
        val names = sortedSetOf("Google-Gemini_4_Argon_Medium")
        runCatching { names.addAll(fromModelsDev()) }.onFailure { log("[CATALOG] models.dev not reached ($it)") }
        pythonFolder.listFiles { f -> f.isFile && f.name.endsWith(".py") && f.length() > 0 && !f.name.startsWith("_") }
            ?.forEach { names.add(it.name.removeSuffix(".py")) }
        var created = 0
        for (n in names) {
            if (!n.matches(Regex("[A-Za-z0-9._-]+"))) continue
            try {
                Files.createFile(File(folder, "$n.kt").toPath())
                created++
            } catch (_: FileAlreadyExistsException) {
            } catch (e: Exception) {
                log("[CATALOG] $n.kt not created ($e)")
            }
        }
        if (created > 0) log("[CATALOG] $created new empty files in ${folder.path} (empty files do not play)")
        return created
    }

    /** _clean_filename_token */
    private fun clean(text: String): String =
        text.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").replace(Regex("_+"), "_").trim('_')

    private fun fromModelsDev(): Set<String> {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        val req = HttpRequest.newBuilder(URI(URL)).timeout(Duration.ofSeconds(30)).header("User-Agent", "Mozilla/5.0").build()
        val body = client.send(req, HttpResponse.BodyHandlers.ofString()).body()
        val root = entries(Json(body).parse())
        // labs that create models (others list them as canonical_model_id "lab/model"), not resellers
        val reach = HashMap<String, Int>()
        for ((host, pdata) in root) {
            for (m in models(pdata).values) {
                val cid = (m as? Map<*, *>)?.get("canonical_model_id") as? String ?: continue
                val lab = cid.substringBefore("/", "")
                if (lab.isNotEmpty() && lab != host) reach.merge(lab, 1, Int::plus)
            }
        }
        val out = HashSet<String>()
        for ((pid, pdata) in root) {
            if (pdata !is Map<*, *> || (reach[pid] ?: 0) == 0) continue
            val provider = clean(pdata["name"] as? String ?: pid)
            for ((mid, m) in models(pdata)) {
                if (m !is Map<*, *>) continue
                val name = m["name"] as? String ?: ""
                if (EXCLUDED.containsMatchIn(mid) || EXCLUDED.containsMatchIn(name)) continue
                val output = ((m["modalities"] as? Map<*, *>)?.get("output") as? List<*>) ?: listOf("text")
                if ("text" !in output) continue
                val cid = m["canonical_model_id"] as? String ?: ""
                if (cid.isNotEmpty() && !cid.startsWith("$pid/")) continue
                val model = clean(name.ifEmpty { mid })
                if (provider.isNotEmpty() && model.isNotEmpty()) out.add("$provider-$model")
            }
        }
        return out
    }

    private fun models(pdata: Any?): Map<String, Any?> = entries((pdata as? Map<*, *>)?.get("models"))

    /** A JSON object as a map with String keys (empty when it is not an object): no unchecked cast. */
    private fun entries(v: Any?): Map<String, Any?> =
        (v as? Map<*, *>)?.entries?.associate { (k, x) -> k.toString() to x } ?: emptyMap()

    /** A small JSON reader (objects keep their order). */
    private class Json(private val s: String) {
        private var i = 0

        fun parse(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> {
                    i++
                    val m = LinkedHashMap<String, Any?>()
                    ws()
                    if (s[i] == '}') { i++; return m }
                    while (true) {
                        ws(); val k = str(); ws(); i++ // ':'
                        m[k] = parse(); ws()
                        if (s[i++] == '}') return m
                    }
                }
                '[' -> {
                    i++
                    val l = ArrayList<Any?>()
                    ws()
                    if (s[i] == ']') { i++; return l }
                    while (true) {
                        l.add(parse()); ws()
                        if (s[i++] == ']') return l
                    }
                }
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> {
                    val st = i
                    while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
                    if (st == i) error("bad JSON at $i: '$c'")
                    s.substring(st, i).toDouble()
                }
            }
        }

        private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }
    }
}

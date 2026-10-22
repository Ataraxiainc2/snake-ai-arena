package arena

import arena.loader.Audit
import arena.loader.TacticLoader
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuditTest {
    private fun tactic(body: String, imports: String = "") = """
        $imports
        class Tactic : SnakeTactic {
            $body
        }
    """.trimIndent()

    @Test
    fun sourceRules() {
        val ok = tactic("override fun decide(p: Perception): Int = p.validMoves[0]")
        assertNull(Audit.source(ok))
        assertNull(Audit.source(tactic("override fun decide(p: Perception) = p.validMoves[0]", "import kotlin.math.abs\nimport kotlin.random.Random")))
        // strings and comments may say anything
        assertNull(Audit.source(tactic("val s = \"System.exit\" // Thread\noverride fun decide(p: Perception) = p.validMoves[0]")))
        // a var in a class, a function or an object expression inside a function is fine
        assertNull(Audit.source(tactic("var n = 0\noverride fun decide(p: Perception): Int { var k = 0; val o = object { var x = 1 }; return p.validMoves[k + o.x - 1] }")))

        val bad = mapOf(
            "System.nanoTime()" to "'System'",
            "Thread.sleep(1)" to "'Thread'",
            "println(1)" to "'println'",
            "java.io.File(\"x\")" to "'java.'",
            "String::class" to "'::class'",
            "\"\${System.currentTimeMillis()}\"" to "'System'",
        )
        for ((code, why) in bad) {
            val r = Audit.source(tactic("override fun decide(p: Perception): Int { val x = $code; return p.validMoves[0] }"))
            assertNotNull(r, code)
            assertTrue(why in r, "$code -> $r")
        }
        assertNotNull(Audit.source("var shared = 0\n" + tactic("override fun decide(p: Perception) = p.validMoves[0]")))
        assertNotNull(Audit.source("object M { var games = 0 }\n" + tactic("override fun decide(p: Perception) = p.validMoves[0]")))
        assertNotNull(Audit.source(tactic("companion object { var games = 0 }\noverride fun decide(p: Perception) = p.validMoves[0]")))
        assertNotNull(Audit.source("package x\n" + tactic("override fun decide(p: Perception) = p.validMoves[0]")))
        assertNotNull(Audit.source(tactic("override fun decide(p: Perception) = p.validMoves[0]", "import java.util.PriorityQueue")))
    }

    /** Compiles and loads through the real loader: ordinary Kotlin passes, a class outside the allow-list does not. */
    @Test
    fun compileAndBytecode() {
        val dir = Files.createTempDirectory("audit").toFile()
        val folder = File(dir, "tacticas").apply { mkdirs() }
        val loader = TacticLoader(folder, File(dir, "cache"))
        File(folder, "Rich.kt").writeText(
            tactic(
                """
                private enum class Way { A, B }
                private data class Node(val c: Int, val d: Int)
                private val q = ArrayDeque<Int>()
                private val seen = HashSet<Int>()
                override fun decide(p: Perception): Int {
                    q.clear(); seen.clear(); q.add(p.myBody[0])
                    val m = HashMap<Int, Int>(); m[1] = 2
                    val sorted = p.validMoves.sortedBy { -it }.toMutableList(); sorted.sortWith(compareBy { it })
                    val ss = sortedSetOf(3, 1, 2)
                    val seq = sequence { yield(1) }.toList()
                    val nodes = listOf(Node(1, 2)).maxBy { it.d }
                    val txt = "n=${'$'}{nodes.c} ${'$'}{Way.valueOf("A")}" + String.format("%d", 3)
                    val r = Random(p.turn).nextInt(1) + abs(-1) - 1 + max(0, 0) + floor(0.5).toInt()
                    val arr = IntArray(4).also { it.fill(1) }; arr.sort()
                    return p.validMoves[(r + txt.length * 0 + ss.first() - 1 + seq[0] - 1 + m.size - 1 + arr[0] - 1) % p.validMoves.size]
                }
                """,
                "import kotlin.math.*\nimport kotlin.random.Random",
            ),
        )
        val rich = loader.load(File(folder, "Rich.kt"))
        assertNull(rich.reason, rich.reason)
        File(folder, "Buffer.kt").writeText(tactic("override fun decide(p: Perception): Int { val b = StringBuffer(); b.append(1); return p.validMoves[b.length - 1] }"))
        val buffer = loader.load(File(folder, "Buffer.kt"))
        assertNotNull(buffer.reason)
        assertTrue("java.lang.StringBuffer" in buffer.reason, buffer.reason)
        File(folder, "Empty.kt").writeText("  \n")
        assertTrue(loader.load(File(folder, "Empty.kt")).empty)
        assertEquals(1, loader.loadAll().size)
        dir.deleteRecursively()
    }
}

package arena.loader

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * The anti-cheat audit of a Kotlin tactic (kotlin/tactics/rules_kotlin.txt section 4), in two layers:
 *  - source: what the rules forbid by name (imports outside kotlin.math/random/collections, java./android./... names,
 *    System, Thread, reflection, printing, top-level or object vars, ...), on the code without comments and strings;
 *  - bytecode: every class the compiled tactic refers to must be its own, the arena's API, the Kotlin standard library
 *    without its I/O, system, thread, time and reflection parts, or a short list of plain java.lang/java.util classes.
 * The source layer gives readable reasons; the bytecode layer is what makes the audit hard to get around.
 */
object Audit {
    private val ALLOWED_IMPORTS = listOf("kotlin.math.", "kotlin.random.", "kotlin.collections.", "arena.api.")
    private val FORBIDDEN_NAMES = setOf(
        "System", "Runtime", "Thread", "ThreadLocal", "InheritableThreadLocal", "Process", "ProcessBuilder",
        "ProcessHandle", "ClassLoader", "Class", "KClass", "javaClass", "javaObjectType", "Unsafe", "println", "print",
        "readLine", "readln", "readlnOrNull", "synchronized", "external", "JvmStatic", "Volatile", "Synchronized",
        "exitProcess", "measureTimeMillis", "measureNanoTime", "TimeSource", "StackWalker", "SecurityManager",
    )
    private val FORBIDDEN_ROOTS = setOf("java", "javax", "android", "kotlinx", "dalvik", "sun", "jdk", "androidx")

    /** null when the source passes, else the reason. */
    fun source(src: String): String? {
        val toks = tokens(src) ?: return "unterminated comment or string"
        var depth = 0
        val blocks = ArrayList<Char>() // 'o' object, 'c' class/interface, 'f' fun, 'b' other block
        var pending: Char? = null
        var prev = ""
        for ((i, t) in toks.withIndex()) {
            val nextTok = toks.getOrNull(i + 1) ?: ""
            when {
                t == "package" && depth == 0 -> return "package line (the arena adds the package)"
                t == "@" && nextTok == "file" -> return "file annotation"
                t == "import" -> {
                    // the dotted name after import (tokens carry no newlines: it ends where two names meet)
                    val sb = StringBuilder()
                    var k = i + 1
                    var lastIdent = false
                    while (k < toks.size) {
                        val x = toks[k]
                        val id = isIdent(x)
                        if (id && lastIdent || !id && x != "." && x != "*") break
                        sb.append(x)
                        lastIdent = id
                        k++
                    }
                    val path = sb.toString()
                    if (ALLOWED_IMPORTS.none { path.startsWith(it) })
                        return "import $path (allowed: kotlin.math, kotlin.random, kotlin.collections)"
                }
                t in FORBIDDEN_NAMES && prev != "." -> return "'$t' is not allowed"
                t in FORBIDDEN_NAMES && prev == "." && t in setOf("javaClass", "javaObjectType") -> return "'$t' is not allowed"
                t in FORBIDDEN_ROOTS && nextTok == "." && prev != "." -> return "'$t.' names are not allowed"
                t == "::" && nextTok == "class" -> return "'::class' (reflection) is not allowed"
                t == "class" || t == "interface" -> if (prev != "::") pending = 'c'
                t == "fun" -> pending = 'f'
                t == "object" -> pending = if (prev in setOf("=", "(", ",", "return", "->", "else", "?:", "{")) 'b' else 'o'
                t == "{" -> {
                    blocks.add(pending ?: 'b'); pending = null; depth++
                }
                t == "}" -> {
                    if (blocks.isNotEmpty()) blocks.removeAt(blocks.size - 1)
                    depth--
                }
                t == "var" -> {
                    if (depth == 0) return "top-level var (state shared by every snake and game)"
                    if (blocks.lastOrNull() == 'o') return "var in an object or companion object (shared state)"
                }
            }
            prev = t
        }
        return null
    }

    private fun isIdent(t: String) = t.isNotEmpty() && (t[0].isLetter() || t[0] == '_' || t[0] == '`')

    /** Identifiers, numbers and operator characters, without comments; string literals keep only their templates. */
    fun tokens(src: String): List<String>? {
        val out = ArrayList<String>()
        var i = 0
        val n = src.length
        fun template(start: Int): Int { // src[start] == '{' after '$': tokens of the expression, returns index after '}'
            var depth = 0
            var j = start
            val inner = StringBuilder()
            while (j < n) {
                val c = src[j]
                if (c == '{') depth++
                if (c == '}') { depth--; if (depth == 0) break }
                if (depth > 0 && !(depth == 1 && c == '{')) inner.append(c)
                j++
            }
            out.addAll(tokens(inner.toString()) ?: return -1)
            return j + 1
        }
        while (i < n) {
            val c = src[i]
            when {
                c.isWhitespace() -> i++
                src.startsWith("//", i) -> { while (i < n && src[i] != '\n') i++ }
                src.startsWith("/*", i) -> {
                    var depth = 0
                    while (i < n) {
                        if (src.startsWith("/*", i)) { depth++; i += 2 } else if (src.startsWith("*/", i)) { depth--; i += 2; if (depth == 0) break } else i++
                    }
                    if (depth != 0) return null
                }
                src.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (true) {
                        if (i >= n) return null
                        if (src.startsWith("\"\"\"", i)) { i += 3; while (i < n && src[i] == '"') i++; break }
                        if (src[i] == '$' && i + 1 < n && src[i + 1] == '{') { i = template(i + 1); if (i < 0) return null; continue }
                        if (src[i] == '$' && i + 1 < n && (src[i + 1].isLetter() || src[i + 1] == '_')) {
                            var j = i + 1; while (j < n && (src[j].isLetterOrDigit() || src[j] == '_')) j++
                            out.add(src.substring(i + 1, j)); i = j; continue
                        }
                        i++
                    }
                    out.add("\"\"")
                }
                c == '"' -> {
                    i++
                    while (true) {
                        if (i >= n || src[i] == '\n') return null
                        val d = src[i]
                        if (d == '\\') { i += 2; continue }
                        if (d == '"') { i++; break }
                        if (d == '$' && i + 1 < n && src[i + 1] == '{') { i = template(i + 1); if (i < 0) return null; continue }
                        if (d == '$' && i + 1 < n && (src[i + 1].isLetter() || src[i + 1] == '_')) {
                            var j = i + 1; while (j < n && (src[j].isLetterOrDigit() || src[j] == '_')) j++
                            out.add(src.substring(i + 1, j)); i = j; continue
                        }
                        i++
                    }
                    out.add("\"\"")
                }
                c == '\'' -> {
                    i++
                    while (i < n && src[i] != '\'') { if (src[i] == '\\') i++; i++ }
                    i++
                    out.add("''")
                }
                c == '`' -> {
                    val j = src.indexOf('`', i + 1)
                    if (j < 0) return null
                    out.add(src.substring(i + 1, j)); i = j + 1
                }
                c.isLetter() || c == '_' -> {
                    var j = i; while (j < n && (src[j].isLetterOrDigit() || src[j] == '_')) j++
                    out.add(src.substring(i, j)); i = j
                }
                c.isDigit() -> {
                    var j = i; while (j < n && (src[j].isLetterOrDigit() || src[j] == '_' || src[j] == '.' && j + 1 < n && src[j + 1].isDigit())) j++
                    out.add(src.substring(i, j)); i = j
                }
                src.startsWith("::", i) -> { out.add("::"); i += 2 }
                src.startsWith("?:", i) -> { out.add("?:"); i += 2 }
                src.startsWith("->", i) -> { out.add("->"); i += 2 }
                else -> { out.add(c.toString()); i++ }
            }
        }
        return out
    }

    private val JAVA_OK = setOf(
        "java/lang/Object", "java/lang/String", "java/lang/StringBuilder", "java/lang/Math", "java/lang/StrictMath",
        "java/lang/Integer", "java/lang/Long", "java/lang/Double", "java/lang/Float", "java/lang/Short",
        "java/lang/Byte", "java/lang/Boolean", "java/lang/Character", "java/lang/Number", "java/lang/Comparable",
        "java/lang/Iterable", "java/lang/CharSequence", "java/lang/Enum", "java/lang/Void", "java/lang/Cloneable",
        "java/lang/Throwable", "java/lang/Exception", "java/lang/RuntimeException", "java/lang/Error",
        "java/lang/IllegalStateException", "java/lang/IllegalArgumentException", "java/lang/IndexOutOfBoundsException",
        "java/lang/ArrayIndexOutOfBoundsException", "java/lang/NullPointerException",
        "java/lang/UnsupportedOperationException", "java/lang/ArithmeticException", "java/lang/ClassCastException",
        "java/lang/NumberFormatException", "java/lang/AssertionError",
        "java/lang/invoke/LambdaMetafactory", "java/lang/invoke/StringConcatFactory", "java/lang/invoke/MethodHandles",
        "java/lang/invoke/MethodHandles\$Lookup", "java/lang/invoke/MethodHandle", "java/lang/invoke/MethodType",
        "java/lang/invoke/CallSite",
        "java/util/Arrays", "java/util/Collection", "java/util/Collections", "java/util/List", "java/util/ArrayList",
        "java/util/Map", "java/util/HashMap", "java/util/LinkedHashMap", "java/util/Set", "java/util/HashSet",
        "java/util/LinkedHashSet", "java/util/Iterator", "java/util/ListIterator", "java/util/Comparator",
        "java/util/Map\$Entry", "java/util/NoSuchElementException", "java/util/RandomAccess", "java/util/AbstractList",
        "java/util/AbstractCollection", "java/util/AbstractMap", "java/util/AbstractSet", "java/util/Objects",
        // what kotlin.collections hands out on the JVM (sortedSetOf, toSortedMap, ...)
        "java/util/SortedSet", "java/util/TreeSet", "java/util/SortedMap", "java/util/TreeMap", "java/util/NavigableSet",
        "java/util/NavigableMap", "java/util/Locale",
    )
    private val KOTLIN_NO = listOf(
        "kotlin/io/", "kotlin/system/", "kotlin/concurrent/", "kotlin/time/", "kotlin/reflect/",
        "kotlin/jvm/internal/Reflection", "kotlin/jvm/internal/ClassReference", "kotlin/jvm/JvmClassMappingKt",
        "kotlin/random/jdk8/", "kotlin/streams/",
    )

    /** null when every class [classFile] refers to is allowed, else the first one that is not. */
    fun bytecode(classFile: ByteArray, ownPackage: String): String? {
        val refs = referencedClasses(classFile)
        for ((name, inDescriptor) in refs) {
            if (name.startsWith(ownPackage) || name.startsWith("arena/api/")) continue
            if (name.startsWith("kotlin/")) {
                if (KOTLIN_NO.any { name.startsWith(it) }) return name.replace('/', '.')
                continue
            }
            if (name in JAVA_OK) continue
            if (inDescriptor && name == "java/lang/Class") continue // Enum.valueOf(Class, String) of enum classes
            return name.replace('/', '.')
        }
        return null
    }

    /** Classes named by CONSTANT_Class entries (false) and by member-reference descriptors (true). */
    private fun referencedClasses(bytes: ByteArray): List<Pair<String, Boolean>> {
        val inp = DataInputStream(ByteArrayInputStream(bytes))
        require(inp.readInt() == 0xCAFEBABE.toInt()) { "not a class file" }
        inp.readUnsignedShort(); inp.readUnsignedShort()
        val count = inp.readUnsignedShort()
        val utf = arrayOfNulls<String>(count)
        val classIdx = ArrayList<Int>()
        val natDesc = ArrayList<Int>()
        val memberNat = ArrayList<Int>()
        val natOf = HashMap<Int, Int>()
        var i = 1
        while (i < count) {
            when (val tag = inp.readUnsignedByte()) {
                1 -> utf[i] = inp.readUTF()
                3, 4 -> inp.readInt()
                5, 6 -> { inp.readLong(); i++ }
                7 -> classIdx.add(inp.readUnsignedShort())
                8, 16, 19, 20 -> inp.readUnsignedShort()
                9, 10, 11 -> { inp.readUnsignedShort(); memberNat.add(inp.readUnsignedShort()) }
                12 -> { inp.readUnsignedShort(); natOf[i] = inp.readUnsignedShort() }
                15 -> { inp.readUnsignedByte(); inp.readUnsignedShort() }
                17, 18 -> { inp.readUnsignedShort(); natDesc.add(inp.readUnsignedShort()) }
                else -> error("bad constant pool tag $tag")
            }
            i++
        }
        val out = ArrayList<Pair<String, Boolean>>()
        val typeRe = Regex("L([^;]+);")
        for (ci in classIdx) {
            val name = utf[ci] ?: continue
            if (name.startsWith("[")) typeRe.findAll(name).forEach { out.add(it.groupValues[1] to false) } else out.add(name to false)
        }
        for (nat in memberNat + natDesc) {
            val desc = natOf[nat]?.let { utf[it] } ?: continue
            typeRe.findAll(desc).forEach { out.add(it.groupValues[1] to true) }
        }
        return out
    }
}

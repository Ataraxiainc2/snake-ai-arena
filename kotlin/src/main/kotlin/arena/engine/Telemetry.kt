package arena.engine

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * telemetry_kotlin.json: the same compact layout as SNAKES.py's telemetry.json (one line per ranking row and per
 * tactic), for the Kotlin arena's rounds. ms are per decide() call; "over_budget" counts calls above the budget,
 * "timeouts" those above twice the budget (they played a random move).
 */
object Telemetry {
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    fun now(): String = LocalDateTime.now().format(TIME)

    fun write(file: File, a: Arena, brain: Brain?, run: Map<String, Any?>, events: List<String>) {
        val ranked = a.snakes.sortedByDescending { it.rankingScore }
        val ranking = ranked.mapIndexed { i, s ->
            listOf(i + 1, s.name, round(s.rankingScore, 1), s.bestScore, s.entry?.chars, round(s.codeFactor, 3), type(s))
        }
        val llm = LinkedHashMap<String, Any?>()
        for (s in ranked) {
            if (s.kind != Kind.TACTIC) continue
            val ended = s.lives.filter { it.cause != "episode_reset" }
            val milestones = HashMap<Int, MutableList<Long>>()
            for (l in s.lives) for ((k, v) in l.turnsToScore) milestones.getOrPut(k) { ArrayList() }.add(v)
            for ((k, v) in s.milestones) milestones.getOrPut(k) { ArrayList() }.add(v)
            llm[s.name] = linkedMapOf(
                "max" to s.bestScore, "max_game" to s.gameBest, "now" to s.score, "foods" to s.foodsEaten,
                "deaths" to count(ended.map { it.cause }),
                "death_score_phase" to count(ended.map { phase(it.score) }),
                "killed_by" to count(ended.mapNotNull { it.killer }),
                "ms" to linkedMapOf(
                    "avg" to if (s.decCount > 0) round(s.decSumMs / s.decCount, 3) else null,
                    "p99" to s.p99Ms()?.let { round(it, 3) }, "max" to round(s.decMaxMs, 2), "over_budget" to s.decOver,
                ),
                "timeouts" to s.timeouts, "errors" to s.exceptions, "invalid" to s.invalid,
                "first_error" to s.firstError,
                "median_turns_to" to milestones.toSortedMap().mapKeys { it.key.toString() }
                    .mapValues { (_, v) -> v.sorted()[v.size / 2] },
                "last_lives" to ended.takeLast(8).map { listOf(it.score, it.cause, it.killer, it.endTurn - it.startTurn) },
            )
        }
        val dqn = a.snakes.filter { it.kind == Kind.DQN }
        val data = linkedMapOf<String, Any?>(
            "readme" to ("Kotlin arena (kotlin/ next to SNAKES.py), same game as SNAKES.py in cells. Layout as telemetry.json: " +
                "ranking rows = [rank, name, ranking_score, max_score, code_chars, code_factor, type]; llm.<name>: max (all games) / max_game (this game) / now score, " +
                "foods, deaths by cause, death_score_phase, killed_by, ms = decide() time per call (avg, p99, max, " +
                "over_budget = calls above arena.budget_ms; timeouts = above twice the budget, played at random), median_turns_to " +
                "10/100/200/300, last_lives rows = [score, cause, killer, turns lived]."),
            "updated" to now(),
            "arena" to linkedMapOf(
                "engine" to "kotlin", "cells" to listOf(a.width, a.height), "snakes" to a.snakes.size, "foods" to a.cfg.foods,
                "turns" to a.frame, "episodes" to a.episode, "budget_ms" to round(a.budget / 1e6, 3),
                "threads" to a.cfg.threads, "seed" to a.cfg.seed, "run" to run,
            ),
            "ranking" to ranking,
            "llm" to llm,
            "dqn" to linkedMapOf(
                "snakes" to dqn.size, "best_max" to dqn.maxOfOrNull { it.bestScore },
                "deaths" to count(dqn.flatMap { s -> s.lives.filter { it.cause != "episode_reset" }.map { it.cause } }),
                "epsilon" to dqn.firstOrNull()?.let { round(it.epsilon, 3) },
                "replay_buffer" to brain?.size, "avg_train_loss" to brain?.let { if (it.lossCount > 0) round(it.lossSum / it.lossCount, 4) else null },
            ),
            "integrity" to events.takeLast(30),
        )
        val sb = StringBuilder("{\n")
        val keys = data.keys.toList()
        for ((i, k) in keys.withIndex()) {
            val v = data[k]
            sb.append(' ').append(json(k)).append(": ")
            when {
                (k == "ranking" || k == "integrity") && v is List<*> && v.isNotEmpty() ->
                    sb.append("[\n  ").append(v.joinToString(",\n  ") { json(it) }).append("\n ]")
                k == "llm" && v is Map<*, *> && v.isNotEmpty() ->
                    sb.append("{\n  ").append(v.entries.joinToString(",\n  ") { json(it.key) + ": " + json(it.value) }).append("\n }")
                else -> sb.append(json(v))
            }
            sb.append(if (i < keys.size - 1) ",\n" else "\n")
        }
        sb.append("}\n")
        val tmp = File(file.path + ".tmp")
        tmp.writeText(sb.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun type(s: Snake) = when (s.kind) {
        Kind.HUMAN -> "human"
        Kind.TACTIC -> "llm"
        Kind.DQN -> "dqn"
    }

    private fun phase(score: Int) = when {
        score < 100 -> "0-99"
        score < 200 -> "100-199"
        score < 300 -> "200-299"
        else -> "300+"
    }

    private fun count(items: List<String>): Map<String, Int> = items.groupingBy { it }.eachCount()

    private fun round(v: Double, digits: Int): Double {
        val f = Math.pow(10.0, digits.toDouble())
        return Math.round(v * f) / f
    }

    fun json(v: Any?): String = when (v) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (c in v) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
            append('"')
        }
        is Double -> if (v.isNaN() || v.isInfinite()) "null" else v.toString()
        is Float -> json(v.toDouble())
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> v.entries.joinToString(", ", "{", "}") { json(it.key.toString()) + ": " + json(it.value) }
        is Iterable<*> -> v.joinToString(", ", "[", "]") { json(it) }
        else -> json(v.toString())
    }
}

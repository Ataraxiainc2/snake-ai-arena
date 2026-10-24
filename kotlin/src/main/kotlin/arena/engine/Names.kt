package arena.engine

/**
 * Tactic names (the file name is the snake's name), v2 since 2026-10-24: Lab-Model[_Effort][_TAG].
 *  - Lab: who made the model, in one word as the lab writes it (OpenAI, Anthropic, MoonshotAI, ZhipuAI, xAI), never the
 *    host that serves it or the tool that wrote the file.
 *  - Model: the lab's own name for it, spaces as "_" (Claude_Opus_5.5, GPT-5.6_Terra, Kimi_K3.1_Pro).
 *  - Effort: _Minimal, _Low, _Medium, _High, _XHigh or _Max when the model was asked for one.
 *  - TAG: capitals, to tell two entries of the same model apart (_IAN).
 * The phone's voice skips the lab: "MoonshotAI-Kimi_K3_High" is read "Kimi K3 High".
 */
object Names {
    /** Old name -> v2 name of every entry that had played before the rename: its records follow it. */
    val RENAMED = mapOf(
        "Cursor_Grok_4.6_Medium" to "xAI-Grok_4.6_Medium",
        "Devin_SWE-1.6_Slow" to "Cognition-SWE-1.6_Slow",
        "Github_Copilot_mai-code-1.1-flash" to "Microsoft-MAI-Code-1.1-Flash",
        "KIRO_UNKNOWN" to "Unknown-Kiro",
        "TRAE_Seed_2.1_Turbo" to "ByteDance-Seed_2.1_Turbo",
        "OpenAI-GPT-5.6_terra_high" to "OpenAI-GPT-5.6_Terra_High",
        "Moonshot_AI-Kimi_K2.8 Preview" to "MoonshotAI-Kimi_K2.8_Preview",
        "Moonshot_AI-Kimi_K3_High" to "MoonshotAI-Kimi_K3_High",
    )

    private val BEFORE = RENAMED.entries.associate { (old, new) -> new to old }

    /** The name [name] had before the rename, for records saved under it; null when it was not renamed. */
    fun before(name: String): String? = BEFORE[name]
}

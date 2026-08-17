package dev.havoc.rokidhome.shared.state

import dev.havoc.rokidhome.shared.model.ContextRule

class ContextSelector(
    rules: List<ContextRule>,
    private val defaultPageId: String,
) {
    private data class Runtime(
        val rule: ContextRule,
        var raw: Boolean = false,
        var effective: Boolean = false,
        var changedAt: Long = 0,
    )

    private val runtimes = rules.associate { it.id to Runtime(it) }.toMutableMap()

    fun update(ruleId: String, value: Boolean, nowMs: Long): String {
        val runtime = requireNotNull(runtimes[ruleId]) { "Unknown rule $ruleId" }
        if (runtime.raw != value) {
            runtime.raw = value
            runtime.changedAt = nowMs
        }
        refresh(nowMs)
        return selectedPage()
    }

    fun refresh(nowMs: Long): String {
        runtimes.values.forEach { runtime ->
            val delay = if (runtime.raw) runtime.rule.activateAfterMs else runtime.rule.deactivateAfterMs
            if (runtime.effective != runtime.raw && nowMs - runtime.changedAt >= delay) {
                runtime.effective = runtime.raw
            }
        }
        return selectedPage()
    }

    fun selectedPage(): String = runtimes.values
        .asSequence()
        .filter { it.rule.enabled && it.effective }
        .sortedWith(compareByDescending<Runtime> { it.rule.priority }.thenBy { it.rule.order })
        .map { it.rule.pageId }
        .firstOrNull() ?: defaultPageId
}

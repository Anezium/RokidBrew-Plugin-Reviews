package dev.havoc.rokidhome.shared.state

import dev.havoc.rokidhome.shared.model.PublishedConfiguration
import dev.havoc.rokidhome.shared.model.actions
import dev.havoc.rokidhome.shared.protocol.ActionRequestPayload
import dev.havoc.rokidhome.shared.validation.CanonicalData
import java.util.LinkedHashMap

class ActionRequestGate(private val capacity: Int = 256) {
    private val recent = object : LinkedHashMap<String, Unit>(capacity, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > capacity
    }

    @Synchronized
    fun accept(config: PublishedConfiguration, envelopeVersion: Long?, payload: ActionRequestPayload): String? {
        val rejection = when {
            envelopeVersion != config.configVersion -> "Устаревшая версия конфигурации"
            CanonicalData.actionHash(payload.action) != payload.actionHash -> "Неверный хеш action"
            config.actions().none { CanonicalData.actionHash(it) == payload.actionHash } -> "Action отсутствует в опубликованной конфигурации"
            recent.containsKey(payload.requestId) -> "Повторный запрос"
            else -> null
        }
        if (rejection == null) recent[payload.requestId] = Unit
        return rejection
    }

    @Synchronized fun clear() = recent.clear()
}

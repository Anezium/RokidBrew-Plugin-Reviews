package dev.havoc.rokidhome.phone.data

import androidx.room.withTransaction
import dev.havoc.rokidhome.shared.model.*
import dev.havoc.rokidhome.shared.validation.CanonicalData
import dev.havoc.rokidhome.shared.validation.ConfigurationValidator
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConfigurationRepository(private val database: AppDatabase) {
    private val dao = database.configurationDao()
    val pages: Flow<List<PageWithWidgets>> = dao.observePages()
    val rules: Flow<List<ContextRuleEntity>> = dao.observeRules()
    val published: Flow<PublishedConfigEntity?> = dao.observePublished()

    suspend fun ensureSeeded() {
        if (dao.pages().isEmpty()) installStarterConfiguration()
    }

    /** Installs a neutral guide and removes starter content shipped by older builds. */
    suspend fun installStarterConfiguration(
        restoreMissingStarterContent: Boolean = true,
    ): Boolean = database.withTransaction {
        var changed = false

        dao.pages()
            .filter { it.page.id.startsWith(LEGACY_STARTER_PREFIX) && it.page.id != STARTER_GUIDE_PAGE_ID }
            .forEach {
                dao.deletePage(it.page.id)
                changed = true
            }
        dao.rules()
            .filter { it.id.startsWith(LEGACY_STARTER_PREFIX) }
            .forEach {
                dao.deleteRule(it.id)
                changed = true
            }

        val remainingPages = dao.pages()
        if (
            remainingPages.none { it.page.id == STARTER_GUIDE_PAGE_ID } &&
            (restoreMissingStarterContent || remainingPages.isEmpty())
        ) {
            val position = (remainingPages.maxOfOrNull { it.page.position } ?: -1) + 1
            dao.putPage(PageEntity(STARTER_GUIDE_PAGE_ID, "Getting started", position))
            dao.putWidget(WidgetEntity(STARTER_CONNECTION_WIDGET_ID, STARTER_GUIDE_PAGE_ID, WidgetType.STATUS.name, 0))
            dao.putBindings(
                listOf(
                    BindingEntity(STARTER_CONNECTION_WIDGET_ID, "label", encodeSource(ValueSource.Literal("Connection"))),
                    BindingEntity(STARTER_CONNECTION_WIDGET_ID, "state", encodeSource(ValueSource.Literal("Set URL and token on the phone"))),
                ),
            )
            dao.putWidget(WidgetEntity(STARTER_DASHBOARD_WIDGET_ID, STARTER_GUIDE_PAGE_ID, WidgetType.STATUS.name, 1))
            dao.putBindings(
                listOf(
                    BindingEntity(STARTER_DASHBOARD_WIDGET_ID, "label", encodeSource(ValueSource.Literal("Dashboard"))),
                    BindingEntity(STARTER_DASHBOARD_WIDGET_ID, "state", encodeSource(ValueSource.Literal("Add your own pages and widgets"))),
                ),
            )
            changed = true
        }
        changed
    }

    suspend fun addPage(name: String): String {
        val current = dao.pages()
        val id = "page-${UUID.randomUUID()}"
        dao.putPage(PageEntity(id, name.trim().ifEmpty { "Страница" }, current.size))
        return id
    }

    suspend fun renamePage(id: String, name: String) {
        val page = dao.pages().firstOrNull { it.page.id == id }?.page ?: return
        dao.putPage(page.copy(name = name.trim().ifEmpty { page.name }))
    }

    suspend fun deletePage(id: String) = dao.deletePage(id)

    suspend fun movePage(id: String, delta: Int) = database.withTransaction {
        val list = dao.pages().map { it.page }.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(list.indices)
        if (from < 0 || from == to) return@withTransaction
        val item = list.removeAt(from)
        list.add(to, item)
        list.forEachIndexed { index, page -> dao.putPage(page.copy(position = index)) }
    }

    suspend fun movePageTo(id: String, targetIndex: Int) = database.withTransaction {
        val list = dao.pages().map { it.page }.sortedBy { it.position }.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return@withTransaction
        val item = list.removeAt(from)
        list.add(targetIndex.coerceIn(0, list.size), item)
        list.forEachIndexed { index, page -> dao.putPage(page.copy(position = index)) }
    }

    suspend fun addWidget(pageId: String, type: WidgetType): String = database.withTransaction {
        val page = dao.pages().firstOrNull { it.page.id == pageId } ?: error("Page not found")
        val id = "widget-${UUID.randomUUID()}"
        dao.putWidget(WidgetEntity(id, pageId, type.name, page.widgets.size))
        dao.putBindings(listOf(BindingEntity(id, "label", encodeSource(ValueSource.Literal(type.name.lowercase().replaceFirstChar(Char::uppercase))))) )
        id
    }

    suspend fun saveBinding(widgetId: String, slot: String, source: ValueSource) =
        dao.putBindings(listOf(BindingEntity(widgetId, slot, encodeSource(source))))

    suspend fun saveAction(widgetId: String, slot: String, action: HomeAssistantAction) =
        dao.putActions(listOf(ActionEntity(widgetId, slot, CanonicalData.json.encodeToString(HomeAssistantAction.serializer(), action))))

    suspend fun deleteWidget(id: String) = dao.deleteWidget(id)

    suspend fun moveWidget(pageId: String, id: String, delta: Int) = database.withTransaction {
        val list = dao.pages().firstOrNull { it.page.id == pageId }?.widgets?.map { it.widget }?.sortedBy { it.position }?.toMutableList() ?: return@withTransaction
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return@withTransaction
        val to = (from + delta).coerceIn(list.indices)
        if (from == to) return@withTransaction
        val item = list.removeAt(from)
        list.add(to, item)
        list.forEachIndexed { index, widget -> dao.putWidget(widget.copy(position = index)) }
    }

    suspend fun moveWidgetTo(pageId: String, id: String, targetIndex: Int) = database.withTransaction {
        val list = dao.pages().firstOrNull { it.page.id == pageId }
            ?.widgets?.map { it.widget }?.sortedBy { it.position }?.toMutableList() ?: return@withTransaction
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return@withTransaction
        val item = list.removeAt(from)
        list.add(targetIndex.coerceIn(0, list.size), item)
        list.forEachIndexed { index, widget -> dao.putWidget(widget.copy(position = index)) }
    }

    suspend fun saveRule(rule: ContextRuleEntity) = dao.putRule(rule)

    suspend fun deleteRule(id: String) = dao.deleteRule(id)

    suspend fun publish(): Result<PublishedConfiguration> = runCatching {
        database.withTransaction {
            val pages = dao.pages().map { row ->
                PageConfig(row.page.id, row.page.name, row.widgets.sortedBy { it.widget.position }.map(::decodeWidget))
            }
            val rules = dao.rules().map {
                ContextRule(it.id, it.enabled, it.conditionTemplate, it.pageId, it.priority, it.position, it.activateAfterMs, it.deactivateAfterMs)
            }
            val previous = dao.published()
            val next = PublishedConfiguration(
                configVersion = (previous?.version ?: 0) + 1,
                defaultPageId = pages.firstOrNull()?.id ?: error("Добавьте хотя бы одну страницу"),
                pages = pages,
                contextRules = rules,
            )
            val errors = ConfigurationValidator.validate(next)
            require(errors.isEmpty()) { errors.joinToString("\n") }
            val signed = CanonicalData.withChecksum(next)
            dao.putPublished(PublishedConfigEntity(version = signed.configVersion, checksum = signed.checksum, json = CanonicalData.json.encodeToString(PublishedConfiguration.serializer(), signed)))
            signed
        }
    }

    suspend fun currentPublished(): PublishedConfiguration? = dao.published()?.let {
        runCatching { CanonicalData.json.decodeFromString(PublishedConfiguration.serializer(), it.json) }.getOrNull()
    }

    private fun decodeWidget(row: WidgetWithDetails): WidgetConfig {
        val bindings = row.bindings.associate { it.slot to decodeSource(it.json) }
        val actions = row.actions.associate { it.slot to CanonicalData.json.decodeFromString(HomeAssistantAction.serializer(), it.json) }
        return WidgetConfig(
            id = row.widget.id,
            type = WidgetType.valueOf(row.widget.type),
            label = bindings["label"], primary = bindings["primary"], secondary = bindings["secondary"],
            state = bindings["state"], progress = bindings["progress"],
            minimum = bindings["minimum"], maximum = bindings["maximum"], step = bindings["step"],
            action = actions["action"], onAction = actions["on"], offAction = actions["off"],
        )
    }

    companion object {
        fun encodeSource(source: ValueSource) = CanonicalData.json.encodeToString(ValueSource.serializer(), source)
        fun decodeSource(value: String) = CanonicalData.json.decodeFromString(ValueSource.serializer(), value)
        private const val LEGACY_STARTER_PREFIX = "starter-"
        private const val STARTER_GUIDE_PAGE_ID = "starter-guide-v1"
        private const val STARTER_CONNECTION_WIDGET_ID = "starter-guide-connection-v1"
        private const val STARTER_DASHBOARD_WIDGET_ID = "starter-guide-dashboard-v1"
    }
}

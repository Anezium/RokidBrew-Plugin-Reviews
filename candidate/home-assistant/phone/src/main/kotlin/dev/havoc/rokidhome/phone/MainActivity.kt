package dev.havoc.rokidhome.phone

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.anezium.rokidbus.client.ui.BusTheme
import com.anezium.rokidbus.client.ui.NexusUi
import dev.havoc.rokidhome.phone.data.ConfigurationRepository
import dev.havoc.rokidhome.phone.data.ContextRuleEntity
import dev.havoc.rokidhome.phone.data.PageWithWidgets
import dev.havoc.rokidhome.phone.data.WidgetWithDetails
import dev.havoc.rokidhome.phone.ha.HaConnectionState
import dev.havoc.rokidhome.phone.ha.HomeAssistantClient
import dev.havoc.rokidhome.shared.model.HomeAssistantAction
import dev.havoc.rokidhome.shared.model.ValueSource
import dev.havoc.rokidhome.shared.model.WidgetType
import dev.havoc.rokidhome.shared.validation.CanonicalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import java.util.UUID

abstract class NexusSettingsActivity : Activity() {
    protected val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    protected lateinit var runtime: PhoneRuntime
    protected val repository: ConfigurationRepository get() = runtime.repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = PhoneRuntime.get(this)
        window.statusBarColor = NexusUi.BG
        window.navigationBarColor = NexusUi.BG
    }

    override fun onStart() {
        super.onStart()
        runtime.acquire()
    }

    override fun onStop() {
        runtime.release()
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    protected fun showScreen(
        title: String,
        subtitle: String,
        content: LinearLayout,
    ) {
        val root = NexusUi.fixedRoot(this).apply {
            addView(
                NexusUi.pluginHeader(
                    this@NexusSettingsActivity,
                    R.drawable.nexus_glyph_home_assistant,
                    title,
                    subtitle,
                ),
                NexusUi.block(),
            )
            addView(
                NexusUi.screen(this@NexusSettingsActivity, content),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
    }

    protected fun content(): LinearLayout = NexusUi.contentColumn(this)

    protected fun LinearLayout.section(label: String, value: String? = null) {
        addView(NexusUi.sectionRow(this@NexusSettingsActivity, label, value), NexusUi.block())
        gap(10)
    }

    protected fun LinearLayout.gap(dp: Int) {
        addView(BusTheme.gap(this@NexusSettingsActivity, dp))
    }

    protected fun LinearLayout.block(view: View) {
        addView(view, NexusUi.block())
    }

    protected fun field(hint: String, value: String = ""): EditText =
        NexusUi.field(this, hint).apply { setText(value) }

    protected fun multiline(hint: String, value: String = ""): EditText =
        field(hint, value).apply {
            setSingleLine(false)
            minLines = 2
            maxLines = 7
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(
                NexusUi.dp(this@NexusSettingsActivity, 16),
                NexusUi.dp(this@NexusSettingsActivity, 13),
                NexusUi.dp(this@NexusSettingsActivity, 16),
                NexusUi.dp(this@NexusSettingsActivity, 13),
            )
        }

    protected fun actionRow(vararg buttons: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            buttons.forEachIndexed { index, button ->
                addView(
                    button,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = NexusUi.dp(this@NexusSettingsActivity, 8)
                    },
                )
            }
        }

    protected fun report(view: TextView, message: String, error: Boolean = false) {
        view.text = message
        view.setTextColor(if (error) NexusUi.DANGER else NexusUi.GREEN_DIM)
    }

    protected fun enableDragReorder(view: View, itemId: String, onDrop: (String) -> Unit) {
        view.setOnLongClickListener {
            view.startDragAndDrop(
                ClipData.newPlainText("configuration-item", itemId),
                View.DragShadowBuilder(view),
                itemId,
                0,
            )
            true
        }
        view.setOnDragListener { target, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.localState is String
                DragEvent.ACTION_DRAG_ENTERED -> {
                    target.alpha = 0.55f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    target.alpha = 1f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    target.alpha = 1f
                    (event.localState as? String)?.takeIf { it != itemId }?.let(onDrop)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    target.alpha = 1f
                    true
                }
                else -> true
            }
        }
    }
}

class MainActivity : NexusSettingsActivity() {
    private lateinit var haValue: TextView
    private lateinit var configValue: TextView
    private lateinit var statusLine: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = content().apply {
            section("Status")
            block(statusCard())
            gap(24)
            section("Configuration")
            block(
                NexusUi.navCard(
                    this@MainActivity,
                    "Home Assistant connection",
                    "URL and encrypted long-lived access token",
                ) { startActivity(Intent(this@MainActivity, ConnectionActivity::class.java)) },
            )
            gap(8)
            block(
                NexusUi.navCard(
                    this@MainActivity,
                    "Pages and widgets",
                    "HUD cards, values, buttons and toggles",
                ) { startActivity(Intent(this@MainActivity, PagesActivity::class.java)) },
            )
            gap(8)
            block(
                NexusUi.navCard(
                    this@MainActivity,
                    "Dynamic page 0",
                    "Prioritized Home Assistant context rules",
                ) { startActivity(Intent(this@MainActivity, ContextActivity::class.java)) },
            )
            gap(16)
            val publish = NexusUi.pillButton(this@MainActivity, "Publish to Nexus HUD")
            publish.setOnClickListener {
                publish.isEnabled = false
                report(statusLine, "Publishing…")
                uiScope.launch {
                    runtime.publish().fold(
                        onSuccess = { report(statusLine, "Configuration ${it.configVersion} published") },
                        onFailure = { report(statusLine, it.message ?: "Publish failed", true) },
                    )
                    publish.isEnabled = true
                }
            }
            block(publish)
            gap(8)
            statusLine = NexusUi.statusLine(this@MainActivity).apply { text = "Ready" }
            block(statusLine)
            gap(24)
            section("Plugin")
            block(
                NexusUi.card(this@MainActivity).apply {
                    addView(NexusUi.cardTitle(this@MainActivity, "Managed by Rokid Nexus"))
                    addView(BusTheme.gap(this@MainActivity, 5))
                    addView(
                        NexusUi.cardBody(
                            this@MainActivity,
                            "No Hi Rokid token and no separate glasses APK. Approve the Surfaces capability once in Nexus, then launch Home Assistant from the glasses.",
                        ),
                    )
                    addView(BusTheme.gap(this@MainActivity, 8))
                    addView(
                        NexusUi.cardBody(
                            this@MainActivity,
                            "Unofficial community plugin. Not affiliated with or endorsed by the Home Assistant project, Nabu Casa, or Rokid.",
                        ),
                    )
                },
            )
            gap(8)
            block(
                NexusUi.uninstallCard(this@MainActivity, "Home Assistant") {
                    startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                },
            )
        }
        showScreen("Home Assistant", "Nexus HUD dashboard · v0.2.1", body)
        uiScope.launch {
            runtime.status.collect { status ->
                haValue.text = status.haState.name
                haValue.setTextColor(
                    if (status.haState == HaConnectionState.ONLINE) NexusUi.GREEN else NexusUi.INK3,
                )
                configValue.text = status.publishedVersion?.toString() ?: "—"
                status.message?.let { report(statusLine, it, status.haState == HaConnectionState.AUTH_ERROR) }
            }
        }
    }

    private fun statusCard(): LinearLayout = NexusUi.card(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(statusRow("HOME ASSISTANT").also { haValue = it.second }.first)
        addView(NexusUi.divider(this@MainActivity))
        addView(statusRow("CONFIG VERSION").also { configValue = it.second }.first)
        addView(NexusUi.divider(this@MainActivity))
        addView(
            NexusUi.cardBody(
                this@MainActivity,
                "Nexus owns the phone-to-glasses link, input normalization and HUD renderer.",
            ),
        )
    }

    private fun statusRow(label: String): Pair<LinearLayout, TextView> {
        val value = NexusUi.rowValue(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(NexusUi.rowLabel(this@MainActivity, label), LinearLayout.LayoutParams(0, -2, 1f))
            addView(value)
        }
        return row to value
    }
}

class ConnectionActivity : NexusSettingsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = runtime.credentials.load()
        val url = field("Home Assistant URL", saved.homeAssistantUrl)
        val token = field("Long-lived access token", saved.homeAssistantToken).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = NexusUi.statusLine(this).apply { text = "Token is stored through Android Keystore" }
        val test = NexusUi.outlinePillButton(this, "Test")
        val save = NexusUi.pillButton(this, "Save and connect")
        test.setOnClickListener {
            test.isEnabled = false
            report(status, "Testing…")
            uiScope.launch {
                runtime.homeAssistant.test(url.text.toString(), token.text.toString()).fold(
                    onSuccess = { report(status, "Home Assistant connected") },
                    onFailure = { report(status, it.message ?: "Connection failed", true) },
                )
                test.isEnabled = true
            }
        }
        save.setOnClickListener {
            runCatching {
                val normalized = HomeAssistantClient.normalizeUrl(url.text.toString())
                val accessToken = token.text.toString().trim()
                require(accessToken.isNotEmpty()) { "Enter a long-lived access token" }
                runtime.credentials.save(
                    runtime.credentials.load().copy(
                        homeAssistantUrl = normalized,
                        homeAssistantToken = accessToken,
                    ),
                )
                runtime.reconnect()
            }.fold(
                onSuccess = { report(status, "Saved and reconnecting") },
                onFailure = { report(status, it.message ?: "Save failed", true) },
            )
        }
        val body = content().apply {
            section("Home Assistant")
            block(url)
            gap(8)
            block(token)
            gap(12)
            block(actionRow(test, save))
            gap(10)
            block(status)
            gap(24)
            section("Security")
            block(
                NexusUi.card(this@ConnectionActivity).apply {
                    addView(
                        NexusUi.cardBody(
                            this@ConnectionActivity,
                            "The Home Assistant token never enters a Nexus surface, plugin descriptor, log message or glasses payload.",
                        ),
                    )
                },
            )
        }
        showScreen("Home Assistant", "Connection settings", body)
    }
}

class PagesActivity : NexusSettingsActivity() {
    private lateinit var pagesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val newName = field("New page name")
        val add = NexusUi.pillButton(this, "Add page")
        add.setOnClickListener {
            uiScope.launch {
                repository.addPage(newName.text.toString())
                newName.text.clear()
            }
        }
        pagesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = content().apply {
            section("Dynamic slot")
            block(
                NexusUi.card(this@PagesActivity).apply {
                    addView(NexusUi.cardTitle(this@PagesActivity, "Page 0 · contextual"))
                    addView(BusTheme.gap(this@PagesActivity, 5))
                    addView(
                        NexusUi.cardBody(
                            this@PagesActivity,
                            "The selected context page appears only in slot 0. It stays pinned while visible and updates after leaving.",
                        ),
                    )
                },
            )
            gap(24)
            section("Pages")
            block(newName)
            gap(8)
            block(add)
            gap(12)
            block(pagesContainer)
        }
        showScreen("Pages", "Nexus card configuration", body)
        uiScope.launch { repository.pages.collect(::renderPages) }
    }

    private fun renderPages(pages: List<PageWithWidgets>) {
        pagesContainer.removeAllViews()
        pages.sortedBy { it.page.position }.forEachIndexed { index, row ->
            if (index > 0) pagesContainer.addView(BusTheme.gap(this, 8))
            val card = NexusUi.navCard(
                this,
                row.page.name,
                "${row.widgets.size} widgets · hold and drag",
            ) {
                startActivity(
                    Intent(this, PageEditorActivity::class.java)
                        .putExtra(EXTRA_PAGE_ID, row.page.id),
                )
            }
            enableDragReorder(card, row.page.id) { sourceId ->
                uiScope.launch { repository.movePageTo(sourceId, index) }
            }
            pagesContainer.addView(card, NexusUi.block())
        }
    }
}

class PageEditorActivity : NexusSettingsActivity() {
    private lateinit var pageId: String
    private lateinit var name: EditText
    private lateinit var widgets: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageId = intent.getStringExtra(EXTRA_PAGE_ID) ?: run { finish(); return }
        name = field("Page name")
        widgets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = NexusUi.statusLine(this).apply { text = pageId }
        val save = NexusUi.pillButton(this, "Save name").apply {
            setOnClickListener {
                uiScope.launch {
                    repository.renamePage(pageId, name.text.toString())
                    report(status, "Page name saved")
                }
            }
        }
        val delete = NexusUi.textButton(this, "Delete", danger = true).apply {
            setOnClickListener { uiScope.launch { repository.deletePage(pageId); finish() } }
        }
        val addWidgets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        WidgetType.entries.forEach { type ->
            addWidgets.addView(
                NexusUi.navCard(
                    this@PageEditorActivity,
                    "+ ${type.name.lowercase().replaceFirstChar(Char::uppercase)}",
                    widgetDescription(type),
                ) { uiScope.launch { repository.addWidget(pageId, type) } },
                NexusUi.block(),
            )
            addWidgets.addView(BusTheme.gap(this@PageEditorActivity, 6))
        }
        val body = content().apply {
            section("Page")
            block(name)
            gap(8)
            block(save)
            gap(8)
            block(delete)
            gap(8)
            block(status)
            gap(24)
            section("Widgets")
            block(widgets)
            gap(16)
            section("Add widget")
            block(addWidgets)
        }
        showScreen("Page", "HUD card and controls", body)
        uiScope.launch {
            repository.pages.collect { rows ->
                val page = rows.firstOrNull { it.page.id == pageId } ?: return@collect
                if (!name.hasFocus()) name.setText(page.page.name)
                renderWidgets(page)
            }
        }
    }

    private fun renderWidgets(page: PageWithWidgets) {
        widgets.removeAllViews()
        page.widgets.sortedBy { it.widget.position }.forEachIndexed { index, row ->
            if (index > 0) widgets.addView(BusTheme.gap(this, 8))
            val type = WidgetType.valueOf(row.widget.type)
            val card = NexusUi.navCard(
                this,
                widgetDisplayLabel(row),
                "${type.displayName()} · ${row.bindings.size} values · ${row.actions.size} actions · hold and drag",
            ) {
                startActivity(
                    Intent(this, WidgetEditorActivity::class.java)
                        .putExtra(EXTRA_WIDGET_ID, row.widget.id),
                )
            }
            enableDragReorder(card, row.widget.id) { sourceId ->
                uiScope.launch { repository.moveWidgetTo(page.page.id, sourceId, index) }
            }
            widgets.addView(card, NexusUi.block())
        }
    }
}

class WidgetEditorActivity : NexusSettingsActivity() {
    private lateinit var widgetId: String
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getStringExtra(EXTRA_WIDGET_ID) ?: run { finish(); return }
        showScreen(
            "Widget",
            "Loading configuration…",
            content().apply { block(NexusUi.statusLine(this@WidgetEditorActivity).apply { text = widgetId }) },
        )
        uiScope.launch {
            val row = repository.pages.first()
                .asSequence()
                .flatMap { it.widgets.asSequence() }
                .firstOrNull { it.widget.id == widgetId }
                ?: run { finish(); return@launch }
            buildEditor(row)
        }
    }

    private fun buildEditor(row: WidgetWithDetails) {
        val type = WidgetType.valueOf(row.widget.type)
        status = NexusUi.statusLine(this).apply { text = row.widget.id }
        val delete = NexusUi.textButton(this, "Delete", danger = true).apply {
            setOnClickListener { uiScope.launch { repository.deleteWidget(row.widget.id); finish() } }
        }
        val body = content().apply {
            section("Widget", type.name)
            block(delete)
            gap(8)
            block(status)
            gap(24)
            section("Value sources")
            slotsFor(type).forEachIndexed { index, slot ->
                if (index > 0) gap(8)
                block(bindingCard(row, slot))
            }
            if (type == WidgetType.BUTTON || type == WidgetType.TOGGLE || type == WidgetType.SLIDER) {
                gap(24)
                section("Home Assistant actions")
                actionSlotsFor(type).forEachIndexed { index, slot ->
                    if (index > 0) gap(8)
                    block(actionCard(row, slot))
                }
            }
        }
        showScreen(type.name.lowercase().replaceFirstChar(Char::uppercase), "Widget editor", body)
    }

    private fun bindingCard(row: WidgetWithDetails, slot: String): LinearLayout {
        val existing = row.bindings.firstOrNull { it.slot == slot }?.let {
            runCatching { ConfigurationRepository.decodeSource(it.json) }.getOrNull()
        }
        var kind = when (existing) {
            is ValueSource.Entity -> SOURCE_ENTITY
            is ValueSource.Template -> SOURCE_TEMPLATE
            else -> SOURCE_LITERAL
        }
        val value = when (existing) {
            is ValueSource.Entity -> existing.entityId
            is ValueSource.Template -> existing.template
            is ValueSource.Literal -> existing.value
            null -> ""
        }
        val valueField = multiline("Value, entity_id or Jinja", value)
        val attribute = field("Entity attribute (optional)", (existing as? ValueSource.Entity)?.attribute.orEmpty())
        val buttons = listOf(SOURCE_LITERAL, SOURCE_ENTITY, SOURCE_TEMPLATE).associateWith { option ->
            NexusUi.textButton(this, option).apply {
                setOnClickListener {
                    kind = option
                    (parent as? LinearLayout)?.let { rowView ->
                        rowView.childViews<Button>().forEach { button ->
                            button.text = if (button === this) "● ${button.tag}" else button.tag.toString()
                        }
                    }
                }
                tag = option
                text = if (kind == option) "● $option" else option
            }
        }
        val kindRow = actionRow(*buttons.values.toTypedArray())
        val save = NexusUi.pillButton(this, "Save $slot").apply {
            setOnClickListener {
                val source = when (kind) {
                    SOURCE_ENTITY -> ValueSource.Entity(
                        valueField.text.toString().trim(),
                        attribute.text.toString().trim().ifEmpty { null },
                    )
                    SOURCE_TEMPLATE -> ValueSource.Template(valueField.text.toString())
                    else -> ValueSource.Literal(valueField.text.toString())
                }
                val valid = when (source) {
                    is ValueSource.Entity -> '.' in source.entityId
                    is ValueSource.Template -> source.template.isNotBlank()
                    is ValueSource.Literal -> source.value.length <= 1_024
                }
                if (!valid) {
                    report(status, "Invalid $slot value source", true)
                    return@setOnClickListener
                }
                uiScope.launch {
                    repository.saveBinding(row.widget.id, slot, source)
                    report(status, "$slot saved")
                }
            }
        }
        return NexusUi.card(this).apply {
            addView(NexusUi.cardTitle(this@WidgetEditorActivity, slot))
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(kindRow, NexusUi.block())
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(valueField, NexusUi.block())
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(attribute, NexusUi.block())
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(save, NexusUi.block())
        }
    }

    private fun actionCard(row: WidgetWithDetails, slot: String): LinearLayout {
        val existing = row.actions.firstOrNull { it.slot == slot }?.let {
            runCatching {
                CanonicalData.json.decodeFromString(HomeAssistantAction.serializer(), it.json)
            }.getOrNull()
        }
        val action = field("domain.service", existing?.action.orEmpty())
        val target = field("target entity_id", existing?.target?.get("entity_id").orEmpty())
        val dataHint = if (WidgetType.valueOf(row.widget.type) == WidgetType.SLIDER && slot == "action") {
            "data JSON (use \$value for absolute slider value)"
        } else if (WidgetType.valueOf(row.widget.type) == WidgetType.SLIDER) {
            "data JSON (on = up, off = down)"
        } else {
            "data JSON"
        }
        val data = multiline(dataHint, existing?.data?.toString() ?: "{}")
        val save = NexusUi.pillButton(this, "Save $slot action").apply {
            setOnClickListener {
                val parsed = runCatching {
                    CanonicalData.json.parseToJsonElement(data.text.toString()).jsonObject
                }.getOrNull()
                val actionName = action.text.toString().trim()
                if (!actionName.matches(Regex("[a-z0-9_]+\\.[a-z0-9_]+")) || parsed == null) {
                    report(status, "Check domain.service and JSON", true)
                    return@setOnClickListener
                }
                val model = HomeAssistantAction(
                    action = actionName,
                    target = target.text.toString().trim().takeIf(String::isNotEmpty)
                        ?.let { mapOf("entity_id" to it) }
                        .orEmpty(),
                    data = parsed,
                )
                uiScope.launch {
                    repository.saveAction(row.widget.id, slot, model)
                    report(status, "$slot action saved")
                }
            }
        }
        return NexusUi.card(this).apply {
            addView(NexusUi.cardTitle(this@WidgetEditorActivity, slot))
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(action, NexusUi.block())
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(target, NexusUi.block())
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(data, NexusUi.block())
            addView(BusTheme.gap(this@WidgetEditorActivity, 8))
            addView(save, NexusUi.block())
        }
    }
}

class ContextActivity : NexusSettingsActivity() {
    private lateinit var rulesContainer: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val template = multiline("Jinja condition")
        val pageId = field("Target page ID")
        val priority = field("Priority", "100").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        status = NexusUi.statusLine(this).apply { text = "Higher priority wins; editor order breaks ties" }
        val add = NexusUi.pillButton(this, "Add context rule").apply {
            setOnClickListener {
                val condition = template.text.toString()
                val target = pageId.text.toString().trim()
                val priorityValue = priority.text.toString().toIntOrNull()
                if (condition.isBlank() || target.isBlank() || priorityValue == null) {
                    report(status, "Condition, target and numeric priority are required", true)
                    return@setOnClickListener
                }
                uiScope.launch {
                    val count = repository.rules.first().size
                    repository.saveRule(
                        ContextRuleEntity(
                            id = "rule-${UUID.randomUUID()}",
                            enabled = true,
                            conditionTemplate = condition,
                            pageId = target,
                            priority = priorityValue,
                            position = count,
                            activateAfterMs = 500,
                            deactivateAfterMs = 1_500,
                        ),
                    )
                    template.text.clear()
                    report(status, "Rule added")
                }
            }
        }
        rulesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = content().apply {
            section("New rule")
            block(template)
            gap(8)
            block(pageId)
            gap(8)
            block(priority)
            gap(8)
            block(add)
            gap(8)
            block(status)
            gap(24)
            section("Rules")
            block(rulesContainer)
        }
        showScreen("Dynamic page 0", "Context rules", body)
        uiScope.launch { repository.rules.collect(::renderRules) }
    }

    private fun renderRules(rules: List<ContextRuleEntity>) {
        rulesContainer.removeAllViews()
        rules.sortedBy(ContextRuleEntity::position).forEachIndexed { index, rule ->
            if (index > 0) rulesContainer.addView(BusTheme.gap(this, 8))
            rulesContainer.addView(
                NexusUi.navCard(
                    this,
                    if (rule.enabled) "Priority ${rule.priority}" else "Disabled · ${rule.priority}",
                    "${rule.pageId} · ${rule.conditionTemplate.replace('\n', ' ').take(72)}",
                ) {
                    startActivity(
                        Intent(this, RuleEditorActivity::class.java)
                            .putExtra(EXTRA_RULE_ID, rule.id),
                    )
                },
                NexusUi.block(),
            )
        }
    }
}

class RuleEditorActivity : NexusSettingsActivity() {
    private lateinit var ruleId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: run { finish(); return }
        showScreen(
            "Context rule",
            "Loading configuration…",
            content().apply { block(NexusUi.statusLine(this@RuleEditorActivity).apply { text = ruleId }) },
        )
        uiScope.launch {
            val rule = repository.rules.first().firstOrNull { it.id == ruleId }
                ?: run { finish(); return@launch }
            buildEditor(rule)
        }
    }

    private fun buildEditor(rule: ContextRuleEntity) {
        val template = multiline("Jinja condition", rule.conditionTemplate)
        val page = field("Target page ID", rule.pageId)
        val priority = field("Priority", rule.priority.toString())
        val activate = field("Activation delay, ms", rule.activateAfterMs.toString())
        val deactivate = field("Deactivation delay, ms", rule.deactivateAfterMs.toString())
        listOf(priority, activate, deactivate).forEach { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val status = NexusUi.statusLine(this).apply { text = if (rule.enabled) "Enabled" else "Disabled" }
        val save = NexusUi.pillButton(this, "Save rule").apply {
            setOnClickListener {
                val priorityValue = priority.text.toString().toIntOrNull()
                val activateValue = activate.text.toString().toLongOrNull()
                val deactivateValue = deactivate.text.toString().toLongOrNull()
                val valid = template.text.isNotBlank() && page.text.isNotBlank() &&
                    priorityValue != null && activateValue != null && activateValue in 0L..60_000L &&
                    deactivateValue != null && deactivateValue in 0L..60_000L
                if (!valid) {
                    report(status, "Check fields and delay range 0..60000 ms", true)
                    return@setOnClickListener
                }
                uiScope.launch {
                    repository.saveRule(
                        rule.copy(
                            conditionTemplate = template.text.toString(),
                            pageId = page.text.toString().trim(),
                            priority = requireNotNull(priorityValue),
                            activateAfterMs = requireNotNull(activateValue),
                            deactivateAfterMs = requireNotNull(deactivateValue),
                        ),
                    )
                    report(status, "Rule saved")
                }
            }
        }
        val toggle = NexusUi.textButton(this, if (rule.enabled) "Disable" else "Enable").apply {
            setOnClickListener {
                uiScope.launch { repository.saveRule(rule.copy(enabled = !rule.enabled)); finish() }
            }
        }
        val delete = NexusUi.textButton(this, "Delete", danger = true).apply {
            setOnClickListener { uiScope.launch { repository.deleteRule(rule.id); finish() } }
        }
        val body = content().apply {
            section("Rule", if (rule.enabled) "Enabled" else "Disabled")
            block(template)
            gap(8)
            block(page)
            gap(8)
            block(priority)
            gap(8)
            block(activate)
            gap(8)
            block(deactivate)
            gap(10)
            block(save)
            gap(8)
            block(actionRow(toggle, delete))
            gap(8)
            block(status)
        }
        showScreen("Context rule", "Dynamic page 0", body)
    }
}

private fun slotsFor(type: WidgetType): List<String> = when (type) {
    WidgetType.TEXT -> listOf("primary", "secondary")
    WidgetType.STATUS -> listOf("label", "state")
    WidgetType.BUTTON -> listOf("label")
    WidgetType.TOGGLE -> listOf("label", "primary", "secondary", "state")
    WidgetType.PROGRESS -> listOf("label", "progress")
    WidgetType.SLIDER -> listOf("label", "progress", "minimum", "maximum", "step")
}

private fun actionSlotsFor(type: WidgetType): List<String> = when (type) {
    WidgetType.BUTTON -> listOf("action")
    WidgetType.TOGGLE -> listOf("on", "off")
    WidgetType.SLIDER -> listOf("action", "on", "off")
    else -> emptyList()
}

private fun widgetDescription(type: WidgetType): String = when (type) {
    WidgetType.TEXT -> "Primary and secondary text"
    WidgetType.STATUS -> "Label with a compact state badge"
    WidgetType.BUTTON -> "Selectable Home Assistant action"
    WidgetType.TOGGLE -> "State-aware labels with on/off actions"
    WidgetType.PROGRESS -> "Label with normalized percentage"
    WidgetType.SLIDER -> "Adjustable value; absolute action or relative on/off actions"
}

private fun WidgetType.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun widgetDisplayLabel(row: WidgetWithDetails): String {
    fun source(slot: String): ValueSource? = row.bindings.firstOrNull { it.slot == slot }?.let {
        runCatching { ConfigurationRepository.decodeSource(it.json) }.getOrNull()
    }
    val value = source("label") ?: source("primary")
    return when (value) {
        is ValueSource.Literal -> value.value.ifBlank { WidgetType.valueOf(row.widget.type).displayName() }
        is ValueSource.Entity -> value.entityId
        is ValueSource.Template -> value.template.replace('\n', ' ').trim().take(72).ifBlank { "Template" }
        null -> WidgetType.valueOf(row.widget.type).displayName()
    }
}

private inline fun <reified T : View> ViewGroup.childViews(): List<T> =
    (0 until childCount).mapNotNull { getChildAt(it) as? T }

const val EXTRA_PAGE_ID = "page_id"
const val EXTRA_WIDGET_ID = "widget_id"
const val EXTRA_RULE_ID = "rule_id"
private const val SOURCE_LITERAL = "literal"
private const val SOURCE_ENTITY = "entity"
private const val SOURCE_TEMPLATE = "template"

package dev.havoc.rokidhome.phone

import dev.havoc.rokidhome.shared.model.HomeAssistantAction
import dev.havoc.rokidhome.shared.model.PageConfig
import dev.havoc.rokidhome.shared.model.PublishedConfiguration
import dev.havoc.rokidhome.shared.model.RuntimeValue
import dev.havoc.rokidhome.shared.model.ValueSource
import dev.havoc.rokidhome.shared.model.WidgetConfig
import dev.havoc.rokidhome.shared.model.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusDashboardControllerTest {
    @Test
    fun `open commits latest context and dynamic page exists only once`() {
        val controller = NexusDashboardController()
        controller.setConfiguration(configuration())
        controller.setContextPage("tv")

        assertEquals("home", controller.currentPageId)
        assertEquals("tv", controller.pendingContextPageId)

        controller.open()

        assertEquals("tv", controller.currentPageId)
        assertEquals("tv", controller.activeContextPageId)
        assertNull(controller.pendingContextPageId)
        assertEquals(3, controller.pageCount)
        controller.move(1)
        assertEquals("home", controller.currentPageId)
    }

    @Test
    fun `context becoming the visible ordinary page remaps it to zero`() {
        val controller = NexusDashboardController()
        controller.setConfiguration(configuration())
        controller.open()
        controller.move(1)
        assertEquals("tv", controller.currentPageId)
        assertEquals(1, controller.selectedIndex)

        controller.setContextPage("tv")

        assertEquals("tv", controller.currentPageId)
        assertEquals(0, controller.selectedIndex)
        assertEquals(3, controller.pageCount)
    }

    @Test
    fun `page zero stays pinned until swipe commits the pending context`() {
        val controller = NexusDashboardController()
        controller.setConfiguration(configuration())
        controller.open()

        controller.setContextPage("tv")
        assertEquals("home", controller.currentPageId)
        assertEquals("tv", controller.pendingContextPageId)
        assertEquals(3, controller.pageCount)

        controller.move(1)
        assertEquals("home", controller.currentPageId)
        assertEquals("tv", controller.activeContextPageId)
        assertNull(controller.pendingContextPageId)
        controller.move(-1)
        assertEquals("tv", controller.currentPageId)
    }

    @Test
    fun `tap enters focus then resolves the displayed action once`() {
        val controller = NexusDashboardController()
        controller.setConfiguration(configuration())
        controller.open()

        assertEquals(DashboardTapResult.Render, controller.tap(actionsEnabled = true))
        assertEquals("home-button", controller.selectedWidgetId)
        assertTrue(controller.presentation(homeAssistantOnline = true).handlesBack)

        assertEquals(
            DashboardTapResult.Execute("home-button"),
            controller.tap(actionsEnabled = true),
        )
        assertEquals(DashboardTapResult.Ignored, controller.tap(actionsEnabled = true))

        controller.actionFinished("Готово")
        assertFalse(controller.back())
        assertNull(controller.selectedWidgetId)
        assertTrue(controller.back())
    }

    @Test
    fun `offline action is refused but local focus and navigation remain available`() {
        val controller = NexusDashboardController()
        controller.setConfiguration(configuration())
        controller.open()
        controller.tap(actionsEnabled = false)

        assertEquals(DashboardTapResult.Render, controller.tap(actionsEnabled = false))
        val presentation = controller.presentation(homeAssistantOnline = false)
        assertTrue("HA offline" in presentation.footer)
        assertTrue(presentation.lines.any { it.text.startsWith("> ") })

        controller.back()
        controller.move(1)
        assertEquals("tv", controller.currentPageId)
    }

    @Test
    fun `runtime values become structured badges and stale marker`() {
        val controller = NexusDashboardController()
        controller.setConfiguration(configuration())
        controller.setValues(
            mapOf(
                "home-status.state" to RuntimeValue(
                    value = "Включён",
                    stale = true,
                    updatedAtEpochMs = 1,
                ),
            ),
        )
        controller.open()

        val status = controller.presentation(homeAssistantOnline = true)
            .lines.first { it.text == "Телевизор ⟳" || it.badge?.startsWith("Включён") == true }
        assertEquals("Включён ⟳", status.badge)
    }

    @Test
    fun `successful toggle changes its action label optimistically`() {
        val toggle = WidgetConfig(
            id = "playback",
            type = WidgetType.TOGGLE,
            label = ValueSource.Literal("Воспроизведение / пауза"),
            primary = ValueSource.Literal("Пауза"),
            secondary = ValueSource.Literal("Воспроизведение"),
            state = ValueSource.Literal("off"),
            onAction = HomeAssistantAction("remote.send_command"),
            offAction = HomeAssistantAction("remote.send_command"),
        )
        val controller = NexusDashboardController()
        controller.setConfiguration(
            PublishedConfiguration(
                configVersion = 8,
                defaultPageId = "media",
                checksum = "toggle",
                pages = listOf(PageConfig("media", "Медиа", listOf(toggle))),
            ),
        )
        controller.open()

        assertEquals("Воспроизведение", controller.presentation(true).lines.single().text.trim())
        assertEquals(DashboardTapResult.Render, controller.tap(actionsEnabled = true))
        assertEquals(
            DashboardTapResult.Execute("playback", toggleOn = true),
            controller.tap(actionsEnabled = true),
        )
        controller.actionFinished("Готово", appliedToggleState = true)
        assertEquals("Пауза", controller.presentation(true).lines.single().text.removePrefix("> ").trim())
        assertEquals("ON", controller.presentation(true).lines.single().badge)
        assertEquals(
            DashboardTapResult.Execute("playback", toggleOn = false),
            controller.tap(actionsEnabled = true),
        )
    }

    @Test
    fun `stale toggle waits for fresh state instead of showing false off`() {
        val toggle = WidgetConfig(
            id = "power",
            type = WidgetType.TOGGLE,
            label = ValueSource.Literal("Питание"),
            state = ValueSource.Entity("media_player.tv", fallback = "off"),
            onAction = HomeAssistantAction("remote.turn_on"),
            offAction = HomeAssistantAction("remote.turn_off"),
        )
        val controller = NexusDashboardController()
        controller.setConfiguration(PublishedConfiguration(configVersion = 1, defaultPageId = "media", pages = listOf(PageConfig("media", "TV", listOf(toggle))), checksum = "fresh"))
        controller.open()

        assertEquals("SYNC", controller.presentation(true).lines.single().badge)
        controller.tap(true)
        assertEquals(DashboardTapResult.Render, controller.tap(true))

        controller.setValues(mapOf("power.state" to RuntimeValue("on", stale = false, updatedAtEpochMs = 10)))
        assertEquals("ON", controller.presentation(true).lines.single().badge)
        assertEquals(DashboardTapResult.Execute("power", toggleOn = false), controller.tap(true))
    }

    @Test
    fun `opening again clears optimistic toggle state`() {
        val toggle = WidgetConfig(
            id = "power",
            type = WidgetType.TOGGLE,
            label = ValueSource.Literal("Питание"),
            state = ValueSource.Literal("off"),
            onAction = HomeAssistantAction("remote.turn_on"),
            offAction = HomeAssistantAction("remote.turn_off"),
        )
        val controller = NexusDashboardController()
        controller.setConfiguration(PublishedConfiguration(configVersion = 1, defaultPageId = "media", pages = listOf(PageConfig("media", "TV", listOf(toggle))), checksum = "reopen"))
        controller.open()
        controller.tap(true)
        controller.tap(true)
        controller.actionFinished("Готово", appliedToggleState = true)
        assertEquals("ON", controller.presentation(true).lines.single().badge)

        controller.close()
        controller.open()
        assertEquals("OFF", controller.presentation(true).lines.single().badge)
    }

    @Test
    fun `slider enters adjustment mode and swipes produce bounded values`() {
        val slider = WidgetConfig(
            id = "volume",
            type = WidgetType.SLIDER,
            label = ValueSource.Literal("Громкость"),
            progress = ValueSource.Entity("media_player.tv", "volume_level", "0"),
            minimum = ValueSource.Literal("0"),
            maximum = ValueSource.Literal("1"),
            step = ValueSource.Literal("0.05"),
            action = HomeAssistantAction("media_player.volume_set"),
        )
        val controller = NexusDashboardController()
        controller.setConfiguration(PublishedConfiguration(configVersion = 1, defaultPageId = "media", pages = listOf(PageConfig("media", "TV", listOf(slider))), checksum = "slider"))
        controller.setValues(mapOf("volume.progress" to RuntimeValue("0.5", stale = false, updatedAtEpochMs = 1)))
        controller.open()

        assertEquals(DashboardTapResult.Render, controller.tap(true))
        assertEquals(DashboardTapResult.Render, controller.tap(true))
        assertTrue("swipe: значение" in controller.presentation(true).footer)
        assertEquals(DashboardSliderChange("volume", 0.55, 1), controller.move(1))
        assertEquals("55%", controller.presentation(true).lines.single().badge)
        assertEquals(DashboardTapResult.Render, controller.tap(true))
        assertFalse(controller.back())
    }

    private fun configuration(): PublishedConfiguration = PublishedConfiguration(
        configVersion = 7,
        defaultPageId = "home",
        checksum = "test",
        pages = listOf(
            PageConfig(
                id = "home",
                name = "Дом",
                widgets = listOf(
                    WidgetConfig(
                        id = "home-status",
                        type = WidgetType.STATUS,
                        label = ValueSource.Literal("Телевизор"),
                        state = ValueSource.Entity("media_player.tv"),
                    ),
                    WidgetConfig(
                        id = "home-button",
                        type = WidgetType.BUTTON,
                        label = ValueSource.Literal("Включить"),
                        action = HomeAssistantAction("light.turn_on"),
                    ),
                ),
            ),
            PageConfig("tv", "Телевизор"),
            PageConfig("work", "Работа"),
        ),
    )
}

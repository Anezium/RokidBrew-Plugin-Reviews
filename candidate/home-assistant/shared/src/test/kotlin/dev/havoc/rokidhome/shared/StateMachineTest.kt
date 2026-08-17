package dev.havoc.rokidhome.shared

import dev.havoc.rokidhome.shared.model.ContextRule
import dev.havoc.rokidhome.shared.state.ContextSelector
import dev.havoc.rokidhome.shared.state.DynamicPageState
import dev.havoc.rokidhome.shared.state.GlassesNavigation
import dev.havoc.rokidhome.shared.state.GlassesControlFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateMachineTest {
    @Test fun contextUsesDelayPriorityAndOrder() {
        val selector = ContextSelector(
            listOf(
                ContextRule("timer", conditionTemplate = "timer", pageId = "timer_page", priority = 50, order = 0, activateAfterMs = 100),
                ContextRule("tv", conditionTemplate = "tv", pageId = "tv_page", priority = 100, order = 1, activateAfterMs = 500),
            ),
            defaultPageId = "home",
        )
        assertEquals("home", selector.update("timer", true, 0))
        assertEquals("timer_page", selector.refresh(100))
        assertEquals("timer_page", selector.update("tv", true, 100))
        assertEquals("tv_page", selector.refresh(600))
    }

    @Test fun visiblePageZeroPinsLatestPendingUntilLeave() {
        var state = DynamicPageState("tv")
        state = state.receive("timer", pageZeroVisible = true)
        state = state.receive("home", pageZeroVisible = true)
        assertEquals("tv", state.activePageId)
        assertEquals("home", state.pendingPageId)
        state = state.commitPending()
        assertEquals("home", state.activePageId)
        assertEquals(null, state.pendingPageId)
    }

    @Test fun warmForegroundAlwaysResetsToZero() {
        val navigation = GlassesNavigation(listOf("dynamic", "home", "climate")).swipe(1).swipe(1)
        assertEquals(2, navigation.selectedIndex)
        val reset = navigation.newForegroundSession()
        assertEquals(0, reset.selectedIndex)
        assertTrue(reset.history.isEmpty())
    }

    @Test fun backClosesOnlyFromZeroWithoutHistory() {
        val pages = listOf("dynamic", "home")
        val fromOne = GlassesNavigation(pages).swipe(1).back()
        assertFalse(fromOne.closeRequested)
        assertEquals(0, fromOne.state.selectedIndex)
        assertTrue(GlassesNavigation(pages).back().closeRequested)
    }

    @Test fun contextFlappingDoesNotPassActivationOrDeactivationDelay() {
        val selector = ContextSelector(
            listOf(ContextRule("tv", conditionTemplate = "tv", pageId = "tv", priority = 1, order = 0, activateAfterMs = 500, deactivateAfterMs = 500)),
            "home",
        )
        selector.update("tv", true, 0)
        selector.update("tv", false, 200)
        assertEquals("home", selector.refresh(1_000))
        selector.update("tv", true, 1_100)
        assertEquals("tv", selector.refresh(1_600))
        selector.update("tv", false, 1_700)
        selector.update("tv", true, 1_900)
        assertEquals("tv", selector.refresh(2_500))
    }

    @Test fun pendingContextCommitsImmediatelyWhenZeroIsNotVisible() {
        val state = DynamicPageState("home").receive("tv", pageZeroVisible = false)
        assertEquals("tv", state.activePageId)
        assertEquals(null, state.pendingPageId)
    }

    @Test fun controlFocusEntersMovesCyclicallyAndClears() {
        val actionable = listOf("power", "mute", "volume")
        val entered = GlassesControlFocus().enter(actionable)
        assertEquals("power", entered.selectedWidgetId)
        assertEquals("volume", entered.move(-1, actionable).selectedWidgetId)
        assertEquals("mute", entered.move(1, actionable).selectedWidgetId)
        assertEquals("power", GlassesControlFocus("volume").move(1, actionable).selectedWidgetId)
        assertFalse(entered.clear().active)
    }

    @Test fun controlFocusIgnoresEmptyPageAndClearsMissingSelection() {
        assertFalse(GlassesControlFocus().enter(emptyList()).active)
        assertFalse(GlassesControlFocus("removed").move(1, listOf("current")).active)
    }
}

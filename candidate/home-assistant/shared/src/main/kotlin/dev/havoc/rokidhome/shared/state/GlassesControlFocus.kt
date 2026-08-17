package dev.havoc.rokidhome.shared.state

data class GlassesControlFocus(
    val selectedWidgetId: String? = null,
) {
    val active: Boolean get() = selectedWidgetId != null

    fun enter(actionableWidgetIds: List<String>): GlassesControlFocus =
        if (active || actionableWidgetIds.isEmpty()) this else GlassesControlFocus(actionableWidgetIds.first())

    fun move(delta: Int, actionableWidgetIds: List<String>): GlassesControlFocus {
        val selected = selectedWidgetId ?: return this
        val currentIndex = actionableWidgetIds.indexOf(selected)
        if (currentIndex == -1) return GlassesControlFocus()
        val nextIndex = Math.floorMod(currentIndex + delta, actionableWidgetIds.size)
        return GlassesControlFocus(actionableWidgetIds[nextIndex])
    }

    fun clear(): GlassesControlFocus = GlassesControlFocus()
}

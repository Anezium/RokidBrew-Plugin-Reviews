package dev.havoc.rokidhome.shared.state

data class DynamicPageState(
    val activePageId: String,
    val pendingPageId: String? = null,
) {
    fun receive(pageId: String, pageZeroVisible: Boolean): DynamicPageState = when {
        pageId == activePageId -> copy(pendingPageId = null)
        pageZeroVisible -> copy(pendingPageId = pageId)
        else -> DynamicPageState(activePageId = pageId)
    }

    fun commitPending(): DynamicPageState = pendingPageId?.let { DynamicPageState(it) } ?: this
}

data class NavigationResult(val state: GlassesNavigation, val closeRequested: Boolean = false)

data class GlassesNavigation(
    val pageIds: List<String>,
    val selectedIndex: Int = 0,
    val history: List<Int> = emptyList(),
) {
    init {
        require(pageIds.isNotEmpty())
        require(selectedIndex in pageIds.indices)
    }

    fun swipe(delta: Int): GlassesNavigation {
        val next = (selectedIndex + delta).coerceIn(pageIds.indices)
        return if (next == selectedIndex) this else copy(selectedIndex = next, history = history + selectedIndex)
    }

    fun back(): NavigationResult = if (history.isNotEmpty()) {
        NavigationResult(copy(selectedIndex = history.last(), history = history.dropLast(1)))
    } else if (selectedIndex != 0) {
        NavigationResult(copy(selectedIndex = 0))
    } else {
        NavigationResult(this, closeRequested = true)
    }

    fun newForegroundSession(updatedPageIds: List<String> = pageIds): GlassesNavigation =
        GlassesNavigation(pageIds = updatedPageIds, selectedIndex = 0, history = emptyList())
}

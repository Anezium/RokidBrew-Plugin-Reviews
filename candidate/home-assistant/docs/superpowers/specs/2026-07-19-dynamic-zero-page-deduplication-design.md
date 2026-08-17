# Dynamic Zero Page Deduplication Design

## Goal

Show the page assigned to dynamic slot 0 only once in glasses navigation and exclude its configured position from the page counter.

## Navigation Model

The glasses derive their visible navigation IDs as the dynamic slot followed by every configured page except `DynamicPageState.activePageId`. For `N` configured pages, the counter therefore contains `N` positions: one dynamic position plus `N - 1` ordinary positions.

## Context Changes

- While slot 0 is visible, a new context assignment remains pending. Navigation IDs and the counter continue to reflect the pinned active page until the pending assignment is committed.
- When the active page changes while another page is visible, navigation is rebuilt and the visible page is preserved by page ID rather than by index.
- If the visible ordinary page becomes the new active page for slot 0, its content remains visible and its selected position becomes index 0 (`1/N`).
- Committing a pending assignment rebuilds navigation before applying the swipe away from slot 0, so the new active page cannot appear again at its configured position.

## State Boundaries

Navigation derivation stays in the glasses runtime. Published configuration and phone-side context rules remain unchanged. Rebuilding navigation clears obsolete index history; Back does not consume page history in the current interaction model.

## Verification

- Initial navigation excludes the default page from ordinary positions and its count equals the configured page count.
- A pending assignment does not alter navigation until commit.
- Leaving slot 0 commits pending state, rebuilds navigation, and skips the new active page.
- If the currently visible ordinary page becomes active for slot 0, the visible page is preserved and the selected index becomes 0.
- Foreground reset derives the same deduplicated list and opens index 0.


# Glasses Back Exit Design

## Goal

Make Back close the glasses application immediately while page-navigation mode is active, regardless of the selected page or navigation history.

## Behavior

- In control-focus mode, Back clears widget focus and keeps the current page visible.
- In page-navigation mode, Back requests application close immediately.
- Page history is not traversed by Back.
- Existing foreground-session behavior remains unchanged: reopening selects page slot 0 and clears history.

## Implementation

Keep the decision in `GlassesRuntime.back()` so Compose Back handling and every hardware input path use the same state-machine rule. When focus is active, clear it and return `false`. Otherwise return `true` without mutating page navigation.

## Verification

- A runtime test enters control-focus mode and verifies the first Back clears focus without requesting close.
- The same test verifies the next Back requests close without changing the selected page.
- A runtime test verifies Back requests close from a nonzero page even when navigation history exists.


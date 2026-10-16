# Agent Note: Preserve nested topic navigation

Status: implemented

## Problem

Opening a second topic from a topic detail used `launchSingleTop` on the shared topic destination. Navigation treated the destination as the same top entry and replaced the first topic's arguments. Pressing back therefore returned to the list instead of the previous topic detail.

## Decision

Topic links always create a new navigation entry. The topic route carries the target topic and optional floor in its arguments, so a later back action restores the earlier entry and its reading state. User, category, and unavailable destinations retain their existing single-top behavior.

## Alternatives considered

- Reusing `launchSingleTop` and manually storing the previous topic: this would duplicate NavController back-stack state and could lose the floor argument or saved UI state.
- Popping to the list before opening the next topic: this matches the current bug and cannot support returning through nested topic details.

## Consequences

Nested topic links now consume one back-stack entry per topic and return in reverse order. Reopening the same topic may add another entry, but it preserves the explicit navigation history and the linked floor.

## Testing

`DeepLinkUiTest.articleLinksOpenNativeTargetsAndUnknownPagesUseTheInAppWebRoute` opens topic 3001000, opens topic 3002000, pops once, and asserts topic 3001000 is visible again. The test failed before this change and passes after it.

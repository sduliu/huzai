# Agent Note: Native tag topic page

Status: implemented

## Problem

Links such as `/tag/193-tag/193` were not recognized as native Linux.do content and opened in the in-app web reader. The app had no tag-scoped topic feed or compact tag header.

## Decision

Parse a tag slug with an optional positive numeric ID and route it to a native tag destination. The repository requests the Discourse topic-list contract at `/tag/{slug}/{id}.json`, uses `/l/{filter}` for non-latest filters, and follows the server cursor unchanged for pagination. A tag feed reuses `FeedViewModel` loading, filtering, caching, and `FeedPage` rendering. Its toolbar starts in the category page's collapsed state, uses a normal back affordance plus a `LogoYellow` tag marker, shows the slug as the title, and keeps the topic list below it. WebView navigation remains unchanged because only the native content-link dispatcher recognizes tags.

## Alternatives considered

- Reusing the category route with a synthetic `Category`: this would send tag requests to `/c/{id}` and expose category-only publish and expanded metadata.
- Opening the tag page in WebView and scraping its HTML: this would bypass the existing topic-list parser and make pagination and login-scoped filters fragile.
- Dropping the numeric suffix and requesting only `/tag/{slug}.json`: Linux.do's canonical links include the tag ID, so the request could resolve to a different or unavailable route on installations that require it.

## Consequences

Topic detail tag chips reuse the native tag destination and preserve the existing back stack.

Supported tag links now open a native page and return through the normal navigation stack. The title is the URL slug until a separate tag metadata endpoint is available. The API path is covered by MockWebServer tests; live endpoint verification was unavailable in this environment because Linux.do returned a Cloudflare challenge to direct requests.

## Testing

`LinuxDoLinkTest` covers slug-only, slug-plus-ID, and malformed tag paths. `FeedRepositoryTest` covers the canonical tag endpoint and cursor. `TagFeedUiTest` verifies the yellow compact toolbar and topic list. `DeepLinkUiTest` verifies `/tag/193-tag/193` leaves the web reader path and reaches the native tag destination.

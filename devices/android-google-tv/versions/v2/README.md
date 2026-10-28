# v2 · Continue watching

Adds a horizontal Continue watching shelf above the original app row. Resume cards show real provider thumbnails (with poster-art and app-icon fallbacks), title, streaming app and available playback position. Select a card to open its owning app's deep link. Use Up from the first app row to reach the shelf, Up again for the version control, and Down to return to apps. Down from the top-right controls enters the shelf when cards are available.

Select **v2** in the top-right version menu, or set `options=version=v2` in your installation configuration. Switch back to v1 at any time without resetting settings.

## Service support

Uses Android TV's local Watch Next provider on Android 8+. This is not a direct integration with Netflix, YouTube or other account histories. Items appear only when their apps publish resume entries and the firmware permits this launcher to read them. Netflix, YouTube and other services may contribute no items; Google TV can restrict access to the system launcher. No accounts or credentials are collected.

The shelf refreshes when Home resumes. It includes Continue, Next episode and New episode entries, and omits completed entries, watchlist-only recommendations, duplicate deep links, missing apps and unresolvable links. Empty, restricted, unsupported and unavailable feeds have explicit messages; the app row remains usable. No fake viewing history is displayed.

## TV listings permission

Select the access message in the shelf and allow Android's TV listings prompt. Overlook requests `android.permission.READ_TV_LISTINGS`, which allows reading other apps' searchable TV provider entries. The former `READ_EPG_DATA` permission does not provide this access. Queries without the correct permission can silently return only Overlook's own rows; an empty result is not proof that no apps publish content.

Implementation references reviewed:
- [tv-launcher manifest and source](https://github.com/nielsvanvelzen/tv-launcher): declares READ_TV_LISTINGS, requests it at runtime, and queries WatchNextPrograms.
- [CleanYourTV's Watch Next investigation](https://github.com/Gathin23/cleanyourtv/blob/main/docs/watch-next.md).
- [Android TV provider source](https://android.googlesource.com/platform/packages/providers/TvProvider/+/master/src/com/android/providers/tv/TvProvider.java): permission checks and searchable-row filtering.

No third-party source code was copied.

## Validation

1.5.1 (17) builds and verifies with the existing signing key. An in-place update succeeded on Android TV 9 without clearing app data. The text-only version control no longer includes a dots icon.

The permission request was exercised from the shelf and accepted through Android's standard dialog. With READ_TV_LISTINGS granted, real YouTube entries appeared, including resume and next/new episode entries. Remote focus reached the cards; selecting a resume entry opened YouTube, and Home returned to Overlook. Exact playback position was not independently measured. Other services remain dependent on their own shared entries and are not guaranteed.

The previous 1.5.0 empty-feed observation was made without READ_TV_LISTINGS and must not be treated as evidence of absent viewing activity. Restricted-provider behavior and other firmware still need separate validation.

### 1.5.2 artwork and service checks

Artwork loads off the main thread with two workers, a bounded queue, 4 MB response limits, downsampled images and a 6 MB memory cache. Missing/inaccessible images fall back to the owning app icon; titles and remote controls remain usable. Native TV links are not required to declare the browser category.

The shelf interleaves services, limits each app to ten entries and caps the total at thirty. On the tested Android TV 9 device, shared rows came from YouTube, Crunchyroll, Jellyfin and JioHotstar. After resolving native link handling, 23 playable cards were available from YouTube, Crunchyroll and JioHotstar; thumbnail rendering was verified for YouTube and JioHotstar. Jellyfin rows did not produce playable cards in this check. Netflix, Prime Video, Apple TV and Sony LIV were installed but published no readable Watch Next rows during this check. This is a device observation, not a permanent service support list. Watch Next data is not a service account history API.

## V2 layouts (1.6.0)

Open the text-only version control in the top-right. The shared **Home screen & layout** menu lists the newest version first:

1. **v2 · Cinematic spotlight** — focused-title artwork, playback action and compact resume shelf on graphite. Up from resume cards reaches the hero action; Up again reaches controls.
2. **v2 · Artwork first** — larger thumbnails and a spacious resume shelf on graphite.
3. **v2 · Scenic dock** — resume cards over the configured wallpaper with apps on a translucent dock.
4. **v1 · Classic home** — existing classic layout.

All v2 layouts use a large live clock and date in place of launcher branding. App order, folders and shared viewing activity are common to all layouts. Version and layout selection persist independently; new installations still default to v1. V2 defaults to Cinematic when no layout has been saved. Configuration may use `options=version=v2;layout=cinematic`, `layout=shelves` or `layout=scenic`.

TV validation at 1280×720: all three layouts rendered with real shared artwork; menu order and current-layout checkmark verified; Cinematic selection through the remote menu survived a process restart. Long card titles were resized/ellipsized to avoid clipping, date and hero text no longer overlap, and hero focus restoration was reviewed. Final screenshot/source review accepted these fixes. Browser preview of local HTML remains unavailable to the agent because file URLs are blocked.

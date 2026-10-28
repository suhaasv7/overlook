# v2 · Continue watching

Adds a horizontal Continue watching shelf above the original app row. Resume cards show the title, streaming app and available playback position. Select a card to open its owning app's deep link. Use Up from the first app row to reach the shelf, Up again for the version control, and Down to return to apps. Down from the top-right controls enters the shelf when cards are available.

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

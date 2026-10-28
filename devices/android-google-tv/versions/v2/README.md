# v2 · Continue watching

Adds a horizontal Continue watching shelf above the original app row. Resume cards show the title, streaming app and available playback position. Select a card to open its owning app's deep link. Use Up from the first app row to reach the shelf, Up again for the version control, and Down to return to apps. Down from the top-right controls enters the shelf when cards are available.

Select **v2** in the top-right version menu, or set `options=version=v2` in your installation configuration. Switch back to v1 at any time without resetting settings.

## Service support

Uses Android TV's local Watch Next provider on Android 8+. This is not a direct integration with Netflix, YouTube or other account histories. Items appear only when their apps publish resume entries and the firmware permits this launcher to read them. Netflix, YouTube and other services may contribute no items; Google TV can restrict access to the system launcher. No accounts or credentials are collected.

The shelf refreshes when Home resumes. It omits completed entries, non-resume recommendations, duplicate deep links, missing apps and unresolvable links. Empty, restricted, unsupported and unavailable feeds have explicit messages; the app row remains usable. No fake viewing history is displayed.

## Validation

APK 1.5.0 (16) builds successfully and passes signature verification. The signing certificate matches the previous installation, and an in-place update succeeded without uninstalling or clearing app data.

Checked on Android TV 9:
- v1 opens with existing apps and settings.
- The top-right version menu switches to v2 using remote navigation.
- v2 selection survives a launcher process restart.
- Watch Next read permission is granted; the shelf shows its empty-feed message without a crash.

No resume cards were available during validation. Actual playback deep links, populated-shelf navigation, restricted provider access and other Android TV / Google TV firmware remain unverified. Service-by-service support depends on app contributions and firmware access; the absence of cards does not prove a service has no viewing history.

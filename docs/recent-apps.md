# Remote-friendly recent apps

Hold the configured PatchWall shortcut to open Overlook's recent-apps panel. Short press keeps the existing time/weather overlay. The panel works independently of the selected home version.

- Left/right selects an app; OK on its artwork opens it.
- Down selects that app's **Clear app** button; OK dismisses its recent tasks.
- Up from app artwork selects **Clear all**.
- Back closes the panel. The system may return Home when the final task is dismissed.

Clear actions remove available recent tasks, keeping app data. Home, System UI and Tailscale are protected. The panel lists actual available tasks, not a usage-history approximation. Apps can publish new tasks after a clear.

## Android 9 helper

A regular launcher cannot enumerate/remove all other apps' tasks. This firmware also denies the ADB shell the direct REMOVE_TASKS permission. The optional helper reads the real task list and invokes the system Recents screen's actual Dismiss controls, then verifies deletion by task ID. A progress overlay hides the system handoff. No root, force-stop, package disabling or app-data clearing is used.

The helper currently supports Android 9 (API 28). Other Android versions need their own validated adapter. No helper means an explicit offline state with Retry, not a fake cleared list.

Build the signed app with its existing key, then build the helper:

```sh
sh build.sh
sh build-recents-helper.sh
adb -s TV_IP:5555 install -r out/tvhome.apk
sh recents-helper.sh TV_IP
```

Use JAVA_HOME for an installed JDK if the build cannot locate Java. The helper is separate from the APK and needs restarting after a TV reboot:

```sh
sh recents-helper.sh TV_IP
```

The existing shortcut accessibility service must also be enabled. The helper does not change its enabled state or grant new accessibility capabilities. The existing opt-in `overlay.sh` script configures that service on the legacy supported Mi TV; do not run it without the device owner's permission.

## Local access and recovery

- No TCP or Unix socket listener. The helper attaches a Binder through a DUMP-protected provider; both the provider and helper validate the caller UID. Only Overlook and the ADB shell can use the adapter.
- Commands are limited to listing, clearing one package's eligible tasks, clearing all eligible tasks, and a shell-only helper stop. No arbitrary shell execution or task-ID input is accepted.
- Temporary UI automation preserves enabled accessibility services with FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES. It inspects only the system Recents window and matches exact task descriptions. No screen-coordinate guesses or external-app UI inspection is used.
- Ambiguous app labels are rejected rather than risking dismissal of another package. Long clear operations stop after a bounded interval and report remaining failures.
- The helper serializes operations, reconnects its Binder if Overlook restarts, and logs counts/errors without storing task titles or artwork. Startup atomically replaces only its own helper binary.

## Device validation (1.6.8)

The local build and APK signature verification passed, preserving the installed certificate. Tests used actual directional-key/OK events over ADB through the enabled PatchWall shortcut service. The panel displayed real app banners. Per-app dismissal and bulk dismissal were verified against the system task list; bulk validation removed eight eligible tasks with zero failures. Enabled accessibility services and the remote TV connection were preserved. Screenshots stay outside Git because they contain private usage data. Physical remote timing remains a separate check when the user is back at the TV.

References:
- https://developer.android.com/reference/android/app/ActivityManager#getRecentTasks(int,%20int)
- https://developer.android.com/reference/android/app/UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
- https://github.com/RikkaApps/Shizuku

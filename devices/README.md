# TVs

One pair of files per TV:

- **`<tv>.md`**: what was changed and why it's safe, how to undo it, and anything done by hand.
- **`<tv>.conf`**: what Overlook shows on that TV (apps, folders, greeting, options), for
  `sh install.sh <TV_IP> devices/<tv>.conf`. See [mi-tv-stick.conf](mi-tv-stick.conf) for the format.
- **`<tv>.txt`**: the same changes as a list that `check.sh` reads. Run it after a system update to
  see whether anything came back, and add `--fix` to put it right:

  ```sh
  sh check.sh <TV_IP> devices/<tv>.txt
  sh check.sh <TV_IP> --fix devices/<tv>.txt
  ```

| TV | System | Notes |
|---|---|---|
| [Mi TV Stick](mi-tv-stick.md) | Android TV 9, 1 GB | Streaming stick, no HDMI inputs or tuner |
| [Mi TV](mi-tv.md) | Android TV 9, 1 GB, 720p UI | 3 HDMI, AV and a tuner, under Source at the top right |

Never run another TV's list with `--fix` on a new TV. Package names change from brand to brand, so
every TV gets its own list, checked app by app.

## Setting up a new TV

Same steps every time. Ask before every change, turn apps off rather than uninstalling them, and write
each change down as you go.

1. **Connect.** Turn on developer mode and USB debugging (the README's Install section), then
   `adb connect <TV_IP>:5555`, and press **Allow** on the TV.
2. **Look before touching anything.** Run `sh candidates.sh <TV_IP>` for every app with a verdict, and
   `adb shell grep MemAvailable /proc/meminfo` for free memory. Note how long the TV has been on
   (`adb shell uptime`), so the after reading can be taken at the same point.
3. **Check the remote.** `adb shell dumpsys window policy | grep -A12 -i globalkey` shows which app
   handles each special button, like Netflix, Prime Video or Live TV. Keep those apps on.
4. **Turn off the junk** in small batches, going by [docs/debloat.md](../docs/debloat.md). Add each one
   to `<tv>.txt` as `off <package>`.
5. **Install Overlook** with this TV's own settings file: `sh build.sh`, then
   `sh install.sh <TV_IP> devices/<tv>.conf` (apps, folders, greeting and options; copy
   `mi-tv-stick.conf` to start).
6. **Settings.** These go to `<tv>.txt` as `global` / `secure` / `system` lines:
   - animations: `window_animation_scale 0`, `transition_animation_scale 0`, `animator_duration_scale 0.5`
   - screensaver and sleep: `system screen_off_timeout`, `secure sleep_timeout`. Also look for the
     brand's own sleep timer with `adb shell settings list system | grep -i sleep`.
7. **Streaming apps** get `appop <package> RUN_ANY_IN_BACKGROUND ignore`, so they don't load at startup.
8. **By hand on the TV:** Play Store → profile → Settings → Auto-update apps → Don't auto-update apps.
   Note it in `<tv>.md`.
9. **Restart once.** Wait 30 seconds after the last change, then `adb reboot`. Check that the TV starts
   up on Overlook, run `check.sh` with the list, and take the free-memory reading at the same uptime as
   before.
10. **Write it up:** `<tv>.md`, `<tv>.txt` and `<tv>.conf` here, plus a row in the table above.

## Same on every TV, and what changes

| Same everywhere | Changes per TV |
|---|---|
| Overlook and how it's installed | The vendor's junk apps: names differ by brand (TCL, Xiaomi, Sony…) |
| The never-turn-off list in docs/debloat.md | **HDMI inputs and the tuner**: a real TV has them. Keep the vendor's TV input app and Live TV (it's what opens an input). Overlook lists them under **Source** by itself. |
| Animation, screensaver and sleep values, unless asked otherwise | **Google TV** (`launcherx`) or **Android TV** (`tvlauncher`) home screen. On Google TV, the setup app can take over Home: see TV Detox's notes. |
| Background restriction for streaming apps | **Wi-Fi name**: Android 9 needs coarse location. Android 10 and newer need more, so Overlook shows "Wi-Fi". |
| Play Store auto-update off | The keyboard and the remote's button handlers, which have to stay on |

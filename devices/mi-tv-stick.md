# Mi TV Stick

| | |
|---|---|
| Model | Xiaomi Mi TV Stick, `MiTV-AESP0` (device `aquaman`) |
| System | Android TV 9 (API 28), Amlogic chip, Mali-450 graphics |
| Memory | 1 GB RAM, 4.6 GB storage (71% used) |
| Set up | 20 October 2026 |
| Change list | [`mi-tv-stick.txt`](mi-tv-stick.txt): check or re-apply everything below with `check.sh` |
| Overlook settings | [`mi-tv-stick.conf`](mi-tv-stick.conf): its apps, greeting and options, for `install.sh` |

## Result

| Free memory, 13 minutes after switching on | |
|---|---|
| Before | 152 MB |
| After | 294 MB, with YouTube and Aerial Views loaded |

The home screen itself went from 79 MB (Google's) to about 22 MB (Overlook, photo included).

## Apps turned off

Turned off with `pm disable-user`, never uninstalled. Undo one with `adb shell pm enable <package>`.

| Package | What it is | Why it's safe |
|---|---|---|
| `com.google.android.tvrecommendations` | Google's sponsored row | Ads; nothing else uses it once Overlook is the home screen |
| `com.miui.tv.analytics` | Xiaomi analytics | Tracking only |
| `tv.alphonso.alphonso_eula` | Alphonso | Watches what's on screen for ad targeting |
| `com.mitv.tvhome.michannel` | Mi Channel | Xiaomi's promoted row |
| `com.mitv.tvhome.atv` | Xiaomi content recommendations | Starts at every boot to feed promoted rows |
| `com.google.android.feedback` | Google feedback | Unused |
| `com.android.printspooler` | Printing | Unused on a TV |
| `com.android.htmlviewer` | HTML file viewer | Unused |
| `com.google.android.tv.bugreportsender` | Bug report sender | Unused |
| `com.mitv.videoplayer` | Mi video player | For local files; the stick has no storage port |
| `com.android.dreams.basic` | Basic screensaver | Aerial Views is the screensaver |
| `com.google.android.backdrop` | Google's Backdrop screensaver | Same |
| `com.mitv.dream` | Mi screensaver | Same |
| `com.google.android.apps.mediashell` | Google Cast | Casting isn't used on this TV. **Turn it back on to cast from a phone.** |
| `com.google.android.youtube.tvmusic` | YouTube Music | Not used (YouTube is unaffected) |
| `com.mitv.milinkservice` | Mi screen mirroring | Not used |
| `com.google.android.tvlauncher` | Google's home screen | Overlook replaces it. It has to stay off: it outranks any other home screen. |

Already off before the cleanup, left alone: `com.google.android.play.games`, `com.google.android.videos`,
`com.google.android.tv` (Live Channels).

### Kept on, on purpose

- `com.xiaomi.android.tvsetup.partnercustomizer`: the **Netflix and Prime Video buttons** on the remote
  (`KEYCODE_BUTTON_12` to `15`), and Android keeps it running anyway.
- `mitv.service`, `com.droidlogic.*`, `com.xiaomi.mitv.res`: the stick's own system and chip services.
- `com.xiaomi.mitv.updateservice`, `com.mitv.download.service`: system updates.
- `com.google.android.katniss`, `com.google.android.speech.pumpkin`: voice search, for the mic button.
- Everything `candidates.sh` marks `never`: Google Play services, Play Store, Settings, System UI, the
  keyboard, Bluetooth, setup apps and the TV input framework.

## Settings

| Setting | Was | Now | Undo |
|---|---|---|---|
| `global window_animation_scale` | 0.5 | 0 | `adb shell settings put global window_animation_scale 0.5` |
| `global transition_animation_scale` | 0.5 | 0 | `adb shell settings put global transition_animation_scale 0.5` |
| `global animator_duration_scale` | 0.5 | 0.5 (Overlook's glide) | `adb shell settings put global animator_duration_scale 0` for no motion at all |
| `system screen_off_timeout` (screensaver starts) | 30 min | 10 min | `adb shell settings put system screen_off_timeout 1800000` |
| `secure sleep_timeout` (stick sleeps) | never | never | `adb shell settings put secure sleep_timeout 300000` for 5 minutes |

The screensaver is Aerial Views, chosen on the TV before the cleanup. With sleep off it plays until
a button is pressed. Xiaomi's own sleep timer (`system tv_device_sleep_timer_key`) was already off.

## Streaming apps: no running in the background

Android's background restriction (`appops RUN_ANY_IN_BACKGROUND ignore`) on Netflix, Prime Video,
YouTube, JioHotstar, Sony LIV, Crunchyroll and Apple TV. They work as usual while open, but can't
run jobs or services once you leave them, so they stop loading themselves at startup.
Undo one with `adb shell cmd appops set <package> RUN_ANY_IN_BACKGROUND allow`.

## By hand on the TV

`check.sh` can't see these, because they live inside other apps:

- **Play Store doesn't auto-update apps.** In Play Store, go to your profile → Settings →
  Auto-update apps → **Don't auto-update apps**. Update apps from Manage apps & games → Updates.
  To undo, pick "Auto-update apps at any time".

## Overlook

```sh
sh build.sh
sh install.sh <TV_IP> devices/mi-tv-stick.conf
```

The settings file sets the app row and the "Hi Suhaas" greeting. `install.sh` also grants location
access, so the top right can show the Wi-Fi network's name, and usage access, for "Most used first".
Aerial Views was taken off the row on the TV (it's still the screensaver).

## Things this stick taught us

- `adb connect <TV_IP>:5555` works as soon as USB debugging is on. There's no separate network
  debugging switch.
- Google's home screen has priority 2 for Home, so making Overlook the default isn't enough: it has
  to stay off. To swap home screens safely, turn it back on for the duration as a fallback.
- The Mali-450 is slow at full-screen effects, so Overlook draws its colour fallback at a tenth of the
  size and stretches it.
- Android 9 shows the Wi-Fi name to apps that have only coarse location access, and location here is on.

## Undo everything

```sh
sh undo.sh <TV_IP>                               # Google's home screen back, Overlook removed
grep '^off ' devices/mi-tv-stick.txt | cut -d' ' -f2 | while read p; do adb shell pm enable "$p"; done
grep '^appop ' devices/mi-tv-stick.txt | cut -d' ' -f2 | while read p; do adb shell cmd appops set "$p" RUN_ANY_IN_BACKGROUND allow; done
```

Then reset the settings with the commands in the table above, and turn Play Store auto-update back on.

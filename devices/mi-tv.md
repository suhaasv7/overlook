# Mi TV

| | |
|---|---|
| Model | Xiaomi Mi TV, `MiTV-AXSO0` (device `dangal`) |
| System | Android TV 9 (API 28), Amlogic chip, Mali-450 graphics, UI at 1280×720 |
| Memory | 1 GB RAM, 4.4 GB storage (57% used) |
| Inputs | HDMI 1 (ARC, for a soundbar), HDMI 2, HDMI 3, AV, antenna/cable tuner. All three HDMI ports do CEC. |
| Set up | 21 October 2026 |
| Change list | [`mi-tv.txt`](mi-tv.txt): check or re-apply everything below with `check.sh` |
| Overlook settings | [`mi-tv.conf`](mi-tv.conf): its apps and greeting, for `install.sh` |
| Overlook build | `a921fa1` (21 October 2026) |
| Last checked | 21 October 2026, after a restart: `check.sh` passed all 44 items |

## Result

| Free memory, about 16½ minutes after switching on | |
|---|---|
| Before | 275 MB |
| After | 278 MB |

The home screen went from 98 MB (Google's) to 18 MB (Overlook), but free memory barely moved: after
the restart, voice search (about 110 MB, kept for the mic button) and Netflix (86 MB, which still
starts at boot despite its background limit) loaded themselves. Android parks those and reclaims them
when something needs the room. Turning off voice search would free the most, if the mic isn't used.

## Apps turned off

Turned off with `pm disable-user`, never uninstalled. Undo one with `adb shell pm enable <package>`.

| Package | What it is | Why it's safe |
|---|---|---|
| `com.google.android.tvrecommendations` | Google's sponsored row | Ads; nothing uses it once Overlook is the home screen |
| `com.google.android.leanbacklauncher.recommendations` | The old Android TV launcher's recommendations | Same |
| `com.miui.tv.analytics` | Xiaomi analytics | Tracking only |
| `com.xiaomi.statistic` | Xiaomi usage statistics | Uploads usage, nothing else |
| `com.mitv.tvhome.atv` | Xiaomi's content recommendations | Starts at every boot to feed promoted rows (22 MB) |
| `com.google.android.feedback` | Google feedback | Unused |
| `com.android.printspooler` | Printing | Unused on a TV |
| `com.android.htmlviewer` | HTML file viewer | Unused |
| `com.google.android.tv.bugreportsender` | Bug report sender | Unused |
| `com.android.wallpapercropper` | Wallpaper cropper | No wallpapers on a TV |
| `com.xiaomi.mitv.handbook` | The built-in TV manual | Unused |
| `com.android.dreams.basic` | Basic screensaver | Aerial Views is the screensaver |
| `com.google.android.backdrop` | Google's Backdrop screensaver | Same |
| `com.mitv.dream` | Mi screensaver | Same |
| `com.xiaomi.mitv.mediaexplorer` | Mi media explorer | USB drives aren't used. **Turn these five back on to play files from USB.** |
| `com.mitv.gallery` | Mi gallery | Same |
| `com.xiaomi.mimusic2` | Mi music | Same |
| `com.mitv.videoplayer` | Mi video player | Same |
| `com.android.gallery3d` | Android gallery | Same |
| `com.google.android.apps.mediashell` | Google Cast | Casting isn't used. **Turn it back on to cast from a phone.** |
| `com.google.android.youtube.tvmusic` | YouTube Music | Not used (YouTube is unaffected) |
| `com.mitv.milinkservice` | Mi screen mirroring | Not used |
| `com.xiaomi.mitv.smartshare` | Mi Smart Share (Miracast/MiPlay receiver) | Not used |
| `com.xiaomi.wfdsinkhelperservice` | Miracast receiver helper | Not used |
| `com.xiaomi.smarthome.tv.global4` | Mi Home smart-home control | Not used |
| `com.google.android.tvlauncher` | Google's home screen | Overlook replaces it. It has to stay off: it outranks any other home screen. |

Already off before the cleanup, left alone: `com.google.android.play.games`, `com.google.android.videos`,
`com.android.camera2`.

### Kept on, on purpose

- **The TV's inputs and Live TV:** `com.droidlogic.tvinput` (HDMI, AV and the tuner),
  `com.android.tv` (Live TV, which shows an HDMI input or the antenna), `com.droidlogic.droidlivetv`,
  `com.android.providers.tv`.
- **Picture and sound:** `com.droidlogic.tv.settings`, `com.droidlogic.tv.soundeffectsettings`,
  `com.xiaomi.android.TV.audio`.
- `com.xiaomi.android.tvsetup.partnercustomizer`: the **Netflix and Prime Video buttons** on the remote
  (`KEYCODE_BUTTON_2` to `5` and `15`), and Android keeps it running anyway.
- `com.mitv.tvlock` (child lock), `mitv.service`, `com.droidlogic.*`, `com.xiaomi.mitv.res`,
  `com.xiaomi.mitv.updateservice` (system updates), `com.xiaomo.tv.milegal`.
- Not sure what they do, so they stay: `com.xiaomi.floatingframe`, `com.xiaomi.mitv.tvmanager`,
  `com.xm.webcontent`, `com.google.android.leanbacklauncher`.
- Voice search (`com.google.android.katniss`, `com.google.android.speech.pumpkin`) and everything
  `candidates.sh` marks `never`.

## Settings

| Setting | Was | Now | Undo |
|---|---|---|---|
| `global window_animation_scale` | 0.5 | 0 | `adb shell settings put global window_animation_scale 0.5` |
| `global transition_animation_scale` | 0.5 | 0 | `adb shell settings put global transition_animation_scale 0.5` |
| `global animator_duration_scale` | 0.5 | 0.5 (Overlook's glide) | `adb shell settings put global animator_duration_scale 0` for no motion at all |
| `system screen_off_timeout` (screensaver starts) | 60 min | 10 min | `adb shell settings put system screen_off_timeout 3600000` |
| `secure sleep_timeout` (TV sleeps) | never | never | unchanged |
| `global hdmi_control_enabled` (HDMI-CEC) | off | on | `adb shell settings put global hdmi_control_enabled 0` |

HDMI control is on so devices plugged into HDMI can report their names to Source. Its auto wake-up
and auto device-off (`hdmi_control_auto_wakeup_enabled`, `hdmi_control_auto_device_off_enabled`) stay
off. A device may still switch the TV to its input when it turns on (its own "one touch play").

The screensaver is Aerial Views, already chosen on the TV. Xiaomi's own sleep timer
(`system tv_device_sleep_timer_key`) is off.

## Streaming apps: no running in the background

Background restriction (`appops RUN_ANY_IN_BACKGROUND ignore`) on Netflix, Prime Video, YouTube,
JioHotstar, Sony LIV, Crunchyroll, Apple TV and Jellyfin. Undo one with
`adb shell cmd appops set <package> RUN_ANY_IN_BACKGROUND allow`.

## By hand on the TV

- **Play Store auto-update** was already off ("Don't auto-update apps"), so nothing changed. 7 app
  updates were waiting on 21 October: Play Store → profile → Manage apps and games → Updates.

## Overlook

```sh
sh build.sh
sh install.sh <TV_IP> devices/mi-tv.conf
```

The settings file sets the app row (Jellyfin at the end) and the "Hi Suhaas" greeting. `install.sh`
grants location access (Wi-Fi name) and usage access ("Most used first").

**Source** sits at the top right on this TV: HDMI 1, HDMI 2, HDMI 3, AV and TV. HDMI and AV open
straight away (through Live TV); TV opens Live TV on the antenna or cable channels. A device plugged
into a port shows by name when it reports one over HDMI-CEC ("HDMI 1 · PlayStation 5"); it has to be
on, with CEC turned on in its own settings. Names set in TV Settings → Inputs show instead of "HDMI 1".
A device that gives no name still shows as **connected**, from the TV sensing it on the cable
("HDMI 1 · connected"). On 21 October HDMI 1 had a device on it that didn't answer over CEC (CEC off
in its settings, or not supported), so it shows as connected rather than by name.

## Things this TV taught us

- Its screen runs at 1280×720 (density 213), not 1080p. Overlook scales itself from its 1920×1080 design.
- No app answers a plain "view HDMI" request here; Live TV (`com.android.tv`) opens an input when
  asked by name, which is what Source does.
- The tuner's own name is a service's (`DroidLogicTvInput`), so Source calls it "TV".
- The remote's TV and DVR keys are sent to `com.google.android.tv`, which this TV doesn't have, so they
  do nothing. That was already so before the cleanup; Source covers switching inputs.
- Input states (Android's `getInputState`) are reliable here: the port with a device on it reports
  connected, the others don't. The tuner always reports connected, so Source doesn't mark it.

## Undo everything

```sh
sh undo.sh <TV_IP>                                       # Google's home screen back, Overlook removed
grep '^off ' devices/mi-tv.txt | cut -d' ' -f2 | while read p; do adb shell pm enable "$p"; done
grep '^appop .* RUN_ANY' devices/mi-tv.txt | cut -d' ' -f2 | while read p; do adb shell cmd appops set "$p" RUN_ANY_IN_BACKGROUND allow; done
```

Then reset the settings with the commands in the table above.

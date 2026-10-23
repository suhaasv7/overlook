#!/bin/sh
# Opt in/out of the verified Android 9 Mi TV PatchWall bridge. Install the APK first.
# Usage: sh overlay.sh <TV_IP or adb serial> [on|off]
set -eu
TARGET=${1:-}; MODE=${2:-on}
[ -n "$TARGET" ] || { echo "Usage: sh overlay.sh <TV_IP or adb serial> [on|off]" >&2; exit 2; }
case "$MODE" in on|off) ;; *) echo "Choose on or off." >&2; exit 2 ;; esac
case "$TARGET" in *.*.*.*:*) TV=$TARGET ;; *.*.*.*) TV=$TARGET:5555 ;; *) TV=$TARGET ;; esac
case "$TV" in *:*) adb connect "$TV" >/dev/null ;; esac
[ "$(adb -s "$TV" get-state)" = device ] || exit 1
ME=com.ansub.tvhome/.InfoOverlayService
EXISTING=$(adb -s "$TV" shell settings get secure enabled_accessibility_services | tr -d '\r')
[ "$EXISTING" != null ] || EXISTING=
# Remove only our component, preserving every other enabled accessibility service.
REMAINING=$(printf '%s' "$EXISTING" | tr ':' '\n' | awk 'NF && $0 != "com.ansub.tvhome/.InfoOverlayService" && $0 != "com.ansub.tvhome/com.ansub.tvhome.InfoOverlayService"' | paste -sd ':' -)
if [ "$MODE" = on ]; then
  MODEL=$(adb -s "$TV" shell getprop ro.product.model | tr -d '\r')
  [ "$MODEL" = MiTV-AXSO0 ] || { echo "This bridge is verified only on MiTV-AXSO0; leaving this device unchanged." >&2; exit 2; }
  adb -s "$TV" shell pm grant com.ansub.tvhome android.permission.READ_LOGS
  # Keep quoting for the TV's shell: the semicolon is part of the option value.
  adb -s "$TV" shell "am start -n com.ansub.tvhome/.MainActivity --es options 'patchwall_key=189;patchwall_bridge=mi-tv'" >/dev/null
  SERVICES=${REMAINING:+$REMAINING:}$ME
  adb -s "$TV" shell settings put secure enabled_accessibility_services "$SERVICES"
  adb -s "$TV" shell settings put secure accessibility_enabled 1
  echo "PatchWall info overlay enabled. Press PatchWall; press again to close (or wait eight seconds)."
else
  if [ -n "$REMAINING" ]; then
    adb -s "$TV" shell settings put secure enabled_accessibility_services "$REMAINING"
  else
    adb -s "$TV" shell settings delete secure enabled_accessibility_services >/dev/null
    adb -s "$TV" shell settings put secure accessibility_enabled 0
  fi
  adb -s "$TV" shell pm revoke com.ansub.tvhome android.permission.READ_LOGS
  echo "PatchWall overlay disabled and its log permission revoked. Other accessibility services are preserved."
fi

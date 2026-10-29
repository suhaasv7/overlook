#!/bin/sh
# Enable the optional Android 9 recent-task adapter. Re-run after a TV reboot.
# Build first: JAVA_HOME=... sh build.sh && JAVA_HOME=... sh build-recents-helper.sh
set -eu
cd "$(dirname "$0")"
TARGET=${1:?Usage: sh recents-helper.sh TV_IP_OR_ADB_SERIAL}
case "$TARGET" in *.*.*.*:*) TV=$TARGET ;; *.*.*.*) TV=$TARGET:5555 ;; *) TV=$TARGET ;; esac
case "$TV" in *:*) adb connect "$TV" >/dev/null ;; esac
[ "$(adb -s "$TV" shell getprop ro.build.version.sdk | tr -d '\r')" = 28 ] || {
  echo 'The task adapter currently supports Android 9 only; leaving this TV unchanged.' >&2; exit 2;
}
[ -f out/recents-helper.jar ] || { echo 'Build the recent-apps helper first.' >&2; exit 1; }
USER_ID=$(adb -s "$TV" shell am get-current-user | tr -d '\r')
case "$USER_ID" in ''|*[!0-9]*) echo 'Cannot identify the current TV user.' >&2; exit 1 ;; esac
# Stop the previous instance before atomically replacing its code.
adb -s "$TV" shell 'if [ -f /data/local/tmp/overlook-recents-helper.jar ]; then CLASSPATH=/data/local/tmp/overlook-recents-helper.jar app_process /system/bin com.ansub.tvhome.RecentsHelper stop; fi'
adb -s "$TV" push out/recents-helper.jar /data/local/tmp/overlook-recents-helper.next.jar >/dev/null
adb -s "$TV" shell 'chmod 400 /data/local/tmp/overlook-recents-helper.next.jar && mv -f /data/local/tmp/overlook-recents-helper.next.jar /data/local/tmp/overlook-recents-helper.jar'
adb -s "$TV" shell "umask 077; CLASSPATH=/data/local/tmp/overlook-recents-helper.jar nohup app_process /system/bin com.ansub.tvhome.RecentsHelper serve $USER_ID >/data/local/tmp/overlook-recents-helper.log 2>&1 </dev/null &"
echo 'Recent-apps helper started. Hold PatchWall to open the panel.'

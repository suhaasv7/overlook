#!/bin/sh
# Android 9 adapter; separate from the app and started explicitly through ADB.
set -eu
cd "$(dirname "$0")"
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
JAR=$SDK/platforms/android-34/android.jar
rm -rf out/helper-classes out/helper-dex
mkdir -p out/helper-classes out/helper-dex
javac -nowarn -source 8 -target 8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" -d out/helper-classes helper/com/ansub/tvhome/*.java
"$BT/d8" --min-api 28 --lib "$JAR" --output out/helper-dex out/helper-classes/com/ansub/tvhome/*.class
(cd out/helper-dex && zip -q -j ../recents-helper.jar classes.dex)

#!/bin/sh
cd "$(dirname "$0")"
ADB=./adb
[ -x "$ADB" ] || ADB=adb
APK=$(ls *.apk 2>/dev/null | head -n 1)
[ -z "$APK" ] && { echo "No .apk file found in this folder."; exit 1; }
$ADB install -r "$APK"

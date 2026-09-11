#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
JDK="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BT="$SDK/build-tools/36.0.0"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
mkdir -p build/classes build/dex artifacts
"$JDK/bin/javac" -source 8 -target 8 -classpath "$SDK/platforms/android-36/android.jar" -d build/classes app/src/dev/duo/probe/*.java
"$BT/d8" --lib "$SDK/platforms/android-36/android.jar" --output build/dex build/classes/dev/duo/probe/*.class
"$BT/aapt2" compile --dir app/res -o build/resources.zip
"$BT/aapt2" link -I "$SDK/platforms/android-36/android.jar" --manifest app/AndroidManifest.xml -o build/unsigned.apk build/resources.zip
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f build/debug.jks ]; then
  "$JDK/bin/keytool" -genkeypair -keystore build/debug.jks -storepass android -keypass android -alias androiddebugkey -dname "CN=Duo Debug" -keyalg RSA -validity 3650 >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks build/debug.jks --ks-pass pass:android --out artifacts/duo-probe.apk build/aligned.apk
"$BT/apksigner" verify artifacts/duo-probe.apk

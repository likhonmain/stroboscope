#!/usr/bin/env bash
set -euo pipefail

echo "=== [1/4] Preparing Android SDK Licenses ==="
if [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
  sudo chmod -R 777 "${ANDROID_HOME}" || true
  mkdir -p "${ANDROID_HOME}/licenses"
  printf "\n24333f8a63b6825ea9c5514f83c2829b004d1fee\nd56f5187479451eabf01fb78af6dfcb131a6481e\n84831b9409646a918e30573bab4c9c91346d8abd\n" > "${ANDROID_HOME}/licenses/android-sdk-license"
  printf "\n84831b9409646a918e30573bab4c9c91346d8abd\n504667f4c0de7af1a06de9f4b1727b84351f2910\n" > "${ANDROID_HOME}/licenses/android-sdk-preview-license"
fi

echo "=== [2/4] Generating Signing Keystores ==="
if [ ! -f "debug.keystore" ]; then
  keytool -genkeypair -v \
    -keystore debug.keystore \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

if [ ! -f "my-upload-key.jks" ]; then
  keytool -genkeypair -v \
    -keystore my-upload-key.jks \
    -storepass android \
    -alias upload \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=StroboScope Pro,O=AIStudio,C=US"
fi

echo "=== [3/4] Building Debug & Signed Release APKs ==="
export STORE_PASSWORD="android"
export KEY_PASSWORD="android"
gradle assembleDebug assembleRelease --no-daemon --stacktrace

echo "=== [4/4] Packaging APK Output ==="
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/StroboScope-Pro-debug.apk
if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
  cp app/build/outputs/apk/release/app-release.apk dist/StroboScope-Pro-release.apk
fi
ls -lh dist/

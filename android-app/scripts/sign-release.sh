#!/bin/sh
# Requires owner-provided signing material. No private keys or passwords belong in this repository.
set -eu
TASK_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
: "${PEDAL_SIGN_KEYSTORE:?Set path to your signing keystore outside the repository}"
: "${PEDAL_SIGN_ALIAS:?Set the signing alias}"
: "${PEDAL_STORE_PASSWORD:?Set the keystore password in the environment}"
: "${PEDAL_KEY_PASSWORD:?Set the key password in the environment}"
PEDAL_NAME=${PEDAL_TEAM_NAME:-SA}
PEDAL_OUTPUT="$TASK_DIR/../apk/${PEDAL_NAME}_페달체크_release.apk"
mkdir -p "$TASK_DIR/../apk"
"$TASK_DIR/gradlew" -p "$TASK_DIR" assembleRelease
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -f -p 4 "$TASK_DIR/app/build/outputs/apk/release/app-release-unsigned.apk" "$TASK_DIR/app/build/outputs/apk/release/app-release-aligned.apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" sign --ks "$PEDAL_SIGN_KEYSTORE" --ks-key-alias "$PEDAL_SIGN_ALIAS" --ks-pass env:PEDAL_STORE_PASSWORD --key-pass env:PEDAL_KEY_PASSWORD --out "$PEDAL_OUTPUT" "$TASK_DIR/app/build/outputs/apk/release/app-release-aligned.apk"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$PEDAL_OUTPUT"

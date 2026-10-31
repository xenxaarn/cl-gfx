#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$ROOT/../.toolchain/sdk}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/build"
APP_ID="com.clgfx"
LIBS="$ROOT/libs"

for f in "$BT/aapt2" "$BT/aidl" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$ANDROID_JAR"; do
  if [ ! -e "$f" ]; then
    echo "missing: $f" >&2
    exit 1
  fi
done

if ! ls "$LIBS"/*.jar >/dev/null 2>&1; then
  echo "missing Shizuku jars in $LIBS (see libs/README.md)" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/7] resources"
"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/res.zip"

# aapt2 has no manifest-placeholder support; substitute the one AGP placeholder
# used by the Shizuku provider authority before linking.
echo "[2/7] manifest"
sed "s/\${applicationId}/$APP_ID/g" "$ROOT/AndroidManifest.xml" >"$OUT/AndroidManifest.xml"

echo "[3/7] link"
"$BT/aapt2" link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 29 \
  --version-code 2 \
  --version-name 1.1.0 \
  "$OUT/res.zip"

echo "[4/7] aidl"
"$BT/aidl" -I"$ROOT/java" -o"$OUT/gen" "$ROOT/java/com/clgfx/IStorageService.aidl"

echo "[5/7] javac"
find "$ROOT/java" "$OUT/gen" -name '*.java' >"$OUT/sources.txt"
javac -encoding UTF-8 --release 11 -Xlint:-options \
  -classpath "$ANDROID_JAR:$LIBS/*" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "[6/7] d8"
find "$OUT/classes" -name '*.class' >"$OUT/classes.txt"
# Shizuku API/provider are plain jars (extracted from the official AARs), so
# they are dexed into the same single DEX as the app. The annotation jar is a
# compile-only dependency and is intentionally not shipped.
"$BT/d8" --lib "$ANDROID_JAR" --min-api 24 --output "$OUT/dex" \
  @"$OUT/classes.txt" "$LIBS"/shizuku-*.jar

echo "[7/7] package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"
(cd "$OUT" && zip -q -X unsigned.apk classes.dex)
if [ -d "$ROOT/assets" ] && [ -n "$(ls -A "$ROOT/assets")" ]; then
  (cd "$ROOT" && zip -q -X -r "$OUT/unsigned.apk" assets)
fi
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[sign]"
KEYSTORE="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
"$BT/apksigner" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "$OUT/cl-gfx.apk" "$OUT/aligned.apk"

rm -f "$OUT/classes.dex"

echo
echo "built: $OUT/cl-gfx.apk"
ls -lh "$OUT/cl-gfx.apk"

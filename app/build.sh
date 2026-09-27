#!/usr/bin/env bash
# Builds app/coffee-break.apk with the plain Android build tools (no Gradle).
# Needs: aapt, dalvik-exchange (dx), zipalign, apksigner, a JDK, and an android.jar (API 34).
#   ANDROID_JAR=/path/to/android-34.jar KEYSTORE=/path/to/ks KS_PASS=... ./app/build.sh
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$APP_DIR")"
OUT="${BUILD_DIR:-$APP_DIR/build}"
: "${ANDROID_JAR:?set ANDROID_JAR to an API 34 android.jar}"
: "${KEYSTORE:?set KEYSTORE to the signing keystore}"
: "${KS_PASS:?set KS_PASS}"

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

# The app ships with the newest issue so it works before its first sync.
python3 "$ROOT/tools/build_feed.py"
python3 - "$ROOT/feed.json" "$APP_DIR/assets/seed.json" <<'PY'
import json, sys
feed = json.load(open(sys.argv[1], encoding="utf-8"))
json.dump({"app": feed["app"], "issues": feed["issues"][:1]}, open(sys.argv[2], "w", encoding="utf-8"), ensure_ascii=False)
PY

aapt package -f -m \
  -J "$OUT/gen" \
  -M "$APP_DIR/AndroidManifest.xml" \
  -S "$APP_DIR/res" \
  -A "$APP_DIR/assets" \
  -I "$ANDROID_JAR" \
  -0 arsc \
  -F "$OUT/app.unaligned.apk"

javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $(find "$APP_DIR/src" "$OUT/gen" -name '*.java')

dalvik-exchange --dex --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes"

( cd "$OUT" && aapt add -f app.unaligned.apk classes.dex >/dev/null )
zipalign -p -f 4 "$OUT/app.unaligned.apk" "$OUT/app.aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias coffeebreak \
  --out "$APP_DIR/coffee-break.apk" "$OUT/app.aligned.apk"
apksigner verify --print-certs "$APP_DIR/coffee-break.apk" | head -3
rm -f "$APP_DIR/coffee-break.apk.idsig"
echo "Built $APP_DIR/coffee-break.apk ($(du -h "$APP_DIR/coffee-break.apk" | cut -f1))"

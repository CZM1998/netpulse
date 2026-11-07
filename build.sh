#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Works both locally on Windows (Git Bash) and on GitHub Actions' Linux runner.
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  if [ -d "C:/Users/CZM/AppData/Local/Android/Sdk" ]; then
    SDK="C:/Users/CZM/AppData/Local/Android/Sdk"
  else
    echo "ERROR: ANDROID_SDK_ROOT is not set and no fallback SDK was found." >&2
    exit 1
  fi
fi

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) WIN=1 ;;
  *) WIN=0 ;;
esac
EXE=""
[ "$WIN" = 1 ] && EXE=".exe"

# Pick build-tools: NP_BUILD_TOOLS_VERSION, then a known installed version, then latest.
if [ -n "${NP_BUILD_TOOLS:-}" ]; then
  BT="$NP_BUILD_TOOLS"
elif [ -n "${NP_BUILD_TOOLS_VERSION:-}" ]; then
  BT="$SDK/build-tools/$NP_BUILD_TOOLS_VERSION"
elif [ -d "$SDK/build-tools/36.1.0" ]; then
  BT="$SDK/build-tools/36.1.0"
else
  BT="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
fi

PLATFORM="$SDK/platforms/android-36/android.jar"
if [ ! -f "$PLATFORM" ]; then
  PLATFORM="$(find "$SDK/platforms" -path '*/android.jar' | sort -V | tail -1)"
fi
if [ ! -f "$PLATFORM" ] || [ ! -d "$BT" ]; then
  echo "ERROR: Android platform or build-tools missing (SDK=$SDK, build-tools=$BT)." >&2
  exit 1
fi

JAVA_HOME="${JAVA_HOME:-${NP_JAVA_HOME:-}}"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/javac$EXE" ]; then
  JAVA_BIN="$JAVA_HOME/bin"
elif [ -n "${NP_JAVA_BIN:-}" ]; then
  JAVA_BIN="$NP_JAVA_BIN"
else
  JAVA_BIN="$(dirname "$(command -v javac)")"
fi
JAVAC="$JAVA_BIN/javac$EXE"
KEYTOOL="$JAVA_BIN/keytool$EXE"

if [ "$WIN" = 1 ]; then
  AAPT2="$BT/aapt2.exe"
  D8="$BT/d8.bat"
  ZIPALIGN="$BT/zipalign.exe"
  APKSIGNER="$BT/apksigner.bat"
else
  AAPT2="$BT/aapt2"
  D8="$BT/d8"
  ZIPALIGN="$BT/zipalign"
  APKSIGNER="$BT/apksigner"
fi
PYTHON=""
for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c 'import sys' >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done
if [ -z "$PYTHON" ]; then
  echo "ERROR: Python 3 is required for packaging." >&2
  exit 1
fi

OUT="build"
APK="netpulse.apk"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0.0}"
KEYSTORE="${NETPULSE_KEYSTORE:-netpulse.keystore}"
KS_PASS="${NETPULSE_KEYSTORE_PASS:-netpulse-local}"
KEY_ALIAS="${NETPULSE_KEY_ALIAS:-netpulse}"
KEY_PASS="${NETPULSE_KEY_PASS:-$KS_PASS}"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/6] compile resources"
"$AAPT2" compile --dir res -o "$OUT/resources.zip"

echo "[2/6] link resources"
"$AAPT2" link \
  -o "$OUT/unsigned.apk" \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  --java "$OUT/gen" \
  --min-sdk-version 29 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay \
  "$OUT/resources.zip"

echo "[3/6] compile java"
find src -name '*.java' > "$OUT/sources.txt"
find "$OUT/gen" -name '*.java' >> "$OUT/sources.txt"
"$JAVAC" \
  -encoding UTF-8 \
  -source 8 -target 8 \
  -classpath "$PLATFORM" \
  -d "$OUT/classes" \
  @"$OUT/sources.txt"

echo "[4/6] dex"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$D8" --lib "$PLATFORM" --min-api 29 --output "$OUT/dex" @"$OUT/classes.txt"

echo "[5/6] package + align"
"$PYTHON" - "$OUT/unsigned.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile, shutil
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
shutil.copyfile(apk, tmp)
with zipfile.ZipFile(tmp, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
shutil.move(tmp, apk)
PY
"$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[6/6] sign"
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=NetPulse, OU=LocalBuild, O=NetPulse, L=Local, ST=Local, C=CN" >/dev/null
fi
rm -f "$APK"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$APK" "$OUT/aligned.apk"
"$APKSIGNER" verify --print-certs "$APK" | head -4
ls -lh "$APK"
echo "BUILD_OK"

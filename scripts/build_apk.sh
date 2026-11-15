#!/bin/bash
# 从源码直接打一个签名 APK（不依赖 Gradle）
# 用法：
#   export ANDROID_HOME=~/android-sdk        # 里面要有 build-tools/35.0.0 和 platforms/android-33
#   export KS=/path/to/your.jks              # 你的签名密钥
#   export KS_PASS=你的密码
#   export KS_ALIAS=dailycat
#   bash scripts/build_apk.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app-src"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-33/android.jar"
B="$ROOT/build"

KS="${KS:-$HOME/.daycat/release.jks}"
KS_PASS="${KS_PASS:?请先 export KS_PASS=你的密钥库密码}"
KS_ALIAS="${KS_ALIAS:-dailycat}"

[ -d "$BT" ] || { echo "找不到 $BT，请设置 ANDROID_HOME"; exit 1; }
[ -f "$PLATFORM" ] || { echo "找不到 $PLATFORM"; exit 1; }
[ -f "$KS" ] || { echo "找不到密钥库 $KS，请 export KS=/path/to/xxx.jks"; exit 1; }

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex"
cd "$SRC"

echo "== 0. 检查 HTML 里的 id 引用"
python3 "$ROOT/scripts/check_ids.py"

echo "== 1. 编译资源"
"$BT/aapt2" compile --dir res -o "$B/res.zip"

echo "== 2. 链接"
"$BT/aapt2" link -o "$B/app.unsigned.apk" \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -R "$B/res.zip" \
  --java "$B/gen" \
  -A assets \
  --min-sdk-version 21 --target-sdk-version 33 \
  --auto-add-overlay

echo "== 3. 编译 Java"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM" -classpath "$PLATFORM" \
  -d "$B/classes" \
  "$B/gen/com/liu/dailycat/R.java" \
  src/com/liu/dailycat/*.java

echo "== 4. dex"
"$BT/d8" --lib "$PLATFORM" --min-api 21 --output "$B/dex" "$B/classes/com/liu/dailycat/"*.class

echo "== 5. 塞进 APK"
cd "$B/dex" && zip -q "$B/app.unsigned.apk" classes.dex && cd "$SRC"

echo "== 6. 对齐"
"$BT/zipalign" -f 4 "$B/app.unsigned.apk" "$B/app.aligned.apk"

echo "== 7. 签名"
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
  --out "$ROOT/Schedule.apk" "$B/app.aligned.apk"

echo "== 8. 校验"
"$BT/apksigner" verify --print-certs "$ROOT/Schedule.apk" | head -4
ls -lh "$ROOT/Schedule.apk"

#!/bin/bash
set -e
SDK=/root/android-sdk
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-33/android.jar
SRC=/sandbox/workspace/app-src
B=/root/apkbuild

rm -rf $B && mkdir -p $B/gen $B/classes $B/dex
cd $SRC

echo "== 0.0 sync version into index.html"
VN=$(grep -oP 'android:versionName="\K[^"]+' AndroidManifest.xml | head -1)
VC=$(grep -oP 'android:versionCode="\K[^"]+' AndroidManifest.xml | head -1)
if [ -n "$VN" ] && [ -n "$VC" ]; then
  sed -i "s/var BUILD='[^']*'; var VCODE=[0-9]*;/var BUILD='$VN'; var VCODE=$VC;/" assets/www/index.html
  echo "   BUILD=$VN VCODE=$VC"
else
  echo "   !! 读不到 manifest 版本号"; exit 1
fi

echo "== 0. check id bindings"
python3 /sandbox/workspace/check_ids.py

echo "== 1. compile resources"
$BT/aapt2 compile --dir res -o $B/res.zip

echo "== 2. link"
$BT/aapt2 link -o $B/app.unsigned.apk \
  -I $PLATFORM \
  --manifest AndroidManifest.xml \
  -R $B/res.zip \
  --java $B/gen \
  -A assets \
  --min-sdk-version 21 --target-sdk-version 33 \
  --auto-add-overlay

echo "== 3. javac"
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $PLATFORM -classpath $PLATFORM \
  -d $B/classes \
  $B/gen/com/liu/dailycat/R.java \
  src/com/liu/dailycat/*.java 2>&1 | grep -v "^Note" || true
ls $B/classes/com/liu/dailycat/

echo "== 4. d8"
$BT/d8 --lib $PLATFORM --min-api 21 --output $B/dex $B/classes/com/liu/dailycat/*.class

echo "== 5. add dex"
cd $B/dex && zip -q $B/app.unsigned.apk classes.dex && cd $SRC

echo "== 6. zipalign"
$BT/zipalign -f 4 $B/app.unsigned.apk $B/app.aligned.apk

echo "== 7. keystore"
if [ ! -f /sandbox/workspace/我的日程签名.jks ]; then
  keytool -genkeypair -v -keystore /sandbox/workspace/我的日程签名.jks -alias dailycat \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass daily2026 -keypass daily2026 \
    -dname "CN=Liu Buyu, OU=Personal, O=Personal, L=Nanning, ST=Guangxi, C=CN" 2>&1 | tail -2
fi

echo "== 8. sign"
$BT/apksigner sign --ks /sandbox/workspace/我的日程签名.jks --ks-pass pass:daily2026 \
  --key-pass pass:daily2026 --ks-key-alias dailycat \
  --out /sandbox/workspace/我的日程.apk $B/app.aligned.apk

echo "== 9. verify"
$BT/apksigner verify --print-certs /sandbox/workspace/我的日程.apk | head -4
ls -lh /sandbox/workspace/我的日程.apk

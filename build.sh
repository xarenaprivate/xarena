#!/bin/bash
# usage: build.sh CODE VER   (builds build/out/XARENA.apk; needs keystore/xarena.keystore + keystore/pass)
set -e
cd /home/sandbox/xarena-app
BT=/home/sandbox/android/sdk/build-tools/34.0.0; AJ=/home/sandbox/android/sdk/platforms/android-34/android.jar
export JAVA_HOME=/home/sandbox/android/jdk17; export PATH=$JAVA_HOME/bin:$PATH
rm -rf build; mkdir -p build/classes build/out build/res
$BT/aapt2 compile --dir res -o build/res/res.zip
$BT/aapt2 link -o build/out/unsigned.apk -I $AJ --manifest AndroidManifest.xml -A assets --min-sdk-version 24 --target-sdk-version 34 --version-code $1 --version-name $2 -R build/res/res.zip --auto-add-overlay
javac -source 8 -target 8 -cp $AJ -d build/classes src/app/xarena/esports/*.java 2>&1 | grep -v "^Note\|warning" || true
$BT/d8 --release --min-api 24 --lib $AJ --output build/out build/classes/app/xarena/esports/*.class
cd build/out; zip -q -j unsigned.apk classes.dex; cd ../..
$BT/zipalign -f 4 build/out/unsigned.apk build/out/aligned.apk
$BT/apksigner sign --ks keystore/xarena.keystore --ks-pass file:keystore/pass --out build/out/XARENA.apk build/out/aligned.apk
ls -l build/out/XARENA.apk

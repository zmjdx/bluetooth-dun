#! /bin/bash

svnversion=`svnversion AndroidManifest.xml`

baseVersion='0.99'
OUT_FILE="res/values/myversion.xml";
versionCode=$svnversion
versionName=$baseVersion.$svnversion

echo '<?xml version="1.0" encoding="utf-8"?>' > $OUT_FILE
echo '<resources>'                             >> $OUT_FILE
echo '        <integer name="versionCode">'$versionCode'</integer>'   >> $OUT_FILE
echo '        <string name="versionName">'$versionName'</string>'   >> $OUT_FILE
echo '        <string name="build_at">'`date "+%d-%b-%Y %H:%M"`'</string>'   >> $OUT_FILE
echo '</resources>'  >> $OUT_FILE

sed -i "s/android:versionCode=\"[0-9a-zA-Z]*\"/android:versionCode=\"$versionCode\"/g" AndroidManifest.xml
sed -i "s/android:versionName=\"[0-9\.a-zA-Z]*\"/android:versionName=\"$versionName\"/g" AndroidManifest.xml






#!/usr/bin/env bash
set -uo pipefail
mkdir -p capturas
resultado=0
for perfil in celular tablet; do
  if [ "$perfil" = celular ]; then
    adb shell wm size 1080x1920
    adb shell wm density 480
  else
    adb shell wm size 1600x2560
    adb shell wm density 320
  fi
  ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.perfil="$perfil"
  codigo=$?
  if [ "$codigo" != 0 ]; then resultado=$codigo; fi
  adb pull /sdcard/Pictures/ChaveiroCarVisual/. capturas/ || resultado=1
  mkdir -p "capturas/relatorios-$perfil"
  cp -R app/build/reports/androidTests/. "capturas/relatorios-$perfil/"
done
exit "$resultado"

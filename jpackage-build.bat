@echo off
cd /d "%~dp0"

jpackage ^
  --input target ^
  --main-jar acj-firma-local-1.0-SNAPSHOT.jar ^
  --main-class com.acj.firma.acjfirmalocal.Launcher ^
  --name "ACJSignature" ^
  --type app-image ^
  --app-version 1.0 ^
  --vendor "ACJ Software" ^
  --icon icon.ico ^
  --runtime-image runtime

echo.
echo App-image generado en .\ACJSignature\ - empaquetarlo con installer\setup.iss (Inno Setup).
pause


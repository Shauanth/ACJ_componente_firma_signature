@echo off
cd /d "%~dp0"

jpackage ^
  --input target ^
  --main-jar acj-firma-local-1.0-SNAPSHOT.jar ^
  --main-class com.acj.firma.acjfirmalocal.Launcher ^
  --name "ACJ-Firma-Digital" ^
  --type exe ^
  --app-version 1.0 ^
  --vendor "ACJ Software" ^
  --icon icon.ico ^
  --runtime-image runtime ^
  --win-shortcut ^
  --win-menu ^
  --license-file licencia.txt ^
  --install-dir "C:\Program Files\ACJ-Firma-Digital"
  
echo.
echo Instalador generado exitosamente.
pause


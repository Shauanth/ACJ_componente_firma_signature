@echo off
setlocal
set "INSTALL_DIR=%~dp0"
set "INSTALL_DIR=%INSTALL_DIR:~0,-1%"

(
echo Windows Registry Editor Version 5.00
echo.
echo [HKEY_CLASSES_ROOT\acjfirma]
echo @="URL:ACJ Firma Protocol"
echo "URL Protocol"=""
echo.
echo [HKEY_CLASSES_ROOT\acjfirma\DefaultIcon]
echo @="\"%INSTALL_DIR%ACJ-Firma-Digital.exe\",1"
echo.
echo [HKEY_CLASSES_ROOT\acjfirma\shell]
echo.
echo [HKEY_CLASSES_ROOT\acjfirma\shell\open]
echo.
echo [HKEY_CLASSES_ROOT\acjfirma\shell\open\command]
echo @="\"%INSTALL_DIR%ACJ-Firma-Digital.exe\" \"%%1\""
) > "%TEMP%\acjfirma.reg"

reg import "%TEMP%\acjfirma.reg"

endlocal
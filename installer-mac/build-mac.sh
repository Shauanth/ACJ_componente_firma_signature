#!/bin/bash
# Pipeline de build para macOS. Equivalente exacto del flujo de Windows
# (mvn package -> jpackage --type app-image -> empaquetador nativo con
# postinstall), pero con las herramientas de macOS: pkgbuild en vez de Inno
# Setup, LaunchAgent en vez de Task Scheduler, CFBundleURLTypes en vez del
# Registro de Windows.
#
# Debe ejecutarse EN UNA MAC (jpackage genera instaladores nativos del SO en
# el que corre; no se puede cross-compilar un .pkg desde Windows/Linux).
# Requiere: JDK 17 completo (con jpackage) instalado en la Mac, y Maven.
set -e

cd "$(dirname "$0")/.."   # raíz del proyecto (ACJ Firma Local)

APP_NAME="ACJ-Signature-Agente"
BUNDLE_ID="com.acjfirma.agente"
VERSION="1.0"
MAIN_JAR="acj-firma-local-1.0-SNAPSHOT.jar"
MAIN_CLASS="com.acj.firma.acjfirmalocal.Launcher"

echo "== 1/5: Compilando (mvn package) =="
mvn -q -DskipTests package

echo "== 2/5: Generando el app-image con jpackage =="
rm -rf "$APP_NAME.app"
jpackage \
  --type app-image \
  --input target \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --name "$APP_NAME" \
  --app-version "$VERSION" \
  --vendor "ACJ Software" \
  --icon icon.icns \
  --mac-package-identifier "$BUNDLE_ID" \
  --mac-package-name "ACJ Signature Agente"

# Nota: no se pasa --runtime-image porque no hay un runtime jlink'eado para
# macOS en este repo (el de Windows solo sirve en Windows); jpackage arma uno
# automáticamente a partir del JDK que lo ejecuta. Si querés un instalador
# más liviano, generá el tuyo con `jlink` y agregá --runtime-image acá.

echo "== 3/5: Registrando el protocolo acjfirma:// en Info.plist =="
PLIST="$APP_NAME.app/Contents/Info.plist"
/usr/libexec/PlistBuddy -c "Delete :CFBundleURLTypes" "$PLIST" 2>/dev/null || true
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes array" "$PLIST"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLName string $BUNDLE_ID.protocolo" "$PLIST"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes array" "$PLIST"
/usr/libexec/PlistBuddy -c "Add :CFBundleURLTypes:0:CFBundleURLSchemes:0 string acjfirma" "$PLIST"

echo "== 4/5: Firmando el app-image (ad-hoc) =="
# Firma ad-hoc: evita el bloqueo más agresivo de Gatekeeper ("app dañada"),
# pero NO elimina la advertencia de "desarrollador no identificado" al abrir
# por primera vez. Para distribución real sin advertencias hace falta un
# certificado Developer ID + notarización (ver README-mac.md).
codesign --force --deep --sign - "$APP_NAME.app"

echo "== 5/5: Empaquetando .pkg (con LaunchAgent + registro automático) =="
STAGE_DIR="$(mktemp -d)"
mkdir -p "$STAGE_DIR/Applications"
cp -R "$APP_NAME.app" "$STAGE_DIR/Applications/"

chmod +x installer-mac/preinstall installer-mac/postinstall

pkgbuild \
  --root "$STAGE_DIR" \
  --identifier "$BUNDLE_ID" \
  --version "$VERSION" \
  --install-location "/" \
  --scripts installer-mac \
  "installer-mac/$APP_NAME-Setup.pkg"

rm -rf "$STAGE_DIR"

echo
echo "Listo: installer-mac/$APP_NAME-Setup.pkg"
echo "Copialo al frontend con:"
echo "  cp installer-mac/$APP_NAME-Setup.pkg ../acj-frt-signature/src/assets/software/$APP_NAME-Setup.pkg"

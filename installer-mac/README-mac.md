# ACJ Signature Agente — build para macOS

## La buena noticia primero

La lógica de firma (`libs/acj-libreria-firma-2.0.jar`, basada en el proyecto
DSS de la Unión Europea) **ya soporta macOS de forma nativa**: tiene
`AppleSignatureToken` que lee los certificados directamente del Keychain, y
`UtilWin.verifySO()` detecta el sistema operativo en tiempo de ejecución para
elegir automáticamente MSCAPI (Windows) o Keychain (Mac). El código Java
propio de este proyecto (`FirmaApplication`, `Launcher`, `FirmaController`,
`LocalHttpServer`, etc.) tampoco tiene nada específico de Windows. Todo lo
que faltaba era el empaquetado/instalador — que es lo que agrega esta carpeta.

## Qué se agregó

| Pieza | Windows (ya existía) | macOS (nuevo) |
|---|---|---|
| Empaquetado | `jpackage --type app-image` + Inno Setup | `jpackage --type app-image` + `pkgbuild` |
| Protocolo `acjfirma://` | Registro (`HKEY_CLASSES_ROOT`) | `CFBundleURLTypes` en `Info.plist` (auto-detectado por macOS, sin paso manual) |
| Autoarranque | Task Scheduler (`schtasks /sc onlogon`) | LaunchAgent (`~/Library/LaunchAgents/com.acjfirma.agente.plist`) |
| Icono | `icon.ico` | `icon.icns` (generado a partir de `acj-icon.png`, ya en la raíz del proyecto) |
| Desinstalador | Generado automático por Inno Setup | No existe en formato `.pkg` — se agregó `uninstall-mac.sh` manual |

Archivos nuevos en `installer-mac/`:
- `build-mac.sh` — pipeline completo (mvn → jpackage → firma ad-hoc → pkgbuild).
- `com.acjfirma.agente.plist` — LaunchAgent.
- `preinstall` / `postinstall` — scripts que corre `pkgbuild` (detienen la versión previa, registran e inician el LaunchAgent).
- `uninstall-mac.sh` — para que el usuario lo desinstale manualmente.
- `../.github/workflows/build-mac-agent.yml` — permite generar el `.pkg` en un runner de GitHub con macOS **sin necesitar una Mac física**, disparándolo a mano desde la pestaña Actions.

## Cómo generarlo

**Opción A — con una Mac real:**
```bash
cd "ACJ Firma Local"
chmod +x installer-mac/build-mac.sh installer-mac/preinstall installer-mac/postinstall
./installer-mac/build-mac.sh
```
Esto necesita un JDK 17 completo (con `jpackage`) instalado en la Mac. Al
terminar, copiá el resultado al frontend:
```bash
cp installer-mac/ACJ-Signature-Agente-Setup.pkg ../acj-frt-signature/src/assets/software/ACJ-Signature-Agente-Setup.pkg
```

**Opción B — sin Mac, vía GitHub Actions:**
Pusheá este repo a GitHub, andá a la pestaña *Actions* → *Build macOS agent
(.pkg)* → *Run workflow*. El runner de macOS de GitHub compila y empaqueta
todo, y queda descargable como artifact del run. Bajalo y copialo al mismo
lugar del frontend que en la Opción A.

## El punto que hay que decidir: firma de código y notarización

El script firma el `.app` de forma **ad-hoc** (`codesign --sign -`), lo cual
evita el error más agresivo de Gatekeeper ("la app está dañada y no se puede
abrir"). Pero sin un certificado **Developer ID Application** de Apple y sin
**notarización**, la primera vez que un usuario lo abra macOS le va a mostrar
"no se puede verificar el desarrollador" — el usuario puede abrirlo igual
desde *Configuración del Sistema → Privacidad y Seguridad → Abrir de todas
formas*, pero es una fricción real para usuarios no técnicos.

Para eliminar esa advertencia hace falta:
1. Una cuenta de **Apple Developer Program** (99 USD/año).
2. Un certificado "Developer ID Application" generado desde esa cuenta.
3. Firmar con ese certificado (`codesign --sign "Developer ID Application: ..."`)
   y notarizar con `xcrun notarytool submit ... --wait` antes de empaquetar
   el `.pkg`.

Si esto no es prioridad ahora mismo, el `.pkg` ad-hoc generado funciona
igual — solo con esa advertencia inicial una vez por usuario. Avisame si
querés que deje el script ya preparado con las variables de firma/notarización
cuando tengas la cuenta de desarrollador.

## Verificación rápida después de generarlo

En la Mac de prueba:
```bash
# Confirmar que se armó con natives de mac (no de Windows)
jar tf target/acj-firma-local-1.0-SNAPSHOT-shaded.jar | grep -i "\.dylib" | head

# Instalar y probar
open installer-mac/ACJ-Signature-Agente-Setup.pkg
launchctl list | grep acjfirma        # debe aparecer corriendo
open "acjfirma://verificar"           # debe traer la app al frente sin error
```

Nota sobre Apple Silicon vs Intel: el `pom.xml` no fija un `classifier` para
las dependencias de JavaFX — Maven elige automáticamente el jar nativo según
el sistema donde corre `mvn package` (incluye `mac-aarch64` para M1/M2/M3).
Si vas a distribuir a ambas arquitecturas, hay que correr el build en una Mac
de cada tipo (o en runners `macos-14` y `macos-13` del workflow) y publicar
los dos `.pkg`.

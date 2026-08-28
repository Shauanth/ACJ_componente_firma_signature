#!/bin/bash
# Desinstalador manual de ACJ Signature Agente.
#
# A diferencia de Windows (donde Inno Setup genera un uninstall.exe
# automáticamente), un instalador .pkg de macOS no crea un desinstalador:
# hay que borrar la app, el LaunchAgent y desregistrarlo a mano. Ejecutar
# con: bash uninstall-mac.sh

AGENT_PLIST="$HOME/Library/LaunchAgents/com.acjfirma.agente.plist"
USER_UID=$(id -u)

if [ -f "$AGENT_PLIST" ]; then
    echo "Deteniendo el agente..."
    launchctl bootout "gui/$USER_UID" "$AGENT_PLIST" 2>/dev/null
    launchctl unload "$AGENT_PLIST" 2>/dev/null
    rm -f "$AGENT_PLIST"
fi

if [ -d "/Applications/ACJ-Signature-Agente.app" ]; then
    echo "Eliminando la aplicación..."
    sudo rm -rf "/Applications/ACJ-Signature-Agente.app"
fi

echo "ACJ Signature Agente desinstalado."

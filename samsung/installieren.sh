#!/bin/bash
# OCMate auf einen Samsung-Fernseher installieren (macOS)
#
# Aufruf im Terminal:
#   bash installieren.sh <IP-des-Fernsehers> [Zertifikatsprofil]
# Beispiel:
#   bash installieren.sh 192.168.178.50 OCMate

set -e

TV_IP="$1"
PROFIL="${2:-OCMate}"
STUDIO="${TIZEN_STUDIO:-$HOME/tizen-studio}"
TIZEN="$STUDIO/tools/ide/bin/tizen"
SDB="$STUDIO/tools/sdb"
HIER="$(cd "$(dirname "$0")" && pwd)"
BAU="$HIER/build"
APP_ID="OCMateTV01.OCMate"

rot()  { printf "\033[31m%s\033[0m\n" "$1"; }
gruen(){ printf "\033[32m%s\033[0m\n" "$1"; }
schritt(){ printf "\n\033[1m▶ %s\033[0m\n" "$1"; }

if [ -z "$TV_IP" ]; then
  rot "Bitte die IP-Adresse des Fernsehers angeben."
  echo "Beispiel: bash installieren.sh 192.168.178.50 OCMate"
  exit 1
fi

if [ ! -x "$TIZEN" ] || [ ! -x "$SDB" ]; then
  rot "Tizen Studio wurde nicht unter $STUDIO gefunden."
  echo "Liegt es woanders, so aufrufen:  TIZEN_STUDIO=/Pfad/zu/tizen-studio bash installieren.sh $TV_IP $PROFIL"
  exit 1
fi

schritt "1/5  Verbindung zum Fernseher $TV_IP"
"$SDB" connect "$TV_IP" || true
SERIAL="$TV_IP:26101"
if ! "$SDB" devices | grep -q "$TV_IP"; then
  rot "Der Fernseher ist nicht erreichbar."
  echo "Prüfe: Fernseher an? Entwicklermodus an? Steht dort die IP-Adresse dieses Macs? Danach den Fernseher einmal aus- und einschalten."
  exit 1
fi
gruen "Verbunden."

schritt "2/5  App-Dateien vorbereiten"
rm -rf "$BAU"
mkdir -p "$BAU"
cp "$HIER"/app/* "$BAU"/
gruen "Fertig."

schritt "3/5  App mit deinem Zertifikat \"$PROFIL\" signieren"
if ! "$TIZEN" package -t wgt -s "$PROFIL" -- "$BAU"; then
  rot "Signieren fehlgeschlagen."
  echo "Vorhandene Zertifikatsprofile:"
  "$TIZEN" security-profiles list || true
  echo "Den richtigen Namen als zweiten Wert angeben, z. B.:  bash installieren.sh $TV_IP MeinProfil"
  exit 1
fi
WGT="$(cd "$BAU" && ls *.wgt | head -n 1)"
gruen "Paket erstellt: $WGT"

schritt "4/5  Installation auf dem Fernseher erlauben"
"$TIZEN" install-permit -s "$SERIAL" || echo "(Hinweis übersprungen – bei Tizen-Zertifikaten nicht nötig)"

schritt "5/5  OCMate installieren und starten"
"$TIZEN" install -n "$WGT" -s "$SERIAL" -- "$BAU"
"$TIZEN" run -p "$APP_ID" -s "$SERIAL" || true

gruen ""
gruen "Fertig! OCMate läuft jetzt auf dem Fernseher und liegt unter „Apps“."

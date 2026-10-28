# OCMate auf den Samsung-Fernseher – ohne Tizen Studio

Mit dem kostenlosen Programm **Apps2Samsung** (für Mac, iPhone und Android). Es installiert eigene Apps auf Samsung-Fernseher und erledigt das Zertifikat selbst.

Voraussetzung: Samsung Smart TV mit Tizen (ab 2015), Mac bzw. Handy im selben WLAN wie der Fernseher.

## 1. OCMate-Datei laden
Auf dem Mac (oder iPhone) herunterladen:

https://github.com/oender1973-cmd/ocmate/releases/latest/download/OCMate.wgt

## 2. Apps2Samsung installieren
- Download: https://github.com/Apps2Samsung/Apps2Samsung/releases (die neueste Version für **macOS** wählen)
- Falls macOS das Öffnen blockiert: **Systemeinstellungen → Datenschutz & Sicherheit → Trotzdem öffnen**.

## 3. Entwicklermodus am Fernseher einschalten
1. **IP-Adresse des Macs** notieren: Systemeinstellungen → WLAN → Details… → IP-Adresse (z. B. 192.168.178.20).
2. Am Fernseher die Übersicht **Apps** öffnen.
3. Auf der Fernbedienung **1 2 3 4 5** drücken (ohne Zifferntasten: Bildschirm-Zahlentastatur).
4. **Developer mode → On**, bei **Host PC IP** die IP-Adresse des Macs eintragen → OK.
5. Fernseher **neu starten** (Ein/Aus-Taste lang drücken).

Wenn du vom **iPhone** installierst, trägst du dort stattdessen die IP-Adresse des iPhones ein (Einstellungen → WLAN → ⓘ).

## 4. OCMate installieren
1. Apps2Samsung öffnen.
2. Fernseher auswählen (Netzwerk durchsuchen oder IP-Adresse des Fernsehers eingeben).
3. Als App die **eigene .wgt-Datei** wählen → **OCMate.wgt** aus Schritt 1.
4. **Installieren.** Beim ersten Mal meldest du dich mit einem (kostenlosen) **Samsung-Konto** an. Daraus wird das Zertifikat für deinen Fernseher erstellt.

OCMate liegt danach am Fernseher unter **Apps**.

## Updates
Neue `OCMate.wgt` über denselben Link laden und Schritt 4 wiederholen. Playlists und Einstellungen bleiben erhalten.

## Wenn etwas nicht klappt
| Problem | Lösung |
|---|---|
| Fernseher wird nicht gefunden | Entwicklermodus prüfen, richtige Host-PC-IP? Fernseher neu starten. |
| „Author certificate not match“ | OCMate am Fernseher deinstallieren und neu installieren. |
| Sender zeigt nur Ton oder schwarzes Bild | In OCMate **Einstellungen → Player → Web-Player** wählen. |

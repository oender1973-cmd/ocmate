# OCMate auf dem Samsung-Fernseher (Mac)

Samsung-Fernseher laufen mit Tizen, nicht mit Android. Deshalb wird OCMate hier einmalig vom Mac auf den Fernseher übertragen. Teil A machst du nur beim ersten Mal, Teil B bei jeder neuen Version.

Voraussetzung: Samsung Smart TV ab Baujahr 2017. Mac und Fernseher sind im selben WLAN bzw. Heimnetz.

---

## Teil A – Einmalig einrichten (ca. 30 Minuten)

### 1. Tizen Studio auf dem Mac installieren
1. Installationsprogramm für macOS laden: https://download.tizen.org/sdk/Installer/tizen-studio_6.1/
   Dort die Datei mit **„macos“** im Namen und der Endung **.dmg** wählen (bei mehreren: die mit „web-ide“).
2. Öffnen und installieren. Speicherort so lassen (`~/tizen-studio`).
3. Am Ende **„Launch the Package Manager“** angehakt lassen und **Finish** klicken.
4. Im Package Manager den Reiter **Extension SDK** öffnen und dort installieren:
   - **TV Extensions**
   - **Samsung Certificate Extension**
5. Lizenzen bestätigen und warten, bis alles installiert ist.

Falls macOS meldet, das Programm stamme von einem nicht verifizierten Entwickler: **Systemeinstellungen → Datenschutz & Sicherheit → Trotzdem öffnen**.

### 2. IP-Adresse des Macs herausfinden
**Systemeinstellungen → WLAN → (verbundenes Netz) Details…** → „IP-Adresse“ notieren, z. B. `192.168.178.20`.

### 3. Entwicklermodus am Fernseher einschalten
1. Am Fernseher die Übersicht **Apps** öffnen.
2. Auf der Fernbedienung nacheinander **1 2 3 4 5** drücken. Bei Fernbedienungen ohne Zifferntasten die Bildschirm-Zahlentastatur nutzen.
3. **Developer mode** auf **On** stellen.
4. Bei **Host PC IP** die IP-Adresse des Macs aus Schritt 2 eintragen → **OK**.
5. Fernseher **aus- und wieder einschalten** (Ein/Aus-Taste lang drücken, bis er neu startet).
6. Die IP-Adresse des **Fernsehers** notieren: **Einstellungen → Allgemein → Netzwerk → Netzwerkstatus → IP-Einstellungen**, z. B. `192.168.178.50`.

### 4. Fernseher mit Tizen Studio verbinden
1. Tizen Studio öffnen → Menü **Tools → Device Manager**.
2. Oben auf **Remote Device Manager** (Symbol mit Bildschirm) → **+** → Name „Fernseher“, IP-Adresse des Fernsehers, Port 26101 → **Add**.
3. Den Schalter **Connection** auf **On** stellen. Der Fernseher erscheint in der Liste.

### 5. Eigenes Samsung-Zertifikat erstellen
Das Zertifikat erlaubt, dass deine App auf genau deinem Fernseher läuft.
1. In Tizen Studio: **Tools → Certificate Manager** → **+**
2. **Samsung** wählen → Gerätetyp **TV** → **Next**
3. Profilname: **OCMate** → **Next**
4. **Create a new author certificate** → Name (z. B. „Caki“) und ein Passwort eingeben → **Next**
5. Mit deinem **Samsung-Konto** anmelden (oder kostenlos eines anlegen).
6. Beim Distributor-Zertifikat **Privilege: Public** lassen. Die Geräte-ID (DUID) des Fernsehers wird automatisch eingetragen, weil er verbunden ist → **Next** → anmelden → **Finish**.
7. Das Profil **OCMate** ist jetzt aktiv (Häkchen).

---

## Teil B – OCMate installieren (und bei jedem Update)

1. Den Ordner **OCMate-Samsung** entpacken, z. B. in „Downloads“.
2. **Terminal** öffnen (Spotlight: `Terminal`).
3. Diesen Befehl eingeben, mit der IP-Adresse **deines Fernsehers**:

   ```
   bash ~/Downloads/OCMate-Samsung/installieren.sh 192.168.178.50 OCMate
   ```

4. Das Skript verbindet sich mit dem Fernseher, signiert die App, installiert sie und startet sie. Am Ende steht **„Fertig!“**.

OCMate liegt danach am Fernseher unter **Apps** und lässt sich wie jede andere App starten. Playlists, Favoriten und Einstellungen bleiben bei Updates erhalten.

---

## Bedienung mit der Samsung-Fernbedienung

| Taste | Funktion |
|---|---|
| Pfeiltasten, OK | Bewegen, auswählen |
| OK auf laufenden Sender | Vollbild |
| Hoch/Runter im Vollbild, Kanal +/− | Umschalten |
| Ziffern | Kanalnummer direkt |
| Zurück | Vollbild/Fenster schließen, zuletzt App beenden |
| Guide | TV-Guide |
| Info | Vollbild bzw. Programm-Einblendung |
| PRE-CH | Vorheriger Sender |

## Wenn etwas nicht klappt

| Meldung | Lösung |
|---|---|
| „Der Fernseher ist nicht erreichbar“ | Entwicklermodus prüfen, Mac-IP am Fernseher richtig? Fernseher neu starten. |
| „Signieren fehlgeschlagen“ | Profilname prüfen; das Skript zeigt die vorhandenen Namen an. |
| Installation bricht mit „certificate“ ab | Zertifikat neu erstellen, während der Fernseher verbunden ist (Teil A, Schritt 5). |
| Sender zeigt nur Ton oder schwarzes Bild | In OCMate **Einstellungen → Player → Web-Player** wählen. |
| Fernseher wird nach einiger Zeit nicht mehr gefunden | Entwicklermodus prüfen (Teil A, Schritt 3) und ggf. neu einschalten. |

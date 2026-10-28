# OCMate – IPTV-App für Android TV, Google TV und Fire TV

Dieses Projekt erzeugt die installierbare App **OCMate.apk**. Du brauchst dafür kein Android Studio. GitHub baut die App kostenlos für dich.

## 1. App bauen lassen (einmalig, am PC oder Handy-Browser)

1. Auf **github.com** anmelden oder kostenlos registrieren.
2. Oben rechts **+ → New repository**:
   - Name: `ocmate`
   - Sichtbarkeit: **Public** (nötig, damit der Fernseher die APK ohne Login herunterladen kann; deine Zugangsdaten landen nie im Projekt)
   - **Create repository**
3. Im neuen Repo auf **uploading an existing file** klicken und den **Inhalt** dieses Ordners hineinziehen (alle Dateien und Ordner, auch `.github`). Danach **Commit changes**.
   - Tipp: Am PC den entpackten Ordner öffnen, alles markieren (Strg+A) und ins Browserfenster ziehen. Versteckte Ordner wie `.github` vorher sichtbar machen.
4. Oben auf **Actions** klicken. Der Lauf „OCMate APK bauen“ startet automatisch und dauert etwa 3–5 Minuten. Grüner Haken = fertig.
5. Rechts unter **Releases** liegt jetzt **OCMate.apk**.

Dein Download-Link lautet dann:

```
https://github.com/DEIN-NAME/ocmate/releases/latest/download/OCMate.apk
```

## 2. Auf dem Fernseher installieren

1. **Downloader** erlauben, Apps zu installieren:
   - **Google TV / Android TV:** Einstellungen → Apps → Sicherheit und Einschränkungen → Unbekannte Quellen → Downloader einschalten
   - **Fire TV:** Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft → Downloader einschalten
2. In Downloader den Link von oben eingeben (mit `https://` vorne).
   - Der Link ist lang. Kürzer geht es mit einem Kurzlink-Dienst (z. B. is.gd) am Handy.
3. **Installieren** → **Öffnen**. OCMate erscheint danach auf dem Startbildschirm.

## 3. Einrichten

In OCMate auf **Einstellungen → Playlist hinzufügen**:

- **Xtream Codes:** Server, Benutzername, Passwort. Format **m3u8** lassen.
- **M3U-Link:** die Playlist-Adresse deines Anbieters.

## Fernbedienung

| Taste | Funktion |
|---|---|
| Pfeiltasten | Bewegen |
| OK | Auswählen / Abspielen |
| Kanal + / − | Umschalten |
| Ziffern | Kanalnummer direkt |
| Zurück | Schließen, zurück zu Live TV, zuletzt App beenden |
| Guide-Taste | TV-Guide |

## Updates

Neue Version von `app/src/main/assets/ocmate.html` hochladen → GitHub baut automatisch neu. Auf dem Fernseher denselben Link in Downloader erneut öffnen und über die alte App installieren. Favoriten und Playlists bleiben erhalten.

## Grenzen

- Streams im Format MPEG-TS (`.ts`) spielt die App nicht. Bei Xtream deshalb **m3u8** wählen.
- Aufnahmen auf Speicher gibt es nicht. Catch-up funktioniert, wenn dein Anbieter Archive hat.

## Technik

- Kotlin, WebView, Android 5.0+ (minSdk 21), Leanback-Starter für TV
- Netzwerk-Anfragen laufen über die App selbst. Dadurch funktionieren http-Streams und Server, die Browser blockieren (CORS).
- Der Signaturschlüssel `app/ocmate.keystore` liegt bewusst im Projekt, damit jede neue Version sich über die alte installieren lässt. Für eine Veröffentlichung im Play Store bräuchte es einen eigenen, geheimen Schlüssel.

## Samsung-Fernseher

Samsung-Fernseher (Tizen) können keine APK installieren. Die Samsung-Version liegt im Ordner `samsung/`.

- Einfach (ohne Tizen Studio): `samsung/ANLEITUNG-APPS2SAMSUNG.md` – Paket: https://github.com/oender1973-cmd/ocmate/releases/latest/download/OCMate.wgt
- Mit Tizen Studio: `samsung/ANLEITUNG.md`

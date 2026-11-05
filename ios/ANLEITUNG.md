# OCMate auf dem iPhone

Ohne App Store: Du installierst OCMate mit dem kostenlosen Mac-Programm **Sideloadly** und deiner Apple-ID.

**Gut zu wissen**
- Mit einer kostenlosen Apple-ID läuft die App **7 Tage**. Danach einfach Teil B wiederholen (2 Minuten). Mit dem Apple-Entwicklerprogramm (99 € pro Jahr) gilt sie 1 Jahr.
- Kostenlose Apple-IDs erlauben höchstens 3 selbst installierte Apps.
- Das iPhone spielt nur **HLS-Streams (m3u8)**. Bei Xtream ist das die Standardeinstellung.

---

## Teil A – Einmalig vorbereiten

### 1. Sideloadly auf dem Mac installieren
https://sideloadly.io → Version für macOS laden und installieren.
Falls macOS das Öffnen blockiert: **Systemeinstellungen → Datenschutz & Sicherheit → Trotzdem öffnen**.

### 2. Entwicklermodus am iPhone einschalten
Erst nach dem ersten Installationsversuch (Teil B) sichtbar:
**Einstellungen → Datenschutz & Sicherheit → Entwicklermodus → Ein** → iPhone startet neu → **Einschalten** bestätigen.

---

## Teil B – OCMate installieren (und alle 7 Tage erneut)

1. OCMate auf den Mac laden:
   https://github.com/oender1973-cmd/ocmate/releases/latest/download/OCMate.ipa
2. iPhone per **Kabel** an den Mac anschließen und am iPhone **„Vertrauen“** tippen.
3. **Sideloadly** öffnen:
   - Oben wird dein iPhone angezeigt.
   - Die Datei **OCMate.ipa** in das Fenster ziehen.
   - Bei **Apple account** deine Apple-ID eintragen.
   - **Start** klicken und das Apple-ID-Passwort eingeben (bei Zwei-Faktor-Schutz auch den Code vom iPhone).
4. Am iPhone einmalig dem Entwickler vertrauen:
   **Einstellungen → Allgemein → VPN und Geräteverwaltung → (deine Apple-ID) → Vertrauen**.
5. OCMate auf dem Home-Bildschirm öffnen.

Bei der Neuinstallation nach 7 Tagen dieselbe Apple-ID verwenden. Dann wird die App überschrieben, und deine Playlists bleiben in der Regel erhalten.

---

## Bedienung

| Aktion | So geht's |
|---|---|
| Sender wählen | Gruppe antippen (oben), dann Sender antippen |
| Vollbild | Laufenden Sender nochmal antippen oder Vollbild-Taste – iPhone quer drehen |
| Bild-in-Bild | Bild-in-Bild-Taste unter dem Video |
| Favorit | Stern-Taste unter dem Video |

## Wenn etwas nicht klappt

| Problem | Lösung |
|---|---|
| App startet nicht / „Nicht vertrauenswürdiger Entwickler“ | Teil B, Schritt 4 |
| „Entwicklermodus erforderlich“ | Teil A, Schritt 2 |
| App geht nach einer Woche nicht mehr | Normal bei kostenloser Apple-ID → Teil B wiederholen |
| Sender: „Videoformat nicht unterstützt“ | Der Sender liefert nur MPEG-TS. Am iPhone geht nur m3u8. |

---

## Für Entwickler: selbst in Xcode bauen

```
brew install xcodegen
cd ios
cp ../app/src/main/assets/{ocmate.html,hls.min.js,mpegts.js} web/
xcodegen generate
open OCMate.xcodeproj
```
In Xcode unter **Signing & Capabilities** dein Team wählen und auf dem angeschlossenen iPhone starten.

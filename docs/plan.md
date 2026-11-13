# Parakeet — commit, analysis, UX, brand SVG, modernization roadmap

## Context
Parakeet (com.ilsecondodasinistra.parakeet, Play Store, last release v3.0 / versionCode 30, 2017) is a "wake-up calculator":
user keeps a list of morning activities with durations; in the evening picks the departure time and ticks today's
activities; app computes wake-up time = departure − Σ durations and sets an alarm (by delegating to the system Clock app)
plus an optional "Ora di uscire!" notification at departure time.
This round: commit the work that made the old project build and run (branch `modernize-build`), save project knowledge to
memory, write analysis docs (features, instant app, Play publishing 2026, UX + onboarding, roadmap), vectorize the brand
(icon + lettering). The Kotlin/Compose rewrite is the NEXT step, after the user reviews the docs.

User decisions (already taken):
- Alarm model, **two releases**:
  - **v1 (4.0)**: alarm via system Clock intent (`ACTION_SET_ALARM`, `SET_ALARM` only) + exact exit notification. No restricted Play permissions.
  - **v2 (4.x/5.0)**: adds in-app ringing alarm as user option, with full permission flow and Play declarations (`USE_EXACT_ALARM`, FSI, FGS `systemExempted`).
- Original 2017 **keystore available** → publish as update of same listing (versionCode > 30).
- Exit notification: **exact**, via `SCHEDULE_EXACT_ALARM` asked to user, fallback inexact.
- minSdk **29**; **single :app module + Hilt**; **migrate** v3.0 user data once.
- Docs as **Markdown in `docs/`** (Italian); lettering **traced from PNG** (potrace).
- Emulator: use already-running **Pixel_9a (emulator-5554)**.

## Step 1 — Finish & commit "make it run"
- Bump `myTargetSdkVersion`/`myCompileSdkVersion` to **36** in `gradle.properties` (Play requirement since 31 Aug 2026; android-36 platform installed).
- `git rm -r parakeet-instantapp` (already excluded from `settings.gradle`); remove empty `instant app data/` dir and `parakeetapk/release/instant-app.json`.
- `./gradlew :parakeetapk:assembleDebug`, install + launch on emulator-5554, check logcat for FATAL, screenshot.
- Shut down the Pixel_6a emulator I started (emulator-5556).
- Commit on `modernize-build` (exclude `.idea/`). Message e.g. "Make legacy project build and run on modern toolchain" —
  body: AGP 8.9.3/Gradle 8.13, AndroidX, instant app removed, feature modules → libraries, FLAG_IMMUTABLE, exported,
  targetSdk 36. Ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Step 2 — Save memory (`~/.claude/projects/-Users-marco-projects-my-parakeet/memory/`)
One fact per file + MEMORY.md index lines:
- `project-parakeet-purpose` (project): what the app does, Play URL, v3.0/2017, 1000+ installs, IT/EN.
- `project-parakeet-brand` (project): asset paths (Dropbox `app/grafiche/parakeet_solo_sveglia.xcf|png`, `_bigger.png` 512, `parakeet.png` lettering 800x202, cover 1024x500, WP uploads 2013/10), palette, digital-7 LCD font; user wants to keep icon + colors.
- `project-parakeet-modernization` (project): decisions above, branch `modernize-build`, rewrite = next step, docs in `docs/`.
- `project-parakeet-release-strategy` (project): v1 = Clock intent + exact exit notification, no restricted perms; v2 = in-app alarm + Play declarations; 2017 keystore still owned → update same listing.
- `project-parakeet-ux-direction` (project): UX/onboarding direction agreed (summary of Step 4), link [[project-parakeet-modernization]].
- `reference-parakeet-emulators` (reference): use Pixel_9a emulator-5554; don't touch other AVDs.
- `user-marco-working-style` (user): writes in Italian; wants to be questioned until 99% sure before work.

## Step 3 — `docs/analysis.md`
1. **Feature già implementate** (from code):
   - Lista attività (nome, durata min, checkbox) con default IT/EN (`ParakeetConfig.getNewListOfThingsToDo`).
   - Aggiungi / modifica via dialog (`row_add_layout`, `row_modify_layout`); swipe-to-delete con undo (`EnhancedListView`).
   - "Ora di uscita" via TimePicker (oggi/domani automatico) e calcolo live ora sveglia in display LCD (font digital-7).
   - "Punta sveglia": `AlarmClock.ACTION_SET_ALARM` → app Orologio (label "New Alarm", nuova sveglia ogni volta → duplicati).
   - Notifica "Ora di uscire!" a orario di uscita (AlarmManager inexact → `NotificationService`), testo personalizzabile, on/off da impostazioni; notifiche cancellate all'apertura.
   - Drawer: punta sveglia, imposta uscita, reset testo notifica, impostazioni, info, aiuto (no-op).
   - Impostazioni (template PreferenceActivity: usate solo `notification_on_exit`, `exit_noti_text`; resto boilerplate morto).
   - Dialog "What's new" per versione; About; persistenza via Java serialization in SharedPreferences.
   - Tutorial ShowcaseView (codice commentato); `OnBootReceiver`/`OnAlarmReceiver` non registrati (codice morto).
   - Bug noti: DAY_OF_MONTH+1 a fine mese, `Date` deprecated, `MyApplication` vuota, costruttore finto `ParakeetConfig()`.
2. **Instant app**: cosa faceva (nov 2017: base feature + about feature, app links su `parakeet-instantapp.000webhostapp.com` /open /about /settings, bottone "Installa" in About via API `InstantApps`; commit "I wish it worked as Instant App, it did not"). Verdetto: **non ha più senso** — Google Play Instant dismesso dic. 2025, API non funzionano più, dominio 000webhost chiuso. Rimuovere moduli, app links, `asset_statements`.
3. **Pubblicare un'app sveglia nel 2026**: targetSdk 36 obbligatorio; `USE_EXACT_ALARM` (solo app sveglia/timer/calendario, dichiarazione Play) vs `SCHEDULE_EXACT_ALARM` (concessa dall'utente, `canScheduleExactAlarms`, broadcast cambio stato); `setAlarmClock()`; `USE_FULL_SCREEN_INTENT` (auto-concesso solo a sveglia/chiamate, dichiarazione Play Console); `POST_NOTIFICATIONS` runtime; foreground service type + dichiarazione per suonare; `RECEIVE_BOOT_COMPLETED` + reschedule; Doze/batterie OEM; Data safety, privacy policy, verifica account sviluppatore. Tabella per le due modalità (delega a Orologio = solo `SET_ALARM`; sveglia interna = `USE_EXACT_ALARM` + FSI + FGS). Con link alle fonti.
   Dettagli da includere: pattern manifest `SCHEDULE_EXACT_ALARM maxSdkVersion=32` + `USE_EXACT_ALARM`; FGS type `systemExempted` (consentito ad app con exact alarm per far suonare la sveglia); `<queries>` per `ACTION_SET_ALARM`; `USAGE_ALARM`, `CATEGORY_ALARM`, `showWhenLocked`/`turnScreenOn`; reschedule su BOOT/TIME_SET/TIMEZONE_CHANGED; checklist Play Console (App content: dichiarazioni exact alarm, FSI, FGS con video; Data safety; privacy policy; rating); **chiave di firma 2017 / Play App Signing** necessaria per pubblicare come aggiornamento.
4. **Roadmap rewrite** — due release, fasi ordinate con stima:

   **Versione 1 (4.0) — rewrite + sveglia via Orologio di sistema**
   - Fondamenta: Kotlin 2.x, version catalog, Compose + Material 3 (tema verde/lime, LCD digital-7), single :app + Hilt, MVVM/StateFlow, Room (attività/routine) + DataStore (impostazioni), java.time, minSdk 29, targetSdk 36, edge-to-edge.
   - Dominio: calcolo sveglia puro e testato (oggi/domani esplicito, totale preparativi).
   - Interfaccia `AlarmScheduler` con sola impl `ClockAppScheduler`: `ACTION_SET_ALARM` + `EXTRA_SKIP_UI`, label "Parakeet", `ACTION_DISMISS_ALARM` best-effort per evitare duplicati, `<queries>` nel manifest, fallback se nessuna app Orologio.
   - Notifica uscita: canale dedicato, `POST_NOTIFICATIONS` in contesto, `SCHEDULE_EXACT_ALARM` richiesto all'utente con spiegazione + fallback `setAndAllowWhileIdle`, receiver `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, reschedule su BOOT/TIME_SET/TIMEZONE_CHANGED.
   - Nuova UX e onboarding (Step 4) senza la scelta della modalità sveglia.
   - Migrazione dati v3.0 (deserializza lista `ThingToDo` dalle prefs di `ParakeetMain`), schermata "Novità" per chi aggiorna.
   - Adaptive + themed icon dagli SVG; test unit + UI Compose; release firmata con keystore 2017, versionCode > 30.
   - Play: Data safety (nessun dato), privacy policy, scheda store aggiornata. Nessuna dichiarazione permessi ristretti.

   **Versione 2 (5.0) — sveglia in-app**
   - Impostazione "Modalità sveglia": Orologio di sistema (default) / Suoneria Parakeet; step onboarding "Come vuoi essere svegliato?" (solo per nuovi utenti o come scheda novità).
   - `InternalAlarmScheduler`: `setAlarmClock()`; manifest `SCHEDULE_EXACT_ALARM maxSdkVersion=32` + `USE_EXACT_ALARM`.
   - Suoneria: FGS tipo `systemExempted` (`FOREGROUND_SERVICE_SYSTEM_EXEMPTED`), `AudioAttributes.USAGE_ALARM`, scelta suoneria, vibrazione, volume crescente, snooze/stop, notifica `CATEGORY_ALARM`.
   - Schermata che suona sopra il lock screen: `USE_FULL_SCREEN_INTENT` + check `canUseFullScreenIntent()` → `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, fallback heads-up; activity `showWhenLocked`/`turnScreenOn` in Compose.
   - Affidabilità: `directBootAware` + `LOCKED_BOOT_COMPLETED`, test su Doze (`adb shell dumpsys deviceidle force-idle`).
   - Play: dichiarazioni exact alarm, full-screen intent, FGS (con video); scheda store che presenta la sveglia come funzione centrale.

## Step 4 — `docs/ux.md` (UX analysis + new flow + onboarding)
**Cosa funziona (tenere)**: risultato (ora sveglia) in alto grande con LCD = cuore del brand; concetto uscita − attività = sveglia immediato; swipe-to-delete con undo.

**Problemi attuali**
1. Calcolo invisibile: niente totale attività ("1h15 di preparativi") né giorno ("domani, ven 14"); oggi/domani deciso implicitamente (bug fine mese).
2. Primo avvio: uscita 0:00 → calcolo assurdo, mascherato da hint lungo che si sovrappone alla status bar.
3. Pulsanti solo-icona (sveglia, lista+) ambigui; azione principale "Punta sveglia" stesso peso di "aggiungi".
4. Punta sveglia apre app Orologio: duplicati "New Alarm", serve back manuale, nessuna conferma né stato visibile.
5. Righe: checkbox piccola; tap riga = modifica mentre l'azione frequente è includere/escludere; durate in LCD poco leggibili e senza unità; icona delete per riga = rumore.
6. Dialog modifica: titolo troncato, durata solo da tastiera.
7. Drawer inutile per app a schermata singola: duplica azioni, "reset testo notifica" fuori posto, "Aiuto" no-op.
8. Impostazioni: quasi tutte boilerplate morto.
9. "What's new" mostrato anche a nuove installazioni.
10. Accessibilità: contrasto lime su verde chiaro, niente contentDescription, LCD usato dove serve leggibilità.
11. Lista default troppo personale ("cane", "porta fuori bici", "slavazzamento") → solo suggerimenti.

**Nuovo flusso (schermata unica, dall'alto in basso)**
```
┌──────────────────────────────┐
│  SVEGLIA          06:45 (LCD)│  card principale: risultato
│  domani, ven 14              │
│  Uscita 08:00 ✎ · 1h15 prep. │  input tappabili + calcolo visibile
├──────────────────────────────┤
│ ☑ Doccia               15 min│  tap riga = includi/escludi
│ ☑ Colazione            20 min│  long-press/icona = modifica
│ ☐ Barba                10 min│  swipe = elimina + snackbar annulla
│ + Aggiungi attività          │
├──────────────────────────────┤
│ [  ⏰ Punta sveglia 06:45  ]  │  CTA fissa in basso, con testo
└──────────────────────────────┘
dopo: "Sveglia puntata · notifica uscita 8:00 · Annulla"
```
- Sveglia creata silenziosamente (`EXTRA_SKIP_UI`, label "Parakeet", no duplicati) o da motore interno se scelto.
- Stato sveglia attiva sempre visibile, annullabile.
- Via drawer → Impostazioni / Info nel menu top app bar. Impostazioni ridotte a: modalità sveglia, notifica uscita on/off, testo notifica (con anteprima).
- Durate con chip rapidi (5/10/15/20/30) + valore libero; LCD solo per orari principali.
- Idee extra (da valutare, non obbligatorie): timeline del mattino (6:45 doccia → 7:00 colazione → … → 8:00 esci); routine salvate (feriale/weekend/palestra); promemoria serale opzionale "Hai puntato la sveglia?".

**Onboarding**
- Vecchio: tour ShowcaseView 4 step (coach marks) + "What's new". Spiega la UI, non fa fare nulla → spesso saltato. Eliminare.
- Nuovo: onboarding breve che raccoglie dati veri → a fine flusso c'è già una sveglia calcolata ("aha"):
  1. Benvenuto: icona + "Dimmi quando esci, ti dico quando svegliarti" → Inizia.
  2. "A che ora esci di solito?" time picker (default 8:00) → uscita predefinita.
  3. "Cosa fai la mattina?" chip suggerimenti con durate modificabili + aggiungi; anteprima live "Sveglia alle 6:50".
  4. v1: "Avvisami quando è ora di uscire?" → permessi notifiche + sveglie esatte chiesti qui, in contesto, con spiegazione e fallback.
     v2: in più "Come vuoi essere svegliato?" Orologio di sistema / suoneria Parakeet → schermo intero chiesto solo se sceglie suoneria interna.
- Ogni step saltabile con default sensati.
- Dopo: hint contestuali una tantum (swipe) + empty state chiaro invece del tour.
- Utenti v3.0 in aggiornamento: migrazione dati, niente onboarding, una schermata "Novità". "What's new" solo su update, mai su prima installazione.

## Step 5 — Brand SVG (`docs/brand/`)
- Sample exact colors from `parakeet_solo_sveglia_bigger.png` and `parakeet.png` with `magick` (dark green ~#2f4010/#1e290a, lime #a9c606, gloss white α, yellow→orange gradient).
- **Icon** `parakeet_icon.svg`: hand-built geometry (outer dark ring, 2 ears, lime dial, hands + center dot, gloss ellipse with gradient). Use xcf layers (ears 61x57+3+2 and 73x57+134+1 on 200x200) for proportions. Render at 512px with inkscape/magick, compare side-by-side with original, iterate until equivalent.
- **Lettering** `parakeet_logo.svg`: `brew install potrace`; isolate color layers of `parakeet.png` (upscaled ×4): dark outline, yellow fill; trace each; recompose with linear gradient yellow→orange; reuse icon SVG for clock part. Compare render vs PNG.
- Android-ready exports in `docs/brand/android/`: `ic_launcher_foreground` vector drawable, background color, monochrome layer (wired into app during rewrite).

## Step 6 — Copy plan into repo
- Copy this file to `docs/plan.md`.

## Verification
- Build passes; app launches on emulator-5554 without FATAL in logcat; screenshot shown.
- `git log -1 --stat` shows commit on `modernize-build`, `.idea` excluded.
- Memory files exist and are indexed in MEMORY.md.
- `docs/analysis.md`, `docs/ux.md`, `docs/plan.md` present and complete (analysis with sources).
- SVGs render (inkscape/magick → PNG); side-by-side comparison images shown to user.

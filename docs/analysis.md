# Parakeet — analisi del progetto (novembre 2026)

## 1. Cos'è
App "sveglia intelligente": l'utente definisce attività mattutine con durata (doccia 20', colazione 20'…),
la sera sceglie l'ora di uscita e le attività da fare; l'app calcola **ora sveglia = uscita − Σ durate**
e punta la sveglia. Opzionalmente avvisa con una notifica all'ora di uscita.
Play Store: `com.ilsecondodasinistra.parakeet` — ultima release v3.0 (versionCode 30, nov 2017), oltre 1000 installazioni, IT/EN.

## 2. Feature già implementate (dal codice)
| Feature | Dove | Note |
|---|---|---|
| Lista attività con nome, durata (min), checkbox | `ParakeetMain`, `CustomAdapter`, `ThingToDo` | lista di default IT/EN in `ParakeetConfig.getNewListOfThingsToDo` |
| Aggiungi / modifica attività | dialog `row_add_layout`, `row_modify_layout` | durata solo da tastiera |
| Elimina con swipe + annulla | `EnhancedListView` (libreria incorporata, 910 righe) | |
| Scelta "Ora di uscita" | `TimePickerDialog` in `ParakeetMain.chooseTime` | oggi/domani deciso in automatico |
| Calcolo live ora sveglia, display LCD | `calculateWakeUpTime`, font `digital-7` | |
| "Punta sveglia" | `AlarmClock.ACTION_SET_ALARM` verso l'app Orologio | label fissa "New Alarm", una nuova sveglia a ogni tap → duplicati |
| Notifica "Ora di uscire!" | `AlarmManager.set` → `NotificationService` | testo personalizzabile, on/off da impostazioni; cancellata all'apertura app |
| Navigation drawer | `DrawerLayoutHelper` | punta sveglia, ora uscita, reset testo notifica, impostazioni, info, aiuto (non fa nulla) |
| Impostazioni | `SettingsActivity` (template Studio) | usate solo `notification_on_exit` e `exit_noti_text`; sync/reset notifiche sono boilerplate morto |
| "What's new" per versione | `WhatsNewScreen` | mostrato anche alle nuove installazioni |
| Info / About | `AboutActivity` | |
| Persistenza | `ObjectSerializer` (Java serialization in SharedPreferences) | fragile, da migrare |
| Tutorial ShowcaseView | `ParakeetMain` (commentato) | disattivo |
| `OnBootReceiver` / `OnAlarmReceiver` | — | **non registrati nel manifest**: codice morto |

Bug / debito tecnico noto: `DAY_OF_MONTH + 1` sbaglia a fine mese; uso di `Date.getHours()` deprecato; `MyApplication` vuota (residuo ACRA);
`ParakeetConfig()` è un metodo, non un costruttore; analytics rimosse ma risorse restano; costruttore `ActionBarDrawerToggle` con icona perso nel porting.

## 3. Il modulo Instant App
**Cosa faceva** (commit nov 2017, "Splitted app in modules", "I wish it worked as Instant App, it did not"):
- `parakeet-base` come *base feature*, `aboutmodule` come feature, `parakeet-instantapp` come bundle, `parakeetapk` come APK installabile;
- app links su `parakeet-instantapp.000webhostapp.com` (`/open`, `/about`, `/settings`) con `asset_statements`;
- nella schermata Info un bottone "Installa" (API `InstantApps.showInstallPrompt`) visibile solo se in esecuzione come Instant App.

**Ha ancora senso? No.** Google ha dismesso Play Instant: da dicembre 2025 non si possono più pubblicare Instant App e le API di Play services non funzionano.
Il dominio 000webhost è inoltre andato. Rimossi dal progetto: moduli instantapp, app links, `InstantApps`, `targetSandboxVersion`.
Da togliere anche in seguito: `asset_statements` e il bottone "Installa".

## 4. Pubblicare un'app sveglia nel 2026

### Requisiti generali
- **targetSdk 36** (Android 16) per nuove app e aggiornamenti dal 31 agosto 2026 (proroga a 1 novembre 2026 già scaduta).
- Firma: pubblicare come aggiornamento richiede la stessa chiave del 2017 (o reset upload key se l'app usa Play App Signing). **Il keystore è disponibile.**
- Play Console → *App content*: Data safety, privacy policy (URL), classificazione contenuti, pubblico, annunci, dichiarazioni permessi ristretti.

### Modalità A — sveglia nell'app Orologio (v1)
| Cosa | Permesso | Play |
|---|---|---|
| Creare sveglia nell'Orologio | `com.android.alarm.permission.SET_ALARM` (normale) | nessuna dichiarazione |
| Trovare l'app Orologio (Android 11+) | `<queries>` con intent `ACTION_SET_ALARM` | — |
| Notifica "ora di uscire" | `POST_NOTIFICATIONS` (runtime, Android 13+) | — |
| Notifica puntuale | `SCHEDULE_EXACT_ALARM`: non concesso di default (target 33+), l'utente lo attiva da "Sveglie e promemoria" (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) | nessuna dichiarazione |

Note: controllare `canScheduleExactAlarms()` prima di ogni schedule; fallback `setAndAllowWhileIdle`; ascoltare
`ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (revoca ⇒ il sistema cancella gli allarmi esatti); riprogrammare su `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`.
Con `EXTRA_SKIP_UI` la sveglia si crea senza aprire l'Orologio; i produttori (Samsung, Xiaomi…) possono ignorare alcuni extra.

### Modalità B — sveglia che suona nell'app (v2)
Parakeet diventa un'app sveglia a tutti gli effetti:
1. **Allarme esatto non revocabile**
   ```xml
   <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32"/>
   <uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
   ```
   `USE_EXACT_ALARM` è **ristretto** su Play: ammesso solo se la funzione principale è sveglia/timer/calendario; richiede dichiarazione in Console.
   Programmare con `AlarmManager.setAlarmClock()` (mai ritardata, esce dal Doze, icona sveglia in status bar).
2. **Schermata sopra il blocco schermo**: `USE_FULL_SCREEN_INTENT`. Da Android 14 è auto-concesso solo ad app sveglia/chiamate; dichiarazione Play obbligatoria.
   Runtime: `canUseFullScreenIntent()` → altrimenti `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`, con ripiego su notifica heads-up. Activity con `showWhenLocked`/`turnScreenOn`, notifica `CATEGORY_ALARM`, canale importanza alta.
3. **Suono prolungato**: foreground service tipo `systemExempted` (`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`) — dichiarazione tipi FGS in Console con video. Audio con `AudioAttributes.USAGE_ALARM`; `VIBRATE`.
4. **Affidabilità**: `RECEIVE_BOOT_COMPLETED`; facoltativo `directBootAware` + `LOCKED_BOOT_COMPLETED`; test in Doze (`adb shell dumpsys deviceidle force-idle`). Evitare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (sorvegliato da Play).
5. **Scheda store**: descrizione e screenshot devono mostrare che la sveglia è la funzione centrale, altrimenti rischio rifiuto di `USE_EXACT_ALARM`/FSI.

### Checklist Play Console
Target API 36 · firma con keystore 2017 · exact alarm (solo B) · full-screen intent (solo B) · FGS + video (solo B) · Data safety ("nessun dato raccolto" se tutto resta locale) · privacy policy · classificazione contenuti · verifica account sviluppatore.
I requisiti di test chiuso (12 tester/14 giorni) valgono solo per account personali creati dopo novembre 2023: verificare nella Console.

### Fonti
- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://support.google.com/googleplay/android-developer/answer/17517561
- https://support.google.com/googleplay/android-developer/answer/13392821
- https://source.android.com/docs/core/permissions/fsi-limits
- https://developer.android.com/google/play/requirements/target-sdk

## 5. Roadmap
### Versione 1 (4.0) — riscrittura + sveglia via Orologio
1. Fondamenta: Kotlin 2.x, version catalog, Compose + Material 3 (tema verde/lime, LCD digital-7), modulo `:app` + Hilt, MVVM/StateFlow, Room + DataStore, `java.time`, minSdk 29, targetSdk 36, edge-to-edge.
2. Dominio: calcolo sveglia puro e testato (oggi/domani esplicito, totale preparativi).
3. `AlarmScheduler` + `ClockAppScheduler` (`ACTION_SET_ALARM`, `EXTRA_SKIP_UI`, label "Parakeet", `ACTION_DISMISS_ALARM` best-effort, `<queries>`, fallback senza Orologio).
4. Notifica uscita: canale, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` con spiegazione e fallback, reschedule su boot/time/timezone.
5. UX e onboarding (vedi `ux.md`).
6. Migrazione dati v3.0 (lista `ThingToDo` serializzata nelle prefs di `ParakeetMain`), schermata "Novità".
7. Adaptive + themed icon dagli SVG (`docs/brand/`), test unit + UI, release firmata (keystore 2017, versionCode > 30), scheda store, privacy policy.

### Versione 2 (5.0) — sveglia in-app
1. Impostazione "Modalità sveglia": Orologio (default) / Suoneria Parakeet; step di onboarding dedicato.
2. `InternalAlarmScheduler` (`setAlarmClock`), manifest con `USE_EXACT_ALARM`.
3. Suoneria: FGS `systemExempted`, `USAGE_ALARM`, scelta suoneria, vibrazione, volume crescente, snooze/stop.
4. Schermata full-screen su lock screen + gestione permesso FSI.
5. Direct boot, test Doze, dichiarazioni Play (exact alarm, FSI, FGS + video), scheda store che posiziona la sveglia come funzione centrale.

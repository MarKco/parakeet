# Play Store – materiali per Parakeet 5.3

Tutto quello che serve per compilare la scheda su Play Console. Lingue: **it-IT** (predefinita) e **en-US**.

## Contenuto
| Cartella / file | Cosa è |
|---|---|
| `listing/<lingua>/title.txt` | Titolo (max 30 caratteri) |
| `listing/<lingua>/short-description.txt` | Descrizione breve (max 80) |
| `listing/<lingua>/full-description.txt` | Descrizione completa (max 4000) |
| `listing/<lingua>/release-notes.txt` | Note di rilascio (max 500) |
| `graphics/app-icon-512.png` | Icona dell'app 512×512 |
| `graphics/feature-graphic-<it|en>.png` | Immagine di testata 1024×500 |
| `screenshots/<lingua>/phone/` | 6 screenshot telefono 1080×2160 (rapporto 2:1) |
| `screenshots/<lingua>/tablet/` | 4 screenshot tablet 2560×1600 (orizzontale, 16:10) |
| `privacy-policy.html` / `privacy-policy.md` | Informativa sulla privacy, italiano + inglese in un'unica pagina (stesso testo in HTML e in Markdown) |

Gli screenshot sono in ordine di numero: welcome → attività → principale → sveglia puntata → tema scuro → impostazioni (i primi due non ci sono per tablet).
Lo stesso set tablet va bene sia per i 7" sia per i 10". Gli screenshot sono generati dall'app reale su emulatore con barra di stato pulita.

## Privacy policy: va pubblicata online
Play richiede un URL pubblico. Carica `privacy-policy.html` sul tuo sito (ad es. `https://www.marcozanetti.it/parakeet/privacy.html`) e incolla l'URL in *App content → Privacy policy*. Non l'ho pubblicata io. Contiene il tuo indirizzo e-mail (lo stesso già presente nella schermata Info dell'app): cambialo se preferisci un altro contatto.

## Risposte pronte per Play Console (5.3)
**Data safety**
- Raccoglie o condivide dati? **No** (nessun permesso INTERNET; la sincronizzazione scrive solo in una cartella scelta dall'utente, la fotocamera è usata solo sul dispositivo).
- Dati cifrati in transito: non applicabile. Richiesta di cancellazione: non applicabile (nessun dato raccolto).

**Altre dichiarazioni**
- Annunci: **No**. Account / login: **No**. Acquisti in-app: **No**.
- Categoria: *Strumenti* (o *Produttività*). Tag: sveglia, routine, organizzazione.
- Classificazione contenuti: nessun contenuto sensibile (questionario IARC tutto "No").
- Pubblico di destinazione: 13+ / tutti (non è rivolta ai bambini).
- Target API: **36** (richiesto). Firma: stesso keystore del 2017, versionCode > 30 (ora **53** (versione 5.3)).

**Permessi: giustificazioni**
| Permesso | Stato su Play | Motivo da indicare |
|---|---|---|
| `SET_ALARM` | normale, nessuna dichiarazione | crea la sveglia nell'app Orologio |
| `POST_NOTIFICATIONS` | runtime | notifica "Ora di uscire!" (facoltativa) |
| `POST_PROMOTED_NOTIFICATIONS` | normale (Android 16, nessuna dichiarazione) | notifica "Prima di uscire" come Live Update (chip nella barra di stato); facoltativa, l'utente può spegnerla |
| `SCHEDULE_EXACT_ALARM` | **da valutare** | la notifica di uscita deve arrivare all'ora impostata dall'utente; l'utente la concede da "Sveglie e promemoria"; senza, l'app ripiega su un allarme non esatto |
| `RECEIVE_BOOT_COMPLETED` | normale | riprogramma sveglia e notifica dopo il riavvio |
| `USE_EXACT_ALARM` | **dichiarazione Console** (sveglia) | la funzione principale è una sveglia: deve suonare all'ora esatta |
| `USE_FULL_SCREEN_INTENT` | **dichiarazione Console** | schermata della sveglia sopra il blocco schermo |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | **dichiarazione Console** (con video) | servizio che fa suonare la sveglia; tipo consentito alle app con allarmi esatti |
| `CAMERA` | runtime, facoltativo | solo per leggere il QR code di spegnimento, elaborato sul dispositivo (ML Kit incluso nell'app) |
| `WAKE_LOCK`, `VIBRATE`, `FOREGROUND_SERVICE` | normali | far suonare/vibrare la sveglia |

Nota su `SCHEDULE_EXACT_ALARM`: Play lo limita alle app la cui funzione principale richiede tempi precisi (sveglie, calendari). Parakeet è una sveglia "a ritroso" e la funzione dichiarata è la notifica all'ora esatta scelta dall'utente, ma è l'unico permesso della v1 che potrebbe richiedere una risposta in Console. Se Play lo contestasse, la v1 funziona anche senza (basta toglierlo dal manifest: la notifica diventa non esatta).

## Dopo la pubblicazione
- Se la grafica cambia, gli screenshot vanno rifatti: sono stati generati dall'app reale su emulatore (non c'è uno script nel repo).
- La 5.3 richiede le dichiarazioni in Console su allarme esatto, schermo intero e servizio in primo piano (video dimostrativo: impostazioni → "Prova la sveglia" → schermata di spegnimento). Compaiono in *Contenuti app* solo **dopo** aver caricato un AAB che dichiara i permessi (crea la release in bozza, compila le dichiarazioni, poi invia). Vedi `docs/analysis.md`. In Data safety: nessun dato raccolto o condiviso (la fotocamera è usata solo sul dispositivo).

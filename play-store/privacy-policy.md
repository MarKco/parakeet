# Parakeet – Informativa sulla privacy / Privacy policy

[Italiano](#italiano) · [English](#english)

---

## Italiano

*Ultimo aggiornamento: 14 novembre 2026*

Parakeet (`com.ilsecondodasinistra.parakeet`) è un'app Android che calcola l'ora della sveglia a partire dall'orario di uscita da casa e dalle attività mattutine scelte dall'utente. Questa informativa spiega quali dati usa l'app e come vengono trattati.

### In breve

**Parakeet non raccoglie, non trasmette e non condivide alcun dato personale.** Non richiede un account, non contiene pubblicità, non usa strumenti di analisi o di segnalazione degli errori e non dichiara il permesso di accesso a Internet: l'app non può inviare dati fuori dal dispositivo.

### Dati che l'app memorizza sul dispositivo

- l'elenco delle attività mattutine (nome e durata in minuti) e se sono selezionate;
- l'orario abituale di uscita da casa;
- le impostazioni (notifica all'uscita attiva o no, testo personalizzato della notifica);
- le informazioni sull'ultima sveglia impostata (orario della sveglia e dell'uscita), per mostrarne lo stato e per riprogrammare sveglia e notifica dopo un riavvio.
- le opzioni della sveglia (suoneria, vibrazione, posticipo, modo di spegnimento e, se lo scegli, il testo del QR code che hai inquadrato).

Questi dati restano esclusivamente nella memoria privata dell'app sul dispositivo. Vengono eliminati disinstallando l'app o cancellandone i dati dalle impostazioni di sistema. Chi aggiorna dalle versioni precedenti di Parakeet ritrova le proprie attività: i dati vengono letti una sola volta dal vecchio archivio dell'app e spostati in quello nuovo, sempre sul dispositivo.

### Permessi richiesti

| Permesso | A cosa serve |
|---|---|
| Impostare sveglie (`SET_ALARM`) | Creare la sveglia nell'app Orologio del dispositivo. |
| Notifiche (`POST_NOTIFICATIONS`) | Mostrare la notifica "Ora di uscire!" all'orario di uscita. Facoltativo: si può rifiutare o disattivare nelle impostazioni. |
| Aggiornamenti in tempo reale (`POST_PROMOTED_NOTIFICATIONS`, Android 16) | Mostrare nella barra di stato, durante la mattina, la notifica "Prima di uscire" con l'attività in corso. Facoltativo: si può disattivare dalle impostazioni di sistema. |
| Sveglie e promemoria (`SCHEDULE_EXACT_ALARM`) | Far arrivare la notifica di uscita all'ora esatta. Facoltativo: senza, la notifica può arrivare con qualche minuto di ritardo. |
| Avvio del dispositivo (`RECEIVE_BOOT_COMPLETED`) | Riprogrammare la notifica di uscita e la sveglia dopo un riavvio del telefono. |
| Sveglie esatte (`USE_EXACT_ALARM`) | Far suonare la sveglia di Parakeet all'ora esatta. Concesso automaticamente (Android 13+); su Android 12 si usa `SCHEDULE_EXACT_ALARM`, concesso dall'utente. |
| Schermo intero (`USE_FULL_SCREEN_INTENT`) | Mostrare la schermata della sveglia anche a telefono bloccato. Facoltativo. |
| Servizio in primo piano (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`), `WAKE_LOCK`, `VIBRATE` | Far suonare e vibrare la sveglia finché non viene spenta. |
| Fotocamera (`CAMERA`) | Solo se scegli di spegnere la sveglia con un QR code: l'app legge il codice sul dispositivo. Nessuna immagine viene salvata o inviata. Facoltativo. |

### Servizi di terze parti e link esterni

L'app non integra servizi di terze parti. Nella schermata "Info" sono presenti due collegamenti (sito dell'autore e indirizzo e-mail) che, se toccati, aprono rispettivamente il browser e l'app di posta del dispositivo: da quel momento valgono le regole di tali applicazioni.

### Backup di Android

Il sistema Android può includere i dati dell'app nel backup del dispositivo (ad esempio su Google Drive), secondo le impostazioni scelte dall'utente. Questo è gestito dal sistema operativo e non dall'app.

### Minori

Parakeet non raccoglie dati di alcun utente, compresi i minori.

### Modifiche

Se questa informativa cambierà, la nuova versione sarà pubblicata a questo indirizzo con la data di aggiornamento.

### Contatti

Marco Zanetti – [ilsecondodasinistra@gmail.com](mailto:ilsecondodasinistra@gmail.com) – [marcozanetti.it](https://www.marcozanetti.it)

---

## English

*Last updated: 14 November 2026*

Parakeet (`com.ilsecondodasinistra.parakeet`) is an Android app that works out your wake-up time from the time you leave home and the morning activities you choose. This policy explains which data the app uses and how it is handled.

### In short

**Parakeet does not collect, transmit or share any personal data.** It needs no account, contains no advertising, uses no analytics or crash-reporting tools and does not declare the Internet permission: the app cannot send data off your device.

### Data stored on your device

- your list of morning activities (name and duration in minutes) and whether they are selected;
- your usual exit time;
- settings (exit notification on or off, custom notification text);
- details of the last alarm you set (alarm and exit times), to show its status and to reschedule the alarm and the notification after a restart.
- your alarm options (sound, vibration, snooze, how to stop it and, if you choose it, the text of the QR code you scanned).

This data stays exclusively in the app's private storage on your device. It is deleted when you uninstall the app or clear its data in the system settings. If you update from a previous version of Parakeet your activities are carried over: the data is read once from the old app storage and moved to the new one, always on your device.

### Permissions

| Permission | Purpose |
|---|---|
| Set alarms (`SET_ALARM`) | Create the alarm in your device's Clock app. |
| Notifications (`POST_NOTIFICATIONS`) | Show the "Time to leave!" notification at your exit time. Optional: you can decline or turn it off in settings. |
| Live updates (`POST_PROMOTED_NOTIFICATIONS`, Android 16) | Show the "Before you leave" notification, with your current activity, in the status bar during the morning. Optional: you can turn it off in system settings. |
| Alarms & reminders (`SCHEDULE_EXACT_ALARM`) | Deliver the exit notification at the exact time. Optional: without it the notification may arrive a few minutes late. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Reschedule the exit notification and the alarm after the phone restarts. |
| Exact alarms (`USE_EXACT_ALARM`) | Ring the Parakeet alarm at the exact time. Granted automatically (Android 13+); on Android 12 `SCHEDULE_EXACT_ALARM` is used, granted by the user. |
| Full screen (`USE_FULL_SCREEN_INTENT`) | Show the alarm screen even when the phone is locked. Optional. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`), `WAKE_LOCK`, `VIBRATE` | Ring and vibrate until the alarm is switched off. |
| Camera (`CAMERA`) | Only if you choose to stop the alarm with a QR code: the code is read on the device. No image is stored or sent. Optional. |

### Third parties and external links

The app integrates no third-party services. The "About" screen has two links (the author's website and e-mail address) which, when tapped, open your browser and your e-mail app respectively; from that point those applications' own rules apply.

### Android backup

Android may include the app's data in your device backup (for example on Google Drive), according to the settings you chose. This is handled by the operating system, not by the app.

### Children

Parakeet collects no data from any user, including children.

### Changes

If this policy changes, the new version will be published at this address with its update date.

### Contact

Marco Zanetti – [ilsecondodasinistra@gmail.com](mailto:ilsecondodasinistra@gmail.com) – [marcozanetti.it](https://www.marcozanetti.it)

# Parakeet — analisi UX e nuovo flusso

## Cosa funziona (da tenere)
- Il risultato (ora sveglia) in alto, grande, in stile LCD: è il cuore del brand.
- Il concetto "uscita − attività = sveglia" è immediato.
- Swipe per eliminare con annulla.

## Problemi dell'app attuale
1. **Calcolo invisibile**: compare solo "6:45". Mancano il totale dei preparativi ("1h15") e il giorno ("domani, ven 14"). Oggi/domani è deciso in silenzio (con bug a fine mese).
2. **Primo avvio**: uscita 0:00 ⇒ risultato assurdo, coperto da un hint lungo che si sovrappone alla status bar.
3. **Pulsanti solo icona** (sveglia, lista+) ambigui; "Punta sveglia", l'azione principale, ha lo stesso peso di "aggiungi".
4. **Punta sveglia apre l'Orologio**: duplicati "New Alarm", ritorno manuale, nessuna conferma né stato visibile.
5. **Righe**: checkbox piccola; tap = modifica, mentre l'azione frequente è includere/escludere; durate LCD poco leggibili e senza unità; icona di cancellazione = rumore.
6. **Dialog di modifica**: titolo troncato, durata solo da tastiera.
7. **Drawer superfluo** per un'app a schermata unica: duplica azioni, contiene "reset testo notifica" e un "Aiuto" che non fa nulla.
8. **Impostazioni** quasi tutte boilerplate morto.
9. **"What's new"** mostrato anche alle nuove installazioni.
10. **Accessibilità**: contrasto lime su verde chiaro, niente `contentDescription`, LCD usato anche dove serve leggibilità.
11. **Lista di default troppo personale** ("cane", "porta fuori bici", "slavazzamento") ⇒ meglio come suggerimenti.

## Nuovo flusso (schermata unica)
```
┌──────────────────────────────┐
│  SVEGLIA          06:45 (LCD)│  card principale: il risultato
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
- Sveglia creata in silenzio (`EXTRA_SKIP_UI`, label "Parakeet", senza duplicati); in v2 anche dal motore interno.
- Stato della sveglia attiva sempre visibile e annullabile.
- Drawer eliminato: Impostazioni e Info nel menu della top bar. Impostazioni ridotte a: modalità sveglia (v2), notifica di uscita on/off, testo notifica con anteprima.
- Durate con chip rapidi (5/10/15/20/30) + valore libero; LCD solo per gli orari principali.
- Timeline del mattino: fatta nella 5.2, vedi "Dopo la sveglia" sotto. Routine salvate e promemoria serale: fatti nella 5.1.

## Dopo la sveglia (5.2)
Opzionale, spenta di default, solo con la sveglia interna. Le attività della routine del giorno diventano una lista guidata fino all'uscita.
- **Un anello che non cambia mai scala**: sveglia → uscita. Un arco colorato per attività (palette delle routine, a rotazione), consumato in senso antiorario come un timer; il puntino è l'orologio reale. Il numero al centro è sempre il tempo vero all'uscita.
- **Niente avanza da solo.** L'attività corrente resta tale finché non agisci, così si capisce sempre se sei in anticipo o in ritardo. Se sfora, il suo arco cresce con il cursore e spinge in avanti i successivi.
- **L'anticipo**: un solo arco grigio in fondo, lungo zero all'inizio. "Fatto" (o "Salta oggi") prima del previsto fa scivolare il tempo non usato fin lì, animato, e la riga sotto l'anello dice "Sei in anticipo di X min". Sforando, l'anticipo si accorcia (riga in ambra); quando è finito il piano non entra più prima dell'uscita: riga corallo "In ritardo di X min", arco corallo sottile fuori dall'anello, puntino corallo sulle attività che non entrano. Un box compare brevemente la prima volta che nasce l'anticipo e quando finisce.
- **Tempo per gli imprevisti**: un'attività speciale del catalogo (flag nel database, migrazione 2→3), sempre in fondo al piano e senza riga né pulsanti nella schermata. È tempo del piano (la sveglia suona prima di quel tanto), ma sull'anello è fin dall'inizio l'arco grigio dell'anticipo: si consuma quando le attività sforano, senza avvisi, e il ritardo parte solo a cuscinetto finito. È precompilata (5 minuti, spuntata) in fondo all'elenco dell'onboarding; chi non l'ha trova sotto la lista, e nell'editor della routine, una riga discreta "Aggiungi tempo per gli imprevisti?" che si può scartare per sempre. "Tempo guadagnato!" si dice solo per ciò che supera il cuscinetto; la riga sotto l'anello dice sempre "Sei in anticipo di X min", cuscinetto compreso.
- **Tre pulsanti** sull'attività corrente: "Fatto", "Faccio dopo" (in coda con i minuti che le servono ancora, almeno 5; compare solo se dopo c'è altro) e "Salta oggi" (i suoi minuti non usati diventano anticipo).
- **Coriandoli** e "Complimenti, hai finito tutto e sei in anticipo!" quando finisci l'ultima attività con margine (non se le animazioni di sistema sono spente).
- **Notifica in corso** "Ora: <attività>" / "Poi: <prossima>", con la barra segmentata come l'anello (Android 16: colori delle attività e indicatore sulla fase corrente, promossa a Live Update con un chip nella barra di stato; sotto, barra semplice del tempo), il conto alla rovescia di sistema (che diventa conto in avanti se sfori), gli stessi tre pulsanti e, una sola volta, un avviso senza suono quando inizia il ritardo. Si aggiorna ogni minuto. Niente overlay sopra le altre app: richiederebbe `SYSTEM_ALERT_WINDOW`.
- **Sopra il blocco**: dopo "Spegni" la schermata si apre da `AlarmActivity` (già in primo piano), senza permessi nuovi. Sopra il blocco è scura (come la schermata della sveglia); altrimenti segue il tema dell'app.
- **Proposta**: una sola card in schermata principale dopo la 2ª sveglia suonata davvero ("Provala" / "No grazie"), solo se serve (sveglia interna, funzione spenta, almeno 2 attività). Nessun passo di onboarding, nessun dialog "novità". Sempre nelle Impostazioni → "Dopo la sveglia".

## Onboarding
**Prima**: tour ShowcaseView a 4 cerchi + dialog "What's new". Spiega l'interfaccia ma non fa fare nulla, viene saltato. Da eliminare.

**Nuovo**: breve, raccoglie dati veri, così alla fine c'è già una sveglia calcolata (momento "aha").
1. **Benvenuto** — icona + "Dimmi quando esci, ti dico quando svegliarti" → *Inizia*.
2. **"A che ora esci di solito?"** — time picker, default 8:00 → diventa l'uscita predefinita.
3. **"Cosa fai la mattina?"** — chip di suggerimenti con durate modificabili + aggiungi; anteprima live "Sveglia alle 6:50".
4. **v1:** "Avvisami quando è ora di uscire?" → permessi notifiche + sveglie esatte, chiesti qui con spiegazione e fallback.
   **v2:** in più "Come vuoi essere svegliato?" Orologio di sistema / suoneria Parakeet → il permesso schermo intero solo se sceglie la suoneria interna.

Ogni passo è saltabile, con default sensati. Dopo l'onboarding: hint contestuali una tantum (swipe) e stato vuoto chiaro, niente tour.

**Utenti che aggiornano dalla v3.0**: migrazione dei dati, niente onboarding, una schermata "Novità". "What's new" mai a chi installa da zero.

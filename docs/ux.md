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
- Da valutare: timeline del mattino (6:45 doccia → 7:00 colazione → … → 8:00 esci), routine salvate (feriale/weekend/palestra), promemoria serale "Hai puntato la sveglia?".

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

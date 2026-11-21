# Sincronizzazione e backup (5.3)

Stesso metodo di Discography ([docs/sync.md](../../discography/docs/sync.md)) e bvzm, più semplice perché i dati sono pochi:
una cartella condivisa (Syncthing-Fork o qualsiasi provider SAF), ogni dispositivo scrive **solo il proprio file**, unione per voce con `updatedAt` e lapidi.

## Cosa viaggia
- **Attività** (nome, minuti, «tempo per gli imprevisti», posizione), **routine** (nome, colore, uscita, giorni, attiva, posizione, attività membri), **piani del giorno** (una tantum).
  In modo semplice le attività spuntate e l'uscita vivono nella *prima routine*, quindi viaggiano con le routine.
- **Impostazioni condivise** (`SharedSettings`): modo piano, testo dell'uscita, vibrazione, snooze, sfida (+ operazioni, scosse, QR), promemoria (prima, sera), «Dopo la sveglia» (4 interruttori).
- Restano **del telefono**: suoneria (l'indirizzo è locale), modo sveglia Orologio/in-app, notifica d'uscita, `tasks.checked`, uscita semplice (`settings.exitTime`), pausa, sveglia armata, sessione e contatori di «Dopo la sveglia», suggerimenti chiusi, cartella/nome/id del sync.
  Le impostazioni del telefono stanno in un blocco `device` del proprio file: nessuno lo unisce, serve solo a «È questo dispositivo».

## File
`parakeet-<idDispositivo>.json`: `SyncFile{format:"parakeet-sync", version:1, deviceId, deviceName, writtenAt, state, device}`, lo stato **completo** (anche ciò che ha ricevuto).
Riscritto solo se lo stato (non l'ora) cambia. File illeggibili o di versione più nuova: saltati, contati, riletti al giro dopo.

## Identità e dati (Room v4)
`tasks` e `routines` hanno `uid` (UUID) e `updatedAt`; `day_plans` ha `updatedAt` (chiave = giorno). Gli id `Long` restano locali.
Le cancellazioni scrivono una lapide in `sync_tombstones` (kind, key, localId, deletedAt): le query esistenti non cambiano. L'undo di un'attività riusa il suo uid. Lapidi più vecchie di un anno eliminate.
Migrazione 3→4: uid casuale e `updatedAt = 1` («vecchio»: viaggia, ma perde contro qualunque modifica vera).

**Seed** (lista dell'onboarding, import 3.x, conversione 5.0): `updatedAt = 0`, **non viaggiano**. Se qualcosa che viaggia li usa (routine, piano) vengono «promossi» al giro (`promoteReferencedSeeds`).
Al primo collegamento a una cartella con altri dispositivi i seed intoccati vengono scartati (senza lapide) e rimpiazzati da quelli della cartella: niente doppioni dell'onboarding.

## Unione (`SyncState.mergedWith`, `SyncEngine`, `RoomSyncStore.apply`)
Per voce vince l'`updatedAt` più recente, a parità il locale; la lapide batte una voce più vecchia, una voce più nuova la rianima. Idempotente e indipendente dall'ordine (`SyncMergeTest`).
Dopo l'unione: un giorno della settimana appartiene a una sola routine (vince la più recente, poi l'uid; non si datano le modifiche, ogni dispositivo arriva allo stesso risultato).
Un piano il cui routine non è arrivata (o è cancellata) non si applica e si riprova al giro dopo. Piani di giorni passati ignorati. Cancellare una routine cancella (in locale, su ogni dispositivo) i suoi piani.
Impostazioni: un timestamp per chiave nel DataStore `sync` (valore ≠ ultimo noto ⇒ modificata ora; mai toccata ma non al default ⇒ timestamp 1). Chi arriva da un altro dispositivo non rimbalza.
Dopo un'unione `AlarmSync` (che osserva i dati) ripianifica la sveglia da solo.

## Conflitti `.sync-conflict-`
Una copia è comunque uno stato completo: viene unita e poi **archiviata** (`parakeet-<id>.resolved-<aaaammgg>-<resto>.json`, resta `.json`); le archiviate si cancellano dopo 30 giorni. Se il rename fallisce la copia resta e si unisce di nuovo.

## Quando (`SyncManager`)
All'apertura (dopo 1,5 s), 5 s dopo una modifica del piano/catalogo/impostazioni condivise, ogni 15 min ad app aperta, «Sincronizza ora». Un giro alla volta. **Niente WorkManager**: se cambi un piano su un telefono e l'altro non apre l'app, il secondo suona con il piano vecchio finché non la apre.

## Primo collegamento
Se la cartella ha già altri dispositivi e questa installazione non ha mai sincronizzato: «È questo dispositivo?». *È questo*: prende id e nome, riapplica il suo blocco `device` e il primo giro legge anche il suo vecchio file (con un solo dispositivo è l'unica copia). *Nuovo, da zero*: id nuovo. Il sync si accende solo dopo la scelta.
Il DataStore `sync` è escluso dai backup Android (`backup_rules.xml`, `data_extraction_rules.xml`): un ripristino non deve clonare l'id del dispositivo.

## Backup `.parakeet`
Impostazioni → Backup: tutto (anche seed, spunte, blocco `device`). «Ripristina» **unisce** (come il sync); il blocco `device` si applica solo alle chiavi ancora assenti.

## Impostazioni a tab
Generale · Sveglia · Promemoria · Dopo la sveglia · Sincronizzazione · Backup (`PrimaryScrollableTabRow` + `HorizontalPager`, ultimo tab ricordato). Promemoria e Dopo la sveglia con sveglia Orologio mostrano una card che porta al tab Sveglia.

## Verifiche
Test JVM: `SyncMergeTest`, `SyncEngineTest` (cartella finta). Emulatore Pixel_9a: migrazione 3→4 su DB reale 5.2; due installazioni (debug e release) sulla stessa cartella `/sdcard/Documents/ParakeetSync` scelta con il selettore di sistema: file scritto, dialog di collegamento, seed scartati, nessun doppione; copia `.sync-conflict` finta con attività nuova, cancellazione e impostazione: unita, archiviata (rename SAF ok) e arrivata sull'altro dispositivo; backup salvato.
**Non provato**: Syncthing vero e provider diversi dalla cartella locale; ripristino del backup (solo il salvataggio); due dispositivi con orologi sfasati.

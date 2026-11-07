# Luma per Tapo — Persistenza Locale di Accesso, Sessione e Stato

Questo aggiornamento introduce il salvataggio automatico locale sia delle credenziali dell'account Tapo (cifrate con chiave di sistema Android senza richiedere frasi segrete manuali) sia delle sessioni crittografiche attive (**KLAP v2** / **TPAP**) e dell'ultimo stato noto delle lampadine, così da ritrovare l'app subito pronta e connessa anche dopo averla chiusa completamente.

## User Review & Critical Decisions

> [!IMPORTANT]
> Di seguito sono riepilogate le scelte confermate per la gestione della persistenza locale:

- **Decisione Confermata 1 — Salvataggio automatico delle credenziali con cifratura Android**: Le password dell'account Tapo vengono cifrate automaticamente sul dispositivo tramite **AES-256-GCM** protetto da **Android Keystore** (senza dover inserire o ricordare una frase segreta di 12 caratteri).
- **Decisione Confermata 2 — Ripristino istantaneo dello stato e ripresa automatica della sessione**: All'apertura dell'app viene mostrato immediatamente l'ultimo stato salvato della lampadina (accensione, luminosità, temperatura colore o tinta HSV) e viene ripresa la sessione crittografata locale (`KLAP v2` o `TPAP`), rinnovandola in modo trasparente in background se scaduta.

---

## 1. Overview & Core Concept

- **Cosa fa**: Permette di chiudere l'app Android (o riavviare il telefono) e, alla riapertura, ritrovare immediatamente la propria stanza, la lampadina attiva con i suoi controlli già visibili e la sessione locale pronta a inviare comandi senza dover reinserire password o attendere schermate di blocco.
- **Target Audience / Persona**: Utenti domestici che utilizzano Luma come interruttore quotidiano rapido per le proprie luci Tapo e desiderano un'esperienza immediata all'apertura dell'app.
- **Valore Chiave**: Zero attrito all'avvio (nessuna richiesta ripetuta di password o passphrase) e continuità immediata della sessione locale.

---

## 2. User Experience & Visual Design

- **Flussi Utente Chiave**:
  1. **Primo collegamento di una lampadina**: L'utente inserisce email e password Tapo una sola volta nella finestra *Aggiungi lampadina*. Luma autentica la luce e salva automaticamente le credenziali cifrate sul dispositivo insieme alle chiavi di sessione e allo stato iniziale.
  2. **Uscita e riapertura dell'app**: Quando l'utente riapre Luma, l'interfaccia mostra subito la lampadina selezionata con l'ultimo stato noto (colore del bulbo, percentuale di luminosità, temperatura o tinta) e badge **«Connessa»**.
  3. **Ripresa trasparente della sessione in background**: Luma riutilizza i parametri crittografici salvati (`cookie`, chiavi di sessione e numero di sequenza `seq`) per verificare/aggiornare lo stato della lampadina in background; se la lampadina ha invalidato la sessione (es. per timeout o riavvio elettrico), Luma effettua automaticamente un nuovo handshake con le credenziali salvate senza interrompere l'utente.
  4. **Aggiunta di ulteriori lampadine**: Quando si aggiunge una seconda lampadina con lo stesso account Tapo, la password è già pre-caricata e pronta (il campo password indica *«Salvata sul dispositivo»* e non è obbligatorio ridigitarla).
- **Identità Visiva e Tema**:
  - Mantiene intatto il design Luma lavanda e vetro smerigliato con tema chiaro e scuro (`DM Sans` + `DM Serif Display`).
  - La finestra **Aggiungi / Gestisci lampadina** viene snellita rimuovendo la casella e il campo della "frase segreta dell'archivio" manuale, sostituiti da una nota chiara che conferma il salvataggio locale cifrato automatico.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisione 1: Cifratura automatica tramite Android Keystore invece di Passphrase manuale**
  - *Approccio scelto*: Generazione automatica di una chiave simmetrica `AES-256-GCM` nel Keystore di sistema Android (`AndroidKeyStore`) per cifrare su disco sia le password degli account Tapo sia i token/chiavi di sessione.
  - *Perché*: Garantisce sicurezza crittografica locale eliminando completamente l'onere per l'utente di digitare una frase segreta di sblocco a ogni avvio dell'app.
- **Decisione 2: Serializzazione delle Sessioni KLAP/TPAP + Cache dello Stato (`LightInfo`)**
  - *Approccio scelto*: Salvataggio persistente dei parametri di sessione (`key`, `ivPrefix`/`noncePrefix`, `sigKey`, `cookie`, `url`, `seq`) aggiornati a ogni comando, unito alla cache dell'ultimo `LightInfo` per ogni lampadina.
  - *Perché*: Evita di dover eseguire un doppio handshake HTTP a ogni apertura dell'app quando la sessione sulla lampadina è ancora valida e permette alla UI di essere immediatamente interattiva.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architettura e Flusso di Ripristino Sessione**:
```
┌────────────────────────────────────────────────────────────────────────────┐
│                              Avvio App (UI)                                │
│  1. Carica subito ultimo Snapshot salvato (stato, colore, luminosità)      │
│  2. Mostra immediatamente controlli attivi senza schermate di attesa       │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   LumaService · Ripristino Automatico                      │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Archivio Cifrato Android Keystore (AES-256-GCM)                      │  │
│  │  • Credenziali Account Tapo (email -> password cifrata)              │  │
│  │  • Stato Sessione Protocollo (KLAP v2 / TPAP: chiavi, cookie, seq)   │  │
│  │  • Ultimo LightInfo noto per ogni dispositivo                        │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
│                                     ▼                                      │
│  Prova richiesta get_device_info con la sessione KLAP/TPAP salvata         │
│   ├─ Se valida ──► Aggiorna stato live e mantiene la sessione              │
│   └─ Se scaduta ─► Riconnette in automatico con la password decifrata      │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Modello Dati e Persistenza**:
  - **Keystore Cipher Helper**: Cifra e decifra stringhe JSON sensibili tramite `AndroidKeyStore` (`AES/GCM/NoPadding`, IV a 12 byte + ciphertext).
  - **Session Snapshot (`PersistedSessionState`)**:
    - Per **KLAP v2**: `base`, `key`, `ivPrefix`, `sigKey`, `seq`, `sessionCookie`, `fingerprint`.
    - Per **TPAP**: `base`, `url`, `key`, `noncePrefix`, `seq`, `fingerprint`.
    - Ad ogni comando inviato con successo, il contatore `seq` e il `LightInfo` aggiornato vengono salvati localmente.
  - **Backup JSON Esterno (`Esporta / Importa`)**: Per sicurezza, i file JSON esportati manualmente dall'utente continuano a contenere solo stanze, nomi, modelli e IP delle lampadine (senza esportare in chiaro password o chiavi di sessione).

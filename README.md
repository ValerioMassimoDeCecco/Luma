# Luma per Tapo (Android)

**Luma per Tapo** è un'applicazione Android nativa sviluppata in **Kotlin** e **Jetpack Compose** per controllare le lampadine smart TP-Link Tapo direttamente sulla rete Wi-Fi locale, senza dipendere da server cloud esterni o telemetria.

## Caratteristiche principali

- **Ricerca automatica sulla rete locale (Discovery LAN)**:
  - **Broadcast UDP Tapo** sulle porte `20002` e `9999` + **scansione parallela della sottorete `/24`** (`KLAP v2 /app/handshake1` e `TPAP discover`).
  - Possibilità di toccare una singola lampadina trovata per compilare automaticamente IP, modello e nome, oppure **selezionare più lampadine con le caselle di spunta** per aggiungerle tutte insieme a una stanza.
- **Persistenza locale automatica di Accesso, Sessione e Stato**:
  - Salvataggio automatico delle credenziali Tapo cifrate sul dispositivo tramite **Android Keystore (`AES-256-GCM`)**, senza necessità di inserire o ricordare frasi segrete manuali.
  - Salvataggio delle sessioni crittografiche attive (**KLAP v2** / **TPAP**, inclusi chiavi, cookie e contatore di sequenza `seq`) e dell'ultimo stato noto della lampadina, con ripristino istantaneo all'apertura dell'app e rinnovo automatico trasparente se la sessione sulla lampadina è scaduta.
- **Protocolli locali diretti**: Supporto nativo per **KLAP v2** (AES-128-CBC + SHA-256 + gestione sessione) e **HTTP TPAP / SPAKE2+** (curva ellittica NIST P-256, HKDF-SHA256 e cifratura autenticata AES-128-CCM).
- **Modelli supportati**: `L510`, `L520`, `L530`, `L535`, `L610`, `L630`, `L900`, `L920`, `L930`.
- **Gestione Casa (Stanze e Luci)**: Organizzazione delle luci per stanza con creazione, modifica e rimozione protetta.
- **Controlli Luce e Scene**:
  - Regolazione fluida della luminosità (`1–100%`) con coda di invio coalescente.
  - Modalità **Bianco** (`2500–6500 K`) con preset rapidi (*Calda*, *Naturale*, *Fredda*).
  - Modalità **Colore** (`Tonalità 0–360°`, `Saturazione 1–100%`) con campioni rapidi e selettore colore personalizzato.
  - Quattro scene d'atmosfera (*Relax*, *Concentrazione*, *Tramonto*, *Sogno*).
- **Backup JSON**:
  - Importazione ed esportazione della configurazione della casa in formato JSON (`schema: 1`, compatibile con Luma 2.1).
- **Design Originale Luma**: Interfaccia con superfici traslucide, illustrazione vettoriale reattiva della lampadina, font **DM Sans** e **DM Serif Display**, e supporto per **Tema Chiaro** e **Tema Scuro**.

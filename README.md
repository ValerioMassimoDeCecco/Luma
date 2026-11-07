# Luma per Tapo (Android) — v1.01

**Luma per Tapo** è un'applicazione Android nativa sviluppata in **Kotlin** e **Jetpack Compose** per controllare le lampadine smart TP-Link Tapo direttamente sulla rete Wi-Fi locale, senza dipendere da server cloud esterni o telemetria.

## Caratteristiche principali (Release 1.01)

- **Widget Interattivo per la Schermata Home**:
  - Controlla direttamente dalla schermata Home di Android **accensione/spegnimento**, **luminosità (`−` / `+`)**, **4 scene rapide** (*Relax*, *Studio*, *Tramonto*, *Sogno*) e **cambio lampadina** senza dover aprire l'app.
  - Design fedele allo stile Luma lavanda e vetro smerigliato, sincronizzato automaticamente con il Tema Chiaro / Scuro dell'app e con il colore reale della lampadina.
- **Supporto Display a 120Hz (High Refresh Rate)**:
  - Negoziazione automatica della modalità display ad alta frequenza di aggiornamento (`120Hz` o superiore) sugli schermi compatibili, mantenendo la risoluzione nativa del pannello.
- **Ricerca automatica sulla rete locale (Discovery LAN)**:
  - **Broadcast UDP Tapo** sulle porte `20002` e `9999` + **scansione parallela della sottorete `/24`** (`KLAP v2 /app/handshake1` e `TPAP discover`).
  - Possibilità di toccare una singola lampadina trovata per compilare automaticamente IP, modello e nome, oppure **selezionare più lampadine con le caselle di spunta** per aggiungerle tutte insieme a una stanza.
- **Persistenza locale automatica di Accesso, Sessione e Stato**:
  - Salvataggio automatico delle credenziali Tapo cifrate sul dispositivo tramite **Android Keystore (`AES-256-GCM`)**, senza necessità di inserire o ricordare frasi segrete manuali.
  - Salvataggio delle sessioni crittografiche attive (**KLAP v2** / **TPAP**, inclusi chiavi, cookie e contatore di sequenza `seq`) e dell'ultimo stato noto della lampadina, con ripristino istantaneo all'apertura dell'app e rinnovo automatico trasparente se la sessione sulla lampadina è scaduta.
- **Protocolli locali diretti**: Supporto nativo per **KLAP v2** (AES-128-CBC + SHA-256 + gestione sessione) e **HTTP TPAP / SPAKE2+** (curva ellittica NIST P-256, HKDF-SHA256 e cifratura autenticata AES-128-CCM).
- **Modelli supportati**: `L510`, `L520`, `L530`, `L535`, `L610`, `L630`, `L900`, `L920`, `L930`.
- **Backup JSON**:
  - Importazione ed esportazione della configurazione della casa in formato JSON (`schema: 1`, compatibile con Luma 2.1).

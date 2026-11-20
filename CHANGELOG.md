# Changelog

## [1.02] — 2026-11-20

### Added
- **Design nativo Windows allineato all'APK Android**: Palette cromatica lavanda e vetro smerigliato con orbi atmosferici radiali reattivi, tipografia DM Sans e DM Serif Display, e illustrazioni vettoriali Canvas per la lampada 3D e tutte le icone dell'interfaccia.
- **Tasto di ricerca automatica LAN**: Pulsante dedicato «Cerca luci sulla rete» nella barra superiore, nella striscia di stato LAN e nella selezione luci per scoprire e associare automaticamente le lampadine Tapo senza inserimento manuale dell'IP.
- **Opzioni avanzate per l'Installer Windows (`.exe`)**:
  - Selezione guidata della cartella di destinazione personalizzata durante il setup (`dirChooser`).
  - Creazione collegamenti su Desktop e Menu Start (cartella "Luma").
  - Installazione per-utente (senza richiesta di privilegi elevati UAC).
  - Upgrade UUID stabile per consentire l'aggiornamento automatico e pulito delle versioni successive.
  - Icona dell'applicazione incorporata ad alta risoluzione.
- **Versione portabile Windows (`.zip`)**: Archivio zip autonomo contenente eseguibile nativo e runtime JRE 21 integrato, pronto all'uso senza installazione.

## [1.01] — 2026-11-07

### Added
- **Widget Home Interattivo (`LumaBulbWidgetProvider`)**: Controllo diretto dalla schermata Home di accensione/spegnimento, luminosità (`-20%` / `+20%`), scene rapide (*Relax*, *Studio*, *Tramonto*, *Sogno*) e selezione lampadina senza aprire l'app, con estetica Luma lavanda e vetro (tema chiaro e scuro).
- **Pulsante rapido in-app «Aggiungi widget Home»**: Permette di fissare il widget direttamente sulla schermata Home tramite l'API nativa Android `AppWidgetManager.requestPinAppWidget`.
- **Supporto 120Hz (High Refresh Rate)**: Attivazione automatica della modalità display a 120Hz (o frequenza massima supportata dal pannello alla risoluzione nativa) sugli smartphone e tablet compatibili.
- **Persistenza automatica Accesso e Sessione (`LumaKeystoreVault`)**: Cifratura automatica `AES-256-GCM` tramite Android Keystore per credenziali Tapo, sessioni `KLAP v2` / `TPAP` e stato `LightInfo`.
- **Discovery LAN ibrido (`TapoDiscoveryEngine`)**: Ricerca automatica delle lampadine Tapo sulla rete Wi-Fi tramite Broadcast UDP (`20002` / `9999`) e scansione parallela della sottorete `/24` con aggiunta singola o multipla.

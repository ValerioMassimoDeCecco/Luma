# Changelog

## [1.01] — 2026-11-07

### Added
- **Widget Home Interattivo (`LumaBulbWidgetProvider`)**: Controllo diretto dalla schermata Home di accensione/spegnimento, luminosità (`-20%` / `+20%`), scene rapide (*Relax*, *Studio*, *Tramonto*, *Sogno*) e selezione lampadina senza aprire l'app, con estetica Luma lavanda e vetro (tema chiaro e scuro).
- **Pulsante rapido in-app «Aggiungi widget Home»**: Permette di fissare il widget direttamente sulla schermata Home tramite l'API nativa Android `AppWidgetManager.requestPinAppWidget`.
- **Supporto 120Hz (High Refresh Rate)**: Attivazione automatica della modalità display a 120Hz (o frequenza massima supportata dal pannello alla risoluzione nativa) sugli smartphone e tablet compatibili.
- **Persistenza automatica Accesso e Sessione (`LumaKeystoreVault`)**: Cifratura automatica `AES-256-GCM` tramite Android Keystore per credenziali Tapo, sessioni `KLAP v2` / `TPAP` e stato `LightInfo`.
- **Discovery LAN ibrido (`TapoDiscoveryEngine`)**: Ricerca automatica delle lampadine Tapo sulla rete Wi-Fi tramite Broadcast UDP (`20002` / `9999`) e scansione parallela della sottorete `/24` con aggiunta singola o multipla.

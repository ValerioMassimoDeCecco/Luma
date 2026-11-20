# Luma per Tapo — Porting Desktop Windows (v1.01)

Porting **nativo Windows** di **Luma per Tapo**, l'app che controlla le lampadine smart
TP-Link Tapo direttamente sulla rete Wi-Fi locale (senza cloud).

Questo progetto è un **progetto autonomo** che riutilizza il codice di dominio dell'app
Android (**Kotlin + Compose**): i protocolli **KLAP v2** e **TPAP / SPAKE2+**, il motore
di discovery LAN, la cifratura delle credenziali/sessioni e la logica di servizio sono
**portati tal quali** dal progetto Android. La UI è stata ricreata con
**Compose Multiplatform for Desktop**, seguendo lo stesso stile Luma (lavanda e vetro).

> Il progetto Android originale (`app/`) non è stato modificato in alcun modo: il porting
> vive in questo directory `desktop/` ed ha la sua build Gradle autonoma.

## Caratteristiche (stesse dell'app Android)

- **Controllo lampadina**: accensione/spegnimento, luminosità, temperatura bianco
  (Kelvin), colore HSV, 4 scene rapide (*Relax*, *Concentrazione*, *Tramonto*, *Sogno*).
- **Discovery LAN automatica**: broadcast UDP Tapo (porte `20002` / `9999`) + scansione
  parallela della sottorete `/24` (TLAP/KLAP probe), con aggiunta singola o multipla.
- **Protocolli locali diretti**: KLAP v2 (AES-128-CBC + SHA-256) e TPAP (SPAKE2+ su
  curva NIST P-256, HKDF-SHA256, AES-128-CCM) — stessa implementazione crittográfica.
- **Persistenza locale cifrata**: credenziali Tapo e sessioni (KLAP/TPAP) salvate con
  **AES-256-GCM** e chiave maestra generata localmente (nessuna frase segreta manuale).
- **Backup JSON**: import/export della configurazione della casa (schema 1, compatibile
  con Luma 2.1).
- Modelli supportati: `L510`, `L520`, `L530`, `L535`, `L610`, `L630`, `L900`, `L920`, `L930`.

## Requisiti

- **JDK 21**
- Gradle 9.3.1 (wrapper incluso in `desktop/gradlew`)

## Compilazione

```bash
cd desktop
./gradlew run                 # avvia l'app in modalità sviluppo
./gradlew packageUberJarForCurrentOS   # crea il fat jar eseguibile
```

Su **Windows**, per generare la distribuzione nativa:

```powershell
cd desktop
.\gradlew.bat createDistributable     # app-image con exe lanciatore + JRE bundled
.\gradlew.bat packageDistributionForCurrentOS   # package (Exe) su Windows
```

## Installer exe (CI)

Il workflow **`.github/workflows/release-windows.yml`** (runner `windows-latest`):

1. Costruisce il fat jar dell'app con Gradle.
2. Assembla l'**app-image** e genera l'**installer nativo `Luma Setup 1.01.exe`**
   con **`jpackage`** (includeto nel JDK, bundlerà un JRE autonomo: niente Java da
   installare a parte l'app).
3. Pubblica nella **Release `v1.01`** di GitHub:
   - `Luma-per-Tapo-v1.01-setup.exe` — installer nativo (eseguo e installa)
   - `Luma-per-Tapo-v1.01-windows-portable.zip` — versione portable (exe + runtime)

Per rigenerare manualmente: scheda **Actions** → *Build Windows App & Publish Release v1.01*
→ *Run workflow*.

## Cosa non è incluso (specifico Android)

- Widget Home Android e modalità 120Hz (specifiche della piattaforma mobile).
- Android Keystore: sostituito da un vault AES-256-GCM locale equivalente.

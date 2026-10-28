# Luma per Tapo 2.1

**Le tue luci Tapo, direttamente da Chrome.** Una piccola estensione indipendente per organizzare più lampadine in stanze e controllarle dalla rete locale, con un'interfaccia liquid glass e tema chiaro o scuro.

Non servono Python, server, Node.js o programmi esterni: tutti i file necessari sono già inclusi. Luma è un progetto indipendente e non è affiliato a TP-Link o Tapo.

> **Compatibilità:** questa versione supporta KLAP v2 e una specifica variante TPAP. La versione 2.1 è stata verificata con simulatori e un utilizzatore ha confermato il controllo di una lampadina reale dopo aver attivato la compatibilità con terze parti (modello e firmware non specificati). Il funzionamento varia tra dispositivi e non è garantito per ogni firmware.

## Prima di iniziare: compatibilità Tapo

Per molti dispositivi è necessario abilitare la compatibilità con applicazioni di terze parti:

1. Apri l'app **Tapo** sul telefono e vai su **Io → Servizi di terze parti → Compatibilità con terze parti**. Il nome o la posizione possono variare in base alla versione dell'app.
2. Se è disponibile e scegli di usarla, **attiva la compatibilità** e attendi circa un minuto prima di collegare la lampadina a Luma.

Questa impostazione può consentire l'uso di protocolli locali compatibili con Luma, ma **può ridurre la sicurezza dei dispositivi e non funziona necessariamente con ogni firmware**. È facoltativa per i dispositivi la cui variante TPAP è già supportata. Puoi leggere le [informazioni ufficiali Tapo](https://www.tapo.com/it/faqs/frequently-asked-questions-about-the-third-party-compatibility-feature/) prima di decidere.

La lampadina deve essere già configurata nell'app Tapo. Il computer e la lampadina devono potersi raggiungere nella stessa rete locale: le reti ospiti, l'isolamento Wi-Fi e alcune VPN possono impedire la connessione.

## Installazione in Chrome

1. Scarica il progetto: su GitHub scegli **Code → Download ZIP**, oppure scarica lo ZIP di una release se disponibile.
2. Estrai lo ZIP in una cartella stabile. Al suo interno deve essere presente `manifest.json`.
3. In Chrome **116 o successivo**, apri `chrome://extensions` e attiva **Modalità sviluppatore**.
4. Premi **Carica estensione non pacchettizzata** e seleziona la cartella estratta che contiene direttamente `manifest.json`.
5. Apri il menu delle estensioni di Chrome (icona a puzzle) e fissa **Luma per Tapo 2.1** alla barra, se vuoi.

Chrome non installa questo pacchetto direttamente dallo ZIP: occorre estrarlo e caricare la cartella. Non spostare o cancellare la cartella dopo l'installazione. Per aggiornare i file, sostituiscili nella stessa cartella e premi **Ricarica** in `chrome://extensions`.

## Collegare una luce

1. In Luma crea o seleziona una stanza e premi **Aggiungi lampadina**. Dal popup si aprirà la pagina completa, necessaria per gestire senza interruzioni i permessi di Chrome.
2. Inserisci nome, stanza, email e password dell'account Tapo, **indirizzo IPv4 locale** e modello. L'IP si trova nelle informazioni del dispositivo nell'app Tapo oppure nell'elenco dei dispositivi del router.
3. Premi **Aggiungi e collega** e, quando Chrome lo chiede, consenti l'accesso all'IP della lampadina. Il permesso viene richiesto per l'indirizzo specifico che hai scelto.
4. Seleziona stanza e lampadina per controllare quella luce. Le matite permettono di modificare o rimuovere una luce e di gestire le stanze.

L'indirizzo IP va inserito manualmente: la ricerca automatica UDP della versione web non è disponibile in una normale estensione Chrome. Per evitare cambi di IP puoi creare una prenotazione DHCP nel router.

## Cosa puoi fare

- Organizzare fino a **50 stanze e 100 dispositivi**, con selezione e controllo individuale.
- Accendere o spegnere e regolare la luminosità; modificare temperatura e colore dove disponibili per modello e firmware.
- Usare preset di atmosfera e passare tra **tema chiaro e scuro**.
- Aprire il popup oppure una **pagina completa** con il pulsante in alto a forma di freccia.
- Provare la **demo** senza dispositivi: le luci simulate e quelle reali restano separate.
- Esportare un backup della casa o importare un `home.json` della versione web 2.1.

La regolazione di luminosità, temperatura o colore accende la luce se è spenta. Le scene di Luma sono preset locali e non modificano le scene o le programmazioni dell'app Tapo. Le strisce sono gestite come un'unica luce, non per singoli segmenti.

## Modelli e limiti

L'interfaccia propone **L510, L520, L530, L535, L610, L630, L900, L920 e L930**. L510/L520/L610 espongono accensione e luminosità; per gli altri sono proposti anche temperatura e colore se il dispositivo li supporta. L'elenco non certifica il funzionamento su ogni versione hardware o firmware.

Sono implementati KLAP v2 e TPAP via HTTP con SPAKE2+ e `password_shadow` ID 2. Altre varianti TPAP, TLS/certificati/passcode, KLAP v1 e il vecchio protocollo AES/securePassthrough non sono supportati. Una luce può funzionare nell'app Tapo e non essere compatibile con questa estensione, anche dopo aver attivato la compatibilità con terze parti. Non vengono effettuati tentativi automatici ripetuti con la password.

## Password e dati

- Luma comunica con gli indirizzi locali scelti: non usa un server Luma o telemetria. Le informazioni sulle stanze e gli IP restano nello spazio locale dell'estensione.
- Di default la password rimane solo nello spazio di sessione in memoria. Viene cancellata alla chiusura di Chrome o quando l'estensione viene ricaricata, aggiornata o disattivata. Sopravvive invece alla normale sospensione del service worker.
- **Conserva la password cifrata** è un'opzione facoltativa per account: usa AES-256-GCM e PBKDF2-SHA-256 con 310.000 iterazioni. La frase segreta di almeno 12 caratteri non viene memorizzata e serve per sbloccare l'archivio in una nuova sessione. Non è un portachiavi di sistema.
- Una password sbloccata rimane comunque in memoria durante la sessione; la cifratura non protegge da malware o da chi controlla il profilo Chrome sbloccato. Se dimentichi la frase, puoi inserire la password Tapo e creare un nuovo archivio.
- Il backup della casa **non contiene password o archivio cifrato**, ma include nomi, email e indirizzi locali: conservalo in privato. Importare un backup sostituisce la casa reale esistente e richiede di ricollegare le luci.
- Rimuovere l'estensione elimina i suoi dati locali: esporta un backup prima di disinstallarla.

## Risoluzione dei problemi

- **TPAP non supportato:** se desideri utilizzare l'opzione, attiva la compatibilità con terze parti nell'app Tapo, attendi e riprova. Se non basta, il firmware potrebbe richiedere una variante non implementata; il messaggio mostra metadati tecnici non segreti per aiutare la diagnosi.
- **Non si collega:** controlla IP, account, alimentazione, permesso Chrome e rete. Anche cookie bloccati o politiche di accesso alla rete locale possono impedire KLAP; se necessario, preferisci un'eccezione mirata all'IP invece di abbassare globalmente la protezione.
- **Non compare in Chrome:** verifica di aver selezionato la cartella con `manifest.json`, non lo ZIP. Controlla gli errori o premi Ricarica in `chrome://extensions`.
- **Controllo mancante:** verifica modello e funzioni effettive del dispositivo. Non tutti supportano colore e temperatura.
- **Importazione:** si possono importare il `home.json` della versione web 2.1 e i backup di Luma, non direttamente il vecchio `config.json` della 2.0. Le password e i dati del portachiavi non vengono importati.

## Licenza e componenti di terze parti

Il codice originale di Luma è distribuito con [licenza MIT](LICENSE). I componenti di terze parti mantengono le rispettive licenze.

Sono inclusi `@noble/curves` e `@noble/hashes` (MIT), SJCL (BSD-2-Clause) e font DM Sans/DM Serif Display (SIL OFL). Il comportamento dei protocolli è stato confrontato anche con il progetto indipendente `tapo` di Mihai Dinculescu (MIT). Le relative licenze sono nelle cartelle `licenses/` e `static/fonts/`.

# MG4 Launcher — SWI69 Color Radio

🇮🇹 [Italiano](#italiano) · 🇬🇧 [English](#english)

**Ultima versione / Latest version: [0.2.7](releases/MG4Launcher-SWI69-0.2.7.apk)**

<a href="https://www.paypal.com/pool/9tv7fQ36nA?sr=wccr"><img alt="Sostieni il progetto con PayPal · Support the project with PayPal" src="https://img.shields.io/badge/Sostieni%20il%20progetto-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white"></a>

> ⚠️ **Compatibilità / Compatibility:** solo infotainment **SWI69 Color Radio** · only **SWI69 Color Radio** infotainment.
> Garantita al momento su **MG4 standard MY23 R45**; usando i dati della FICM originale dovrebbe funzionare su tutte
> le versioni con **Color Radio** · Currently verified on **MG4 standard MY23 R45**; since it uses the original FICM's
> data it should work on every **Color Radio** version.
> Per **SWI68 SAIC 1.0+** usa [Mg4-Launcher-SWI68](https://github.com/ernestomicco2026-cell/Mg4-Launcher-SWI68) · for **SWI68 SAIC 1.0+** use [Mg4-Launcher-SWI68](https://github.com/ernestomicco2026-cell/Mg4-Launcher-SWI68).

> Progetto amatoriale e **non ufficiale**: non è affiliato né approvato da MG Motor o SAIC Motor.
> I nomi MG / MG4 servono solo a indicare l'auto compatibile (infotainment SWI69). Installalo e usalo a tuo rischio.
>
> Hobby project, **not official**: not affiliated with or endorsed by MG Motor or SAIC Motor.
> The MG / MG4 names only identify the compatible car (SWI69 infotainment). Install and use it at your own risk.

---
<img width="1920" alt="Home MG4 Launcher SWI69 0.2.7 – tema notte" src="screenshots/MG4Launcher-SWI69-0.2.7-notte.png" />
<img width="1920" alt="Home MG4 Launcher SWI69 0.2.7 – tema giorno" src="screenshots/MG4Launcher-SWI69-0.2.7-giorno.png" />
<img width="1920" alt="Home MG4 Launcher SWI69 0.2.7 – guida a destra" src="screenshots/MG4Launcher-SWI69-0.2.7-guida-a-destra.png" />
<img width="1920" alt="App MG4 Launcher SWI69 0.2.7 – tema notte" src="screenshots/MG4Launcher-SWI69-0.2.7-app-notte.png" />
<img width="1920" alt="App MG4 Launcher SWI69 0.2.7 – tema giorno" src="screenshots/MG4Launcher-SWI69-0.2.7-app-giorno.png" />
<img width="1920" alt="Impostazioni MG4 Launcher SWI69 0.2.6 – tema notte" src="screenshots/MG4Launcher-SWI69-0.2.6-impostazioni-notte.png" />
<img width="1920" alt="Impostazioni MG4 Launcher SWI69 0.2.6 – tema giorno" src="screenshots/MG4Launcher-SWI69-0.2.6-impostazioni-giorno.png" />

<sub>Anteprima della grafica 0.2.7 (home di notte, di giorno e con guida a destra; pagina App con Impostazioni e File di Android; impostazioni di notte e di giorno): i valori e le app installate sono di esempio · Preview of the 0.2.7 design (home at night, by day and with right-hand drive; Apps page with Android settings and files; settings at night and by day): values and installed apps are examples.</sub>

---

## Video recensione / Video review

<a href="https://youtu.be/JKZ0N3Lr7FI"><img width="640" alt="Video recensione di MG4 Launcher su YouTube · MG4 Launcher video review on YouTube" src="https://img.youtube.com/vi/JKZ0N3Lr7FI/hqdefault.jpg" /></a>

Guarda la [video recensione su YouTube](https://youtu.be/JKZ0N3Lr7FI) · Watch the [video review on YouTube](https://youtu.be/JKZ0N3Lr7FI).

---

## Sostieni il progetto / Support the project

MG4 Launcher è gratuito e lo resterà. Se ti è utile e ti va di contribuire alle ore passate a svilupparlo e a provarlo
in auto, puoi lasciare una piccola offerta libera. È del tutto **facoltativa**: grazie anche solo per usarlo e per
le segnalazioni.

MG4 Launcher is free and will stay free. If you find it useful and would like to support the hours spent building and
testing it in the car, you can leave a small donation of any amount. It is entirely **optional**: thank you just for
using it and reporting issues.

<a href="https://www.paypal.com/pool/9tv7fQ36nA?sr=wccr"><img alt="Sostieni il progetto con PayPal · Support the project with PayPal" src="https://img.shields.io/badge/Sostieni%20il%20progetto-PayPal-0070BA?style=for-the-badge&logo=paypal&logoColor=white"></a>

## Quale versione installare? / Which version should I install?

Ci sono **due launcher diversi**, uno per ciascun software dell'infotainment MG4. Non sono intercambiabili:
servizi dell'auto, radio, media e clima sono diversi, e con la versione sbagliata il launcher si apre ma non legge
nulla dall'auto ("Servizio veicolo non disponibile").

| Software dell'auto | Repository | File APK |
|---|---|---|
| **SWI68 SAIC 1.0+** | [Mg4-Launcher-SWI68](https://github.com/ernestomicco2026-cell/Mg4-Launcher-SWI68) | `MG4Launcher-X.Y.Z.apk` |
| **SWI69 Color Radio** | **questo repository** | `MG4Launcher-SWI69-X.Y.Z.apk` |

**Come sapere quale software hai:** con il computer collegato all'auto
`adb shell getprop ro.build.mt2712.version` → se inizia con `SWI69-` è la **SWI69**, altrimenti (es. `SWI68-…`) è la **SWI68**.
Se il launcher è già installato: impostazioni → riga **MG4 Launcher X.Y.Z · repository** sotto *Aggiornamenti*;
il nome del repository dice quale versione hai. Se nella *Diagnostica* compaiono `veicolo.bind = fallito` e
`media.usb.bind = fallito`, hai installato la versione SWI68 su un'auto SWI69 (o viceversa).

There are **two different launchers**, one per MG4 infotainment software, and they are not interchangeable.
Check your software with `adb shell getprop ro.build.mt2712.version` (starts with `SWI69-` = SWI69, otherwise SWI68) and
use the matching repository above.

---

## Italiano

Launcher in stile Tesla per l'infotainment della **MG4** con software **SWI69 Color Radio**
(EH32, Android Automotive 9, build `SWI69-29958-1100R45`).

**Compatibilità:** funziona **solo con il software SWI69 Color Radio**. La SWI69 Color Radio non è un aggiornamento
della SWI68: è un software diverso, con altri servizi dell'auto. Su SWI68 usa la [versione SWI68](https://github.com/ernestomicco2026-cell/Mg4-Launcher-SWI68).
Sostituisce la schermata home originale mantenendone le funzioni, con la stessa grafica della versione SWI68.

**Versioni dell'auto:** al momento la compatibilità è **garantita sulla MG4 standard MY23 con software R45**
(`SWI69-29958-1100R45`), dove il launcher è stato provato. Dato che usa gli stessi dati e servizi della **FICM**
originale (l'unità dell'infotainment), dovrebbe funzionare su **tutte le versioni con la radio Color Radio**.
Se lo provi su un'altra versione, le segnalazioni sono benvenute: con l'invio dei log attivo arrivano da sole.

### Cosa fa

- **Auto e batteria** (a sinistra): autonomia e **orologio** grandi, barra batteria con la percentuale accanto e la
  tacca del limite di ricarica, stato di ricarica e V2L, temperatura esterna; nelle impostazioni si attivano la percentuale
  con un decimale, la pressione degli pneumatici (TPMS) sotto le ruote e la temperatura interna. La tua MG4 ricolorabile con gli 8 colori della gamma, tetto nero opzionale.
  Tocca l'auto o V2L → app Energia. Tieni premuta l'auto → impostazioni del launcher.
- **Mappa / Media / CarPlay** (a destra):
  - **Mappa**: la SWI69 non ha un navigatore MG, si naviga con **CarPlay / Android Auto**. Senza guida una scheda
    mostra lo stato del telefono: collegato → pulsante **Apri CarPlay / Android Auto**; non collegato → ti ricorda
    di collegarlo. Durante la guida il pannello mostra le indicazioni del telefono.
  - **Media**: parte dalla sorgente in riproduzione, altrimenti dalla radio; in alto un pulsante per ogni sorgente
    (Radio, USB, Bluetooth, Online se disponibile, e CarPlay/Android Auto se il telefono è collegato);
    con più di 4 sorgenti quelle non selezionate mostrano solo l'icona.
    Per la radio: nome e logo della stazione, **preferiti** con i loghi e, a destra, **FM / DAB** con l'**elenco delle
    stazioni**: DAB dal servizio radio (**Aggiorna** per rileggerlo), FM dalla ricerca (**Cerca**) e dalle stazioni già
    ascoltate. Tocca una stazione per sintonizzarla, tienila premuta per aggiungerla o toglierla dai preferiti
    (★ = già tra i preferiti). Anche **tenendo premuto il riquadro della stazione** la aggiungi o la togli;
    tieni premuto un preferito per eliminarlo.
  - **CarPlay / Android Auto**: la scheda compare da sola quando colleghi il telefono.
  - In alto a destra, prima delle schede, una pillola con **Bluetooth** (pieno = telefono collegato, sbiadito = acceso)
    e **Wi-Fi** con 4 tacche di segnale (solo quando è collegato).
- **Barra in basso**, con comandi grandi da usare guidando: temperatura ‹ › (LO, 16…30, HI) e ventola ‹ ›
  (OFF / 1…11 / A = automatico; tocca il numero = app Clima, tieni premuto = accendi/spegni), pulsanti **A/C** e
  **AUTO** (acceso = colori invertiti), mini-player compatto, **retronebbia** (icona ambra da acceso) e
  **fendinebbia anteriori** (verde, solo se l'auto li ha), Telefono, Radio, Impostazioni.
- **App**: scorri la home verso sinistra per le app dell'auto e quelle che installi tu,
  con le icone tutte nello stesso stile; scorri verso destra per tornare. Con **Modifica** scegli quali app installate mostrare.
  In fondo alle app dell'auto ci sono anche **Impostazioni Android** e **File Android** (solo se presenti sull'auto).
- **Dimensione comandi** (Normale / Grande / Molto grande): ingrandisce pulsanti, icone e testi dei pulsanti
  in proporzione, per usarli meglio guidando.
- **Guida a destra o a sinistra**: automatica dalla configurazione dell'auto o scelta nelle impostazioni; con la
  guida a destra la home si specchia, con auto e clima dal lato del guidatore. Sulle auto che mostrano sempre la
  spia luci del sistema in alto a sinistra, quell'angolo resta libero.
- **Impostazioni** (tieni premuta l'auto), divise in sezioni con un menu laterale: **Auto** (colore, tetto nero),
  **Home** (decimali, pressione pneumatici, temperatura interna), **Aspetto** (tema, dimensione comandi, lingua),
  **Guida** (posizione di guida, home MG originale), **Sistema** (aggiornamenti, invio e salvataggio log, diagnostica).
  In fondo al menu sono sempre visibili la versione e, quando c'è, la nuova versione con **Installa**.
  Si aprono anche dall'ingranaggio in alto a destra nella pagina App.
- **Lingue**: italiano, inglese, spagnolo, tedesco, portoghese, francese e turco. Con **Come l'auto** (predefinita)
  il launcher usa la lingua di sistema e si aggiorna da solo quando la cambi nell'auto; altrimenti scegli una lingua
  nelle impostazioni. Per le lingue non tradotte usa l'inglese.
- **Schermo intero**: barra di stato e dock MG nascosti (trascina dal bordo per richiamarli).
- **Aggiornamenti**: le nuove versioni si scaricano e si installano dalle impostazioni, senza computer. Quando ne
  esce una, compare in basso a destra un piccolo avviso con **Aggiorna** / **Più tardi** (sparisce da solo; "Più tardi"
  lo rimanda di un giorno).
- **Musica che riprende da sola**: se una notifica del telefono fa passare la musica Bluetooth (o USB / online)
  alla radio, il launcher rimette in riproduzione la tua musica dopo qualche secondo. Non interviene se la radio
  l'hai scelta tu.
- **Invio dei log**: il launcher invia in automatico la diagnostica allo sviluppatore
  (all'avvio, ogni 30 minuti e con **Salva log**), per poter correggere i problemi senza dover raccogliere i file a mano.
  Contiene solo versione, valori letti dai servizi dell'auto ed errori: niente posizione, telaio o dati del telefono.
  Si disattiva nelle impostazioni (**Invio log**).

### Installazione

Solo per infotainment **SWI69 Color Radio**. Scarica l'APK dalla cartella [`releases/`](releases/).
Ci sono due modi per installarlo: da chiavetta USB, se riesci ad aprire un file manager sull'auto, oppure con adb da computer.

#### Metodo 1 — Da chiavetta USB (senza computer)

Serve poter aprire un file manager sull'infotainment.

1. Copia `MG4Launcher-SWI69-0.2.7.apk` su una chiavetta USB formattata **FAT32** o **exFAT**.
2. Inserisci la chiavetta nell'auto, apri il file manager e tocca l'APK.
3. Se Android avvisa che non può installare app da questa fonte: tocca **Impostazioni**, attiva
   **Consenti da questa fonte** per il file manager e torna indietro.
4. Tocca **Installa**.
5. Premi il tasto **Home**: Android chiede quale home usare. Scegli **MG4 Launcher** e poi **Sempre**.

Se **Impostazioni** non apre nulla o l'installazione dà errore, usa il metodo 2
(e, se puoi, segnala il messaggio d'errore).

#### Metodo 2 — Con adb da computer

Serve un computer con [adb](https://developer.android.com/tools/releases/platform-tools) collegato all'auto.
Il firmware MG può bloccare l'installazione normale delle app: l'opzione `-f` la permette.

1. Installa:
   ```bash
   adb install -r -f MG4Launcher-SWI69-0.2.7.apk
   ```
2. Rendilo la home predefinita:
   ```bash
   adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
   ```
   In alternativa premi il tasto Home: Android chiede quale home usare.

> **Se premendo Home Android non chiede quale home usare** (su alcune SWI69 il tasto apre direttamente la home
> MG): non serve fare altro, il launcher ora si sostituisce da solo alla home MG ogni volta che compare.
> Se hai un'app che rimappa i tasti (es. **MG4 Control**), controlla che il tasto Home non sia assegnato alla
> home MG.

Con entrambi i metodi il launcher parte poi da solo all'accensione dell'auto, come la home originale,
e gli aggiornamenti successivi si fanno dalle impostazioni del launcher, senza chiavetta né computer.

### Aggiornamenti

Impostazioni del launcher → **Sistema** → **Aggiornamenti** → **Controlla**.
Se c'è una versione più nuova compare **Installa** (anche in fondo al menu delle impostazioni): il launcher la installa da solo e si riavvia.
Serve che l'auto sia collegata a internet (connettività dell'auto o hotspot del telefono).
Gli aggiornamenti arrivano solo da questo repository: una versione SWI68 non viene mai proposta.
Chi ha una versione precedente alla 0.1.3 deve installare una volta a mano una versione più recente (chiavetta o adb).
Si possono installare anche da computer, con lo stesso comando dell'installazione.

### Tornare alla home MG originale

Dal launcher: impostazioni → **Apri la home originale MG**. Per renderla di nuovo quella predefinita:

```bash
adb shell cmd package set-home-activity com.saicmotor.launcher/.view.MainActivity
```

Per rimuovere del tutto il launcher: `adb uninstall com.mg4.launcher`. L'app originale non viene mai modificata.

### Comandi utili

```bash
# riavviare il launcher senza spegnere l'auto
adb shell am force-stop com.mg4.launcher
adb shell am start -n com.mg4.launcher/.MainActivity

# vedere i valori letti dall'auto (Ctrl+C per fermare)
adb logcat -s MG4L MG4L.Link

# scaricare la diagnostica salvata dal launcher (aggiornata ogni 15 s)
adb pull /sdcard/Android/data/com.mg4.launcher/files/diagnostica.txt
```

### Segnalare un problema

Nelle impostazioni la colonna **Diagnostica servizi auto** mostra i valori letti dall'auto e gli errori recenti. Uno screenshot di quella colonna
o il file `diagnostica.txt` bastano per capire cosa succede.

Con **Salva log** (in alto nella colonna Diagnostica) il launcher scrive un log completo — valori, errori, eventi
recenti e logcat del launcher — in `Android/data/com.mg4.launcher/files/mg4-launcher-log-….txt`.
Se è inserita una chiavetta USB il file finisce anche lì, nella stessa cartella.


### Versioni

#### 0.2.7
- **Il launcher parte all'accensione**: se all'avvio il sistema apre la home MG originale al posto del launcher, il
  launcher torna in primo piano da solo (al massimo 3 volte e solo nel primo minuto dopo l'accensione). Non interviene
  se in primo piano c'è un'altra app (retrocamera, CarPlay, Android Auto, chiamata, avvisi di sistema) né se hai aperto
  apposta la home MG dalle impostazioni. Se sulla tua auto il problema non c'è, il controllo non fa nulla.
- **Avvio più rapido**: il launcher attiva da solo un piccolo servizio di accessibilità ("MG4 Launcher · avvio
  rapido") che Android collega subito all'avvio del sistema, prima del segnale di avvio completato (che arriva circa
  30 secondi dopo l'accensione). Così il launcher torna davanti appena compare la home MG, invece di aspettare.
  Il servizio vede solo quale app è in primo piano, nessun contenuto, e lavora solo nel primo minuto dopo
  l'accensione o il risveglio dello schermo; gli altri servizi di accessibilità già attivi restano com'erano.
  Il controllo della prima versione resta come riserva se il servizio non è attivo.
- **Home MG aperta dal tasto Home**: su alcune SWI69 il tasto Home (o un'app che rimappa i tasti, come MG4 Control)
  apre la home MG per nome, ignorando la home predefinita. Ora, con il launcher impostato come home, il servizio
  sostituisce la home MG ogni volta che compare, non solo all'accensione. Non interviene se l'hai aperta apposta
  dalle impostazioni del launcher né se davanti c'è un'altra app; se la home MG ricompare più di 3 volte in
  10 secondi si ferma per 2 minuti. Senza il servizio attivo resta solo il controllo all'accensione.
- All'avvio il launcher riconferma di essere la home predefinita, nel caso un aggiornamento di sistema l'abbia azzerata.
- **Impostazioni Android** e **File Android** in fondo alle app dell'auto (pagina App), con nomi e icone diversi da
  quelli MG; ciascuna compare solo se l'auto ha davvero quell'app. Le Impostazioni e i File MG restano come prima.
- Pagina App: il titolo della sezione è tornato "AUTO" (nella 0.2.6 compariva "Auto").
- Diagnostica: nuove voci per l'avvio (accensione, app in primo piano, ripresa della home, home predefinita, tempi
  dell'avvio, servizio di accessibilità, home MG sostituita dal tasto Home, home predefinita, app che rimappano i
  tasti) e per le app di sistema.

#### 0.2.6
- **Nuove impostazioni**: menu delle sezioni a sinistra (Auto, Home, Aspetto, Guida, Sistema) e contenuto a destra,
  senza scorrere. Righe grandi con una breve spiegazione, interruttori e pulsanti che seguono "Dimensione comandi",
  colori del tema giorno/notte. Con la guida a destra il menu passa a destra.
- **Versione sempre visibile** in fondo al menu, con **Installa** quando è disponibile una nuova versione.
- **Musica Bluetooth che passava alla radio dopo una notifica**: se la radio parte da sola subito dopo la pausa
  della musica (Bluetooth, USB o online), dopo circa 2,5 secondi il launcher rimette in play la sorgente che stavi
  ascoltando. Non interviene se la radio l'hai scelta tu (sorgente, banda, stazione) e al massimo 2 volte al minuto.
- **Temperatura interna** attivabile dalle impostazioni (sezione Home), spenta di serie. Quando è accesa e l'auto non
  fornisce il dato compare "— °", mai 0°.
- Diagnostica: nuove voci per la ripresa automatica della musica.

#### 0.2.5
- **Dimensione comandi** nelle impostazioni: Normale, Grande (×1,15) e Molto grande (×1,3), solo per i comandi toccabili.
- **Posizione di guida** (come l'auto, sinistra o destra): con la guida a destra la home si specchia.
- Angolo in alto a sinistra libero sulle auto che mostrano sempre la spia luci del sistema.
- Musica online: il pulsante compare solo sulle auto dove la funzione è attiva (prima toccandolo spariva).

#### 0.2.4
- Temperatura interna nascosta quando l'auto non la fornisce (prima poteva comparire "0° interni").
- Pressione pneumatici: se il servizio dell'auto restituisce 0, il launcher prova a leggerla dal sistema auto di Android
  (con un nuovo permesso di sola lettura).

#### 0.2.3
- **Percentuale della batteria con un decimale** (es. 88,1%), attivabile nelle impostazioni.
- **Pressione degli pneumatici (TPMS)** sotto le ruote dell'auto disegnata (anteriori sotto la ruota davanti, posteriori
  sotto quella dietro, nell'unità scelta sul quadro; in ambra se l'auto segnala un problema), attivabile nelle impostazioni.
- **Temperatura interna** sotto quella esterna, quando l'auto la fornisce.

#### 0.2.2
- Nome corretto del software (**SWI69**) e del repository (**Mg4-Launcher-SWI69**). Se nelle impostazioni era salvato
  il vecchio repository Mg4-Launcher-SW169, il launcher passa da solo al nuovo per gli aggiornamenti.

#### 0.2.1
- Nome corretto del software: **SWI69** (non SW169) nel repository, nei file APK e nella cartella dei log.

#### 0.2.0
- **Orologio** nel riquadro batteria, grande quanto l'autonomia; percentuale accanto alla barra della batteria.
- **Clima più grande** e più facile da usare guidando: temperatura e ventola con pulsanti da 68 dp su fondo chip,
  nuovi pulsanti **A/C** e **AUTO**; barra in basso alta 100 dp e mini-player più compatto.
- **Fendinebbia**: retronebbia (e anteriori, se presenti) direttamente dalla barra in basso.
- **Indicatori Bluetooth e Wi-Fi** (4 tacche) in alto a destra.
- Con più di 4 sorgenti media, solo l'icona per quelle non selezionate.

#### 0.1.4
- **Invio automatico dei log** nel repository privato dello sviluppatore: all'avvio, ogni 30 minuti e con **Salva log**;
  senza rete restano in coda. Interruttore e stato nelle impostazioni (**Invio log**).
- **Avviso discreto di nuova versione**: piccola scheda in basso a destra con **Aggiorna** / **Più tardi**,
  controllo ogni 6 ore; sparisce da sola e "Più tardi" la rimanda di 24 ore.

#### 0.1.3
- Aggiornamenti più affidabili: niente più errore 403 durante il controllo.

#### 0.1.2
- Riquadro navigazione ridisegnato: scheda centrale con lo stato del telefono e pulsante per aprire CarPlay /
  Android Auto (o l'invito a collegare il telefono).
- Radio: elenco delle stazioni accanto ai comandi (DAB dal servizio radio, FM dalla ricerca e da quelle ascoltate),
  con FM / DAB in testa all'elenco, ★ per i preferiti, **Cerca** / **Aggiorna**; layout adattato allo schermo.
- **Multilingua**: italiano, inglese, spagnolo, tedesco, portoghese, francese e turco; scelta nelle impostazioni,
  con **Come l'auto** che segue la lingua di sistema.

#### 0.1.1
- Tolto il pulsante Telecamere: le MG4 con SWI69 non hanno la telecamera a 360°.
- Navigatore solo da CarPlay / Android Auto: tolti i pulsanti Casa / Lavoro, il pulsante "MG" e la scelta del
  navigatore predefinito nelle impostazioni; il launcher non si collega più al servizio di navigazione MG.

#### 0.1.0
- Prima versione per **SWI69 Color Radio**, con la grafica e le correzioni della versione SWI68 0.4.1.
- Servizi dell'auto riscritti per la SWI69: batteria, ricarica, V2L e clima dal servizio `caradapter`;
  limite di ricarica, potenza e consumo dal CarService (come l'app Energia).
- Clima **16–30 °C** (LO / HI agli estremi), come sulla MG4 EU con SWI69.
- Media con le sessioni standard di Android (USB, Bluetooth, online, radio); bande DAB / FM.
- Preferiti radio gestiti dal launcher (l'app Radio SWI69 non li rende leggibili alle altre app).
- V2L e auto aprono l'app Energia; pulsante Telecamere come la home originale SWI69.
- Pulsante "MG" del navigatore solo se l'auto ha un navigatore MG.
- Aggiornamenti solo da questo repository.

### Compilare

Il launcher si compila con lo script `build.sh` incluso, senza Gradle; alcuni strumenti Android necessari non sono
nel repository e vanno aggiunti in `tools/`. La versione è in `version.properties`, i testi delle 7 lingue in
`tools/strings.py`. Gli APK pubblicati sono nella cartella `releases/`.

### Come comunica con l'auto

Il launcher usa gli stessi servizi di sistema della home originale **SWI69 Color Radio**: veicolo e batteria, clima,
luci, media, radio e CarPlay / Android Auto. Legge i dati e invia all'auto solo i comandi che tocchi tu (clima,
fendinebbia, sorgenti e brani); non modifica nulla del sistema.

La mappa di CarPlay / Android Auto non può essere mostrata dentro il launcher:
il pannello Mappa mostra le indicazioni del telefono e apre CarPlay / Android Auto a schermo intero.

### Note

- L'immagine dell'auto deriva da una foto ufficiale MG; marchi e immagini appartengono ai rispettivi proprietari.
- I loghi delle stazioni FM vengono da [radio-browser.info](https://www.radio-browser.info), database pubblico e gratuito.

---

## English

Tesla-style launcher for the **MG4** infotainment system running **SWI69 Color Radio** software
(EH32, Android Automotive 9, build `SWI69-29958-1100R45`).

**Compatibility:** works **only with SWI69 Color Radio**. SWI69 Color Radio is not an update of SWI68: it is different
software with different car services. On SWI68 use the [SWI68 version](https://github.com/ernestomicco2026-cell/Mg4-Launcher-SWI68).
It replaces the original home screen, keeping its features, with the same design as the SWI68 version.

**Car versions:** compatibility is currently **verified on the MG4 standard MY23 with R45 software**
(`SWI69-29958-1100R45`). Since it uses the same data and services as the original **FICM** (the infotainment unit),
it should work on **every version with the Color Radio**. Reports from other versions are welcome.

### Features

- **Car and battery** (left): large range and **clock**, battery bar with the percentage next to it and a
  charge-limit mark, charging and V2L status, outside temperature; the settings turn on the percentage with one
  decimal, the tyre pressure (TPMS) under the wheels and the inside temperature. Your MG4, recolourable with the 8 factory colours, optional black roof.
  Tap the car or V2L → Energy app. Long-press the car → launcher settings.
- **Map / Media / CarPlay** (right):
  - **Map**: SWI69 has no MG navigator, navigation is done with **CarPlay / Android Auto**. When not guiding, a card
    shows the phone status with an **Open CarPlay / Android Auto** button (or a reminder to connect the phone);
    while guiding, the panel shows the phone's directions.
  - **Media**: starts on the source that is playing, otherwise the radio; one button per source at the top
    (Radio, USB, Bluetooth, Online when available, plus CarPlay/Android Auto when a phone is connected);
    with more than 4 sources the unselected ones show only their icon.
    Radio: station name and logo, **favourites** with logos and, on the right, **FM / DAB** with the **station list**
    (DAB from the radio service, FM from a scan and from stations you have listened to). Tap a station to tune in,
    long-press it to add/remove it from favourites (★). Long-pressing the station tile also adds/removes it.
  - **CarPlay / Android Auto**: the tab appears by itself when you connect a phone.
  - Top right, before the tabs, a pill with **Bluetooth** (solid = phone connected, faded = on) and **Wi-Fi** with
    4 signal bars (only when connected).
- **Bottom bar**, with large controls for use while driving: temperature ‹ › (LO, 16…30, HI) and fan ‹ ›
  (OFF / 1…11 / A = auto; tap the number = Climate app, long-press = climate on/off), **A/C** and **AUTO** buttons
  (on = inverted colours), compact mini player, **rear fog light** (amber icon when on) and **front fog lights**
  (green, only if fitted), Phone, Radio, Settings.
- **Apps**: swipe the home screen left for the car's apps and the ones you install; swipe right to go back.
  Use **Modifica** (Edit) to choose which installed apps to show. At the end of the car's apps there are also
  **Android settings** and **Android files** (only if present on the car).
- **Control size** (Normal / Large / Extra large): scales buttons, icons and button labels proportionally,
  for easier use while driving.
- **Right- or left-hand drive**: automatic from the car configuration or chosen in the settings; with right-hand
  drive the home screen is mirrored, with car and climate on the driver's side. On cars that always show the
  system light indicator at the top left, that corner is kept free.
- **Settings** (long-press the car), split into sections with a side menu: **Car** (colour, black roof),
  **Home** (decimals, tyre pressure, inside temperature), **Appearance** (theme, control size, language),
  **Driving** (driving side, original MG home), **System** (updates, log upload and saving, diagnostics).
  The version, and a new version with **Install** when available, are always shown at the bottom of the menu. Also available from the gear at the top right of the Apps page.
- **Full screen**: status bar and MG dock hidden (swipe from the edge to bring them back).
- **Updates**: new versions download and install from the settings, no computer needed. When one is out, a small
  notice with **Update** / **Later** appears at the bottom right (it hides by itself; "Later" waits a day).
- **Music that resumes by itself**: if a phone notification makes the Bluetooth (or USB / online) music switch to
  the radio, the launcher resumes your music after a few seconds. It does nothing if you chose the radio yourself.
- **Log upload**: the launcher automatically sends its diagnostics to the developer (at start,
  every 30 minutes and with **Save log**). Only version, values read from the car services and errors: no location,
  VIN or phone data. It can be turned off in the settings (**Log upload**).

**Languages:** Italian, English, Spanish, German, Portuguese, French and Turkish. **Same as car** (default) follows
the system language and updates by itself when you change it in the car; or pick a language in the settings.
Other system languages fall back to English.

### Installation

Only for **SWI69 Color Radio** infotainment. Download the APK from the [`releases/`](releases/) folder.
There are two ways to install it: from a USB stick, if you can open a file manager on the car, or with adb from a computer.

#### Method 1 — From a USB stick (no computer)

You need to be able to open a file manager on the infotainment.

1. Copy `MG4Launcher-SWI69-0.2.7.apk` to a USB stick formatted as **FAT32** or **exFAT**.
2. Plug it into the car, open the file manager and tap the APK.
3. If Android says it cannot install apps from this source: tap **Settings**, enable
   **Allow from this source** for the file manager and go back.
4. Tap **Install**.
5. Press the **Home** button: Android asks which home app to use. Pick **MG4 Launcher**, then **Always**.

If **Settings** does nothing or the install fails, use method 2 (and, if you can, report the error message).

#### Method 2 — With adb from a computer

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools) connected to the car.
The MG firmware may block normal app installs; the `-f` option allows it.

1. Install it:
   ```bash
   adb install -r -f MG4Launcher-SWI69-0.2.7.apk
   ```
2. Make it the default home:
   ```bash
   adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
   ```
   Or press the Home button: Android asks which home app to use.

> **If pressing Home doesn't ask which home to use** (on some SWI69 the button opens the MG home directly):
> nothing else is needed, the launcher now replaces the MG home by itself whenever it appears.
> If you have an app that remaps the buttons (e.g. **MG4 Control**), check that the Home button isn't assigned to
> the MG home.

Either way, the launcher then starts by itself when the car switches on, like the original home,
and later updates are installed from the launcher settings, with no USB stick or computer.

### Updates

Launcher settings → **System** → **Updates** → **Check**.
If a newer version exists, **Install** appears (also at the bottom of the settings menu): the launcher installs it and restarts.
The car needs an internet connection (car connectivity or phone hotspot). Updates come only from this repository:
a SWI68 build is never offered. Anyone on a version older than 0.1.3 must install a newer one once by hand
(USB stick or adb).

### Going back to the original MG home

From the launcher: settings → **Apri la home originale MG**. To make it the default again:

```bash
adb shell cmd package set-home-activity com.saicmotor.launcher/.view.MainActivity
```

To remove the launcher completely: `adb uninstall com.mg4.launcher`. The original app is never modified.

### Reporting a problem

Settings → **Diagnostica servizi auto** shows the raw values read from each car service and recent errors;
**Salva log** writes a full log to `Android/data/com.mg4.launcher/files/`: it is the quickest way to fix anything
that does not work.

### Changelog

#### 0.2.7
- **The launcher starts when the car switches on**: if at start-up the system opens the original MG home instead of the
  launcher, the launcher comes back to the front by itself (at most 3 times, and only in the first minute after
  switching on). It does nothing if another app is in front (reversing camera, CarPlay, Android Auto, call, system
  notices) or if you opened the MG home on purpose from the settings. If your car doesn't have the problem, the check
  does nothing.
- **Faster start**: the launcher turns on a small accessibility service by itself ("MG4 Launcher · quick start"),
  which Android connects right at system start-up, before the boot-completed signal (which arrives about 30 seconds
  after switching on). The launcher then comes back to the front as soon as the MG home appears, instead of waiting.
  The service only sees which app is in front, no content, and only works in the first minute after switching on or
  waking the screen; any other accessibility services already on stay as they were. The first check remains as a
  fallback if the service is not active.
- **MG home opened by the Home button**: on some SWI69 the Home button (or an app that remaps buttons, such as
  MG4 Control) opens the MG home by name, ignoring the default home. Now, with the launcher set as home, the service
  replaces the MG home every time it appears, not only at switch-on. It does nothing if you opened it on purpose from
  the launcher settings or if another app is in front; if the MG home comes back more than 3 times in 10 seconds it
  stops for 2 minutes. Without the service only the switch-on check remains.
- At start-up the launcher confirms it is the default home, in case a system update reset it.
- **Android settings** and **Android files** at the end of the car's apps (Apps page), with names and icons different
  from the MG ones; each appears only if the car really has that app. The MG Settings and Files stay as before.
- Apps page: the section title is "CAR" again (0.2.6 showed "Car").
- Diagnostics: new entries for start-up (switch-on, app in front, home recovery, default home, start-up timings,
  accessibility service, MG home replaced from the Home button, default home, button-remapping apps) and for the
  system apps.

#### 0.2.6
- **New settings**: section menu on the left (Car, Home, Appearance, Driving, System) and content on the right,
  no scrolling. Large rows with a short explanation, switches and buttons that follow "Control size", day/night
  theme colours. With right-hand drive the menu moves to the right.
- **Version always visible** at the bottom of the menu, with **Install** when a new version is available.
- **Bluetooth music switching to the radio after a notification**: if the radio starts on its own right after the
  music (Bluetooth, USB or online) pauses, after about 2.5 seconds the launcher resumes the source you were
  listening to. It does nothing if you chose the radio yourself (source, band, station), and at most twice a minute.
- **Inside temperature** can be turned on in the settings (Home section), off by default. When on and the car does
  not provide it, "— °" is shown, never 0°.
- Diagnostics: new entries for the automatic music resume.

#### 0.2.5
- **Control size** in the settings: Normal, Large (×1.15) and Extra large (×1.3), touch controls only.
- **Driving side** (same as car, left or right): with right-hand drive the home screen is mirrored.
- Top-left corner kept free on cars that always show the system light indicator.
- Online music: the button only appears on cars where the feature is enabled (it used to disappear when tapped).

#### 0.2.4
- Inside temperature hidden when the car does not provide it (it could show "0° inside").
- Tyre pressure: if the car service returns 0, the launcher tries the Android car system properties
  (with a new read-only permission).

#### 0.2.3
- **Battery percentage with one decimal** (e.g. 88.1%), can be turned on in the settings.
- **Tyre pressure (TPMS)** under the wheels of the car picture (front under the front wheel, rear under the rear one, in the
  unit chosen on the cluster; amber when the car reports a problem), can be turned on in the settings.
- **Inside temperature** under the outside one, when the car provides it.

#### 0.2.2
- Correct software name (**SWI69**) and repository name (**Mg4-Launcher-SWI69**). If the old Mg4-Launcher-SW169
  repository was saved in the settings, the launcher switches to the new one for updates by itself.

#### 0.2.1
- Correct software name: **SWI69** (not SW169) in the repository, APK files and log folder.

#### 0.2.0
- **Clock** in the battery panel, as large as the range; percentage next to the battery bar.
- **Larger climate controls** for use while driving: temperature and fan with 68 dp buttons on a chip background,
  new **A/C** and **AUTO** buttons; 100 dp bottom bar and a more compact mini player.
- **Fog lights**: rear (and front, if fitted) straight from the bottom bar.
- **Bluetooth and Wi-Fi indicators** (4 bars) at the top right.
- With more than 4 media sources, only the icon for the unselected ones.

#### 0.1.4
- **Automatic log upload** to the developer's private repository (at start, every 30 minutes, with Save log; queued
  when offline), with a switch and status in the settings.
- **Discreet new-version notice** at the bottom right with Update / Later, checked every 6 hours.

#### 0.1.3
- More reliable updates: no more 403 error while checking.

#### 0.1.2
- Redesigned navigation card with phone status and an Open CarPlay / Android Auto button.
- Radio: station list next to the controls (DAB from the radio service, FM from scan and listened stations),
  FM / DAB on top of the list, ★ for favourites, Scan / Refresh; layout adapted to the screen.
- **Multilingual**: Italian, English, Spanish, German, Portuguese, French, Turkish; chosen in the settings, with
  **Same as car** following the system language.

#### 0.1.1
- Cameras button removed: MG4 cars with SWI69 have no 360° camera.
- Navigation only through CarPlay / Android Auto: Home / Work buttons, the "MG" button and the default-navigator
  setting removed; the launcher no longer connects to the MG navigation service.

#### 0.1.0
- First version for **SWI69 Color Radio**, with the design and fixes of SWI68 version 0.4.1.
- Car services rewritten for SWI69 (`caradapter` hub, CarService `bms` properties, standard Android media sessions).
- Climate range **16–30 °C**; radio favourites kept by the launcher; V2L and the car open the Energy app;
  Cameras button like the original SWI69 home; MG navigator button only when present; updates only from this repository.

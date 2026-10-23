package com.mg4.launcher.svc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import com.mg4.launcher.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Sorgenti audio dell'auto, versione SWI69 (EH32 Color Radio), come la home originale SWI69:
 *  - USB e musica online: MediaBrowserService dell'app com.saicmotor.media
 *  - Bluetooth: A2dpMediaBrowserService di com.android.bluetooth
 *  - Radio FM/DAB: RadioMBService (sessione: titolo, testo, logo, play/pausa, ricerca) +
 *    IRadioManager di com.saicmotor.radio (frequenza, sintonia diretta, stazioni DAB)
 *  - CarPlay / Android Auto (allgo): com.allgo.rui + MediaBrowser per i metadati, come sulla SWI68
 * I preferiti radio li tiene il launcher (l'app Radio li salva in un database privato).
 */
public final class MediaClient {
    // --- sorgenti del launcher -------------------------------------------------------------
    public static final int SRC_RADIO = 1, SRC_USB = 2, SRC_ONLINE = 3, SRC_BT = 4, SRC_CARPLAY = 5, SRC_AAUTO = 6;

    public static String sourceName(Context c, int s) {
        return sourceName(s);
    }

    public static String sourceName(int s) {
        switch (s) {
            case SRC_RADIO: return "RADIO";
            case SRC_USB: return "MUSICA USB";
            case SRC_ONLINE: return "MUSICA ONLINE";
            case SRC_BT: return "BLUETOOTH";
            case SRC_CARPLAY: return "CARPLAY";
            case SRC_AAUTO: return "ANDROID AUTO";
            default: return "MEDIA";
        }
    }

    // --- MediaBrowserService (SWI69) ----------------------------------------------------------
    private static final String MEDIA_PKG = "com.saicmotor.media";
    private static final String RADIO_PKG = "com.saicmotor.radio";

    // --- radio: IRadioManager (com.android.car.radio.service) ----------------------------------
    private static final String RADIO_ACTION = "com.saicmotor.IRadioService";
    private static final String RAD = "com.android.car.radio.service.IRadioManager";
    private static final int RAD_TUNE = 1, RAD_SCAN = 8, RAD_ADD_CB = 14, RAD_CURRENT = 16, RAD_ADD_DAB_CB = 37,
            RAD_DAB_TUNE = 41, RAD_DAB_QUERY_LIST = 46;
    private static final String RAD_CB = "com.android.car.radio.service.IRadioCallback";
    private static final String DAB_CB = "com.android.car.radio.service.IDabCallback";
    private static final String ACTION_NEXT_BAND = "__ACTION_NEXT_SOURCE__";
    private static final String ACTION_PREV_BAND = "__ACTION_PREV_SOURCE__";
    /** Bande dell'app Radio SWI69 (ordine di FeatureUtil.getRadioBands: DAB, FM, AM). */
    public static final int BAND_AM = 0, BAND_FM = 1, BAND_DAB = 4;

    // --- CarPlay / Android Auto ---------------------------------------------------------------
    private static final String RUI = "com.allgo.rui.IRemoteUIService";
    private static final int RUI_LAUNCH_APP = 8, RUI_ACTIVE_DEVICE = 15, RUI_GET_STATUS = 17, RUI_MEDIA_KEY = 18;
    public static final int DEV_CARPLAY = 1, DEV_AAUTO = 2;

    /** Stazione radio (preferiti FM / DAB). Per il DAB servono tutti i campi di RadioDabStation. */
    public static final class Station {
        public int band, khz, eid, index, pty, type, channel;
        public long sid;
        public String name = "", ensemble = "";
        public String key = "";
        public Bitmap logo;

        /** Nome breve da mostrare: nome RDS/DAB o frequenza. */
        public String label() {
            if (!name.isEmpty()) return name;
            if (band == BAND_AM) return khz + " kHz";
            return String.format(com.mg4.launcher.L.locale(), "%.1f", khz / 1000f);
        }

        /** Seconda riga nell'elenco: frequenza (FM) o multiplex (DAB). */
        public String detail() {
            if (band == BAND_DAB) return ensemble.isEmpty() ? "DAB+" : "DAB+ · " + ensemble;
            if (band == BAND_AM) return khz + " kHz";
            return String.format(com.mg4.launcher.L.locale(), "%.1f MHz", khz / 1000f);
        }

        JSONObject toJson() throws Exception {
            return new JSONObject().put("band", band).put("khz", khz).put("sid", sid).put("eid", eid)
                    .put("index", index).put("pty", pty).put("type", type).put("channel", channel)
                    .put("name", name).put("ensemble", ensemble);
        }

        static Station fromJson(JSONObject o) {
            Station s = new Station();
            s.band = o.optInt("band", BAND_FM);
            s.khz = o.optInt("khz");
            s.sid = o.optLong("sid");
            s.eid = o.optInt("eid");
            s.index = o.optInt("index");
            s.pty = o.optInt("pty");
            s.type = o.optInt("type");
            s.channel = o.optInt("channel");
            s.name = o.optString("name", "");
            s.ensemble = o.optString("ensemble", "");
            return s;
        }
    }

    /** Stato media mostrato dal launcher. */
    public static final class State {
        public int source;                 // sorgente attiva o scelta
        public boolean playing;
        public String title = "";
        public String subtitle = "";
        public long positionMs = -1, durationMs = -1;
        public int radioBand = BAND_FM;
        public String radioFreq = "";      // es. "101,5"
        public String radioUnit = "MHz";
        public String stationKey = "";     // stazione in onda (per evidenziarla tra i preferiti)
        public boolean stationIsFavorite;
        public Bitmap logo;                // logo della stazione in onda, se disponibile
        public List<Station> favorites = new ArrayList<>();
        public List<Station> stations = new ArrayList<>();   // elenco della banda in onda (FM o DAB)
        public boolean scanning;                              // ricerca stazioni in corso
        public int radioKhz;                                  // frequenza FM in onda (per evidenziarla)
        public int deviceType;             // 0 nessuno, 1 CarPlay, 2 Android Auto
        public String deviceName = "";
        public final List<Integer> sources = new ArrayList<>();
        // indicazioni di navigazione da CarPlay/AA
        public boolean tbtActive;
        public String tbtDistance = "", tbtRoad = "";
    }

    public interface Listener {
        void onMediaState(State s);
    }

    /** Un MediaBrowserService con il suo controller e riconnessione automatica. */
    private final class Browser {
        final String name;
        final ComponentName cn;
        volatile MediaBrowser browser;
        volatile MediaController controller;
        final MediaController.Callback callback;

        Browser(String name, String pkg, String cls, MediaController.Callback cb) {
            this.name = name;
            this.cn = new ComponentName(pkg, cls);
            this.callback = cb;
        }

        void connect() {
            main.post(() -> {
                if (browser != null) return;
                browser = new MediaBrowser(ctx, cn, new MediaBrowser.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        try {
                            MediaController c = new MediaController(ctx, browser.getSessionToken());
                            c.registerCallback(callback != null ? callback : refresh, main);
                            controller = c;
                            Diag.put("media." + name, "collegato");
                            post(MediaClient.this::poll);
                        } catch (Exception e) {
                            Diag.error("media." + name, e.toString());
                        }
                    }

                    @Override
                    public void onConnectionSuspended() {
                        controller = null;
                        Diag.put("media." + name, "sospeso");
                        retry(5000);
                    }

                    @Override
                    public void onConnectionFailed() {
                        controller = null;
                        Diag.put("media." + name, "non disponibile");
                        retry(15000);
                    }
                }, null);
                try {
                    browser.connect();
                } catch (Exception e) {
                    Diag.error("media." + name, e.toString());
                    retry(15000);
                }
            });
        }

        void retry(long ms) {
            main.post(() -> {
                MediaBrowser b = browser;
                browser = null;
                if (b != null) {
                    try {
                        b.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                if (started) main.postDelayed(this::connect, ms);
            });
        }

        void disconnect() {
            main.post(() -> {
                MediaBrowser b = browser;
                browser = null;
                controller = null;
                if (b != null) {
                    try {
                        b.disconnect();
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        boolean ready() {
            return controller != null;
        }

        boolean isPlaying() {
            MediaController c = controller;
            PlaybackState ps = c == null ? null : c.getPlaybackState();
            return ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
        }

        /** Stato della sessione per i log: "in riproduzione", "in pausa", "fermo"… */
        String stateName() {
            MediaController c = controller;
            PlaybackState ps = c == null ? null : c.getPlaybackState();
            if (c == null) return "sessione non collegata";
            if (ps == null) return "nessuno stato";
            switch (ps.getState()) {
                case PlaybackState.STATE_PLAYING: return "in riproduzione";
                case PlaybackState.STATE_PAUSED: return "in pausa";
                case PlaybackState.STATE_STOPPED: return "fermo";
                case PlaybackState.STATE_BUFFERING: return "in caricamento";
                case PlaybackState.STATE_NONE: return "nessuno";
                default: return "stato " + ps.getState();
            }
        }

        boolean isPaused() {
            MediaController c = controller;
            PlaybackState ps = c == null ? null : c.getPlaybackState();
            return ps != null && ps.getState() == PlaybackState.STATE_PAUSED;
        }

        MediaController.TransportControls controls() {
            MediaController c = controller;
            return c == null ? null : c.getTransportControls();
        }
    }

    private final Context ctx;
    private final ServiceLink radio, rui;
    private final Browser usb, online, bt, radioMbs, remote;
    private final HandlerThread thread = new HandlerThread("mg4-media");
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences sp;
    private Listener listener;
    private volatile State last = new State();
    private volatile int chosen;               // sorgente scelta dall'utente
    private volatile Bundle tbt;
    private final StationLogos logos;
    private volatile List<Station> favorites = new ArrayList<>();
    // radio: dati dai callback di IRadioManager
    private volatile Station fmCurrent, dabCurrent;
    private volatile int bandFromCallback = -1;
    private volatile List<Station> dabList = new ArrayList<>();
    private volatile List<Station> fmList = new ArrayList<>();
    private volatile boolean scanning;
    private volatile long scanUntil;
    private volatile String dabText = "";
    private boolean started, running;

    public MediaClient(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
        radio = new ServiceLink(ctx, "radio", ServiceLink.byAction(RADIO_PKG, RADIO_ACTION));
        radio.setOnConnected(() -> post(this::registerRadioCallbacks));
        rui = new ServiceLink(ctx, "carplay", ServiceLink.byComponent("com.allgo.rui", "com.allgo.rui.RemoteUIService", null));
        usb = new Browser("usb", MEDIA_PKG, "com.saicmotor.media.mbs.Usb1MediaBrowserService", null);
        online = new Browser("online", MEDIA_PKG, "com.saicmotor.media.mbs.OnlineMediaBrowserService", null);
        bt = new Browser("bt", "com.android.bluetooth", "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService", null);
        radioMbs = new Browser("radio.sessione", RADIO_PKG, "com.saicmotor.radio.service.RadioMBService", null);
        remote = new Browser("carplay.media", "com.allgo.remoteui.mediabrowserservice",
                "com.allgo.remoteui.mediabrowserservice.RemoteUIMediaBrowserService", remoteCallback);
        logos = new StationLogos(ctx);
        logos.setOnUpdate(this::refreshLogosSoon);
        loadFavorites();
        fmList = loadList("radio_fm_elenco");
        dabList = loadList("radio_dab_elenco");
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** Chiamata in corso su CarPlay/Android Auto (stato di Allgo: com.allgo.extra.CallStatus diverso da 0). */
    public boolean remoteCallActive() {
        IBinder r = rui.binder();
        if (r == null) return false;
        try {
            android.os.Bundle b = Rpc.call(r, RUI, RUI_GET_STATUS, Rpc.NONE,
                    p -> Rpc.readNullable(p, q -> android.os.Bundle.CREATOR.createFromParcel(q)));
            return b != null && b.getInt("com.allgo.extra.CallStatus", 0) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    public State last() {
        return last;
    }

    public void start() {
        if (started) return;
        started = true;
        running = true;
        if (bg == null) {
            thread.start();
            bg = new Handler(thread.getLooper());
        }
        radio.start();
        rui.start();
        usb.connect();
        online.connect();
        bt.connect();
        radioMbs.connect();
        remote.connect();
        bg.post(loop);
    }

    /** Sospende le letture (launcher non visibile) senza scollegarsi dai servizi. */
    public void pause() {
        running = false;
        if (bg != null) bg.removeCallbacks(loop);
    }

    public void resume() {
        if (bg == null || running) return;
        running = true;
        bg.post(loop);
    }

    public void stop() {
        running = false;
        started = false;
        if (bg != null) bg.removeCallbacks(loop);
        radio.stop();
        rui.stop();
        usb.disconnect();
        online.disconnect();
        bt.disconnect();
        radioMbs.disconnect();
        remote.disconnect();
    }

    private void post(Runnable r) {
        if (bg != null) bg.post(r);
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            poll();
            bg.postDelayed(this, 1000);
        }
    };

    /** Callback generico: ogni cambio di stato/metadati aggiorna subito la UI. */
    private final MediaController.Callback refresh = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            post(MediaClient.this::poll);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            post(MediaClient.this::poll);
        }
    };

    // ------------------------------------------------------------------------------------------
    // Lettura stato
    // ------------------------------------------------------------------------------------------

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String join(String a, String b) {
        a = nz(a);
        b = nz(b);
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " · " + b;
    }

    private Browser browserFor(int src) {
        switch (src) {
            case SRC_USB: return usb;
            case SRC_ONLINE: return online;
            case SRC_BT: return bt;
            case SRC_RADIO: return radioMbs;
            case SRC_CARPLAY:
            case SRC_AAUTO: return remote;
            default: return null;
        }
    }

    private void poll() {
        State s = new State();

        // CarPlay / Android Auto collegato?
        IBinder r = rui.binder();
        if (r != null) {
            try {
                int[] dev = Rpc.call(r, RUI, RUI_ACTIVE_DEVICE, Rpc.NONE, p -> Rpc.readNullable(p, q -> {
                    int session = q.readInt();
                    String name = q.readString();
                    q.readString();
                    q.readString();
                    int type = q.readInt();
                    s.deviceName = nz(name);
                    return new int[]{type, session};
                }));
                s.deviceType = dev == null ? 0 : dev[0];
                Diag.put("carplay.dispositivo", dev == null ? "nessuno" : ("tipo " + dev[0] + ", sessione " + dev[1] + ", " + s.deviceName));
            } catch (Exception e) {
                Diag.error("carplay", e.toString());
            }
        }

        // sorgenti disponibili
        s.sources.add(SRC_RADIO);
        s.sources.add(SRC_USB);
        s.sources.add(SRC_BT);
        if (online.ready() && onlineAvailable()) s.sources.add(SRC_ONLINE);
        if (s.deviceType == DEV_CARPLAY) s.sources.add(SRC_CARPLAY);
        if (s.deviceType == DEV_AAUTO) s.sources.add(SRC_AAUTO);

        // sorgente in riproduzione (quella con la sessione in PLAYING)
        int active = 0;
        if (radioMbs.isPlaying()) active = SRC_RADIO;
        else if (usb.isPlaying()) active = SRC_USB;
        else if (bt.isPlaying()) active = SRC_BT;
        else if (online.isPlaying()) active = SRC_ONLINE;
        else if (remote.isPlaying() && s.deviceType != 0) active = s.deviceType == DEV_AAUTO ? SRC_AAUTO : SRC_CARPLAY;
        Diag.put("media.attiva", sourceName(ctx, active));
        if (s.deviceType != 0) Diag.put("carplay.riproduzione", remote.stateName());
        // quando il telefono (CarPlay / Android Auto) inizia a suonare diventa la sorgente attiva: i tasti comandano lui
        if ((active == SRC_CARPLAY || active == SRC_AAUTO) && prevActive != active && chosen != active) {
            chosen = active;
            Diag.put("carplay.sorgente", sourceName(ctx, active) + " in riproduzione: diventa la sorgente attiva");
        }
        checkHijack(active);

        // di default si vede la sorgente in riproduzione, altrimenti la radio
        int shown = chosen != 0 ? chosen : (active != 0 ? active : SRC_RADIO);
        if (!s.sources.contains(shown)) shown = active != 0 && s.sources.contains(active) ? active : SRC_RADIO;
        s.source = shown;

        try {
            if (shown == SRC_RADIO) fillRadio(s);
            else fillSession(s, shown);
        } catch (Exception e) {
            Diag.error("media." + sourceName(ctx, shown), e.toString());
        }

        Bundle t = tbt;
        if (t != null && s.deviceType != 0) {
            s.tbtActive = t.getBoolean("IS_TBT_ACTIVE", false);
            s.tbtDistance = (nz(String.valueOf(t.get("DIST_TO_NEXT_JUNCTION"))) + " "
                    + nz(String.valueOf(t.get("DIST_TO_NEXT_JUNCTION_UNIT")))).replace("null", "").trim();
            s.tbtRoad = nz(t.getString("ROAD_AFTER_JUNCTION"));
        }

        last = s;
        main.post(() -> {
            if (listener != null) listener.onMediaState(s);
        });
    }

    /** USB, Bluetooth, online, CarPlay/AA: tutto dalla sessione media. */
    private void fillSession(State s, int src) {
        Browser b = browserFor(src);
        MediaController c = b == null ? null : b.controller;
        if (c != null) {
            MediaMetadata m = c.getMetadata();
            PlaybackState ps = c.getPlaybackState();
            if (m != null) {
                s.title = nz(m.getString(MediaMetadata.METADATA_KEY_TITLE));
                s.subtitle = join(m.getString(MediaMetadata.METADATA_KEY_ARTIST), m.getString(MediaMetadata.METADATA_KEY_ALBUM));
                s.durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            }
            if (ps != null) {
                s.playing = ps.getState() == PlaybackState.STATE_PLAYING;
                long pos = ps.getPosition();
                if (s.playing && ps.getLastPositionUpdateTime() > 0) {
                    pos += (long) ((SystemClock.elapsedRealtime() - ps.getLastPositionUpdateTime()) * ps.getPlaybackSpeed());
                }
                s.positionMs = pos >= 0 ? Math.min(pos, s.durationMs > 0 ? s.durationMs : pos) : -1;
                Diag.put("media." + b.name + ".stato", ps.getState());
            }
        } else if (b != null) {
            Diag.put("media." + b.name + ".stato", "non collegato");
        }
        switch (src) {
            case SRC_USB:
                if (s.title.isEmpty()) s.title = com.mg4.launcher.L.s(R.string.no_track);
                if (s.subtitle.isEmpty()) s.subtitle = com.mg4.launcher.L.s(R.string.plug_usb);
                break;
            case SRC_BT:
                if (s.title.isEmpty()) s.title = com.mg4.launcher.L.s(R.string.bt_music);
                if (s.subtitle.isEmpty()) s.subtitle = com.mg4.launcher.L.s(R.string.bt_start);
                break;
            case SRC_ONLINE:
                if (s.title.isEmpty()) s.title = com.mg4.launcher.L.s(R.string.online_music);
                break;
            case SRC_CARPLAY:
            case SRC_AAUTO:
                if (s.title.isEmpty()) s.title = src == SRC_CARPLAY ? "CarPlay" : "Android Auto";
                if (s.subtitle.isEmpty()) s.subtitle = s.deviceName.isEmpty() ? com.mg4.launcher.L.s(R.string.from_phone) : s.deviceName;
                break;
            default:
                break;
        }
    }

    /** Radio: sessione RadioMBService (nome, testo, logo DAB, stato) + frequenza da IRadioManager. */
    private void fillRadio(State s) {
        MediaController c = radioMbs.controller;
        MediaMetadata m = c == null ? null : c.getMetadata();
        PlaybackState ps = c == null ? null : c.getPlaybackState();
        String author = m == null ? "" : nz(m.getString(MediaMetadata.METADATA_KEY_AUTHOR));
        String title = m == null ? "" : nz(m.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = m == null ? "" : nz(m.getString(MediaMetadata.METADATA_KEY_ARTIST));
        Bitmap art = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        s.playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;

        // stazione FM/AM corrente (frequenza) dal servizio radio
        IBinder b = radio.binder();
        if (b != null) {
            try {
                Station cur = Rpc.call(b, RAD, RAD_CURRENT, Rpc.NONE, p -> Rpc.readNullable(p, MediaClient::readRadioStation));
                if (cur != null) fmCurrent = cur;
            } catch (Exception e) {
                Diag.error("radio.corrente", e.toString());
            }
        }

        // la sessione radio mette "DAB" come autore per il DAB; per FM/AM nessun autore
        int band;
        if ("DAB".equalsIgnoreCase(author)) band = BAND_DAB;
        else if (m != null) band = fmCurrent != null && fmCurrent.band == BAND_AM ? BAND_AM : BAND_FM;
        else band = bandFromCallback >= 0 ? bandFromCallback : BAND_FM;
        s.radioBand = band;

        if (band == BAND_DAB) {
            Station d = currentDab(title);
            String name = !title.isEmpty() ? title : d != null ? d.name : "";
            s.title = name;
            s.radioFreq = "DAB";
            s.radioUnit = d != null ? d.ensemble : "";
            s.subtitle = !artist.isEmpty() ? artist : nz(dabText);
            s.stationKey = StationLogos.key(BAND_DAB, d != null ? d.sid : 0, 0, name);
            if (art != null) logos.put(s.stationKey, art);
            s.logo = art != null ? art : logos.get(s.stationKey, name);
        } else {
            Station f = fmCurrent;
            int khz = f != null ? f.khz : 0;
            String name = !title.isEmpty() ? title : f != null ? f.name : "";
            if (band == BAND_AM) {
                s.radioFreq = khz > 0 ? String.valueOf(khz) : "";
                s.radioUnit = "kHz";
            } else {
                s.radioFreq = khz > 0 ? String.format(com.mg4.launcher.L.locale(), "%.1f", khz / 1000f) : "";
                s.radioUnit = "MHz";
            }
            // per l'FM la sessione usa il nome RDS come titolo; se è solo la frequenza non è un nome
            s.title = name.matches("[0-9.,\\s]*(MHz|kHz)?") ? "" : name;
            s.subtitle = artist;
            s.stationKey = StationLogos.key(band, 0, khz, s.title);
            s.logo = logos.get(s.stationKey, s.title);
            s.radioKhz = khz;
            if (band == BAND_FM && khz > 0) rememberFm(khz, s.title);
        }
        // elenco delle stazioni della banda in onda
        List<Station> list = band == BAND_DAB ? dabList : fmList;
        for (Station st : list) {
            if (st.key.isEmpty()) st.key = StationLogos.key(st.band, st.sid, st.khz, st.name);
            if (st.logo == null) st.logo = logos.get(st.key, st.name);
        }
        s.stations = list;
        if (scanning && SystemClock.uptimeMillis() > scanUntil) scanning = false;
        s.scanning = scanning;
        Diag.put("radio.corrente", "banda " + band + ", " + s.radioFreq + " " + s.radioUnit + ", " + s.title
                + (ps != null ? " (stato " + ps.getState() + ")" : ""));

        List<Station> fav = favorites;
        for (Station st : fav) {
            if (st.logo == null) st.logo = logos.get(st.key, st.name);
            if (st.key.equals(s.stationKey)) s.stationIsFavorite = true;
        }
        s.favorites = fav;
        if (s.title.isEmpty()) s.title = s.radioFreq.isEmpty() ? com.mg4.launcher.L.s(R.string.src_radio) : s.radioFreq + " " + s.radioUnit;
    }

    /** Stazione DAB in onda: dal callback, oppure cercata per nome nell'elenco DAB. */
    private Station currentDab(String name) {
        Station d = dabCurrent;
        if (d != null && (name.isEmpty() || d.name.equalsIgnoreCase(name))) return d;
        for (Station x : dabList) if (x.name.equalsIgnoreCase(name)) return x;
        return d;
    }

    /** RadioStation: canale (kHz), sottocanale, banda, RadioRds (Parcelable o null). */
    private static Station readRadioStation(Parcel p) {
        Station st = new Station();
        st.khz = p.readInt();
        p.readInt();                               // sottocanale
        st.band = p.readInt();
        String cls = p.readString();               // writeParcelable: nome della classe, null se assente
        if (cls != null) {
            st.name = nz(p.readString());          // programService (nome RDS)
            p.readString();                        // artista
            p.readString();                        // titolo
        }
        return st;
    }

    /** RadioDabStation (ordine di writeToParcel). */
    private static Station readDabStation(Parcel p) {
        Station st = new Station();
        st.band = BAND_DAB;
        st.pty = p.readInt();
        st.name = nz(p.readString());
        st.sid = p.readLong();
        st.ensemble = nz(p.readString());
        st.eid = p.readInt();
        st.index = p.readInt();
        st.type = p.readInt();
        st.channel = p.readInt();
        return st;
    }

    private static void writeDabStation(Parcel p, Station st) {
        p.writeInt(1);                             // non null
        p.writeInt(st.pty);
        p.writeString(st.name);
        p.writeLong(st.sid);
        p.writeString(st.ensemble);
        p.writeInt(st.eid);
        p.writeInt(st.index);
        p.writeInt(st.type);
        p.writeInt(st.channel);
    }

    // ---- callback dal servizio radio (stazione, banda, DAB) ---------------------------------

    private final Binder radioCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(RAD_CB);
                return true;
            }
            try {
                data.enforceInterface(RAD_CB);
                if (code == 1) {                   // onRadioStationChanged(RadioStation)
                    if (data.readInt() != 0) fmCurrent = readRadioStation(data);
                } else if (code == 4) {            // onScanStarted(int banda)
                    scanning = true;
                    scanUntil = SystemClock.uptimeMillis() + 120_000;
                    Diag.put("radio.ricerca", "avviata, banda " + data.readInt());
                } else if (code == 5) {            // onScanFinished(int banda, List<RadioStation>)
                    int band = data.readInt();
                    int n = data.readInt();
                    List<Station> found = new ArrayList<>();
                    for (int i = 0; i < n && i < 200; i++) if (data.readInt() != 0) found.add(readRadioStation(data));
                    scanning = false;
                    Diag.put("radio.ricerca", "finita, banda " + band + ": " + found.size() + " stazioni");
                    if (!found.isEmpty() && band != BAND_DAB) mergeFm(found, true);
                    if (band != BAND_DAB) LogoPacks.noteStations(BAND_FM, found);
                } else if (code == 3) {            // onRadioBandChanged(int)
                    bandFromCallback = data.readInt();
                    Diag.put("radio.banda", bandFromCallback);
                }
            } catch (Exception e) {
                Diag.error("radio.callback", e.toString());
            }
            if (reply != null) reply.writeNoException();
            if (code == 1 || code == 3 || code == 4 || code == 5) post(MediaClient.this::poll);   // non per segnale, RDS…
            return true;
        }
    };

    private final Binder dabCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(DAB_CB);
                return true;
            }
            try {
                data.enforceInterface(DAB_CB);
                if (code == 2) {                   // notifyDabStationListInfoChanged(List<RadioDabStation>)
                    int n = data.readInt();
                    List<Station> out = new ArrayList<>();
                    for (int i = 0; i < n && i < 300; i++) if (data.readInt() != 0) out.add(readDabStation(data));
                    if (!out.isEmpty()) {
                        java.util.Collections.sort(out, (x, y) -> x.name.compareToIgnoreCase(y.name));
                        dabList = out;
                        saveList("radio_dab_elenco", out);
                    }
                    Diag.put("radio.dab.elenco", out.size() + " stazioni");
                    // paese RDS dalle stazioni DAB (conferma o corregge il paese automatico dei loghi)
                    LogoPacks.noteStations(BAND_DAB, out);
                } else if (code == 3) {            // notifyDabMainInfoChanged(RadioDabStation)
                    if (data.readInt() != 0) {
                        dabCurrent = readDabStation(data);
                        bandFromCallback = BAND_DAB;
                    }
                } else if (code == 4) {            // notifyDabDLSChanged(char[])
                    char[] t = data.createCharArray();
                    dabText = t == null ? "" : new String(t).trim();
                }
            } catch (Exception e) {
                Diag.error("radio.dab.callback", e.toString());
            }
            if (reply != null) reply.writeNoException();
            if (code == 2 || code == 3) post(MediaClient.this::poll);
            return true;
        }
    };

    private void registerRadioCallbacks() {
        IBinder b = radio.binder();
        if (b == null) return;
        try {
            Rpc.call(b, RAD, RAD_ADD_CB, p -> p.writeStrongBinder(radioCallback));
            Diag.put("radio.callback", "registrato");
        } catch (Exception e) {
            Diag.error("radio.callback.registra", e.toString());
        }
        try {
            Rpc.call(b, RAD, RAD_ADD_DAB_CB, p -> p.writeStrongBinder(dabCallback));
            Rpc.call(b, RAD, RAD_DAB_QUERY_LIST, Rpc.NONE);
        } catch (Exception e) {
            Diag.error("radio.dab.registra", e.toString());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Comandi
    // ------------------------------------------------------------------------------------------

    /** Passa alla sorgente precedente/successiva e la avvia. */
    public void cycleSource(int dir) {
        State s = last;
        List<Integer> list = s.sources;
        if (list.isEmpty()) return;
        int i = list.indexOf(s.source);
        int next = list.get(((i < 0 ? 0 : i) + dir + list.size()) % list.size());
        selectSource(next);
    }

    /** Mostra la sorgente e la avvia (play sulla sua sessione, come fa la home originale). */
    // --- musica online: funziona solo se l'auto ha la funzione attiva (come l'app Media originale) ---
    private static final String FEATURE_URI = "content://com.saicmotor.caradapter.featureprovider/feature/key/";
    private static final String FEATURE_ONLINE = "media_Onlinemusic___";
    private volatile String onlineFeature;      // valore della configurazione, null = non ancora letto / non leggibile
    private volatile boolean onlineFeatureRead;
    private volatile boolean onlineFailed;      // toccata ma non è partita: nascosta fino al riavvio del launcher

    /** "0" = funzione assente sull'auto: il servizio esiste ma non riproduce nulla, quindi il pulsante non si mostra. */
    private boolean onlineAvailable() {
        if (!onlineFeatureRead) {
            onlineFeatureRead = true;
            try (android.database.Cursor q = ctx.getContentResolver().query(
                    android.net.Uri.parse(FEATURE_URI + FEATURE_ONLINE), null, null, null, null)) {
                if (q != null && q.moveToNext()) onlineFeature = q.getString(0);
            } catch (Throwable e) {
                Diag.error("media.online.funzione", e.toString());
            }
            Diag.put("media.online.funzione", onlineFeature == null ? "non leggibile" : onlineFeature + (("0".equals(onlineFeature)) ? " (assente)" : ""));
        }
        if (onlineFailed) return false;
        return !"0".equals(onlineFeature);
    }

    public void selectSource(int src) {
        chosen = src;
        if (src == SRC_RADIO) markRadioByUser();
        post(() -> {
            try {
                if (src == SRC_CARPLAY || src == SRC_AAUTO) {
                    remotePlay(true);
                } else {
                    Browser b = browserFor(src);
                    MediaController.TransportControls tc = b == null ? null : b.controls();
                    if (src == SRC_BT) {
                        // Bluetooth: come la home originale SWI69 R45 si chiede il cambio sorgente all'app Media, che
                        // prende il fuoco audio, avvia l'audio A2DP e dà play. Il launcher non prende il fuoco da solo
                        // (toglierlo all'app Media la faceva mettere in pausa: play/pausa a ripetizione).
                        boolean sent = btSwitchCommand(com.saicmotor.common.CommandParcelable.SWITCH_SOURCE);
                        if (!sent && tc != null) tc.play();
                        checkBtStarted(sent || tc != null);
                    } else if (tc != null) {
                        tc.play();
                    } else {
                        Diag.put("media.avvio", sourceName(ctx, src) + ": sessione non collegata");
                    }
                    if (src == SRC_ONLINE) {
                        // se dopo qualche secondo non è partita, la funzione non c'è: si torna alla radio e si nasconde
                        bg.postDelayed(() -> {
                            if (!online.isPlaying()) {
                                onlineFailed = true;
                                Diag.put("media.online", "non parte: nascosta");
                                if (chosen == SRC_ONLINE) chosen = SRC_RADIO;
                                poll();
                            }
                        }, 6000);
                    }
                }
            } catch (Exception e) {
                Diag.error("media.avvio " + sourceName(ctx, src), e.toString());
            }
            poll();
        });
    }

    // --- Bluetooth: sorgente audio dell'auto, come l'app Media originale (com.saicmotor.media) ------------

    /**
     * Cambio sorgente alla musica Bluetooth come la home originale SWI69 R45 (ModeManager.sendCommand):
     * startService a com.saicmotor.media/.service.MediaService con extra "command" = CommandParcelable
     * (evento 32769 = cambia sorgente, 32773 = cambia e riproduci; tipo 2 = musica Bluetooth).
     */
    private boolean btSwitchCommand(int event) {
        try {
            Intent i = new Intent().setComponent(new android.content.ComponentName("com.saicmotor.media",
                    "com.saicmotor.media.service.MediaService")).setPackage("com.saicmotor.media");
            i.putExtra("command", new com.saicmotor.common.CommandParcelable(event,
                    com.saicmotor.common.CommandParcelable.TYPE_BT_MUSIC, "com.saicmotor.launcher"));
            boolean ok = ctx.startService(i) != null;
            Diag.put("media.bt.sorgente", "comando all'app Media (evento " + event + "): " + (ok ? "inviato" : "servizio non trovato"));
            return ok;
        } catch (Exception e) {
            Diag.put("media.bt.sorgente", "comando all'app Media non riuscito: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Esito dopo 4,5 secondi (l'app Media prima prende il fuoco audio e avvia l'A2DP): se il Bluetooth non è partito si ripiega sull'app Media originale (pagina Bluetooth),
     * che fa partire la musica come quando si sceglie la sorgente lì.
     */
    private void checkBtStarted(boolean sent) {
        bg.postDelayed(() -> {
            boolean playing = bt.isPlaying();
            if (playing) {
                Diag.put("media.bt.esito", "partito");
                return;
            }
            Diag.put("media.bt.esito", "non partito" + (sent ? "" : " (sessione non collegata)") + ": apro la pagina Bluetooth dell'app Media");
            try {
                Intent i = new Intent(Intent.ACTION_MAIN)
                        .setComponent(new android.content.ComponentName(com.mg4.launcher.apps.MgApps.MEDIA_PKG, com.mg4.launcher.apps.MgApps.MEDIA_CLS))
                        .putExtra("type", 2)   // MainActivity dell'app Media: type 2 = pagina Bluetooth
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
            } catch (Exception e) {
                Diag.error("media.bt.app", e.toString());
            }
            poll();
        }, 4500);
    }

    /**
     * Cambia banda (FM / DAB) come l'app Radio: azione "sorgente successiva/precedente" della sessione,
     * che riprende l'ultima stazione ascoltata su quella banda. Ordine delle bande: DAB, FM, AM.
     */
    // --- ripresa automatica: dopo una notifica il sistema a volte rimette la radio al posto della musica ---
    private static final long HIJACK_WINDOW_MS = 2500, RESUME_DELAY_MS = 2500, RADIO_USER_MS = 6000;
    private int prevActive;                    // sorgente in riproduzione al giro precedente
    private int lastMedia;                     // ultima sorgente musicale in riproduzione (BT, USB, Online)
    private long lastMediaAt;                  // quando era ancora in riproduzione
    private volatile long radioUserAt;         // ultimo comando radio dato dall'utente dal launcher
    private boolean resumePending;
    private final java.util.ArrayDeque<Long> resumes = new java.util.ArrayDeque<>();

    private Browser musicBrowser(int src) {
        return src == SRC_BT ? bt : src == SRC_USB ? usb : src == SRC_ONLINE ? online : null;
    }

    /** L'utente ha scelto o comandato la radio dal launcher: in quel caso la radio non va mai "corretta". */
    private void markRadioByUser() {
        radioUserAt = android.os.SystemClock.uptimeMillis();
    }

    /** Chiamata a ogni giro di poll(), sul thread di lavoro. */
    private void checkHijack(int active) {
        long now = android.os.SystemClock.uptimeMillis();
        if (active == SRC_BT || active == SRC_USB || active == SRC_ONLINE) {
            lastMedia = active;          // vale anche se la musica è partita dall'app di sistema
            lastMediaAt = now;
        }
        boolean radioJustStarted = active == SRC_RADIO && prevActive != SRC_RADIO;
        prevActive = active;
        if (!radioJustStarted || resumePending || lastMedia == 0) return;
        Browser b = musicBrowser(lastMedia);
        if (b == null) return;
        // la musica suonava fino a un attimo fa, ora è in pausa e la radio è partita senza un comando dal launcher
        if (now - lastMediaAt > HIJACK_WINDOW_MS) return;
        if (now - radioUserAt < RADIO_USER_MS) return;
        if (chosen == SRC_RADIO && now - radioUserAt < 60_000) return;
        final int src = lastMedia;
        resumePending = true;
        bg.postDelayed(() -> resumeIfStillHijacked(src), RESUME_DELAY_MS);
    }

    private void resumeIfStillHijacked(int src) {
        resumePending = false;
        Browser b = musicBrowser(src);
        String what = sourceName(ctx, src) + " dopo cambio a RADIO";
        if (b == null || !radioMbs.isPlaying() || !b.isPaused()) return;   // risolto da solo o cambiato dall'utente
        if (android.os.SystemClock.uptimeMillis() - radioUserAt < RADIO_USER_MS) return;
        long now = android.os.SystemClock.uptimeMillis();
        while (!resumes.isEmpty() && now - resumes.peekFirst() > 60_000) resumes.pollFirst();
        if (resumes.size() >= 2) {
            Diag.put("media.ripresaAuto", what + " (saltata: già 2 riprese nell'ultimo minuto)");
            return;
        }
        resumes.addLast(now);
        MediaController.TransportControls tc = b.controls();
        if (tc == null) {
            Diag.put("media.ripresaAuto", what + " (fallita: sessione non collegata)");
            return;
        }
        // Bluetooth: comando all'app Media come in selectSource(); le altre sorgenti: play sulla sessione
        if (src != SRC_BT || !btSwitchCommand(com.saicmotor.common.CommandParcelable.SWITCH_SOURCE_PLAY)) tc.play();
        chosen = src;
        Diag.put("media.ripresaAuto", what + " (inviata)");
        bg.postDelayed(() -> {
            Diag.put("media.ripresaAuto", what + (b.isPlaying() ? " (riuscita)" : " (fallita)"));
            poll();
        }, 3000);
    }

    public void switchBand(int band) {
        chosen = SRC_RADIO;
        markRadioByUser();
        post(() -> {
            try {
                int cur = last.radioBand;
                MediaController.TransportControls tc = radioMbs.controls();
                if (tc == null) throw new RemoteException("sessione radio non collegata");
                if (cur == band) {
                    tc.play();
                } else {
                    String action;
                    if (band == BAND_FM) action = cur == BAND_DAB ? ACTION_NEXT_BAND : ACTION_PREV_BAND;
                    else action = cur == BAND_FM ? ACTION_PREV_BAND : ACTION_NEXT_BAND;
                    tc.sendCustomAction(action, null);
                    bandFromCallback = band;
                }
                Diag.put("radio.banda.scelta", cur + " → " + band);
            } catch (Exception e) {
                Diag.error("radio.banda", e.toString());
            }
            poll();
        });
    }

    /** Sintonizza una stazione dei preferiti e passa alla radio. */
    public void tune(Station st) {
        chosen = SRC_RADIO;
        markRadioByUser();
        post(() -> {
            try {
                IBinder b = radio.binder();
                if (b == null) throw new RemoteException("servizio radio non collegato");
                if (st.band == BAND_DAB) {
                    Station d = st;
                    if (d.sid == 0) for (Station x : dabList) if (x.name.equalsIgnoreCase(st.name)) { d = x; break; }
                    Station dd = d;
                    Rpc.call(b, RAD, RAD_DAB_TUNE, p -> writeDabStation(p, dd));
                    bandFromCallback = BAND_DAB;
                } else {
                    Rpc.call(b, RAD, RAD_TUNE, p -> {
                        p.writeInt(1);             // non null
                        p.writeInt(st.khz);
                        p.writeInt(0);
                        p.writeInt(st.band);
                        p.writeString(null);       // RadioRds assente
                    });
                    bandFromCallback = st.band;
                }
                Diag.put("radio.sintonia", st.label());
            } catch (Exception e) {
                Diag.error("radio.sintonia", e.toString());
            }
            poll();
        });
    }

    // ---- elenco stazioni ---------------------------------------------------------------------

    private List<Station> loadList(String key) {
        List<Station> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                Station st = Station.fromJson(a.getJSONObject(i));
                st.key = StationLogos.key(st.band, st.sid, st.khz, st.name);
                out.add(st);
            }
        } catch (Exception e) {
            Diag.error("radio.elenco", e.toString());
        }
        return out;
    }

    private void saveList(String key, List<Station> list) {
        JSONArray a = new JSONArray();
        try {
            for (Station st : list) a.put(st.toJson());
        } catch (Exception e) {
            Diag.error("radio.elenco.salva", e.toString());
        }
        sp.edit().putString(key, a.toString()).apply();
    }

    /** Unisce stazioni FM all'elenco (stessa frequenza = stessa stazione); replace = risultato di una ricerca completa. */
    private void mergeFm(List<Station> found, boolean replace) {
        java.util.Map<Integer, Station> byKhz = new java.util.TreeMap<>();
        if (!replace) for (Station st : fmList) byKhz.put(st.khz, st);
        for (Station st : found) {
            if (st.khz <= 0 || st.band == BAND_DAB) continue;
            Station old = byKhz.get(st.khz);
            if (old != null && st.name.isEmpty()) st.name = old.name;
            if (replace && old == null) {
                for (Station prev : fmList) if (prev.khz == st.khz && st.name.isEmpty()) st.name = prev.name;
            }
            st.band = BAND_FM;
            st.key = StationLogos.key(BAND_FM, 0, st.khz, st.name);
            byKhz.put(st.khz, st);
        }
        List<Station> out = new ArrayList<>(byKhz.values());
        fmList = out;
        saveList("radio_fm_elenco", out);
        Diag.put("radio.fm.elenco", out.size() + " stazioni");
    }

    /** Ogni stazione FM ascoltata con il suo nome RDS entra nell'elenco (anche senza ricerca). */
    private void rememberFm(int khz, String name) {
        for (Station st : fmList) {
            if (st.khz == khz) {
                if (st.name.isEmpty() && !name.isEmpty()) {
                    st.name = name;
                    st.key = StationLogos.key(BAND_FM, 0, khz, name);
                    saveList("radio_fm_elenco", fmList);
                }
                return;
            }
        }
        if (name.isEmpty()) return;
        Station st = new Station();
        st.band = BAND_FM;
        st.khz = khz;
        st.name = name;
        List<Station> one = new ArrayList<>();
        one.add(st);
        mergeFm(one, false);
    }

    /** FM: ricerca completa delle stazioni (scan dell'app Radio). DAB: aggiorna l'elenco dal servizio. */
    public void searchStations() {
        markRadioByUser();
        int band = last.radioBand;
        post(() -> {
            try {
                IBinder b = radio.binder();
                if (b == null) throw new RemoteException("servizio radio non collegato");
                if (band == BAND_DAB) {
                    Rpc.call(b, RAD, RAD_DAB_QUERY_LIST, Rpc.NONE);
                } else {
                    scanning = true;
                    scanUntil = SystemClock.uptimeMillis() + 120_000;
                    Rpc.call(b, RAD, RAD_SCAN, Rpc.NONE);
                }
                Diag.put("radio.ricerca", "richiesta, banda " + band);
            } catch (Exception e) {
                scanning = false;
                Diag.error("radio.ricerca", e.toString());
            }
            poll();
        });
    }

    /** Aggiunge o toglie dai preferiti una stazione dell'elenco. @return true se ora è tra i preferiti */
    public boolean toggleFavorite(Station st) {
        List<Station> list = new ArrayList<>(favorites);
        for (int i = 0; i < list.size(); i++) {
            Station f = list.get(i);
            boolean same = st.band == BAND_DAB ? f.band == BAND_DAB && (f.sid == st.sid || f.key.equals(st.key))
                    : f.band != BAND_DAB && f.khz == st.khz;
            if (same) {
                list.remove(i);
                saveFavorites(list);
                post(this::poll);
                return false;
            }
        }
        Station c = new Station();
        c.band = st.band;
        c.khz = st.khz;
        c.sid = st.sid;
        c.eid = st.eid;
        c.index = st.index;
        c.pty = st.pty;
        c.type = st.type;
        c.channel = st.channel;
        c.name = st.name;
        c.ensemble = st.ensemble;
        c.key = st.key;
        c.logo = st.logo;
        list.add(c);
        saveFavorites(list);
        post(this::poll);
        return true;
    }

    /** true se la stazione dell'elenco è già tra i preferiti. */
    public boolean isFavorite(Station st) {
        for (Station f : favorites) {
            if (st.band == BAND_DAB ? f.band == BAND_DAB && (f.sid == st.sid || f.key.equals(st.key))
                    : f.band != BAND_DAB && f.khz == st.khz) return true;
        }
        return false;
    }

    // ---- preferiti radio del launcher ------------------------------------------------------

    private void loadFavorites() {
        List<Station> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("radio_preferiti", "[]"));
            for (int i = 0; i < a.length(); i++) {
                Station st = Station.fromJson(a.getJSONObject(i));
                st.key = StationLogos.key(st.band, st.sid, st.khz, st.name);
                out.add(st);
            }
        } catch (Exception e) {
            Diag.error("radio.preferiti", e.toString());
        }
        favorites = out;
    }

    private void saveFavorites(List<Station> list) {
        JSONArray a = new JSONArray();
        try {
            for (Station st : list) a.put(st.toJson());
        } catch (Exception e) {
            Diag.error("radio.preferiti.salva", e.toString());
        }
        sp.edit().putString("radio_preferiti", a.toString()).apply();
        favorites = list;
        Diag.put("radio.preferiti", list.size() + " stazioni");
    }

    /**
     * Aggiunge ai preferiti la stazione in onda, o la toglie se c'è già.
     * @return true se ora è tra i preferiti, false se è stata tolta, null se non c'è una stazione valida
     */
    public Boolean toggleCurrentFavorite() {
        State s = last;
        if (s.source != SRC_RADIO || s.stationKey.isEmpty()) return null;
        List<Station> list = new ArrayList<>(favorites);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key.equals(s.stationKey)) {
                list.remove(i);
                saveFavorites(list);
                post(this::poll);
                return false;
            }
        }
        Station st;
        if (s.radioBand == BAND_DAB) {
            Station d = currentDab(s.title);
            if (d == null || d.sid == 0) return null;
            st = new Station();
            st.band = BAND_DAB;
            st.pty = d.pty;
            st.name = d.name.isEmpty() ? s.title : d.name;
            st.sid = d.sid;
            st.ensemble = d.ensemble;
            st.eid = d.eid;
            st.index = d.index;
            st.type = d.type;
            st.channel = d.channel;
        } else {
            Station f = fmCurrent;
            if (f == null || f.khz <= 0) return null;
            st = new Station();
            st.band = f.band;
            st.khz = f.khz;
            st.name = s.title.equals(s.radioFreq + " " + s.radioUnit) ? "" : s.title;
        }
        st.key = s.stationKey;
        st.logo = s.logo;
        list.add(st);
        saveFavorites(list);
        post(this::poll);
        return true;
    }

    public void removeFavorite(Station st) {
        List<Station> list = new ArrayList<>(favorites);
        list.remove(st);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).key.equals(st.key)) list.remove(i);
        saveFavorites(list);
        post(this::poll);
    }

    private void refreshLogosSoon() {
        post(this::poll);
    }

    // ---- play / pausa / salto ------------------------------------------------------------------

    public void playPause() {
        if (last.source == SRC_RADIO) markRadioByUser();
        post(() -> {
            State s = last;
            try {
                if (s.source == SRC_CARPLAY || s.source == SRC_AAUTO) {
                    remotePlay(!remote.isPlaying());
                } else {
                    Browser b = browserFor(s.source);
                    MediaController.TransportControls tc = b == null ? null : b.controls();
                    if (s.source == SRC_BT && !s.playing
                            && btSwitchCommand(com.saicmotor.common.CommandParcelable.SWITCH_SOURCE_PLAY)) {
                        // ripresa del Bluetooth come la home originale: fuoco audio e A2DP li gestisce l'app Media
                    } else if (tc != null) {
                        if (s.playing) tc.pause();
                        else tc.play();
                    }
                }
            } catch (Exception e) {
                Diag.error("media.play", e.toString());
            }
            poll();
        });
    }

    public void skip(boolean forward) {
        if (last.source == SRC_RADIO) markRadioByUser();
        post(() -> {
            State s = last;
            try {
                if (s.source == SRC_CARPLAY || s.source == SRC_AAUTO) {
                    // CarPlay / Android Auto: sempre i tasti del servizio RemoteUI, come la home originale
                    if (!remoteKey(forward ? "avanti" : "indietro", forward ? 7 : 8, forward ? 11 : 12)) {
                        MediaController.TransportControls tc = remote.controls();
                        if (tc != null) {
                            if (forward) tc.skipToNext();
                            else tc.skipToPrevious();
                            Diag.put("carplay.tasto", (forward ? "avanti" : "indietro") + " · RemoteUI non collegato: MediaController");
                        }
                    }
                } else {
                    Browser b = browserFor(s.source);
                    MediaController.TransportControls tc = b == null ? null : b.controls();
                    if (tc != null) {
                        if (forward) tc.skipToNext();
                        else tc.skipToPrevious();
                    }
                }
            } catch (Exception e) {
                Diag.error("media.salto", e.toString());
            }
            poll();
        });
    }

    /**
     * Play o pausa per CarPlay / Android Auto. Come la home originale (CPAAModel.mediaControlCPAA) si manda sempre il
     * tasto del servizio RemoteUI: 20 premuto / 21 rilasciato, un tasto unico che commuta. Per questo si preme solo se
     * lo stato attuale della sessione è diverso da quello voluto (altrimenti scegliendo la sorgente CarPlay la musica
     * andrebbe in pausa). Il MediaController di Android si usa solo se il servizio RemoteUI non è collegato: molti
     * adattatori wireless (es. AutoKit) lo ignorano.
     */
    private void remotePlay(boolean play) throws RemoteException {
        boolean playingNow = remote.isPlaying();
        if (playingNow == play) {
            Diag.put("carplay.tasto", (play ? "play" : "pausa") + " · già " + remote.stateName() + ": nessun tasto");
            return;
        }
        if (remoteKey(play ? "play" : "pausa", 20, 21)) return;
        MediaController.TransportControls tc = remote.controls();
        if (tc != null) {
            if (play) tc.play();
            else tc.pause();
            Diag.put("carplay.tasto", (play ? "play" : "pausa") + " · RemoteUI non collegato: MediaController");
        } else {
            Diag.put("carplay.tasto", (play ? "play" : "pausa") + " · né RemoteUI né sessione collegati");
        }
    }

    /** Tasto media del servizio RemoteUI (sendMediaPlayControlKey, transazione 18): premuto e poi rilasciato. */
    private boolean remoteKey(String what, int down, int up) {
        IBinder r = rui.binder();
        if (r == null) return false;
        try {
            Integer a = Rpc.call(r, RUI, RUI_MEDIA_KEY, p -> p.writeInt(down), Parcel::readInt);
            Integer b = Rpc.call(r, RUI, RUI_MEDIA_KEY, p -> p.writeInt(up), Parcel::readInt);
            Diag.put("carplay.tasto", what + " · sendMediaPlayControlKey " + down + "/" + up + " → " + a + "/" + b);
            return true;
        } catch (Exception e) {
            Diag.error("carplay.tasto", what + ": " + e);
            return false;
        }
    }

    /** Apre CarPlay o Android Auto a schermo intero. */
    public boolean openProjection() {
        int type = last.deviceType;
        Intent i = new Intent(Intent.ACTION_MAIN);
        if (type == DEV_AAUTO) {
            i.setComponent(new ComponentName("com.allgo.app.androidauto", "com.allgo.app.androidauto.ProjectionActivity"));
        } else {
            i.setComponent(new ComponentName("com.allgo.carplay.service", "com.allgo.carplay.service.CarPlayActivity"));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            Diag.error("carplay.apri", e.toString());
            return false;
        }
    }

    /** Apre l'app di navigazione di CarPlay/AA (launchApp 2, tipo dispositivo). */
    public void openRemoteNavigation() {
        int type = last.deviceType;
        post(() -> {
            try {
                Rpc.call(rui.binder(), RUI, RUI_LAUNCH_APP, p -> {
                    p.writeInt(2);
                    p.writeInt(type);
                }, Parcel::readInt);
            } catch (Exception e) {
                Diag.error("carplay.navigazione", e.toString());
            }
        });
    }

    // ------------------------------------------------------------------------------------------
    // CarPlay / Android Auto: indicazioni di navigazione dalla sessione remota
    // ------------------------------------------------------------------------------------------

    private final MediaController.Callback remoteCallback = new MediaController.Callback() {
        @Override
        public void onSessionEvent(String event, Bundle extras) {
            if ("ACTION_CARPLAY_NAVI_METADATA".equals(event) && extras != null) {
                tbt = extras;
                Diag.put("carplay.navigazione", extras.toString());
            }
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            if (extras != null && extras.containsKey("IS_TBT_ACTIVE")) tbt = extras;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            post(MediaClient.this::poll);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            post(MediaClient.this::poll);
        }

        @Override
        public void onSessionDestroyed() {
            remote.retry(5000);
        }
    };
}

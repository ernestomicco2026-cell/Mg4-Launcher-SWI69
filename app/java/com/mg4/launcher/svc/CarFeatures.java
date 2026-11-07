package com.mg4.launcher.svc;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Configurazione dell'auto che serve alla grafica della home: lato di guida e spia luci disegnata dal sistema
 * in alto a sinistra. Si prova da più fonti, perché non tutte esistono su ogni firmware:
 * <ul>
 *   <li>impostazione di sistema {@code wheel_position} (1 sinistra, 2 destra);</li>
 *   <li>proprietà del CarService INFO_DRIVER_SEAT (sedile del guidatore: 1 sinistra, 4 destra);</li>
 *   <li>fornitore della configurazione delle app originali ({@code com.saicmotor.caradapter.featureprovider}).</li>
 * </ul>
 * Tutti i valori letti finiscono nella diagnostica ({@code auto.config.*}).
 */
public final class CarFeatures {
    private static final String FEATURE_URI = "content://com.saicmotor.caradapter.featureprovider/feature/key/";
    /** VehicleProperty.INFO_DRIVER_SEAT (Android 9). */
    private static final int INFO_DRIVER_SEAT = 356516106;
    private static final int SEAT_ROW_1_LEFT = 1, SEAT_ROW_1_RIGHT = 4;

    private static final Map<String, String> cache = new HashMap<>();
    private static Boolean rhd, indicator;

    private CarFeatures() {}

    /** Guida a destra secondo l'auto (false se non si riesce a saperlo). */
    public static synchronized boolean isRightHandDrive(Context c) {
        if (rhd != null) return rhd;
        int wheel = -1;
        try {
            wheel = Settings.System.getInt(c.getContentResolver(), "wheel_position", -1);
        } catch (Throwable ignored) {
        }
        int seat = Integer.MIN_VALUE;
        boolean carReady = false;
        try {
            CarProps p = VehicleClient.carProps();
            carReady = p != null && p.ready();
            if (carReady) seat = p.getIntQuiet(INFO_DRIVER_SEAT);
        } catch (Throwable ignored) {
        }
        boolean r = wheel == 2 || seat == SEAT_ROW_1_RIGHT;
        boolean known = wheel == 1 || wheel == 2 || seat == SEAT_ROW_1_LEFT || seat == SEAT_ROW_1_RIGHT;
        Diag.put("auto.config.guida", (r ? "destra" : known ? "sinistra" : "sconosciuta (sinistra)")
                + " · wheel_position " + wheel + " · sedile guida " + (seat == Integer.MIN_VALUE ? "?" : String.valueOf(seat)));
        // se il CarService non era ancora pronto, si riprova al prossimo avvio della home
        if (known || carReady) rhd = r;
        return r;
    }

    private static long lastTry;

    /**
     * Lato di guida appena diventa noto (per ricostruire la home se all'avvio il CarService non era pronto);
     * null finché non si sa. Riprova al massimo ogni 5 secondi.
     */
    public static synchronized Boolean knownRhd(Context c) {
        if (rhd != null) return rhd;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastTry < 5000) return null;
        lastTry = now;
        isRightHandDrive(c);
        return rhd;
    }

    /** La barra di sistema disegna sempre la spia luci in alto a sinistra? */
    public static synchronized boolean hasSystemLightIndicator(Context c) {
        if (indicator != null) return indicator;
        String rear = feature(c, "systemui_state_rearfog__");
        String rearNew = feature(c, "systemui_state_rearfognew__");
        String front = feature(c, "systemui_state_frontfog__");
        String frontNew = feature(c, "systemui_state_frontfognew__");
        String hbRear = feature(c, "hiboard_RearFog");
        String hbFront = feature(c, "hiboard_Frontfog");
        boolean on = "1".equals(rear) || "1".equals(rearNew);
        Diag.put("auto.config.spiaLuci", (on ? "sì" : "no") + " · rearfog " + rear + "/" + rearNew + " · frontfog " + front + "/"
                + frontNew + " · hiboard " + hbRear + "/" + hbFront);
        indicator = on;
        return on;
    }

    // ---- 0.4.2 (punto 2): pulsante fendinebbia della SystemUI ---------------------------------------------------
    // Verificato nella SystemUI R45 (StatusBarView + CarLampController.addBallView): dopo il primo disegno della barra
    // di stato la SystemUI nasconde l'icona iv_car_lamp e mette al suo posto una finestra a sé (FloatBallView) di tipo
    // 2021 (TYPE_BOOT_PROGRESS), larga 56 px (+61 px con i fendinebbia anteriori), a x = 20 px dal bordo sinistro.
    //  a) i flag della barra di stato / modalità immersiva nascondono solo la barra (tipo 2000), non quella finestra;
    //     un'altra app non può nascondere o togliere finestre della SystemUI: non si può fare in modo pulito.
    //  b) una finestra di tipo 2017/2010 (come MG4 Control) in Android 9 sta SOTTO il tipo 2021 (strato 19/26 contro 32):
    //     non la copre. Coprirla con un'altra finestra 2021 dipende dall'ordine di aggiunta (la SystemUI la ricrea e torna
    //     sopra) e resterebbe sopra le altre app se il launcher si chiude male: non è sicuro.
    //  c) soluzione applicata: la home lascia libero l'angolo, misurato quando possibile dalla finestra vera.

    private static final String PREF_INSET = "spiaLarghezza";

    /** Larghezza (dp) da lasciare libera in alto a sinistra: misura salvata o stima dalla configurazione. */
    public static int systemLightInset(Context c) {
        int saved = c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE).getInt(PREF_INSET, -1);
        if (saved > 0) return saved;
        boolean front = "1".equals(feature(c, "systemui_state_frontfog__"));
        return front ? 150 : 88;   // 20 margine + 56 icona (+ 5 + 56 anteriore) + aria
    }

    private static long probedAt;

    /**
     * Misura la finestra della spia (dumpsys window, permesso DUMP dalla firma platform) e scrive luci.pulsanteSistema.
     * true se la larghezza da lasciare libera è cambiata (la home va ricostruita). Va chiamata da un thread in background.
     */
    public static synchronized boolean probeSystemLight(Context c) {
        long t = android.os.SystemClock.elapsedRealtime();
        if (probedAt > 0 && t - probedAt < 120_000L) return false;
        probedAt = t;
        String dump = dumpWindows(c);
        StringBuilder sb = new StringBuilder("soluzione: angolo lasciato libero (la finestra della SystemUI non si nasconde né si copre in modo sicuro)");
        int need = -1;
        if (dump == null) {
            sb.append(" · finestra non misurabile (dump non disponibile): stima ").append(systemLightInset(c)).append(" px");
        } else {
            boolean found = false;
            String[] blocks = dump.split("\n  Window #");
            for (String b : blocks) {
                if (!b.contains("com.android.systemui") || !(b.contains("ty=BOOT_PROGRESS") || b.contains("ty=2021"))) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("mFrame=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]").matcher(b);
                if (!m.find()) m = java.util.regex.Pattern.compile("frame=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]").matcher(b);
                boolean vis = b.contains("mViewVisibility=0x0") && !b.contains("mHasSurface=false");
                if (m.find(0)) {
                    int l = Integer.parseInt(m.group(1)), tp = Integer.parseInt(m.group(2)), r = Integer.parseInt(m.group(3)), bt = Integer.parseInt(m.group(4));
                    found = true;
                    sb.append(" · finestra spia (SystemUI tipo 2021) ").append(vis ? "VISIBILE" : "non visibile").append(" [")
                            .append(l).append(',').append(tp).append("][").append(r).append(',').append(bt).append(']');
                    if (vis && l < 400 && tp < 200) need = Math.max(need, r + 12);
                }
            }
            if (!found) sb.append(" · nessuna finestra spia della SystemUI");
            sb.append(dump.contains("StatusBar") ? " · barra di stato presente" : "");
        }
        boolean changed = false;
        if (need > 0) {
            int dp = Math.round(need / Math.max(0.5f, c.getResources().getDisplayMetrics().density));
            dp = Math.max(64, Math.min(260, dp));
            android.content.SharedPreferences sp = c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
            if (sp.getInt(PREF_INSET, -1) != dp) {
                sp.edit().putInt(PREF_INSET, dp).apply();
                changed = true;
            }
        }
        sb.append(" · spazio libero ").append(systemLightInset(c)).append(" dp");
        Diag.put("luci.pulsanteSistema", sb.toString());
        return changed;
    }

    /** dumpsys window windows, letto con il binder del servizio finestre (solo lettura). null se non disponibile. */
    private static String dumpWindows(Context c) {
        java.io.File f = new java.io.File(c.getCacheDir(), "finestre.txt");
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            android.os.IBinder b = (android.os.IBinder) sm.getMethod("getService", String.class).invoke(null, "window");
            if (b == null) return null;
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) {
                b.dump(os.getFD(), new String[]{"windows"});
            }
            byte[] all = java.nio.file.Files.readAllBytes(f.toPath());
            String s = new String(all, "UTF-8");
            if (s.contains("Permission Denial")) return null;
            return s;
        } catch (Throwable e) {
            return null;
        } finally {
            f.delete();
        }
    }

    private static Boolean providerExists;

    /**
     * Il fornitore di configurazione SAIC esiste? Sulla SWI68 no: si verifica una volta sola con
     * resolveContentProvider e poi non lo si interroga più (evita errori a ogni avvio).
     */
    public static synchronized boolean hasFeatureProvider(Context c) {
        if (providerExists == null) {
            boolean ok = false;
            try {
                ok = c.getPackageManager().resolveContentProvider("com.saicmotor.caradapter.featureprovider", 0) != null;
            } catch (Throwable ignored) {
            }
            providerExists = ok;
            Diag.put("auto.config.fornitore", ok ? "presente" : "assente (non interrogato)");
        }
        return providerExists;
    }

    /** Valore di una chiave del fornitore di configurazione, "-" se non esiste. */
    private static String feature(Context c, String key) {
        if (cache.containsKey(key)) return cache.get(key);
        if (!hasFeatureProvider(c)) return "-";
        String v = "-";
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Uri.parse(FEATURE_URI + key), null, null, null, null);
            if (cur != null && cur.moveToFirst() && cur.getColumnCount() > 0) {
                String s = cur.getString(0);
                if (s != null) v = s.trim();
            }
        } catch (Throwable ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        cache.put(key, v);
        return v;
    }
}

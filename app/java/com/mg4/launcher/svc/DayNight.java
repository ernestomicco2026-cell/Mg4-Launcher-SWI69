package com.mg4.launcher.svc;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 0.4.2 (punto 1): tema giorno/notte automatico, uguale in tutte le versioni del launcher.
 * <p>
 * Come fa la home originale MG (firmware SWI69 R45): non ha una logica sua, usa le risorse "notte" di Android, cioè
 * uiMode. uiMode lo cambia solo il CarService (CarNightService) quando arriva la proprietà del veicolo NIGHT_MODE
 * (287310855). Sulla SWI69 quella proprietà non cambia mai, quindi anche le app originali restano in giorno.
 * <p>
 * Catena delle fonti (la prima affidabile decide):
 * <ol>
 *   <li>uiMode di sistema, solo se nelle ultime 12 ore ha detto almeno una volta "notte";</li>
 *   <li>la stessa fonte della home originale letta direttamente: proprietà NIGHT_MODE, con la stessa regola;</li>
 *   <li>fari: anabbaglianti o luci di posizione accesi in automatico = notte (l'interruttore manuale non conta);</li>
 *   <li>alba e tramonto calcolati offline dalla posizione (se manca: fuso orario e centro dell'Italia).</li>
 * </ol>
 * Il cambio di tema aspetta 60 s di valore stabile (gallerie). La scelta manuale Giorno/Notte ha sempre la precedenza.
 */
public final class DayNight {
    private DayNight() {}

    public interface Listener {
        void onDayNightChanged(boolean day);
    }

    /** Proprietà del veicolo (YFVehicleProperty del firmware R45). */
    public static final int NIGHT_MODE = 287310855, PMS_SYSTEM_DAY_NIGHT_MODE = 289442317, FICM_DAY_NIGHT_MD = 557873928,
            DIM_LGHT_LVL = 559971102, DISPLAY_BRIGHTNESS = 289409539, DIPD_BEAM_LGHT = 555782944, VEH_SIDE_LGHT = 557880099,
            LIGHT_SWITCH_POS_STS = 557880119;

    private static final long STABLE_MS = 60_000L, TRUST_MS = 12 * 3600_000L, CANDIDATES_EVERY = 20_000L;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Listener listener;
    private static Boolean applied;
    private static boolean desired = true;
    private static long desiredSince;
    private static String lastChange = "—", source = "—";
    private static long candidatesAt;
    private static String candidates = "";
    private static volatile boolean visible;

    // ultimi valori letti
    private static volatile int lowBeam = -1, sideLight = -1, lightSwitch = -1;
    private static volatile int nightProp = -1;   // 1 notte, 0 giorno, -1 non letta

    public static synchronized void setListener(Listener l) {
        listener = l;
    }

    /** Launcher visibile? Se non lo è il cambio si applica subito, senza attese (nessuno lo vede). */
    public static void setVisible(boolean v) {
        visible = v;
    }

    /** Stato luci dal servizio luci (VehicleClient): -1 se non letto. */
    public static boolean lightsOn() {
        return lowBeam == 1 || sideLight == 3 || lightSwitch == 1;
    }

    /** Fari letti dall'auto? (per l'auto disegnata: se non si sa, resta com'è) */
    public static boolean lightsKnown() {
        return lowBeam >= 0 || sideLight >= 0;
    }

    static void setLamps(int low, int side, int sw) {
        lowBeam = low;
        sideLight = side;
        lightSwitch = sw;
    }

    // ---- fiducia nelle fonti che "dicono sempre giorno" -----------------------------------------

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
    }

    private static boolean trusted(Context c, String key, boolean nightNow) {
        long now = System.currentTimeMillis();
        SharedPreferences s = sp(c);
        if (nightNow) {
            if (now - s.getLong("tema.notteVista." + key, 0) > 60_000L) s.edit().putLong("tema.notteVista." + key, now).apply();
            return true;
        }
        long seen = s.getLong("tema.notteVista." + key, 0);
        return seen > 0 && now - seen < TRUST_MS && now >= seen;
    }

    // ---- valutazione -----------------------------------------------------------------------------

    /** Tema da applicare adesso in automatico (senza attese): true = giorno. */
    public static synchronized boolean evaluate(Context c) {
        String vUi, vProp, vLamp, vSun;
        boolean day;
        String src;

        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean uiNight = night == Configuration.UI_MODE_NIGHT_YES;
        boolean uiTrust = trusted(c, "uimode", uiNight);
        vUi = (uiNight ? "notte" : night == Configuration.UI_MODE_NIGHT_NO ? "giorno" : "non definito") + (uiTrust ? "" : " (mai notte nelle ultime 12 h: ignorato)");

        int np = nightProp;
        boolean propTrust = np >= 0 && trusted(c, "nightmode", np == 1);
        vProp = np < 0 ? "non letta" : (np == 1 ? "notte" : "giorno") + (propTrust ? "" : " (mai notte nelle ultime 12 h: ignorata)");

        boolean manual = lightSwitch == 1;
        boolean autoLights = !manual && (lowBeam == 1 || sideLight == 3);
        vLamp = !lightsKnown() ? "non letti" : "anabbaglianti " + lowBeam + " · posizione " + sideLight + " · interruttore " + lightSwitch
                + (manual ? " (accesi a mano: non contano)" : autoLights ? " → notte" : " → nessuna indicazione");

        double[] pos = position(c);
        TimeZone tz = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        boolean sunDay = SunCalc.isDay(pos[0], pos[1], now, tz);
        int[] rs = SunCalc.sunriseSunset(pos[0], pos[1], now, tz);
        vSun = (sunDay ? "giorno" : "notte") + (rs == null ? "" : " (alba " + SunCalc.hhmm(rs[0]) + ", tramonto " + SunCalc.hhmm(rs[1]) + ")")
                + " · posizione " + (pos[2] > 0 ? "dall'auto" : "Italia (riferimento)");

        if (uiTrust) {
            day = !uiNight;
            src = "uiMode di sistema";
        } else if (propTrust) {
            day = np != 1;
            src = "NIGHT_MODE (come la home originale)";
        } else if (autoLights) {
            day = false;
            src = "fari accesi";
        } else {
            day = sunDay;
            src = "alba/tramonto";
        }
        source = src;
        lastValues = "uiMode " + vUi + " | NIGHT_MODE " + vProp + " | fari " + vLamp + " | sole " + vSun;
        return day;
    }

    private static String lastValues = "";

    /**
     * Chiamata periodicamente (thread di lavoro del veicolo) e al ritorno nel launcher. Applica il cambio solo dopo
     * 60 s di valore stabile; restituisce il tema applicato (true = giorno).
     */
    public static boolean tick(Context c, String themeMode) {
        boolean d = evaluate(c);
        boolean changed = false;
        synchronized (DayNight.class) {
            long t = android.os.SystemClock.elapsedRealtime();
            if (d != desired) {
                desired = d;
                desiredSince = t;
            }
            boolean auto = com.mg4.launcher.ui.Prefs.THEME_AUTO.equals(themeMode);
            if (applied == null) {
                applied = d;
                lastChange = now();
            } else if (auto && applied != d && (!visible || t - desiredSince >= STABLE_MS)) {
                applied = d;
                lastChange = now();
                changed = true;
            }
            String manual = com.mg4.launcher.ui.Prefs.THEME_DAY.equals(themeMode) ? "scelta Giorno"
                    : com.mg4.launcher.ui.Prefs.THEME_NIGHT.equals(themeMode) ? "scelta Notte" : null;
            boolean shown = manual != null ? com.mg4.launcher.ui.Prefs.THEME_DAY.equals(themeMode) : applied;
            String wait = auto && applied != d ? " · in attesa: " + (d ? "giorno" : "notte") + " da " + ((t - desiredSince) / 1000) + " s" : "";
            Diag.putQuiet("tema.stato", (manual != null ? manual : "automatico · fonte " + source) + " · applicato "
                    + (shown ? "giorno" : "notte") + " · ultimo cambio " + lastChange + wait + " || " + lastValues);
        }
        if (changed) {
            Listener l = listener;
            boolean day = applied;
            if (l != null) main.post(() -> l.onDayNightChanged(day));
        }
        return applied;
    }

    /** Tema automatico attuale (true = giorno): l'ultimo applicato, o una valutazione immediata all'avvio. */
    public static boolean currentDay(Context c, String themeMode) {
        synchronized (DayNight.class) {
            if (applied != null) return applied;
        }
        return tick(c, themeMode);
    }

    private static String now() {
        return new SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY).format(new Date());
    }

    // ---- lettura delle proprietà e diagnostica tema.candidati ------------------------------------

    /** Letture "pesanti" (proprietà del veicolo, impostazioni): al massimo ogni 20 s. */
    static void readCar(Context c, CarProps p, int clusterMode) {
        long t = android.os.SystemClock.elapsedRealtime();
        if (t - candidatesAt < CANDIDATES_EVERY && candidatesAt > 0) return;
        candidatesAt = t;
        StringBuilder sb = new StringBuilder();
        if (p != null && p.ready()) {
            int nm = p.getBoolQuiet(NIGHT_MODE);
            nightProp = nm;
            sb.append("NIGHT_MODE ").append(nm < 0 ? "?" : nm == 1 ? "notte" : "giorno");
            sb.append(" · PMS_SYSTEM_DAY_NIGHT_MODE ").append(q(p.getIntQuiet(PMS_SYSTEM_DAY_NIGHT_MODE)));
            sb.append(" · FICM_DAY_NIGHT_MD ").append(q(p.getIntQuiet(FICM_DAY_NIGHT_MD)));
            sb.append(" · DIM_LGHT_LVL ").append(q(p.getIntQuiet(DIM_LGHT_LVL)));
            sb.append(" · DISPLAY_BRIGHTNESS ").append(q(p.getIntQuiet(DISPLAY_BRIGHTNESS)));
            int low = p.getBoolQuiet(DIPD_BEAM_LGHT);
            sb.append(" · DIPD_BEAM_LGHT ").append(low < 0 ? "?" : String.valueOf(low));
            sb.append(" · VEH_SIDE_LGHT ").append(q(p.getIntQuiet(VEH_SIDE_LGHT)));
            sb.append(" · LIGHT_SWITCH_POS_STS ").append(q(p.getIntQuiet(LIGHT_SWITCH_POS_STS)));
        } else {
            sb.append("CarService non pronto");
        }
        sb.append(" · quadro (IPK) ").append(clusterMode == Integer.MIN_VALUE ? "?" : String.valueOf(clusterMode));
        sb.append(" · SAIC_DAY_NIGHT_MODE_FROM_CAR ").append(setting(c, "SAIC_DAY_NIGHT_MODE_FROM_CAR"));
        sb.append(" · CLUSTER_SKIN_AUTO_CONFIG ").append(setting(c, "CLUSTER_SKIN_AUTO_CONFIG"));
        sb.append(" · luminosità schermo ").append(setting(c, "screen_brightness"));
        try {
            android.app.UiModeManager um = (android.app.UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
            int m = um.getNightMode();
            sb.append(" · UiModeManager ").append(m == android.app.UiModeManager.MODE_NIGHT_YES ? "notte"
                    : m == android.app.UiModeManager.MODE_NIGHT_NO ? "giorno" : m == android.app.UiModeManager.MODE_NIGHT_AUTO ? "auto" : String.valueOf(m));
        } catch (Throwable ignored) {
        }
        Diag.putQuiet("tema.candidati", sb.toString());
    }

    private static String q(int v) {
        return v == Integer.MIN_VALUE ? "?" : String.valueOf(v);
    }

    private static String setting(Context c, String key) {
        try {
            String v = android.provider.Settings.System.getString(c.getContentResolver(), key);
            return v == null ? "-" : v;
        } catch (Throwable e) {
            return "?";
        }
    }

    // ---- posizione per alba e tramonto -----------------------------------------------------------

    private static double[] cachedPos;
    private static long posAt;
    private static boolean permAsked;

    /** {lat, lon, 1 se dall'auto / 0 se riferimento}. Ultima posizione nota, riletta al massimo ogni 10 minuti. */
    private static double[] position(Context c) {
        long t = android.os.SystemClock.elapsedRealtime();
        if (cachedPos != null && t - posAt < 10 * 60_000L) return cachedPos;
        posAt = t;
        double[] out = null;
        try {
            if (!permAsked) {
                permAsked = true;
                grant(c, android.Manifest.permission.ACCESS_COARSE_LOCATION);
                grant(c, android.Manifest.permission.ACCESS_FINE_LOCATION);
            }
            android.location.LocationManager lm = (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            android.location.Location best = null;
            if (lm != null) {
                for (String prov : lm.getAllProviders()) {
                    try {
                        android.location.Location l = lm.getLastKnownLocation(prov);
                        if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                    } catch (SecurityException ignored) {
                    }
                }
            }
            if (best != null) {
                out = new double[]{best.getLatitude(), best.getLongitude(), 1};
                sp(c).edit().putFloat("tema.lat", (float) best.getLatitude()).putFloat("tema.lon", (float) best.getLongitude()).apply();
            }
        } catch (Throwable ignored) {
        }
        if (out == null) {
            SharedPreferences s = sp(c);
            if (s.contains("tema.lat")) out = new double[]{s.getFloat("tema.lat", 0), s.getFloat("tema.lon", 0), 1};
        }
        if (out == null) {
            // senza posizione: centro dell'Italia, ma la longitudine segue il fuso orario se è lontano da quello italiano
            double lon = SunCalc.ITALY_LON;
            int offH = TimeZone.getDefault().getRawOffset() / 3600_000;
            if (Math.abs(offH - 1) > 1) lon = offH * 15.0;
            out = new double[]{SunCalc.ITALY_LAT, lon, 0};
        }
        cachedPos = out;
        return out;
    }

    private static void grant(Context c, String perm) {
        if (c.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            pm.getClass().getMethod("grantRuntimePermission", String.class, String.class, android.os.UserHandle.class)
                    .invoke(pm, c.getPackageName(), perm, android.os.Process.myUserHandle());
        } catch (Throwable ignored) {
        }
    }
}

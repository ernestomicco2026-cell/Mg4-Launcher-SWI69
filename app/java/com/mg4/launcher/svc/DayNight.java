package com.mg4.launcher.svc;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 1.0.2: tema automatico giorno/notte uguale per tutte le versioni, con una catena di fonti.
 * 1.0.4: il sistema (uiMode) non è più una fonte: il tema lo decide il launcher e lo scrive nel sistema (syncSystem).
 * <ol>
 *   <li>i fari (anabbaglianti o posizione, proprietà dell'auto): accesi con il sole basso = notte;</li>
 *   <li>l'orario: alba e tramonto calcolati senza internet dall'ultima posizione nota (se manca, fuso orario e Italia).</li>
 * </ol>
 * Un cambio si applica solo se resta uguale per 60 s (in galleria il tema non lampeggia). La scelta manuale Giorno/Notte
 * delle impostazioni ha sempre la precedenza (la controlla Theme).
 */
public final class DayNight {
    public interface Listener {
        void onThemeChange(boolean day);
    }

    private static final long CONFIRM_MS = 60_000, TICK_MS = 20_000, CANDIDATES_EVERY_MS = 10 * 60_000L;
    private static final long RELIABLE_WINDOW_MS = 72L * 60 * 60 * 1000;
    private static final String K_SYS_NIGHT_AT = "temaNotteSistemaAlle", K_LAT = "temaUltimaLat", K_LON = "temaUltimaLon";
    /** Italia, centro: riferimento se non c'è nessuna posizione. */
    private static final double ITALY_LAT = 42.5, ITALY_LON = 12.5;

    private static DayNight instance;

    private final Context ctx;
    private final SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    /** Le letture (CarService, posizione) si fanno su questo thread, mai sul thread principale. */
    private final Handler work;
    private Listener listener;
    private Boolean applied;            // tema automatico applicato (true = giorno)
    private Boolean pending;
    private long pendingSince;
    private String lastChange = "—", source = "—", detail = "";
    private int lastUiNight = -1;
    private long candidatesAt;

    private DayNight(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
        android.os.HandlerThread t = new android.os.HandlerThread("mg4-tema");
        t.start();
        work = new Handler(t.getLooper());
    }

    public static synchronized DayNight get(Context c) {
        if (instance == null) instance = new DayNight(c);
        return instance;
    }

    public void start(Listener l) {
        listener = l;
        work.removeCallbacks(tick);
        work.post(tick);
        new Thread(this::ensureLocationPermission, "mg4-posizione").start();
    }

    public void stop() {
        work.removeCallbacksAndMessages(null);
        listener = null;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            evaluate(false);
            work.postDelayed(this, TICK_MS);
        }
    };

    /** Il sistema ha cambiato uiMode: si rivaluta subito e di nuovo dopo il tempo di conferma. */
    public void onConfigurationChanged() {
        work.post(() -> evaluate(false));
        work.postDelayed(() -> evaluate(false), CONFIRM_MS + 1000);
    }

    /**
     * 1.0.6 (issue #43): i fari sono diventati leggibili (CarService pronto) o è cambiata la fonte nelle impostazioni:
     * si rilegge e si ridecide SUBITO, senza i 60 s di conferma, e la home si ricostruisce se il tema cambia.
     */
    public static void redecideNow(String why) {
        DayNight d = instance;
        if (d == null) return;
        d.work.post(() -> d.redecide(why));
    }

    private synchronized void redecide(String why) {
        boolean target;
        try {
            target = compute();
        } catch (Throwable e) {
            Diag.error("tema.stato", e.toString());
            return;
        }
        pending = null;
        Diag.put("tema.ridecisione", why + " · fonte " + source + " → " + (target ? "giorno" : "notte")
                + (applied != null && applied == target ? " (già così)" : ""));
        if (applied != null && applied == target) {
            writeDiag();
            return;
        }
        applyNow(target, "subito: " + why);
        Listener l = listener;
        if (l != null) h.post(() -> l.onThemeChange(target));
    }

    /** Tema automatico da applicare adesso (alla prima chiamata si calcola subito, senza attesa). */
    public synchronized boolean isDay() {
        if (applied == null) evaluate(true);
        return applied == null || applied;
    }

    /** Valuta le fonti; con immediate = true applica subito (avvio), altrimenti dopo 60 s di conferma. */
    public synchronized void evaluate(boolean immediate) {
        boolean target;
        try {
            target = compute();
        } catch (Throwable e) {
            Diag.error("tema.stato", e.toString());
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (applied == null || immediate && applied != target) {
            applyNow(target, applied == null ? "avvio" : "subito");
            return;
        }
        if (applied == target) {
            pending = null;
        } else if (pending == null || pending != target) {
            pending = target;
            pendingSince = now;
        } else if (now - pendingSince >= CONFIRM_MS) {
            pending = null;
            applyNow(target, "confermato dopo 60 s");
            Listener l = listener;
            // la ricostruzione della home si fa sul thread principale
            if (l != null) h.post(() -> l.onThemeChange(target));
        }
        writeDiag();
        if (now - candidatesAt > CANDIDATES_EVERY_MS || candidatesAt == 0) {
            candidatesAt = now;
            new Thread(this::candidates, "mg4-tema-candidati").start();   // letture lente fuori dal thread principale
        }
    }

    // ---- 1.0.3: letture per il Controllo funzioni ------------------------------------------------

    private volatile double lastSunElevation = Double.NaN;

    /** Fonte usata adesso (testo del log). */
    public String source() {
        return source;
    }

    /** Tema automatico applicato: true giorno, false notte, null non ancora deciso. */
    public Boolean applied() {
        return applied;
    }

    public double sunElevation() {
        return lastSunElevation;
    }

    public boolean uiStuck() {
        return sp.getBoolean(K_UI_STUCK, false);
    }

    private void applyNow(boolean day, String how) {
        applied = day;
        lastChange = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY).format(new Date()) + " (" + how + ")";
        writeDiag();
        syncSystem("decisione del launcher (" + how + ")");
    }

    // ---- 0.4.4 (SWI69): il tema si scrive nel sistema come fanno le app SAIC EH32 ---------------------

    /** Impostazione di sistema delle app SAIC EH32 (SWI69/SWI131/SWI132): 0 = scuro (notte), 1 = chiaro (giorno). */
    public static final String SKIN_KEY = "SKIN_THEME_CONFIG";
    /** Avviso che fa ricaricare il tema a launcher SAIC, SystemUI, Clima e alle altre app SAIC. */
    public static final String SKIN_ACTION = "com.saicmotor.changeSkin";

    private volatile String sysTxt = "non ancora scritto";

    /**
     * Scrive nel sistema il tema effettivo (scelta manuale Giorno/Notte, altrimenti quello automatico).
     * Sulla SWI69 R45 il tema di tutto il sistema non passa da UiModeManager: il servizio CarAdapterService
     * (AutoSkinObserver) e le Impostazioni (GeneralModel) scrivono Settings.System SKIN_THEME_CONFIG (0 scuro, 1 chiaro)
     * e mandano il broadcast com.saicmotor.changeSkin; il launcher SAIC, la SystemUI e le altre app lo rileggono.
     * Qui si fa lo stesso. Si scrive solo quando il valore è diverso: alle decisioni del launcher, all'avvio e quando
     * cambia la scelta manuale; mai a ogni giro.
     */
    public void syncSystem(String why) {
        work.post(() -> syncNow(why));
    }

    // ---- 0.4.6 (punto 14): tema di sistema della SWI69 -------------------------------------------------------
    /** "Tema automatico" SAIC (Impostazioni → Generali): 1 = il sistema segue le luci di posizione, 0 = manuale, -1 assente. */
    public static final String SKIN_MODE_KEY = "screen_skin_mode";
    private static final String K_SKIN_ABSENT = "temaSistemaAssente", K_SKIN_MODE_BEFORE = "temaSaicPrima";

    /** true se su quest'auto SKIN_THEME_CONFIG non esiste: niente scritture in Settings, solo avviso e UiModeManager. */
    public boolean skinAbsent() {
        return sp.getBoolean(K_SKIN_ABSENT, false);
    }

    private int readInt(String key) {
        try {
            return android.provider.Settings.System.getInt(ctx.getContentResolver(), key, -1);
        } catch (Throwable e) {
            return -1;
        }
    }

    /** "Tema automatico" SAIC acceso e scelta del launcher su Automatico: il launcher segue il tema SAIC. */
    private boolean followSaic() {
        return "auto".equals(sp.getString("theme", "auto")) && !skinAbsent() && readInt(SKIN_MODE_KEY) == 1 && readInt(SKIN_KEY) >= 0;
    }

    private void syncNow(String why) {
        String mode = sp.getString("theme", "auto");
        boolean manual = "giorno".equals(mode) || "notte".equals(mode);
        android.content.ContentResolver cr = ctx.getContentResolver();
        String at = new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date());
        int skinBefore = readInt(SKIN_KEY), modeBefore = readInt(SKIN_MODE_KEY);
        StringBuilder note = new StringBuilder();
        // a) auto senza SKIN_THEME_CONFIG (letto -1 con screen_skin_mode -1): mai scritture in Settings
        if (!skinAbsent() && skinBefore < 0 && modeBefore < 0) {
            sp.edit().putBoolean(K_SKIN_ABSENT, true).apply();
            Diag.put("tema.sistemaAssente", "SKIN_THEME_CONFIG e screen_skin_mode non esistono su quest'auto: solo avviso "
                    + SKIN_ACTION + " e UiModeManager, nessuna scrittura in Settings");
        }
        // b) "tema automatico" SAIC: con una scelta MANUALE nel launcher lo si mette in manuale (ricordando il valore di
        //    prima), altrimenti il sistema rimette il suo tema dopo 3 s; con "Automatico" si rimette com'era
        if (!skinAbsent()) {
            int saved = sp.getInt(K_SKIN_MODE_BEFORE, -1);
            try {
                if (manual && modeBefore == 1) {
                    android.provider.Settings.System.putInt(cr, SKIN_MODE_KEY, 0);
                    sp.edit().putInt(K_SKIN_MODE_BEFORE, 1).apply();
                    note.append(" · tema automatico SAIC messo in manuale (prima 1)");
                } else if (!manual && saved >= 0 && modeBefore != saved) {
                    android.provider.Settings.System.putInt(cr, SKIN_MODE_KEY, saved);
                    sp.edit().remove(K_SKIN_MODE_BEFORE).apply();
                    note.append(" · tema automatico SAIC rimesso a ").append(saved);
                    work.postDelayed(() -> redecide("tema automatico SAIC rimesso"), 4000);
                } else if (!manual && saved >= 0) {
                    sp.edit().remove(K_SKIN_MODE_BEFORE).apply();
                }
            } catch (Throwable e) {
                note.append(" · screen_skin_mode non scritto (").append(e.getClass().getSimpleName()).append(")");
            }
        }
        int modeNow = readInt(SKIN_MODE_KEY);
        String head = "screen_skin_mode " + modeBefore + (modeNow != modeBefore ? " → " + modeNow : "") + " · " + SKIN_KEY + " " + skinBefore;
        // c) Automatico con il tema automatico SAIC acceso: non si combatte, si segue il tema SAIC (solo log)
        if (!manual && !skinAbsent() && modeNow == 1) {
            int sk = readInt(SKIN_KEY);
            sysTxt = "segue il tema automatico SAIC: " + (sk == 1 ? "giorno" : sk == 0 ? "notte" : "non leggibile") + " · " + head
                    + note + " · alle " + at + " · " + why;
            appliedOk = true;
            Diag.put("tema.sistema", sysTxt);
            return;
        }
        Boolean day = "giorno".equals(mode) ? Boolean.TRUE : "notte".equals(mode) ? Boolean.FALSE : applied;
        if (day == null) return;
        int want = day ? 1 : 0;
        String res;
        if (skinAbsent()) {
            res = "solo avviso e UiModeManager (" + uiMode(day) + ")";
            try {
                ctx.sendBroadcast(new android.content.Intent(SKIN_ACTION));
            } catch (Throwable ignored) {
            }
        } else if (skinBefore == want) {
            res = "già " + (day ? "giorno" : "notte");
        } else {
            try {
                boolean ok = android.provider.Settings.System.putInt(cr, SKIN_KEY, want);
                ctx.sendBroadcast(new android.content.Intent(SKIN_ACTION));
                int after = readInt(SKIN_KEY);
                res = "scritto " + skinBefore + " → " + want + " con avviso " + SKIN_ACTION + " · esito " + (ok && after == want ? "ok" : "errore (il sistema dice " + after + ")");
            } catch (IllegalArgumentException e) {
                // SKIN_THEME_CONFIG non esiste su quest'auto: una sola riga di log, poi mai più scritture
                sp.edit().putBoolean(K_SKIN_ABSENT, true).apply();
                Diag.put("tema.sistemaAssente", "scrittura di " + SKIN_KEY + " rifiutata (IllegalArgumentException): da ora solo avviso e UiModeManager");
                ctx.sendBroadcast(new android.content.Intent(SKIN_ACTION));
                res = "non scritto (tema di sistema non presente) · " + uiMode(day);
            } catch (Throwable e) {
                res = "errore " + e.getClass().getSimpleName();
            }
        }
        sysTxt = (day ? "giorno" : "notte") + (manual ? " (scelta manuale)" : " (automatico del launcher)") + " · " + res + " · prima: "
                + head + note + " · alle " + at + " · " + why;
        Diag.put("tema.sistema", sysTxt);
        if (skinAbsent()) {
            appliedOk = true;
            return;
        }
        // 3 s dopo si rilegge: se il servizio SAIC lo ha riscritto, si vede nel log (nessuna riscrittura a ripetizione)
        final int wanted = want;
        work.postDelayed(() -> {
            int v = readInt(SKIN_KEY), m = readInt(SKIN_MODE_KEY);
            appliedOk = v == wanted;
            Diag.put("tema.sistema", sysTxt + " · dopo 3 s: " + (v == 1 ? "giorno" : v == 0 ? "notte" : "non leggibile")
                    + " (screen_skin_mode " + m + ")" + (appliedOk ? "" : " (DIVERSO da quello scritto)"));
        }, 3000);
    }

    /** Solo dove SKIN_THEME_CONFIG non c'è: il tema anche in UiModeManager (permesso della firma platform). */
    private String uiMode(boolean day) {
        try {
            android.app.UiModeManager um = (android.app.UiModeManager) ctx.getSystemService(Context.UI_MODE_SERVICE);
            int m = day ? android.app.UiModeManager.MODE_NIGHT_NO : android.app.UiModeManager.MODE_NIGHT_YES;
            if (um.getNightMode() != m) um.setNightMode(m);
            return "UiModeManager " + (um.getNightMode() == m ? "ok" : "non cambiato");
        } catch (Throwable e) {
            return "UiModeManager errore " + e.getClass().getSimpleName();
        }
    }

    /** Alla disinstallazione dal launcher: il "tema automatico" SAIC torna com'era prima della nostra scelta manuale. */
    public static void restoreSaicMode(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
        int saved = p.getInt(K_SKIN_MODE_BEFORE, -1);
        if (saved < 0 || p.getBoolean(K_SKIN_ABSENT, false)) return;
        try {
            android.provider.Settings.System.putInt(c.getContentResolver(), SKIN_MODE_KEY, saved);
            p.edit().remove(K_SKIN_MODE_BEFORE).commit();
            Diag.put("tema.sistema", "disinstallazione: tema automatico SAIC rimesso a " + saved);
        } catch (Throwable e) {
            Diag.put("tema.sistema", "disinstallazione: tema automatico SAIC non rimesso (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Tema scritto nel sistema adesso: true giorno, false notte, null non leggibile (Controllo funzioni). */
    public Boolean systemDay() {
        try {
            int v = android.provider.Settings.System.getInt(ctx.getContentResolver(), SKIN_KEY, -1);
            return v == 1 ? Boolean.TRUE : v == 0 ? Boolean.FALSE : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private volatile boolean appliedOk = true;

    /** True se il tema effettivo della configurazione corrisponde a quello scritto (ultimo controllo). */
    public boolean systemApplied() {
        return appliedOk;
    }

    /** Per il Controllo funzioni: [tema voluto (true giorno), tema del sistema riletto], null se non deciso o non leggibile. */
    public Boolean[] systemCheck() {
        String mode = sp.getString("theme", "auto");
        Boolean day = "giorno".equals(mode) ? Boolean.TRUE : "notte".equals(mode) ? Boolean.FALSE : applied;
        // 0.4.4 (SWI69): il tema del sistema è SKIN_THEME_CONFIG (0 scuro, 1 chiaro)
        return new Boolean[]{day, systemDay()};
    }

    public String systemTxt() {
        return sysTxt;
    }

    // ------------------------------------------------------------------------------------------

    private String uiTxt = "?", mgrTxt = "?", nightPropTxt = "?", lightsTxt = "?", sunTxt = "?";

    private boolean compute() {
        // a) sistema (solo per il log: dalla 0.4.4 il tema lo decide il launcher e lo scrive in SKIN_THEME_CONFIG)
        int ui = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int uiNight = ui == Configuration.UI_MODE_NIGHT_YES ? 1 : ui == Configuration.UI_MODE_NIGHT_NO ? 0 : -1;
        uiTxt = uiNight == 1 ? "notte" : uiNight == 0 ? "giorno" : "non definito";
        int mgr = -1;
        try {
            android.app.UiModeManager um = (android.app.UiModeManager) ctx.getSystemService(Context.UI_MODE_SERVICE);
            int m = um.getNightMode();
            mgr = m == android.app.UiModeManager.MODE_NIGHT_YES ? 1 : m == android.app.UiModeManager.MODE_NIGHT_NO ? 0 : -1;
            mgrTxt = mgr == 1 ? "notte" : mgr == 0 ? "giorno" : m == android.app.UiModeManager.MODE_NIGHT_AUTO ? "auto" : String.valueOf(m);
        } catch (Throwable e) {
            mgrTxt = "?";
        }
        int nightProp = Integer.MIN_VALUE;
        CarProps p = VehicleClient.carProps();
        if (p != null && p.ready()) nightProp = p.getBoolQuiet(CarProps.NIGHT_MODE);
        nightPropTxt = nightProp == 1 ? "notte" : nightProp == 0 ? "giorno" : "non c'è";
        boolean sysNight = uiNight == 1 || mgr == 1 || nightProp == 1;
        long wall = System.currentTimeMillis();
        trackUiChanges(uiNight, wall);
        long prevSeen = sp.getLong(K_SYS_NIGHT_AT, 0);
        boolean changed = lastUiNight >= 0 && uiNight >= 0 && uiNight != lastUiNight;
        // si scrive solo quando serve (al più una volta l'ora), non a ogni giro
        if ((sysNight || changed) && wall - prevSeen > 60 * 60_000L) sp.edit().putLong(K_SYS_NIGHT_AT, wall).apply();
        if (uiNight >= 0) lastUiNight = uiNight;
        long seen = sp.getLong(K_SYS_NIGHT_AT, 0);
        boolean reliable = seen > 0 && wall - seen < RELIABLE_WINDOW_MS && wall >= seen;

        // c) sole (calcolato sempre, serve anche ai fari e alla diagnostica)
        double[] loc = location();
        Sun sun = sun(loc[0], loc[1], wall);
        lastSunElevation = sun.elevation;
        sunTxt = "alba " + sun.rise + " · tramonto " + sun.set + " · sole a " + Math.round(sun.elevation) + "° (" + (loc[2] == 1 ? "posizione"
                : loc[2] == 2 ? "ultima posizione salvata" : "fuso orario/Italia") + ")";

        // b) fari
        int lights = VehicleClient.lights();
        lightsTxt = lights == 1 ? "accesi" : lights == 0 ? "spenti" : "non leggibili";

        // 1.0.4 (punto A): uiMode del sistema NON è più una fonte. Prima lo cambiava il launcher SAIC/il servizio
        // impostazioni; con il nostro launcher nessuno lo cambiava e restava su "notte". Ora lo decidiamo noi (fari →
        // alba/tramonto) e lo SCRIVIAMO nel sistema (syncSystem). uiMode, UiModeManager e NIGHT_MODE restano nel log.
        uiStuckTxt = "";
        boolean sunNight = sun.elevation < -0.833;
        // 1.0.6 (issue #43): "Solo fari": scuro quando i fari sono accesi, a qualunque ora; chiaro quando sono spenti.
        // Con i fari non leggibili si usa l'orario finché il CarService non risponde (poi si ridecide subito).
        // 0.4.6 (punto 14, SWI69): "tema automatico" SAIC acceso (screen_skin_mode 1): il sistema rimette il suo tema
        // dopo 3 s, quindi in Automatico non si combatte: si segue il tema SAIC (SKIN_THEME_CONFIG)
        if (followSaic()) {
            int sk = readInt(SKIN_KEY);
            source = "tema automatico SAIC (screen_skin_mode 1, " + SKIN_KEY + " " + sk + ")";
            return sk == 1;
        }
        boolean onlyLights = "solo_fari".equals(sp.getString("temaAutoFonte", "fari_orario"));
        if (onlyLights && lights >= 0) {
            source = "solo fari (" + lightsTxt + ")";
            return lights != 1;
        }
        if (onlyLights) {
            source = "solo fari, ma fari non leggibili: orario (alba/tramonto) finché il CarService non risponde";
            return !sunNight;
        }
        if (lights >= 0) {
            // con il sole alto i fari accesi sono quelli diurni o la galleria: resta giorno
            boolean night = sunNight || (lights == 1 && sun.elevation < 6);
            source = "fari (" + lightsTxt + ") + sole";
            return !night;
        }
        source = "orario (alba/tramonto)";
        return !sunNight;
    }

    // ---- uiMode bloccato (1.0.3) ----------------------------------------------------------------

    /** Tempo di accensione senza cambi di uiMode oltre il quale, se contraddice il sole, uiMode è considerato bloccato. */
    private static final long UI_STUCK_ON_MS = 12L * 60 * 60 * 1000;
    private static final String K_UI_LAST = "temaUiUltimo", K_UI_CHANGED_AT = "temaUiCambiatoAlle", K_UI_ON_MS = "temaUiAccesoMs",
            K_UI_STUCK = "temaUiBloccato", K_UI_STUCK_WHY = "temaUiBloccatoPerche";
    private long onTickAt, onPending, onSavedAt;
    private String uiStuckTxt = "";

    /**
     * Conta il tempo di accensione da quando uiMode è cambiato l'ultima volta (giorno↔notte), anche tra un'accensione e
     * l'altra. Il tempo a auto spenta non conta: tra due giri si aggiunge al massimo 3 volte l'intervallo del controllo.
     */
    private void trackUiChanges(int uiNight, long wall) {
        long now = SystemClock.elapsedRealtime();
        if (onTickAt > 0) onPending += Math.max(0, Math.min(now - onTickAt, 3 * TICK_MS));
        onTickAt = now;
        if (uiNight < 0) return;
        int last = sp.getInt(K_UI_LAST, -1);
        SharedPreferences.Editor e = null;
        if (last != uiNight) {
            e = sp.edit().putInt(K_UI_LAST, uiNight);
            if (last >= 0) {
                // cambio reale: si azzera il conteggio e uiMode torna affidabile
                e.putLong(K_UI_CHANGED_AT, wall).putLong(K_UI_ON_MS, 0).putBoolean(K_UI_STUCK, false).remove(K_UI_STUCK_WHY);
                onPending = 0;
                onSavedAt = now;
            }
        }
        if (onPending > 0 && (now - onSavedAt > 5 * 60_000L || e != null)) {
            if (e == null) e = sp.edit();
            e.putLong(K_UI_ON_MS, sp.getLong(K_UI_ON_MS, 0) + onPending);
            onPending = 0;
            onSavedAt = now;
        }
        if (e != null) e.apply();
    }

    private void markStuck(String why) {
        sp.edit().putBoolean(K_UI_STUCK, true).putString(K_UI_STUCK_WHY, why).apply();
    }

    private String uiHistoryTxt() {
        long at = sp.getLong(K_UI_CHANGED_AT, 0);
        long onH = (sp.getLong(K_UI_ON_MS, 0) + onPending) / 3_600_000L;
        return "ultimo cambio uiMode " + (at == 0 ? "mai visto" : new SimpleDateFormat("dd/MM HH:mm", Locale.ITALY).format(new Date(at)))
                + " (" + onH + " h di accensione da allora)";
    }

    private void writeDiag() {
        String mode = sp.getString("theme", "auto");
        String manual = "giorno".equals(mode) ? "scelta manuale Giorno (ha la precedenza) · automatico: " : "notte".equals(mode)
                ? "scelta manuale Notte (ha la precedenza) · automatico: " : "";
        Diag.put("tema.stato", manual + "fonte " + source + (uiStuckTxt.isEmpty() ? "" : " · " + uiStuckTxt) + " · " + uiHistoryTxt()
                + " · uiMode " + uiTxt + " · UiModeManager " + mgrTxt + " · NIGHT_MODE " + nightPropTxt
                + " · fari " + lightsTxt + " · " + sunTxt + " · applicato " + (applied == null ? "—" : applied ? "giorno" : "notte")
                + (pending != null ? " (in conferma: " + (pending ? "giorno" : "notte") + ")" : "") + " · ultimo cambio " + lastChange);
    }

    // ---- posizione ---------------------------------------------------------------------------

    private void ensureLocationPermission() {
        for (String perm : new String[]{"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"}) {
            if (ctx.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) continue;
            try {
                android.content.pm.PackageManager pm = ctx.getPackageManager();
                pm.getClass().getMethod("grantRuntimePermission", String.class, String.class, android.os.UserHandle.class)
                        .invoke(pm, ctx.getPackageName(), perm, android.os.Process.myUserHandle());
            } catch (Throwable ignored) {
            }
        }
    }

    /** {lat, lon, tipo}: tipo 1 = posizione attuale, 2 = ultima salvata, 0 = fuso orario/Italia. */
    private double[] location() {
        try {
            android.location.LocationManager lm = (android.location.LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            android.location.Location best = null;
            if (lm != null) {
                for (String prov : lm.getProviders(false)) {
                    try {
                        android.location.Location l = lm.getLastKnownLocation(prov);
                        if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (best != null && (best.getLatitude() != 0 || best.getLongitude() != 0)) {
                sp.edit().putFloat(K_LAT, (float) best.getLatitude()).putFloat(K_LON, (float) best.getLongitude()).apply();
                return new double[]{best.getLatitude(), best.getLongitude(), 1};
            }
        } catch (Throwable ignored) {
        }
        if (sp.contains(K_LAT)) return new double[]{sp.getFloat(K_LAT, 0), sp.getFloat(K_LON, 0), 2};
        TimeZone tz = TimeZone.getDefault();
        String id = tz.getID();
        if (id == null || id.equals("Europe/Rome") || id.equals("GMT") || id.equals("UTC")) return new double[]{ITALY_LAT, ITALY_LON, 0};
        // longitudine dal fuso (15° per ora), latitudine dell'Italia
        double lon = tz.getRawOffset() / 3_600_000.0 * 15.0;
        return new double[]{ITALY_LAT, lon, 0};
    }

    // ---- sole (formule NOAA semplificate, precisione di qualche minuto) ------------------------

    static final class Sun {
        double elevation;
        String rise = "—", set = "—";
    }

    static Sun sun(double lat, double lon, long wallMs) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.setTimeInMillis(wallMs);
        int doy = u.get(Calendar.DAY_OF_YEAR);
        double hour = u.get(Calendar.HOUR_OF_DAY) + u.get(Calendar.MINUTE) / 60.0 + u.get(Calendar.SECOND) / 3600.0;
        double g = 2 * Math.PI / 365.0 * (doy - 1 + (hour - 12) / 24.0);
        double eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g) - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double tst = hour * 60 + eq + 4 * lon;
        double ha = Math.toRadians(tst / 4.0 - 180);
        double la = Math.toRadians(lat);
        double cosZ = Math.sin(la) * Math.sin(decl) + Math.cos(la) * Math.cos(decl) * Math.cos(ha);
        Sun s = new Sun();
        s.elevation = 90 - Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosZ))));
        double c = Math.cos(Math.toRadians(90.833)) / (Math.cos(la) * Math.cos(decl)) - Math.tan(la) * Math.tan(decl);
        if (c >= -1 && c <= 1) {
            double ha0 = Math.toDegrees(Math.acos(c));
            s.rise = localTime(u, 720 - 4 * (lon + ha0) - eq);
            s.set = localTime(u, 720 - 4 * (lon - ha0) - eq);
        }
        return s;
    }

    private static String localTime(Calendar utcDay, double minutesUtc) {
        Calendar c = (Calendar) utcDay.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long ms = c.getTimeInMillis() + Math.round(minutesUtc * 60_000);
        return new SimpleDateFormat("HH:mm", Locale.ITALY).format(new Date(ms));
    }

    // ---- tema.candidati: tutti i segnali giorno/notte che si riescono a leggere -------------------

    private float lastLux = Float.NaN;
    private boolean luxAsked;

    private void candidates() {
        StringBuilder sb = new StringBuilder();
        CarProps p = VehicleClient.carProps();
        if (p != null && p.ready()) {
            sb.append("NIGHT_MODE ").append(val(p.getBoolQuiet(CarProps.NIGHT_MODE)));
            sb.append(" · HEADLIGHTS_STATE ").append(val(p.getIntSafe(CarProps.HEADLIGHTS_STATE)));
            sb.append(" · HEADLIGHTS_SWITCH ").append(val(p.getIntSafe(CarProps.HEADLIGHTS_SWITCH)));
            sb.append(" · DISPLAY_BRIGHTNESS ").append(val(p.getIntSafe(CarProps.DISPLAY_BRIGHTNESS)));
        } else {
            sb.append("CarService non pronto");
        }
        sb.append(" · fari (servizio) ").append(VehicleClient.lightsDescription());
        // sensore di luce
        try {
            android.hardware.SensorManager sm = (android.hardware.SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            android.hardware.Sensor light = sm == null ? null : sm.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT);
            if (light == null) sb.append(" · sensore di luce assente");
            else {
                sb.append(" · sensore di luce ").append(Float.isNaN(lastLux) ? "in lettura" : Math.round(lastLux) + " lux");
                if (!luxAsked) {
                    luxAsked = true;
                    sm.registerListener(new android.hardware.SensorEventListener() {
                        @Override
                        public void onSensorChanged(android.hardware.SensorEvent e) {
                            lastLux = e.values[0];
                            sm.unregisterListener(this);   // una lettura basta per la diagnostica
                            luxAsked = false;
                        }

                        @Override
                        public void onAccuracyChanged(android.hardware.Sensor s, int a) {
                        }
                    }, light, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        } catch (Throwable e) {
            sb.append(" · sensore di luce errore");
        }
        // impostazioni di sistema legate a giorno/notte o luminosità
        sb.append(" · impostazioni ").append(settingsMatching());
        try {
            sb.append(" · ui_night_mode ").append(android.provider.Settings.Secure.getString(ctx.getContentResolver(), "ui_night_mode"));
        } catch (Throwable ignored) {
        }
        sb.append(" · broadcast: cambio di configurazione (uiMode) ascoltato dall'activity");
        Diag.put("tema.candidati", sb.toString());
    }

    private static String val(int v) {
        return v == Integer.MIN_VALUE ? "non c'è" : String.valueOf(v);
    }

    private String settingsMatching() {
        StringBuilder sb = new StringBuilder();
        for (String table : new String[]{"system", "secure", "global"}) {
            android.database.Cursor c = null;
            try {
                c = ctx.getContentResolver().query(android.net.Uri.parse("content://settings/" + table), new String[]{"name", "value"}, null, null, null);
                if (c == null) continue;
                while (c.moveToNext()) {
                    String n = c.getString(0);
                    if (n == null) continue;
                    String l = n.toLowerCase(Locale.ROOT);
                    if (l.contains("night") || l.contains("day_") || l.contains("dark") || l.contains("brightness") || l.contains("daynight")
                            || l.contains("theme") || l.contains("lamp") || l.contains("headlight")) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(table).append('/').append(n).append('=').append(c.getString(1));
                        if (sb.length() > 900) return sb.append("…").toString();
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (c != null) c.close();
            }
        }
        return sb.length() == 0 ? "nessuna" : sb.toString();
    }
}

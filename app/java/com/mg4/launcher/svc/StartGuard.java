package com.mg4.launcher.svc;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;

import java.util.List;

/**
 * Logica comune dell'avvio all'accensione, usata dal servizio di accessibilità (eventi di cambio finestra) e,
 * se il servizio non è attivo, dal controllo di riserva ogni 400 ms (BootWatcher).
 * <p>
 * Finestra di avvio: dall'accensione (o dal risveglio dello schermo) fino a 5 s dopo che il nostro launcher è in
 * primo piano. Dalla 0.3.4: se la home MG non è ancora comparsa, la finestra resta aperta finché il firmware non la
 * porta davanti almeno una volta (al massimo 3 minuti), perché su queste auto arriva spesso dopo il nostro launcher. Dentro la finestra, se compare la home MG, si riapre il nostro launcher (al
 * massimo 3 volte). Non interviene se la home MG è stata aperta apposta, se in primo piano c'è un'altra app
 * (retrocamera, CarPlay, Android Auto, chiamata, avvisi) o se l'interruttore è spento / c'è l'officina.
 */
public final class StartGuard {
    private static final long WINDOW_MS = 60_000, OURS_STABLE_MS = 5000, ORIGINAL_GRACE_MS = 10 * 60_000L;
    /** Attesa massima della home MG, se non è ancora comparsa (0.3.4, misurata sui log di questa versione). */
    private static final long WAIT_MG_MS = 180_000L, AFTER_MG_MS = 30_000;
    private static final int MAX_RESTARTS = 3;

    // tempi da SystemClock.elapsedRealtime() (ms dall'accensione), -1 = non ancora
    private static long tProcess = -1, tA11y = -1, tBoot = -1, tMg = -1, tOurs = -1;
    private static long windowStart = -1, oursSince = -1, deadline = -1;
    private static boolean mgInWindow;
    private static int restarts;
    private static String windowWhy = "";
    static volatile boolean a11yConnected;

    private StartGuard() {}

    /**
     * Utente sbloccato? Prima dello sblocco le impostazioni del launcher (credential storage) non sono leggibili e
     * leggerle fa crashare il processo (0.3.4: ogni lavoro dell'avvio aspetta lo sblocco).
     */
    public static boolean unlocked(Context c) {
        try {
            android.os.UserManager um = (android.os.UserManager) c.getSystemService(Context.USER_SERVICE);
            return um == null || um.isUserUnlocked();
        } catch (Throwable e) {
            return true;
        }
    }

    /** Da chiamare quando l'utente apre apposta la home MG: per un po' l'avvio non interviene. */
    public static void userOpenedOriginal(Context c) {
        // 0.9.6: tempo dall'accensione + numero dell'accensione (l'orologio dell'auto all'avvio può essere indietro di ore e
        // il permesso restava valido anche all'accensione successiva)
        sp(c).edit().putLong("homeOriginaleFinoA", SystemClock.elapsedRealtime() + ORIGINAL_GRACE_MS)
                .putInt("homeOriginaleAvvio", bootCount(c)).apply();
    }

    private static int bootCount(Context c) {
        try {
            return android.provider.Settings.Global.getInt(c.getContentResolver(), android.provider.Settings.Global.BOOT_COUNT, -1);
        } catch (Throwable e) {
            return -1;
        }
    }

    /** Home MG aperta apposta da poco, in questa stessa accensione? */
    private static boolean originalGrace(Context c) {
        SharedPreferences s = sp(c);
        return s.getInt("homeOriginaleAvvio", -2) == bootCount(c)
                && SystemClock.elapsedRealtime() < s.getLong("homeOriginaleFinoA", 0);
    }

    // --- 0.9.6: altre home (un altro launcher, es. MG4Dash) trattate come la home MG all'accensione ---
    private static final java.util.Map<String, Boolean> homeCache = new java.util.HashMap<>();

    static boolean isHome(Context c, String pkg) {
        if (BootWatcher.MG_HOME.equals(pkg)) return true;
        if (pkg == null) return false;
        Boolean v = homeCache.get(pkg);
        if (v == null) {
            v = HomeLock.isOtherHome(c, pkg);
            homeCache.put(pkg, v);
            if (v) Diag.put("avvio.altraHome", pkg + " (trattata come la home MG)");
        }
        return v;
    }

    /** App che all'accensione non si coprono: retrocamera, telefonate, CarPlay/Android Auto, navigatore. */
    private static boolean keepInFront(String pkg) {
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        for (String k : new String[]{"aroundview", "avm", "rvc", "camera", "btcall", "phone", "dialer", "incall", "carplay",
                "androidauto", "allgo", "telenav", "navi", "map", "waze"}) {
            if (p.contains(k)) return true;
        }
        return false;
    }

    /**
     * 0.9.6: se dopo qualche secondo dall'accensione la home MG non è comparsa e davanti non c'è un'app da lasciare
     * (es. la proiezione sul quadro), si apre comunque il launcher (una volta). Chiamato dal servizio di accessibilità.
     */
    public static synchronized void lateCheck(Context c) {
        if (windowStart < 0 || !windowOpen() || mgInWindow || tOurs >= 0 && oursSince >= 0) return;
        if (!HomeMode.active(c) || originalGrace(c)) return;
        String top = topPackage(c);
        if (top != null && (c.getPackageName().equals(top) || keepInFront(top))) {
            Diag.put("avvio.homeAssente", "home MG non comparsa; davanti " + top + ": lasciata");
            return;
        }
        if (restarts < MAX_RESTARTS) {
            restarts++;
            HomeMode.openOurs(c);
            Diag.put("avvio.homeRipresa", "aperto il launcher: home MG non comparsa, davanti " + (top == null ? "nessuna app" : top));
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
    }

    public static synchronized void markProcess() {
        if (tProcess < 0) tProcess = Process.getStartElapsedRealtime();
    }

    public static synchronized void markA11y() {
        markProcess();
        if (tA11y < 0) tA11y = SystemClock.elapsedRealtime();
        times();
    }

    public static synchronized void markBoot() {
        markProcess();
        if (tBoot < 0) tBoot = SystemClock.elapsedRealtime();
        times();
    }

    /** Apre la finestra di avvio (accensione o risveglio dello schermo). */
    public static synchronized void openWindow(Context c, String why) {
        markProcess();
        if (!HomeMode.active(c)) {
            Diag.put("avvio.homeRipresa", "no (" + HomeMode.reason(c) + ")");
            return;
        }
        if (windowOpen()) return;
        windowStart = SystemClock.elapsedRealtime();
        deadline = windowStart + WAIT_MG_MS;
        mgInWindow = false;
        oursSince = -1;
        restarts = 0;
        windowWhy = why;
        Diag.put("avvio.homeRipresa", "controllo in corso (" + why + ")");
    }

    public static synchronized boolean windowOpen() {
        return windowStart >= 0 && SystemClock.elapsedRealtime() < deadline;
    }

    /** true se nella finestra attuale la home MG è già comparsa (o la finestra è chiusa). */
    public static synchronized boolean mgSeenInWindow() {
        return windowStart < 0 || mgInWindow;
    }

    /** Nuova app in primo piano (controllo periodico: solo il pacchetto). */
    public static synchronized void onForeground(Context c, String pkg) {
        onForeground(c, pkg, null, false);
    }

    // --- SWI69: home MG aperta per nome dal tasto Home (o da un'app che rimappa i tasti), fuori dall'avvio ---
    private static final long LOOP_WINDOW_MS = 10_000, LOOP_PAUSE_MS = 2 * 60_000L, HOME_CHECK_MS = 20_000;
    private static final int LOOP_MAX = 3;
    private static final java.util.ArrayDeque<Long> mgSeen = new java.util.ArrayDeque<>();
    private static long pausedUntil = -1, homeCheckedAt = -1;
    private static boolean ourHomeDefault, mgHomeDefault;
    private static String prevPkg = "";

    /**
     * Nuova app in primo piano. anytime = true (servizio di accessibilità): con il launcher come home predefinita, la
     * schermata principale della home MG si sostituisce ogni volta che compare, non solo all'accensione.
     */
    public static synchronized void onForeground(Context c, String pkg, String cls, boolean anytime) {
        if (pkg == null) return;
        String prev = prevPkg;
        if (!pkg.equals(prevPkg)) prevPkg = pkg;
        if (anytime && !windowOpen() && BootWatcher.MG_HOME.equals(pkg) && isMgMain(pkg, cls)) {
            replaceAnytime(c, prev);
        }
        long now = SystemClock.elapsedRealtime();
        if (BootWatcher.MG_HOME.equals(pkg) && tMg < 0) {
            tMg = now;
            Diag.put("avvio.primoPiano", pkg + " dopo " + (now / 1000) + " s");
            times();
        }
        if (c.getPackageName().equals(pkg) && tOurs < 0) {
            tOurs = now;
            times();
        }
        if (windowStart < 0) return;
        if (now >= deadline) {
            close(restarts > 0 ? "sì (tempo scaduto)" : mgInWindow ? "no (tempo scaduto)" : "no (home MG mai comparsa)");
            return;
        }
        if (!HomeMode.active(c)) {
            close("no (" + HomeMode.reason(c) + ")");
            return;
        }
        if (isHome(c, pkg)) {
            oursSince = -1;
            if (!mgInWindow) {
                mgInWindow = true;
                // da qui bastano i tempi di prima: 60 s dall'inizio, ma almeno 30 s per verificare la riapertura
                deadline = Math.max(windowStart + WINDOW_MS, now + AFTER_MG_MS);
            }
            if (originalGrace(c)) {
                close("no (home MG aperta apposta)");
                return;
            }
            if (restarts < MAX_RESTARTS) {
                restarts++;
                HomeMode.openOurs(c);
                Diag.put("avvio.homeRipresa", "riaperto il launcher (tentativo " + restarts + ", " + windowWhy + ")");
            }
        } else if (c.getPackageName().equals(pkg)) {
            if (oursSince < 0) oursSince = now;
            if (now - oursSince >= OURS_STABLE_MS) {
                if (mgInWindow) close(restarts > 0 ? "sì" : "no (già in primo piano)");
                else Diag.put("avvio.homeRipresa", "launcher davanti, in attesa della home MG (" + windowWhy + ")");
            }
        } else {
            oursSince = -1;   // un'altra app (retrocamera, CarPlay, chiamata...): non si tocca
        }
    }

    /** Solo la schermata principale della home MG (non le guide o le altre pagine aperte dal sistema). */
    private static boolean isMgMain(String pkg, String cls) {
        return cls == null || !cls.startsWith(pkg) || com.mg4.launcher.apps.MgApps.HOME_CLS.equals(cls);
    }

    private static void replaceAnytime(Context c, String prev) {
        long now = SystemClock.elapsedRealtime();
        if (!HomeMode.active(c)) return;
        if (originalGrace(c)) {
            Diag.putQuiet("home.tasto", "home MG lasciata (aperta apposta dal launcher)");
            return;
        }
        if (homeCheckedAt < 0 || now - homeCheckedAt > HOME_CHECK_MS) {
            homeCheckedAt = now;
            String home = resolvedHome(c);
            ourHomeDefault = c.getPackageName().equals(home);
            mgHomeDefault = com.mg4.launcher.apps.MgApps.HOME_PKG.equals(home);
        }
        // 0.3.8 / 0.2.5: nel firmware la home MG ha priorità 2 e Android la sceglie sempre, anche se il launcher è
        // impostato come predefinito (succedeva su quasi tutte le auto). Si sostituisce lo stesso, come all'accensione;
        // si lascia stare solo se la home predefinita è un'altra app ancora (un altro launcher scelto dall'utente).
        if (!ourHomeDefault && !mgHomeDefault) {
            Diag.putQuiet("home.tasto", "home MG lasciata (home predefinita: altra app)");
            return;
        }
        if (pausedUntil > now) return;
        mgSeen.addLast(now);
        while (!mgSeen.isEmpty() && now - mgSeen.peekFirst() > LOOP_WINDOW_MS) mgSeen.removeFirst();
        if (mgSeen.size() > LOOP_MAX) {
            pausedUntil = now + LOOP_PAUSE_MS;
            mgSeen.clear();
            Diag.put("home.tasto", "sospeso per 2 minuti: la home MG è ricomparsa più di " + LOOP_MAX + " volte in 10 s");
            return;
        }
        String origin = prev.startsWith("com.saicmotor.") || prev.startsWith("com.saic.") ? "altro (" + prev + ")" : "tasto";
        HomeMode.openOurs(c);
        Diag.put("home.tasto", "home MG sostituita (origine: " + origin + ")");
    }

    /** Pacchetto della home predefinita per ACTION_MAIN + CATEGORY_HOME (scritto nei log come home.predefinita). */
    public static String resolvedHome(Context c) {
        String pkg;
        try {
            android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(
                    new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
                    android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            pkg = ri != null && ri.activityInfo != null ? ri.activityInfo.packageName : "nessuna";
        } catch (Throwable t) {
            pkg = "errore " + t.getClass().getSimpleName();
        }
        Diag.putQuiet("home.predefinita", pkg);
        return pkg;
    }

    /** App che possono rimappare i tasti (es. MG4 Control), scritte nei log come home.appTasti. */
    public static void reportKeyApps(Context c) {
        try {
            java.util.Set<String> found = new java.util.LinkedHashSet<>();
            String enabled = android.provider.Settings.Secure.getString(c.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) c.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am != null) {
                for (android.accessibilityservice.AccessibilityServiceInfo info : am.getInstalledAccessibilityServiceList()) {
                    if ((info.getCapabilities() & android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS) == 0) continue;
                    String p = info.getResolveInfo().serviceInfo.packageName;
                    if (p.equals(c.getPackageName())) continue;
                    found.add(p + (enabled != null && enabled.contains(p + "/") ? " (attivo)" : ""));
                }
            }
            android.content.pm.PackageManager pm = c.getPackageManager();
            for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if ((ai.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 || ai.packageName.equals(c.getPackageName())) continue;
                String label = String.valueOf(pm.getApplicationLabel(ai)).toLowerCase(java.util.Locale.ROOT);
                String p = ai.packageName.toLowerCase(java.util.Locale.ROOT);
                if (label.contains("mg4 control") || p.contains("mg4control") || label.contains("key mapper") || label.contains("button mapper")
                        || p.contains("keymapper") || p.contains("buttonmapper")) {
                    found.add(ai.packageName + " (" + pm.getApplicationLabel(ai) + ")");
                }
            }
            Diag.put("home.appTasti", found.isEmpty() ? "nessuna trovata" : android.text.TextUtils.join(", ", found));
        } catch (Throwable t) {
            Diag.put("home.appTasti", "errore " + t.getClass().getSimpleName());
        }
    }

    private static void close(String result) {
        Diag.put("avvio.homeRipresa", result + ", tentativi " + restarts + " (" + windowWhy + ")");
        windowStart = -1;
        deadline = -1;
        oursSince = -1;
    }

    /** App in primo piano adesso (REAL_GET_TASKS, concesso dalla firma platform). */
    public static String topPackage(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            @SuppressWarnings("deprecation")
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return null;
            return tasks.get(0).topActivity.getPackageName();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String s(long t) {
        return t < 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1fs", t / 1000f);
    }

    private static void times() {
        Diag.put("avvio.tempi", "processo " + s(tProcess) + " · accessibilità " + s(tA11y) + " · boot " + s(tBoot)
                + " · home MG " + s(tMg) + " · nostro launcher " + s(tOurs));
    }
}

package com.mg4.launcher.svc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * 0.4.2 (punto 6): dopo la retromarcia si torna all'app che era davanti (CarPlay, Android Auto anche da box o dongle,
 * navigatore o un'altra app), invece di restare sul launcher.
 * <p>
 * La retromarcia si riconosce dalla schermata delle telecamere che passa in primo piano (servizio di accessibilità) o dai
 * broadcast delle telecamere SAIC; la fine dalla schermata che si chiude. Se dopo la chiusura davanti c'è il launcher (o
 * la home MG che il launcher sostituirebbe) si riapre l'app memorizzata, come fa il suo pulsante nel launcher.
 * Non si riapre se: impostazione spenta, retromarcia più lunga di 3 minuti, l'utente ha scelto un'altra app o premuto
 * Home, il telefono/box non è più collegato.
 */
public final class RetroReturn {
    private RetroReturn() {}

    /** Chi riapre le app: il launcher (MainActivity), con lo stesso metodo dei suoi pulsanti. */
    public interface Opener {
        /** true se l'app è ancora collegata/apribile (per CarPlay/Android Auto: dispositivo collegato). */
        boolean available(String pkg);

        /** Riapre l'app come il suo pulsante nel launcher. */
        boolean reopen(String pkg);
    }

    private static final long MAX_REVERSE_MS = 3 * 60_000L, CHECK_AFTER_MS = 1200;
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static volatile Opener opener;

    private static String current = "";        // app in primo piano adesso (anche il launcher)
    private static String saved;               // app memorizzata all'inizio della retromarcia
    private static long startedAt = -1;
    private static boolean inReverse, userChoice, restorePending;
    private static String broughtBy = "";
    private static String startWhy = "";

    public static void setOpener(Opener o) {
        opener = o;
    }

    public static boolean isCamera(String pkg) {
        if (pkg == null || pkg.startsWith("com.mg4.")) return false;   // MG4 360 aperta apposta non è la retromarcia
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        return p.equals("com.saicmotor.evs") || p.contains("aroundview") || p.contains(".avm") || p.contains("rvc") || p.contains("camera");
    }

    private static boolean isProjection(String pkg) {
        return pkg != null && (pkg.startsWith("com.allgo.carplay") || pkg.startsWith("com.allgo.app.androidauto") || pkg.startsWith("com.allgo."));
    }

    private static boolean ignorable(Context c, String pkg) {
        return pkg == null || "com.android.systemui".equals(pkg) || pkg.equals(c.getPackageName()) || BootWatcher.MG_HOME.equals(pkg)
                || pkg.startsWith("android") || pkg.equals("com.android.settings");
    }

    /** Nuova app in primo piano (servizio di accessibilità). */
    public static synchronized void onForeground(Context c, String pkg) {
        if (pkg == null || "com.android.systemui".equals(pkg)) return;
        if (isCamera(pkg)) {
            if (!inReverse) start(c, "telecamere in primo piano (" + pkg + ")");
            return;
        }
        if (inReverse) end(c, "telecamere chiuse, davanti " + pkg);
        else if (restorePending && !ignorable(c, pkg) && !pkg.equals(saved)) {
            userChoice = true;   // tra la chiusura e il controllo l'utente ha aperto un'altra app
        }
        current = pkg;
    }

    /** Broadcast delle telecamere SAIC (com.saicmotor.360camera.show / close). */
    public static synchronized void onCameraBroadcast(Context c, boolean show) {
        if (show && !inReverse) start(c, "broadcast telecamere aperte");
        else if (!show && inReverse) end(c, "broadcast telecamere chiuse");
    }

    private static void start(Context c, String why) {
        inReverse = true;
        startedAt = SystemClock.elapsedRealtime();
        userChoice = false;
        restorePending = false;
        broughtBy = "";
        startWhy = why;
        saved = ignorable(c, current) || isCamera(current) ? null : current;
        Diag.put("retro.ritorno", "retromarcia iniziata (" + why + ") · app memorizzata " + (saved == null ? "nessuna (davanti c'era il launcher)" : saved));
    }

    private static void end(Context c, String why) {
        inReverse = false;
        long dur = SystemClock.elapsedRealtime() - startedAt;
        if (saved == null) {
            Diag.put("retro.ritorno", "fine retromarcia (" + why + ") · nessuna app da riaprire");
            return;
        }
        if (dur > MAX_REVERSE_MS) {
            Diag.put("retro.ritorno", "fine retromarcia · " + saved + " NON riaperta: retromarcia durata " + (dur / 1000) + " s (più di 3 minuti)");
            saved = null;
            return;
        }
        restorePending = true;
        final Context app = c.getApplicationContext();
        h.postDelayed(() -> check(app, why, dur), CHECK_AFTER_MS);
    }

    /** Annota chi ha riportato il launcher (o la home MG) in primo piano dopo le telecamere. */
    public static synchronized void noteBroughtBy(String who) {
        if (inReverse || restorePending) broughtBy = who;
    }

    /** Tasto Home premuto dall'utente: niente ritorno automatico. */
    public static synchronized void noteHomePressed() {
        if (inReverse || restorePending) {
            userChoice = true;
            broughtBy = "tasto Home dell'utente";
        }
    }

    /**
     * Chiamata da StartGuard quando compare la home MG da sostituire: se c'è un'app da riaprire dopo la retromarcia, si
     * riapre quella al posto del launcher. true = gestito qui.
     */
    public static synchronized boolean interceptMgHome(Context c) {
        if (!restorePending && !inReverse) return false;
        broughtBy = "home MG aperta dal sistema dopo le telecamere";
        if (inReverse) return false;
        return restoreNow(c, "home MG sostituita direttamente dall'app memorizzata");
    }

    private static synchronized void check(Context c, String why, long dur) {
        if (!restorePending) return;
        String top = StartGuard.topPackage(c);
        if (top != null && !top.equals(c.getPackageName()) && !BootWatcher.MG_HOME.equals(top)) {
            restorePending = false;
            Diag.put("retro.ritorno", "fine retromarcia (" + (dur / 1000) + " s) · app memorizzata " + saved + " · davanti "
                    + top + (top.equals(saved) ? ": già tornata da sola" : ": NON riaperta (scelta dell'utente)"));
            return;
        }
        restoreNow(c, "launcher in primo piano dopo le telecamere" + (broughtBy.isEmpty() ? "" : " (riportato da: " + broughtBy + ")"));
    }

    private static boolean restoreNow(Context c, String context) {
        restorePending = false;
        String app = saved;
        saved = null;
        if (app == null) return false;
        String reason = null;
        boolean on = com.mg4.launcher.ui.Prefs.retroReturn(c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE));
        Opener o = opener;
        if (!on) reason = "impostazione spenta";
        else if (userChoice) reason = "l'utente ha scelto un'altra app o premuto Home";
        else if (o != null && !o.available(app)) reason = isProjection(app) ? "telefono/box non più collegato" : "app non più disponibile";
        if (reason != null) {
            Diag.put("retro.ritorno", context + " · app memorizzata " + app + " · NON riaperta: " + reason);
            return false;
        }
        boolean ok;
        if (o != null) ok = o.reopen(app);
        else {
            try {
                android.content.Intent li = c.getPackageManager().getLaunchIntentForPackage(app);
                ok = li != null;
                if (ok) c.startActivity(li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                ok = false;
            }
        }
        Diag.put("retro.ritorno", context + " · app memorizzata " + app + " · " + (ok ? "riaperta" : "riapertura NON riuscita"));
        return ok;
    }
}

package com.mg4.launcher.svc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * 0.4.3 (punto 5): tasti al volante. Sulla SWI69 R45 arrivano come broadcast com.saic.keyevent.hardkey.report (lo
 * ascolta la SystemUI originale, HardKeyManager) con gli extra android.intent.extra.hardkey.keycode (codici KeyEvent di
 * Android: 87 avanti, 88 indietro, 24/25 volume…), android.intent.extra.hardkey.down e android.intent.extra.hardkey.longpress.
 * Qui si ascoltano: per il log media.tastiVolante, per la prova del Controllo funzioni e per la USB 2 (avanti/indietro
 * passano alla sessione USB 2 solo se il sistema non l'ha già fatto, vedi MediaClient.onWheelSkip).
 */
public final class WheelKeys {
    public static final String ACTION = "com.saic.keyevent.hardkey.report";
    private static final String EX_CODE = "android.intent.extra.hardkey.keycode", EX_DOWN = "android.intent.extra.hardkey.down",
            EX_LONG = "android.intent.extra.hardkey.longpress";
    public static final int NEXT = 87, PREV = 88;

    public interface Listener {
        void onKey(int code, boolean down, boolean longPress);
    }

    private static boolean registered;
    private static volatile Listener probe, media;
    private static int count;
    private static final java.util.TreeMap<Integer, Integer> perCode = new java.util.TreeMap<>();

    private WheelKeys() {}

    public static synchronized void start(Context c) {
        if (registered) return;
        try {
            c.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent i) {
                    int code = i.getIntExtra(EX_CODE, -1);
                    boolean down = i.getBooleanExtra(EX_DOWN, false), lp = i.getBooleanExtra(EX_LONG, false);
                    note(code, down, lp);
                    Listener l = probe;
                    if (l != null) l.onKey(code, down, lp);
                    Listener m = media;
                    if (m != null) m.onKey(code, down, lp);
                }
            }, new IntentFilter(ACTION));
            registered = true;
            Diag.put("media.tastiVolante", "in ascolto (" + ACTION + ") · nessun tasto visto finora");
        } catch (Throwable e) {
            Diag.error("media.tastiVolante", e.toString());
        }
    }

    /** Prova del Controllo funzioni: riceve i tasti finché non si rimette null. */
    public static void setProbe(Listener l) {
        probe = l;
    }

    /** MediaClient: avanti/indietro per la USB 2. */
    static void setMedia(Listener l) {
        media = l;
    }

    public static boolean isSkip(int code) {
        return code == NEXT || code == PREV;
    }

    public static String name(int code) {
        switch (code) {
            case 24: return "volume +";
            case 25: return "volume −";
            case 164: return "muto";
            case 85: return "play/pausa";
            case PREV: return "indietro";
            case NEXT: return "avanti";
            case 3: return "home";
            case 5: return "chiama";
            case 6: return "riaggancia";
            case 220: case 221: return "luminosità";
            default: return "codice " + code;
        }
    }

    private static synchronized void note(int code, boolean down, boolean lp) {
        count++;
        Integer n = perCode.get(code);
        perCode.put(code, n == null ? 1 : n + 1);
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<Integer, Integer> e : perCode.entrySet()) {
            sb.append(sb.length() == 0 ? "" : ", ").append(e.getKey()).append(" ").append(name(e.getKey())).append(" ×").append(e.getValue());
        }
        Diag.put("media.tastiVolante", "ultimo codice " + code + " (" + name(code) + ") giù " + down + " lungo " + lp
                + " · sorgente " + MediaClient.currentSourceName() + " · visti " + count + " eventi: " + sb);
    }
}

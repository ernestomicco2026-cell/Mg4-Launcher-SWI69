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
 * <p>
 * 0.4.6 (punto 15): nel Controllo funzioni compaiono anche i codici 297 e 298. Nella home originale EH32 (SwcManager →
 * ModeManager) 297/298 sono volume + / volume − (rotella: CarAudio volumeUp/volumeDown), 299/300 indietro/avanti
 * (skipToPrevious/skipToNext alla sessione media), 301 play/pausa. Le auto possono essere diverse, quindi c'è un passo
 * guidato ("premi AVANTI", "premi INDIETRO") che registra il codice di ciascun tasto: i codici imparati diversi da
 * 87/88 diventano avanti/indietro anche per il launcher. Log tasti.volante.
 */
public final class WheelKeys {
    public static final String ACTION = "com.saic.keyevent.hardkey.report";
    private static final String EX_CODE = "android.intent.extra.hardkey.keycode", EX_DOWN = "android.intent.extra.hardkey.down",
            EX_LONG = "android.intent.extra.hardkey.longpress";
    public static final int NEXT = 87, PREV = 88;
    /** Codici della home originale EH32 (SwcManager): 299 indietro, 300 avanti. */
    public static final int EH32_PREV = 299, EH32_NEXT = 300;
    private static final String K_NEXT = "tastoAvanti", K_PREV = "tastoIndietro";
    private static volatile int learnedNext = -1, learnedPrev = -1;

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
        android.content.SharedPreferences sp = c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
        learnedNext = sp.getInt(K_NEXT, -1);
        learnedPrev = sp.getInt(K_PREV, -1);
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
        return direction(code) != 0;
    }

    /** 1 avanti, -1 indietro, 0 nessuno dei due: codici di Android, della home EH32 e quelli imparati nel passo guidato. */
    public static int direction(int code) {
        if (code < 0) return 0;
        if (code == learnedNext) return 1;
        if (code == learnedPrev) return -1;
        if (code == NEXT || code == EH32_NEXT) return 1;
        if (code == PREV || code == EH32_PREV) return -1;
        return 0;
    }

    /** Codice imparato nel passo guidato del Controllo funzioni. */
    public static boolean learned(int code) {
        return code > 0 && (code == learnedNext || code == learnedPrev);
    }

    /** true per i codici che il sistema esegue già da solo (87/88 di Android): il launcher non li ripete. */
    public static boolean standard(int code) {
        return code == NEXT || code == PREV;
    }

    /** Passo guidato del Controllo funzioni: codici di AVANTI e INDIETRO (-1 = non premuto). */
    public static void learn(Context c, int next, int prev) {
        android.content.SharedPreferences.Editor e = c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE).edit();
        if (next > 0) {
            learnedNext = next;
            e.putInt(K_NEXT, next);
        }
        if (prev > 0 && prev != next) {
            learnedPrev = prev;
            e.putInt(K_PREV, prev);
        }
        e.apply();
        Diag.put("tasti.volante", "imparati: avanti " + (next > 0 ? next + " (" + name(next) + ")" : "non premuto")
                + " · indietro " + (prev > 0 ? prev + " (" + name(prev) + ")" : "non premuto")
                + (next > 0 && next == prev ? " · stesso codice per i due tasti: indietro non salvato" : "")
                + " · in uso: avanti " + learnedNext + ", indietro " + learnedPrev);
    }

    public static String learnedText() {
        return "avanti " + (learnedNext > 0 ? String.valueOf(learnedNext) : "87/300") + " · indietro " + (learnedPrev > 0 ? String.valueOf(learnedPrev) : "88/299");
    }

    public static String name(int code) {
        switch (code) {
            case 24: return "volume +";
            case 25: return "volume −";
            case 164: return "muto";
            case 85: return "play/pausa";
            case PREV: return "indietro";
            case NEXT: return "avanti";
            case 297: return code == learnedNext ? "avanti (imparato)" : code == learnedPrev ? "indietro (imparato)" : "volume + (home EH32)";
            case 298: return code == learnedNext ? "avanti (imparato)" : code == learnedPrev ? "indietro (imparato)" : "volume − (home EH32)";
            case EH32_PREV: return "indietro (home EH32)";
            case EH32_NEXT: return "avanti (home EH32)";
            case 301: return "play/pausa (home EH32)";
            case 3: return "home";
            case 5: return "chiama";
            case 6: return "riaggancia";
            case 220: case 221: return "luminosità";
            default: return code == learnedNext ? "avanti (imparato)" : code == learnedPrev ? "indietro (imparato)" : "codice " + code;
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

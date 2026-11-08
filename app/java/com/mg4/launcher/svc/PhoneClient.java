package com.mg4.launcher.svc;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.4.3 (punto 7): telefono Bluetooth per i "Preferiti telefono" del riquadro sotto la stazione.
 * <ul>
 *   <li>SWI69 (R45): non c'è il servizio com.saicmotor.service.btcall della SWI68. L'app Telefono originale
 *       (com.saicmotor.btphone, BTCall_eh32_ll) espone BTPhoneService con l'azione com.saicmotor.btphone.voice e
 *       l'interfaccia com.saicmotor.btphone.IVoiceBTPhoneService (quella che usa l'assistente vocale):
 *       1 = chiama il numero (0 = avviata, 128 numero vuoto, 129 numero non valido, 131 rifiutata).</li>
 *   <li>telefono collegato: profilo Bluetooth HFP lato auto (16) collegato, come fa l'app Telefono.</li>
 *   <li>rubrica: il Bluetooth (PBAP) la scarica nei contatti di Android; l'app Telefono la legge da lì
 *       (ContactsContract Phone), così come qui.</li>
 * </ul>
 * La chiamata la fa il servizio telefono: al launcher non serve CALL_PHONE. Nei log mai nomi né numeri.
 */
public final class PhoneClient {
    public interface Listener {
        void onPhoneChanged();
    }

    public static final class Contact {
        public final String name, number;

        public Contact(String name, String number) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
        }
    }

    private static final String PKG = "com.saicmotor.btphone", ACTION = "com.saicmotor.btphone.voice";
    private static final String DESC = "com.saicmotor.btphone.IVoiceBTPhoneService";
    private static final int PLACE_CALL = 1;
    /** BluetoothProfile.HEADSET_CLIENT (nascosto): 16. */
    private static final int PROFILE_HFP_CLIENT = 16;
    private static final String HFP_CHANGED = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED";

    private static PhoneClient instance;

    private final Context ctx;
    private final ServiceLink link;
    private final Handler work;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile int btState = -1, syncState = -1;
    private android.content.BroadcastReceiver receiver;
    private int users;

    private PhoneClient(Context c) {
        ctx = c.getApplicationContext();
        HandlerThread t = new HandlerThread("mg4-telefono");
        t.start();
        work = new Handler(t.getLooper());
        link = new ServiceLink(ctx, "telefono", ServiceLink.byAction(PKG, ACTION));
        link.setOnConnected(this::refresh);
    }

    public static synchronized PhoneClient get(Context c) {
        if (instance == null) instance = new PhoneClient(c);
        return instance;
    }

    /** Si collega al servizio (contato: si scollega quando l'ultimo utilizzatore chiama stop). */
    public synchronized void start(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
        if (users++ == 0) {
            link.start();
            try {
                receiver = new android.content.BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, android.content.Intent i) {
                        refresh();
                    }
                };
                android.content.IntentFilter f = new android.content.IntentFilter(HFP_CHANGED);
                f.addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
                ctx.registerReceiver(receiver, f);
            } catch (Throwable e) {
                receiver = null;
            }
            refresh();
        }
    }

    public synchronized void stop(Listener l) {
        if (l != null) listeners.remove(l);
        if (users > 0 && --users == 0) {
            if (receiver != null) {
                try {
                    ctx.unregisterReceiver(receiver);
                } catch (Throwable ignored) {
                }
                receiver = null;
            }
            link.stop();
        }
    }

    public boolean serviceConnected() {
        return link.binder() != null;
    }

    /** Telefono collegato in Bluetooth (HFP). */
    public boolean phoneConnected() {
        return btState == 2;
    }

    public int btState() {
        return btState;
    }

    /** Stato della rubrica: 2 = ci sono contatti, 0 = vuota, -1 = non letta. */
    public int syncState() {
        return syncState;
    }

    /** Rilegge lo stato del telefono (sul thread del telefono). */
    public void refresh() {
        work.post(() -> {
            int bs;
            try {
                android.bluetooth.BluetoothAdapter ad = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                if (ad == null) bs = -1;
                else if (!ad.isEnabled()) bs = 0;
                else bs = ad.getProfileConnectionState(PROFILE_HFP_CLIENT) == 2 ? 2 : 1;
            } catch (Throwable e) {
                bs = -1;
            }
            boolean changed = bs != btState;
            btState = bs;
            Diag.put("telefono.stato", (link.binder() == null ? "servizio telefono non collegato · " : "servizio telefono collegato · ")
                    + "Bluetooth " + btTxt(bs));
            if (changed) notifyListeners();
        });
    }

    private static String btTxt(int s) {
        switch (s) {
            case 0: return "spento";
            case 1: return "acceso, nessun telefono";
            case 2: return "telefono collegato";
            default: return "?";
        }
    }

    private void notifyListeners() {
        main.post(() -> {
            for (Listener l : listeners) l.onPhoneChanged();
        });
    }

    /** Numero pulito come fa l'app Telefono: niente spazi, trattini, parentesi. */
    public static String clean(String n) {
        if (n == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : n.toCharArray()) if (Character.isDigit(ch) || ch == '+' || ch == '*' || ch == '#') sb.append(ch);
        return sb.toString();
    }

    /** Chiama il numero tramite il servizio telefono. Risultato nel log senza il numero. */
    public void call(String number, java.util.function.Consumer<Boolean> done) {
        String n = clean(number);
        work.post(() -> {
            boolean ok = false;
            String why;
            IBinder b = link.binder();
            if (n.isEmpty()) why = "numero vuoto";
            else if (b == null) why = "servizio telefono non collegato";
            else {
                try {
                    Integer r = Rpc.call(b, DESC, PLACE_CALL, p -> p.writeString(n), Parcel::readInt);
                    ok = r != null && r == 0;
                    why = ok ? "avviata" : "rifiutata dal servizio (codice " + r + ": telefono non collegato o numero non valido)";
                } catch (Exception e) {
                    why = "errore " + e.getClass().getSimpleName();
                }
            }
            Diag.put("telefono.chiamata", why + " · " + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ITALY).format(new java.util.Date()));
            if (done != null) {
                boolean res = ok;
                main.post(() -> done.accept(res));
            }
        });
    }

    /**
     * Rubrica del telefono collegato (nome + numero, senza doppioni), in ordine alfabetico. Vuota se il telefono non
     * è collegato o la rubrica non è sincronizzata. Da chiamare fuori dal thread principale.
     */
    public List<Contact> contacts() {
        List<Contact> out = new ArrayList<>();
        ensureContactsPermission(ctx);
        android.database.Cursor c = null;
        try {
            c = ctx.getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{"display_name", "data1"}, null, null, "display_name COLLATE NOCASE ASC");
            java.util.Set<String> seen = new java.util.HashSet<>();
            while (c != null && c.moveToNext() && out.size() < 2000) {
                String name = c.getString(0), num = clean(c.getString(1));
                if (num.isEmpty() || name == null || name.isEmpty() || "本机号码".equals(name)) continue;
                if (!seen.add(name + "|" + num)) continue;
                out.add(new Contact(name, num));
            }
            syncState = out.isEmpty() ? 0 : 2;
            Diag.put("telefono.rubrica", out.size() + " numeri letti");
        } catch (Throwable e) {
            Diag.error("telefono.rubrica", e.getClass().getSimpleName());
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Il launcher è un'app di sistema: il permesso della rubrica se lo concede da solo (come la posizione). */
    static void ensureContactsPermission(Context c) {
        String perm = "android.permission.READ_CONTACTS";
        if (c.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            pm.getClass().getMethod("grantRuntimePermission", String.class, String.class, android.os.UserHandle.class)
                    .invoke(pm, c.getPackageName(), perm, android.os.Process.myUserHandle());
        } catch (Throwable ignored) {
        }
    }
}

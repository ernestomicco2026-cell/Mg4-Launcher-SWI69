package com.mg4.launcher.svc;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.lang.reflect.Method;

/**
 * Proprietà batteria SAIC lette dal CarService di Android (manager "bms", come l'app Energia SWI69).
 * La libreria android.car è nel classpath di sistema: la si usa per riflessione, senza includerla nell'APK.
 * ID presi da android.car.hardware.CarBMSManager del firmware SWI69.
 */
public final class CarBms {
    public static final int CHRG_TRGT_SOC = 557904908;      // int, codice 1..7 (40%..100%), lettura e comando
    public static final int ALTNG_CHRG_CRNT = 557904907;    // int, corrente AC 1..4 (6 A, 8 A, 16 A, Max), lettura e comando
    public static final int CHRG_CTRL = 557904914;          // int, 1 avvia, 2 ferma (come la Gestione ricarica)
    public static final int V2X = 557904944;                // int, scarica V2L: 1 avvia, 2 ferma
    public static final int V2X_SP_SOC = 557904943;         // int, limite di scarica V2L (codice)
    public static final int PACK_SOC_DSP = 560002052;       // float, % mostrata
    public static final int PACK_VOL = 560002054;           // float, V
    public static final int PACK_CRNT = 560002055;          // float, A
    public static final int ESTD_ELEC_RNG = 557904918;      // int, km
    public static final int CHRG_STS = 557904905;           // int, stato ricarica BMS

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Object car;
    private volatile Object bms;
    private volatile Method getProperty, setProperty;
    /** Proprietà che l'auto restituisce vuote o non supporta: non si rileggono più (niente errori ripetuti). */
    private final java.util.Set<Integer> unsupported = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    /** Manager "hvac" del CarService (temperatura dell'abitacolo). */
    private volatile Object hvac;
    private volatile Method hvacGetFloat;
    /** Manager "property" del CarService (proprietà generiche, es. pressione pneumatici). */
    private volatile Object prop;
    private volatile Method propGetFloat, propGetInt;
    private boolean started;

    public CarBms(Context c) {
        ctx = c.getApplicationContext();
    }

    public void start() {
        if (started) return;
        started = true;
        main.post(this::connect);
    }

    public boolean isReady() {
        return bms != null && getProperty != null;
    }

    private void connect() {
        try {
            Class<?> carCls = Class.forName("android.car.Car");
            Method create = carCls.getMethod("createCar", Context.class, ServiceConnection.class);
            car = create.invoke(null, ctx, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    try {
                        bms = car.getClass().getMethod("getCarManager", String.class).invoke(car, "bms");
                        if (bms != null) {
                            getProperty = bms.getClass().getMethod("getProperty", Class.class, int.class, int.class);
                            try {
                                setProperty = bms.getClass().getMethod("setProperty", Class.class, int.class, int.class, Object.class);
                            } catch (Throwable e) {
                                Diag.error("bms.comandi", e.toString());
                            }
                        }
                        Diag.put("bms.stato", bms != null ? "collegato" : "manager bms assente");
                        try {
                            prop = car.getClass().getMethod("getCarManager", String.class).invoke(car, "property");
                            if (prop != null) {
                                propGetFloat = prop.getClass().getMethod("getFloatProperty", int.class, int.class);
                                propGetInt = prop.getClass().getMethod("getIntProperty", int.class, int.class);
                            }
                            Diag.put("property.stato", prop != null ? "collegato" : "manager property assente");
                        } catch (Throwable e) {
                            Diag.error("property.manager", e.toString());
                        }
                        try {
                            hvac = car.getClass().getMethod("getCarManager", String.class).invoke(car, "hvac");
                            if (hvac != null) hvacGetFloat = hvac.getClass().getMethod("getFloatProperty", int.class, int.class);
                            Diag.put("hvac.stato", hvac != null ? "collegato" : "manager hvac assente");
                        } catch (Throwable e) {
                            Diag.error("hvac.manager", e.toString());
                        }
                    } catch (Throwable e) {
                        Diag.error("bms.manager", e.toString());
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    bms = null;
                    Diag.put("bms.stato", "scollegato");
                }
            });
            carCls.getMethod("connect").invoke(car);
            Diag.put("bms.stato", "connessione…");
        } catch (Throwable e) {
            Diag.error("bms.car", e.toString());
            main.postDelayed(this::connect, 15000);
        }
    }

    /** Proprietà float del manager "hvac" (CarHvacManager) per una zona, o NaN se non leggibile. */
    public float hvacFloat(int id, int area) {
        Object m = hvac;
        Method g = hvacGetFloat;
        if (m == null || g == null) return Float.NaN;
        try {
            Object v = g.invoke(m, id, area);
            return v instanceof Float ? (Float) v : Float.NaN;
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            Diag.put("hvac.0x" + Integer.toHexString(id) + "@" + area, "errore " + c.getClass().getSimpleName());
            return Float.NaN;
        }
    }

    /** Proprietà float generica del CarService per un'area, o NaN (l'errore va nei log). */
    public float propFloat(int id, int area) {
        Object v = propCall(propGetFloat, id, area);
        return v instanceof Float ? (Float) v : Float.NaN;
    }

    /** Proprietà int generica del CarService per un'area, o Integer.MIN_VALUE. */
    public int propInt(int id, int area) {
        Object v = propCall(propGetInt, id, area);
        return v instanceof Integer ? (Integer) v : Integer.MIN_VALUE;
    }

    private Object propCall(Method g, int id, int area) {
        Object m = prop;
        if (m == null || g == null) return null;
        try {
            return g.invoke(m, id, area);
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            Diag.put("property.0x" + Integer.toHexString(id) + "@" + area, "errore " + c.getClass().getSimpleName());
            return null;
        }
    }

    /** Valore float della proprietà, o NaN se non leggibile. */
    public float getFloat(int id) {
        Object v = get(Float.class, id);
        return v instanceof Float ? (Float) v : Float.NaN;
    }

    /** Valore int della proprietà, o Integer.MIN_VALUE se non leggibile. */
    public int getInt(int id) {
        Object v = get(Integer.class, id);
        return v instanceof Integer ? (Integer) v : Integer.MIN_VALUE;
    }

    private Object get(Class<?> type, int id) {
        Object m = bms;
        Method g = getProperty;
        if (m == null || g == null || unsupported.contains(id)) return null;
        try {
            Object v = g.invoke(m, type, id, 0);
            if (v == null) {
                // valore vuoto: si segna una volta e non si rilegge più (prima generava un errore ogni 2 secondi)
                unsupported.add(id);
                Diag.put("bms.0x" + Integer.toHexString(id), "valore vuoto: non disponibile su questa auto");
            }
            return v;
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            if (c instanceof NullPointerException || c instanceof IllegalArgumentException) {
                unsupported.add(id);
                Diag.put("bms.0x" + Integer.toHexString(id), "non disponibile su questa auto (" + c.getClass().getSimpleName() + ")");
            } else {
                Diag.error("bms.0x" + Integer.toHexString(id), c.toString());
            }
            return null;
        }
    }

    /** Comando intero al gestore batteria (come la pagina Gestione ricarica). True se inviato. */
    public boolean setInt(int id, int value) {
        Object m = bms;
        Method s = setProperty;
        if (m == null || s == null) return false;
        try {
            s.invoke(m, Integer.class, id, 0, value);
            return true;
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            Diag.error("bms.comando.0x" + Integer.toHexString(id), c.toString());
            return false;
        }
    }

    /** True se la proprietà si legge (non segnata come vuota o non supportata). */
    public boolean supported(int id) {
        return !unsupported.contains(id);
    }
}

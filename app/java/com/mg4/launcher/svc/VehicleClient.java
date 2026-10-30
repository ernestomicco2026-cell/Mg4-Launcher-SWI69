package com.mg4.launcher.svc;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

/**
 * Veicolo, versione SWI69 (EH32 Color Radio). Stessa interfaccia della versione SWI68, ma con i servizi della SWI69:
 * <ul>
 *   <li>com.saicmotor.caradapter: "hub" (ICarAdapterService.queryClient(codice)) che restituisce i binder di
 *       ricarica, clima, luci, configurazione, quadro e impostazioni veicolo, come la home originale (carapi);</li>
 *   <li>gestore batteria "bms" del CarService (CarBms): percentuale, potenza e i comandi di ricarica, come la
 *       pagina Gestione ricarica dell'app Energia (com.saicmotor.saicev, schede EH32 EU/UK);</li>
 *   <li>proprietà del CarService (CarProps): pressione pneumatici e temperatura interna.</li>
 * </ul>
 */
public final class VehicleClient {
    private static final String PKG = "com.saicmotor.caradapter";
    private static final String ACTION = "com.saicmotor.caradapter.CarAdapterService";

    private static final String HUB = "com.saicmotor.carapi.ICarAdapterService";
    private static final int HUB_QUERY_CLIENT = 1;
    private static final int BIND_EV = 4, BIND_HVAC = 7, BIND_CONFIG = 2, BIND_LAMP = 13, BIND_IPK = 5, BIND_VS = 8;

    private static final String EV = "com.saicmotor.carapi.ev.ICarEvService";
    private static final int EV_PERCENT = 3;          // int  getChargePercentChange
    private static final int EV_STATUS = 4;           // int  getChargeStatus (stessi codici della SWI68)
    private static final int EV_REMAIN_TIME = 6;      // int  getChargingRemainTime (minuti)
    private static final int EV_DISCHG_TIME = 7;      // int  getDisChargingRemainTime
    private static final int EV_UNITS = 11;           // int  getDistanceUnits (0 = km, 1 = miglia)
    private static final int EV_RANGE = 12;           // int  getCurrentRange
    private static final int EV_CHG_CTRL = 20;        // int  getChargeCtrlStatus
    private static final int EV_DISCHG_CTRL = 24;     // int  getDisChargeCtrl
    public static final int CTRL_START = 1, CTRL_STOP = 2, CTRL_PREPARING = 3;
    /** Correnti di ricarica AC ammesse dall'auto (codici 1..4, CarBMSManager.ALTNG_CHRG_CRNT_*). */
    public static final String[] CURRENT_LABELS = {"6 A", "8 A", "16 A", "Max"};

    private static final String AC = "com.saicmotor.carapi.hvac.ICarHvacService";
    private static final int AC_SWITCH_POWER = 5;     // void switchHvacPowerStatus()
    private static final int AC_GET_POWER = 6;        // bool getHvacPowerStatus
    private static final int AC_SWITCH_AUTO = 9;      // void switchAutoStatus()
    private static final int AC_GET_AUTO = 10;        // bool getAutoStatus
    private static final int AC_SET_FAN = 19;         // void setFanSpeed(int)
    private static final int AC_GET_FAN = 20;         // int  getFanSpeed
    private static final int AC_SET_DRV_TEMP = 21;    // void setDriverTemperature(float)
    private static final int AC_GET_DRV_TEMP = 22;    // float getDriverTemperature
    private static final int AC_GET_OUT_TEMP = 25;    // float getOutSideTemperature
    private static final int AC_SWITCH_AC = 7, AC_GET_AC = 8;                 // switchACStatus / getACStatus
    private static final int AC_SWITCH_DEFROST = 27, AC_GET_DEFROST = 28;     // parabrezza
    // ricircolo, come la barra clima della home originale: "cambia" senza argomenti; 0 interno, 1 esterno, 2 auto
    private static final int AC_SWITCH_LOOP = 15, AC_GET_LOOP = 16;

    // fendinebbia, come la schermata dell'auto originale: set(1) = "premuto" (commuta), get 1 = acceso
    private static final String LAMP = "com.saicmotor.carapi.lamp.ICarLampService";
    private static final int LAMP_SET_REAR_FOG = 30, LAMP_GET_REAR_FOG = 31, LAMP_SET_FRONT_FOG = 40, LAMP_GET_FRONT_FOG = 41;
    private static final String CONFIG = "com.saicmotor.carapi.config.ICarConfigService";
    private static final int CONFIG_FRONT_FOG = 60;   // getFrontFrogConfigData: 0 = fendinebbia anteriori presenti
    // assistenza alla guida, come l'app Impostazioni veicolo originale della SWI69 R45 (ICarConfigService)
    private static final int CONFIG_LKA = 20;         // getLkaConfigData: 0 = LKA presente, 1 assente
    private static final int CONFIG_ONE_PEDAL = 43;
    /** getRvcAvmConfigData, come la home originale SWI69 R45: 0 = nessuna telecamera; 1, 2, 4 = EVS; 3 = AVM; 5 = AVM quadro. */
    private static final int CONFIG_RVC_AVM = 11;   // getOnePedalConfig: 0 = One Pedal presente (H17A), 1 assente
    private static final String VS = "com.saicmotor.carapi.vs.ICarVehicleSettingService";
    // LKA (getLasMode/setLasMode): 0 nessuna richiesta, 1 spento, 2 LDW, 3 LDP, 4 LKA, 5 ELK
    private static final int VS_SET_LAS = 30, VS_GET_LAS = 31;
    // One Pedal: setSignalPedalState / getSignalPedalState (1 acceso, 0 spento) / getSignalPedalEnable (blocco:
    // 1 limite di carica, 2 sistema frenante, 3 condizioni, 0 nessuno)
    private static final int VS_SET_OPD = 162, VS_GET_OPD = 163, VS_GET_OPD_INHIBIT = 164;
    // chiavi dello stato "previsto" (diverse dai codici delle luci: 31 è anche il retronebbia)
    private static final int K_LKA = 80031, K_OPD = 80163;
    // assistente di guida (ACC/TJA) e pedale del freno (0.3.8 / 0.2.5): stesse proprietà dell'auto della SWI68
    private static final int VS_SET_ACC_TJA = 152, VS_GET_ACC_TJA = 153, VS_SET_PEDAL = 156, VS_GET_PEDAL = 157;
    private static final int K_ACC_TJA = 80153, K_PEDAL = 80157;
    private static final String IPK = "com.saicmotor.carapi.ipk.ICarIpkService";
    private static final int IPK_TYRE_UNIT = 29;      // 0 bar, 1 kPa, 2 psi (scelta dell'utente sul quadro)

    /** Ventola come la barra del launcher: 0 = clima spento, 1..11, 15 = automatico. */
    public static final int FAN_OFF = 0, FAN_AUTO = 15;
    private static final int FAN_MIN = 1, FAN_MAX = 11;
    /** Per quanto tempo il valore impostato dall'utente prevale sulle letture (che arrivano in ritardo). */
    private static final long HOLD_MS = 4000;

    /**
     * Temperatura nella barra: MG4 EU/UK su SWI69 va da 16 a 30 °C, con LO = 15,5 e HI = 30,5
     * (CarModel.isTempRange16to30CarModel della home originale). Nel launcher: 15 = "LO", 31 = "HI".
     */
    public static final int TEMP_MIN = 15;   // "LO"
    public static final int TEMP_MAX = 31;   // "HI"
    private static final float CAR_LO = 15.5f, CAR_HI = 30.5f;

    /** Istantanea dello stato del veicolo letta dai servizi. */
    public static final class State {
        public boolean connected;
        public float soc = -1;            // %
        public int rangeKm = -1;
        public int chargingStatus = -1;   // codici SAIC (uguali alla SWI68), vedi chargeText
        public int predictMinutes = -1;
        public float chargeKw = 0;
        public int chargeLimit = -1;      // % (convertito dal codice SAIC)
        public int chargeLimitCode = -1;  // 1..7
        public int currentLimit = -1;     // 1..4 (6 A, 8 A, 16 A, Max), 0 = non disponibile
        public int chargeControl = -1;    // 1 in carica, 2 fermo, 3 in preparazione
        public int dischargeControl = -1; // idem per il V2L
        public int dischargeLimit = -1;   // % sotto cui il V2L si ferma
        public int dischargeMinutes = -1;
        public boolean bmsAvailable;
        public int distUnit = -1;         // 0 km, 1 miglia (dal quadro)
        public int tyreUnitCluster = -1;  // unità pressione del quadro: 0 bar, 1 kPa, 2 psi
        public int dischargeStatus = -1;
        public int loopMode = -1;         // ricircolo: 0 interno, 1 esterno, 2 auto, -1 non supportato
        public int acTemp = -1;           // 15 = LO, 16..30, 31 = HI
        public int hvacPower = -1;
        public int acOn = -1, autoOn = -1, defrostOn = -1;   // 1 acceso, 0 spento, -1 sconosciuto
        public int seatLeft = -1, seatRight = -1;          // SWI69 R45: nessun comando dei sedili riscaldati
        public int wheelHeat = -1;                         // SWI69 R45: nessun comando del volante riscaldato
        public float[] tireBar = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};   // AS, AD, PS, PD
        public float insideTemp = Float.NaN;
        public int rearFog = -1, frontFog = -1;           // 1 acceso, 0 spento, -1 non disponibile
        public boolean hasFrontFog;
        /** Configurazione telecamere (getRvcAvmConfigData); Integer.MIN_VALUE finché non si sa. */
        public int cameraConfig = Integer.MIN_VALUE;
        public int lka = -1;              // 1 acceso, 0 spento, -1 non disponibile
        public int lkaMode = -1;          // valore grezzo dell'auto (vedi VS_GET_LAS)
        /** Assistente di guida (valori dell'auto): 1 ACC, 2 TJA, 4 spento; -1 non disponibile. */
        public int accTja = -1;
        /** Sensibilità del pedale del freno: 1 Comfort, 0 Medium, 2 Sport; -1 non disponibile. */
        public int pedal = -1;
        public int opd = -1;              // One Pedal Drive: 1 acceso, 0 spento, -1 non disponibile
        public int opdInhibit;            // motivo del blocco OPD (1 in carica, 2 sistema, 3 condizioni), 0 nessuno
        public int fanLevel = -1;         // 0 spenta, 1..max, 15 auto
        public int fanMin = FAN_MIN, fanMax = FAN_MAX;
        public float outsideTemp = -10000f;   // °C
    }

    public interface Listener {
        void onVehicleState(State s);
    }

    private final Context ctx;
    private final ServiceLink hub;
    private final CarBms bms;
    private final Map<Integer, IBinder> subs = new HashMap<>();
    private final HandlerThread thread = new HandlerThread("mg4-vehicle");
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile State last = new State();
    private volatile int pendingTemp = -1, pendingFan = -1;
    private volatile int heldTemp = -1, heldFan = -1;
    private volatile long tempHoldUntil, fanHoldUntil;
    private boolean running;

    public VehicleClient(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        hub = new ServiceLink(ctx, "veicolo", ServiceLink.byAction(PKG, ACTION));
        bms = new CarBms(ctx);
        hub.setOnConnected(() -> {
            synchronized (subs) {
                subs.clear();
            }
            if (bg != null) bg.post(this::poll);
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public State last() {
        return last;
    }

    public void start() {
        if (running) return;
        running = true;
        if (bg == null) {
            thread.start();
            bg = new Handler(thread.getLooper());
        }
        hub.start();
        bms.start();
        bg.post(loop);
    }

    public void stop() {
        pause();
        hub.stop();
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

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            poll();
            bg.postDelayed(this, 2000);
        }
    };

    private IBinder sub(int code) throws RemoteException {
        synchronized (subs) {
            IBinder b = subs.get(code);
            if (b != null && b.isBinderAlive()) return b;
        }
        IBinder h = hub.binder();
        if (h == null) return null;
        IBinder b = Rpc.callBinder(h, HUB, HUB_QUERY_CLIENT, p -> p.writeInt(code));
        if (b != null) {
            synchronized (subs) {
                subs.put(code, b);
            }
        }
        return b;
    }

    private void forget(int code) {
        synchronized (subs) {
            subs.remove(code);
        }
    }

    /** Limite di ricarica: codice 1 = 40% … 7 = 100% (CarBMSManager.CHRG_TRGT_SOC_*). */
    static int limitPercent(int code) {
        if (code >= 1 && code <= 7) return (code + 3) * 10;
        return -1;
    }

    /** Temperatura dell'auto (°C, a mezzi gradi) → valore della barra (15 = LO … 31 = HI). */
    static int tempFromCar(float f) {
        if (Float.isNaN(f) || f <= 0) return -1;
        if (f <= CAR_LO + 0.01f) return TEMP_MIN;
        if (f >= CAR_HI - 0.01f) return TEMP_MAX;
        return Math.max(TEMP_MIN + 1, Math.min(TEMP_MAX - 1, Math.round(f)));
    }

    /** Valore della barra → temperatura da inviare all'auto. */
    static float tempToCar(int v) {
        if (v <= TEMP_MIN) return CAR_LO;
        if (v >= TEMP_MAX) return CAR_HI;
        return v;
    }

    private static boolean ok(float v) {
        return !Float.isNaN(v);
    }

    private int distUnit = -1;

    private void poll() {
        State s = new State();
        s.connected = hub.isConnected();
        long now = android.os.SystemClock.uptimeMillis();
        if (s.connected) {
            try {
                IBinder e = sub(BIND_EV);
                if (e != null) {
                    s.soc = Rpc.callInt(e, EV, EV_PERCENT);
                    s.rangeKm = Rpc.callInt(e, EV, EV_RANGE);
                    s.chargingStatus = Rpc.callInt(e, EV, EV_STATUS);
                    s.predictMinutes = Rpc.callInt(e, EV, EV_REMAIN_TIME);
                    s.dischargeStatus = Rpc.callInt(e, EV, EV_DISCHG_CTRL);
                    if (isDischarging(s.chargingStatus)) s.dischargeMinutes = Rpc.callInt(e, EV, EV_DISCHG_TIME);
                    if (distUnit < 0) distUnit = Rpc.callInt(e, EV, EV_UNITS);
                    s.distUnit = distUnit;
                    Diag.put("veicolo.autonomia", s.rangeKm + " (unità " + distUnit + ")");
                    Diag.put("veicolo.statoRicarica", s.chargingStatus);
                    Diag.put("veicolo.minutiRicarica", s.predictMinutes);
                    Diag.put("veicolo.comandoRicarica(ev)", Rpc.callInt(e, EV, EV_CHG_CTRL));
                    Diag.put("veicolo.statoScarica(V2L)", s.dischargeStatus);
                }
            } catch (Exception ex) {
                Diag.error("ricarica", ex.toString());
                forget(BIND_EV);
            }
            try {
                IBinder a = sub(BIND_HVAC);
                if (a != null) {
                    sendPending(a);
                    boolean power = Rpc.callBool(a, AC, AC_GET_POWER);
                    boolean auto = Rpc.callBool(a, AC, AC_GET_AUTO);
                    float drv = Rpc.callFloat(a, AC, AC_GET_DRV_TEMP);
                    int readTemp = tempFromCar(drv);
                    s.acTemp = (now < tempHoldUntil && heldTemp > 0) ? heldTemp : readTemp;
                    int speed = Rpc.callInt(a, AC, AC_GET_FAN);
                    int readFan = !power ? FAN_OFF : auto ? FAN_AUTO : speed;
                    s.fanLevel = (now < fanHoldUntil && heldFan >= 0) ? heldFan : readFan;
                    s.hvacPower = power ? 1 : 0;
                    s.outsideTemp = Rpc.callFloat(a, AC, AC_GET_OUT_TEMP);
                    // 0.9.3: valore non valido (flag del firmware, come il SystemUI originale) o -40 di "nessun dato": non si mostra
                    float rawOut = s.outsideTemp;
                    int outV = outValid;
                    // 0.3.8/0.2.5: qui "nessun dato" vale 0, uguale a 0 °C veri: lo 0 si mostra solo se l'auto lo dà per valido
                    boolean outBad = outV == 1 || rawOut <= -39.5f || (rawOut == 0f && outV != 0);
                    if (outBad) s.outsideTemp = -10000f;
                    else lastOutside = rawOut;
                    Diag.put("clima.tempEsterna", rawOut + (outV == Integer.MIN_VALUE ? "" : " · validità " + (outV == 0 ? "ok" : outV == 1 ? "NON valida" : String.valueOf(outV)))
                            + (outBad ? " → nascosta" : ""));
                    Diag.put("clima.tempGuidatore", drv + (s.acTemp != readTemp ? " (richiesta " + s.acTemp + ")" : ""));
                    Diag.put("clima.ventola", speed + (auto ? " auto" : "") + (s.fanLevel != readFan ? " (richiesta " + s.fanLevel + ")" : ""));
                    Diag.put("clima.accensione", power);
                    s.acOn = held(AC_GET_AC, Rpc.callBool(a, AC, AC_GET_AC) ? 1 : 0, now);
                    s.autoOn = held(AC_GET_AUTO, auto ? 1 : 0, now);
                    s.defrostOn = held(AC_GET_DEFROST, Rpc.callBool(a, AC, AC_GET_DEFROST) ? 1 : 0, now);
                    Diag.put("clima.ac/auto/sbrinamento", s.acOn + " / " + s.autoOn + " / " + s.defrostOn);
                    if (loopSupported) {
                        try {
                            int loop = Rpc.callInt(a, AC, AC_GET_LOOP);
                            s.loopMode = held(AC_GET_LOOP, loop >= 0 && loop <= 2 ? loop : -1, now);
                            Diag.put("clima.ricircolo", loop + " (0 interno, 1 esterno, 2 auto)");
                        } catch (Exception ex) {
                            loopSupported = false;   // non si richiede più: il pulsante resta nascosto
                            Diag.put("clima.ricircolo", "non supportato: " + ex.getClass().getSimpleName());
                        }
                    }
                }
            } catch (Exception ex) {
                Diag.error("clima", ex.toString());
                forget(BIND_HVAC);
            }
            readCluster(s);
        }
        readLamps(s);
        readBms(s, now);
        readCarProps(s);
        last = s;
        main.post(() -> {
            if (listener != null) listener.onVehicleState(s);
        });
    }

    private boolean loopSupported = true;

    /** Unità della pressione scelta sul quadro (per "Automatica"). */
    private void readCluster(State s) {
        if (clusterUnit != Integer.MIN_VALUE && android.os.SystemClock.uptimeMillis() - clusterAt < 60_000) {
            s.tyreUnitCluster = clusterUnit;
            return;
        }
        try {
            IBinder ipk = sub(BIND_IPK);
            if (ipk != null) {
                clusterUnit = Rpc.callInt(ipk, IPK, IPK_TYRE_UNIT);
                clusterAt = android.os.SystemClock.uptimeMillis();
                Diag.put("tpms.unitàQuadro", clusterUnit + " (0 bar, 1 kPa, 2 psi)");
            }
        } catch (Exception e) {
            forget(BIND_IPK);
        }
        s.tyreUnitCluster = clusterUnit == Integer.MIN_VALUE ? -1 : clusterUnit;
    }

    private int clusterUnit = Integer.MIN_VALUE;
    private long clusterAt;

    /** Gestore batteria: percentuale con decimali, potenza, limite e comandi di ricarica (come l'app Energia). */
    private void readBms(State s, long now) {
        if (!bms.isReady()) {
            Diag.put("bms.letture", "non disponibile");
            return;
        }
        s.bmsAvailable = true;
        float socDsp = bms.getFloat(CarBms.PACK_SOC_DSP);
        if (ok(socDsp) && socDsp >= 0 && socDsp <= 100) s.soc = socDsp;
        int code = bms.getInt(CarBms.CHRG_TRGT_SOC);
        if (code >= 1 && code <= 7) {
            s.chargeLimitCode = code;
            s.chargeLimit = limitPercent(code);
        }
        int lim = held(CarBms.CHRG_TRGT_SOC, s.chargeLimitCode, now);
        if (lim >= 1 && lim != s.chargeLimitCode) {
            s.chargeLimitCode = lim;
            s.chargeLimit = limitPercent(lim);
        }
        int cur = bms.getInt(CarBms.ALTNG_CHRG_CRNT);
        s.currentLimit = held(CarBms.ALTNG_CHRG_CRNT, cur >= 0 && cur <= 4 ? cur : -1, now);
        int ctl = bms.getInt(CarBms.CHRG_CTRL);
        s.chargeControl = held(CarBms.CHRG_CTRL, ctl == Integer.MIN_VALUE ? -1 : ctl, now);
        int v2x = bms.getInt(CarBms.V2X);
        s.dischargeControl = held(CarBms.V2X, v2x == Integer.MIN_VALUE ? -1 : v2x, now);
        int dl = bms.getInt(CarBms.V2X_SP_SOC);
        s.dischargeLimit = dl >= 0 && dl <= 7 ? (dl + 1) * 10 : -1;
        float amps = bms.getFloat(CarBms.PACK_CRNT);
        float volts = bms.getFloat(CarBms.PACK_VOL);
        if (ok(amps) && ok(volts)) s.chargeKw = Math.abs(amps * volts) / 1000f;
        if (s.rangeKm <= 0) {
            int r = bms.getInt(CarBms.ESTD_ELEC_RNG);
            if (r > 0 && r < 2000) s.rangeKm = r;
        }
        Diag.put("bms.soc", socDsp);
        Diag.put("veicolo.limiteRicarica", "codice " + code + " = " + s.chargeLimit + "%");
        Diag.put("ricarica.comandi", "corrente " + cur + " · avvia/ferma " + ctl + " · V2L " + v2x + " · limite V2L " + dl
                + " · unità " + distUnit);
        Diag.putQuiet("veicolo.correnteA/tensioneV", amps + " / " + volts);
    }

    // ---- TPMS e validità della temperatura esterna dal CarService (0.9.5: la temperatura interna non c'è) ----------------------------------------------

    private CarProps props;
    private static volatile CarProps shared;

    /** Lettore del CarService condiviso (null finché il servizio veicolo non l'ha creato). */
    public static CarProps carProps() {
        return shared;
    }

    private long propsAt;
    private final float[] tireCache = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    private volatile int outValid = Integer.MIN_VALUE;
    private volatile float lastOutside = Float.NaN;

    private void readCarProps(State s) {
        if (props == null) {
            props = new CarProps(ctx);
            props.connect();
            shared = props;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (props.ready() && now - propsAt > 5000) {
            propsAt = now;
            int[] ids = {CarProps.TIRE_FL, CarProps.TIRE_FR, CarProps.TIRE_RL, CarProps.TIRE_RR};
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                int v = props.getInt(ids[i]);
                tireCache[i] = CarProps.toBar(v);
                raw.append(i == 0 ? "" : " / ").append(v == Integer.MIN_VALUE ? "?" : String.valueOf(v));
            }
            Diag.put("pneumatici (grezzo AS/AD/PS/PD)", raw);
            // 0.9.3: temperatura esterna, validità (0 valida, 1 non valida)
            outValid = props.getIntQuiet(CarProps.HVAC_TEMPERATURE_OUTCARV);
        }
        System.arraycopy(tireCache, 0, s.tireBar, 0, 4);
    }

    /** Temperatura interna leggibile? true / false, null finché non si sa (CarService non ancora pronto). */
    public static Boolean insideAvailable() {
        CarProps p = shared;
        if (p == null) return null;
        Boolean a = p.available(CarProps.HVAC_TEMPERATURE_CURRENT), b = p.available(CarProps.HVAC_AMBIENT_TEMPERATURE);
        if (Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b)) return true;
        if (Boolean.FALSE.equals(a) && Boolean.FALSE.equals(b)) return false;
        return null;
    }

    private static boolean valid(float t) {
        return !Float.isNaN(t) && t > -40 && t < 80 && t != 0f;
    }

    private int rvcAvmConfig = Integer.MIN_VALUE;

    /**
     * Telecamere presenti secondo la configurazione dell'auto: true / false, null se non si sa ancora
     * (in quel caso il pulsante 360° resta come prima). Stessi valori della home originale SWI69.
     */
    public static Boolean cameraPresent(int cfg) {
        if (cfg < 0) return null;
        return cfg >= 1 && cfg <= 5;
    }

    private void readLamps(State s) {
        if (!s.connected) return;
        try {
            if (rvcAvmConfig < 0) {
                IBinder cfg = sub(BIND_CONFIG);
                if (cfg != null) {
                    rvcAvmConfig = Rpc.callInt(cfg, CONFIG, CONFIG_RVC_AVM);
                    Diag.put("telecamere.config", rvcAvmConfig + " (" + (rvcAvmConfig == 0 ? "nessuna telecamera"
                            : rvcAvmConfig == 3 ? "360° AVM" : rvcAvmConfig == 5 ? "360° AVM quadro"
                            : rvcAvmConfig >= 1 && rvcAvmConfig <= 5 ? "retrocamera / 360° EVS" : rvcAvmConfig < 0 ? "non ancora nota" : "sconosciuta: nessuna telecamera") + ")");
                }
            }
        } catch (Exception e) {
            Diag.error("telecamere.config", e.toString());
        }
        s.cameraConfig = rvcAvmConfig;
        try {
            if (frontFogConfig == Integer.MIN_VALUE) {
                IBinder cfg = sub(BIND_CONFIG);
                if (cfg != null) {
                    frontFogConfig = Rpc.callInt(cfg, CONFIG, CONFIG_FRONT_FOG);
                    Diag.put("luci.fendinebbiaAnterioriConfig", frontFogConfig);
                }
            }
        } catch (Exception e) {
            Diag.error("luci.config", e.toString());
            forget(BIND_CONFIG);
        }
        try {
            IBinder l = sub(BIND_LAMP);
            if (l != null) {
                long now = android.os.SystemClock.uptimeMillis();
                s.rearFog = held(LAMP_GET_REAR_FOG, Rpc.callInt(l, LAMP, LAMP_GET_REAR_FOG) == 1 ? 1 : 0, now);
                s.hasFrontFog = frontFogConfig == 0;
                if (s.hasFrontFog) s.frontFog = held(LAMP_GET_FRONT_FOG, Rpc.callInt(l, LAMP, LAMP_GET_FRONT_FOG) == 1 ? 1 : 0, now);
                Diag.put("luci.fendinebbia ant/post", s.frontFog + " / " + s.rearFog);
            }
        } catch (Exception e) {
            Diag.error("luci", e.toString());
            forget(BIND_LAMP);
        }
        try {
            readAssist(s, android.os.SystemClock.uptimeMillis());
        } catch (Exception e) {
            Diag.error("assistenza", e.toString());
            forget(BIND_VS);
        }
    }

    private int lkaConfig = Integer.MIN_VALUE, onePedalConfig = Integer.MIN_VALUE;
    private Boolean vsValid;

    /** Servizio impostazioni veicolo, solo se il collegamento 8 risponde con l'interfaccia giusta. */
    private IBinder vs() throws RemoteException {
        IBinder b = sub(BIND_VS);
        if (b == null) return null;
        if (vsValid == null) {
            String d = b.getInterfaceDescriptor();
            vsValid = VS.equals(d);
            Diag.put("assistenza.servizio", String.valueOf(d));
        }
        return vsValid ? b : null;
    }

    /** LKA e OPD: configurazione letta una volta, stato a ogni giro. Senza configurazione valida il pulsante non c'è. */
    private void readAssist(State s, long now) throws Exception {
        if (lkaConfig == Integer.MIN_VALUE || onePedalConfig == Integer.MIN_VALUE) {
            IBinder cfg = sub(BIND_CONFIG);
            if (cfg == null) return;
            lkaConfig = Rpc.callInt(cfg, CONFIG, CONFIG_LKA);
            onePedalConfig = Rpc.callInt(cfg, CONFIG, CONFIG_ONE_PEDAL);
            Diag.put("assistenza.config", "LKA " + lkaConfig + ", One Pedal " + onePedalConfig);
        }
        IBinder v = vs();
        if (v == null) return;
        try {
            int at = Rpc.callInt(v, VS, VS_GET_ACC_TJA);
            s.accTja = held(K_ACC_TJA, at == 1 || at == 2 || at == 4 ? at : -1, now);
            int bp = Rpc.callInt(v, VS, VS_GET_PEDAL);
            s.pedal = held(K_PEDAL, bp >= 0 && bp <= 2 ? bp : -1, now);
            Diag.put("assistenza.guida", "ACC/TJA " + at + ", pedale " + bp);
        } catch (Exception e) {
            Diag.put("assistenza.guida", "non leggibile: " + e);
        }
        if (lkaConfig == 0) {
            int mode = Rpc.callInt(v, VS, VS_GET_LAS);
            s.lkaMode = mode;
            int on = mode == 1 ? 0 : mode >= 2 && mode <= 5 ? 1 : -1;
            s.lka = held(K_LKA, on, now);
        }
        if (onePedalConfig == 0) {
            int o = Rpc.callInt(v, VS, VS_GET_OPD);
            s.opd = held(K_OPD, o == 1 ? 1 : o == 0 ? 0 : -1, now);
            s.opdInhibit = Rpc.callInt(v, VS, VS_GET_OPD_INHIBIT);
        }
        Diag.put("assistenza.stato", "LKA " + s.lka + " (modo " + s.lkaMode + "), OPD " + s.opd + " (blocco " + s.opdInhibit + ")");
    }

    /** LKA acceso/spento: come l'interruttore dell'app originale SWI69, accende con 2 (LDW) e spegne con 1. */
    public void toggleLka() {
        if (bg == null) return;
        int cur = predicted(K_LKA, last.lka);
        if (cur < 0) return;
        boolean turnOn = cur == 0;
        hold(K_LKA, turnOn ? 1 : 0);
        bg.post(() -> {
            try {
                IBinder v = vs();
                if (v == null) return;
                int value = turnOn ? 2 : 1;
                Rpc.call(v, VS, VS_SET_LAS, p -> p.writeInt(value));
                Diag.put("comando.ultimo", "assistenza.lka · " + (turnOn ? "acceso (modo " + value + ")" : "spento"));
            } catch (Exception e) {
                Diag.error("assistenza.lka", e.toString());
                forget(BIND_VS);
            }
            poll();
        });
    }

    // ---- assistente di guida e pedale (0.9.5 / 0.3.8 / 0.2.5) -------------------------------------

    /** Velocità in km/h dal CarService (NaN se non leggibile). */
    public static float speedKmh() {
        CarProps p = shared;
        if (p == null) return Float.NaN;
        float v = p.getFloat(CarProps.PERF_VEHICLE_SPEED);   // m/s; NaN se l'auto non concede CAR_SPEED (letta una volta sola)
        return Float.isNaN(v) ? Float.NaN : Math.abs(v) * 3.6f;
    }

    /** Si cambiano solo ad auto ferma (sopra 3 km/h no): sono impostazioni della guida. */
    public static boolean stopped() {
        float v = speedKmh();
        return Float.isNaN(v) || v < 3f;
    }

    /** Assistente di guida: Spento → ACC → TJA → Spento, come le impostazioni dell'auto. */
    public boolean cycleAccTja() {
        if (bg == null || !stopped()) return false;
        int cur = predicted(K_ACC_TJA, last.accTja);
        if (cur < 0) return false;
        int next = cur == 4 ? 1 : cur == 1 ? 2 : 4;
        hold(K_ACC_TJA, next);
        bg.post(() -> {
            try {
                IBinder b = vs();
                if (b == null) return;
                Rpc.call(b, VS, VS_SET_ACC_TJA, p -> p.writeInt(next));
                Diag.put("comando.ultimo", "assistenza.accTja · " + cur + " → " + next);
            } catch (Exception e) {
                Diag.error("assistenza.accTja", e.toString());
                forget(BIND_VS);
            }
            poll();
        });
        return true;
    }

    /** Pedale del freno: Comfort → Medium → Sport → Comfort. */
    public boolean cyclePedal() {
        if (bg == null || !stopped()) return false;
        int cur = predicted(K_PEDAL, last.pedal);
        if (cur < 0) return false;
        int next = cur == 1 ? 0 : cur == 0 ? 2 : 1;
        hold(K_PEDAL, next);
        bg.post(() -> {
            try {
                IBinder b = vs();
                if (b == null) return;
                Rpc.call(b, VS, VS_SET_PEDAL, p -> p.writeInt(next));
                Diag.put("comando.ultimo", "assistenza.pedale · " + cur + " → " + next);
            } catch (Exception e) {
                Diag.error("assistenza.pedale", e.toString());
                forget(BIND_VS);
            }
            poll();
        });
        return true;
    }

    /** One Pedal Drive acceso/spento. Se l'auto lo blocca (in carica, condizioni…) lo stato letto resta com'era. */
    public void toggleOpd() {
        if (bg == null) return;
        int cur = predicted(K_OPD, last.opd);
        if (cur < 0) return;
        int next = cur == 1 ? 0 : 1;
        hold(K_OPD, next);
        bg.post(() -> {
            try {
                IBinder v = vs();
                if (v == null) return;
                Rpc.call(v, VS, VS_SET_OPD, p -> p.writeInt(next));
                Diag.put("comando.ultimo", "assistenza.opd · " + (next == 1 ? "acceso" : "spento") + " · blocco " + Rpc.callInt(v, VS, VS_GET_OPD_INHIBIT));
            } catch (Exception e) {
                Diag.error("assistenza.opd", e.toString());
                forget(BIND_VS);
            }
            poll();
        });
    }

    // ---- stati di ricarica ----------------------------------------------------------------------

    private int frontFogConfig = Integer.MIN_VALUE;

    /** Stati di ricarica (stessi codici della SWI68): in carica / bilanciamento / rapida / wireless. */
    public static boolean isCharging(int st) {
        return st == 1 || st == 3 || st == 10 || st == 16 || st == 11;
    }

    /** V2L: in scarica (13). */
    public static boolean isDischarging(int st) {
        return st == 13;
    }

    /**
     * Modalità del pulsante avvia/ferma, come le due schede della Gestione ricarica originale:
     * 2 = presa V2L collegata (11 collegato, 13 in scarica, 14 sospeso, 15 finito), 1 = cavo di ricarica collegato,
     * 0 = niente di collegato.
     */
    public static int plugMode(State s) {
        int st = s.chargingStatus;
        if (st == 13 || st == 14 || st == 15) return 2;
        if (st == 11) return s.chargeKw > 0.3f ? 1 : 2;
        if (st == 1 || st == 2 || st == 3 || st == 4 || (st >= 6 && st <= 10)) return 1;
        return 0;
    }

    // ---- pulsanti a commutazione ----------------------------------------------------------------

    private final Map<Integer, long[]> holds = new HashMap<>();   // codice lettura -> {valore, scadenza}

    /** Dopo un tocco mostra subito il nuovo stato per qualche secondo, finché l'auto non lo conferma. */
    private int held(int getCode, int read, long now) {
        synchronized (holds) {
            long[] h = holds.get(getCode);
            if (h == null) return read;
            if (now < h[1] && read != h[0]) return (int) h[0];
            holds.remove(getCode);
            return read;
        }
    }

    private int predicted(int getCode, int fallback) {
        synchronized (holds) {
            long[] h = holds.get(getCode);
            return h != null && android.os.SystemClock.uptimeMillis() < h[1] ? (int) h[0] : fallback;
        }
    }

    private void hold(int getCode, int value) {
        synchronized (holds) {
            holds.put(getCode, new long[]{value, android.os.SystemClock.uptimeMillis() + HOLD_MS});
        }
    }

    /** Commuta: il servizio SWI69 ha solo "cambia stato" (senza argomenti) per il clima, set(1) per le luci. */
    private void press(int bind, String desc, int setCode, boolean withOne, int getCode, int current, String what) {
        if (bg == null) return;
        if (current == 0 || current == 1) hold(getCode, 1 - current);
        bg.post(() -> {
            try {
                IBinder b = sub(bind);
                if (b != null) {
                    if (withOne) Rpc.call(b, desc, setCode, p -> p.writeInt(1));
                    else Rpc.call(b, desc, setCode, Rpc.NONE);
                }
            } catch (Exception e) {
                Diag.error(what, e.toString());
                forget(bind);
            }
            poll();
        });
    }

    public void toggleAc() {
        press(BIND_HVAC, AC, AC_SWITCH_AC, false, AC_GET_AC, last.acOn, "clima.ac");
    }

    public void toggleAuto() {
        press(BIND_HVAC, AC, AC_SWITCH_AUTO, false, AC_GET_AUTO, last.autoOn, "clima.auto");
    }

    public void toggleFrontDefrost() {
        press(BIND_HVAC, AC, AC_SWITCH_DEFROST, false, AC_GET_DEFROST, last.defrostOn, "clima.sbrinamento");
    }

    public void toggleRearFog() {
        press(BIND_LAMP, LAMP, LAMP_SET_REAR_FOG, true, LAMP_GET_REAR_FOG, last.rearFog, "luci.retronebbia");
    }

    public void toggleFrontFog() {
        press(BIND_LAMP, LAMP, LAMP_SET_FRONT_FOG, true, LAMP_GET_FRONT_FOG, last.frontFog, "luci.fendinebbia");
    }

    /** Sedili e volante riscaldati: la SWI69 R45 non ha questi comandi (i pulsanti restano nascosti). */
    public void pressSeatLeft() {
    }

    public void pressSeatRight() {
    }

    public void toggleWheelHeat() {
    }

    /** Gruppo Comfort: sulla SWI69 R45 i sedili riscaldati non hanno comandi verificati (il gruppo non compare). */
    public void stepSeat(boolean driver) {
    }

    /**
     * Ricircolo: il servizio ha solo "cambia" (passa allo stato successivo, come il pulsante della home originale);
     * si mostra subito lo stato previsto (interno ↔ esterno) e poi quello letto dall'auto.
     */
    public void toggleRecirc() {
        if (bg == null) return;
        int cur = predicted(AC_GET_LOOP, last.loopMode);
        if (cur < 0) return;
        hold(AC_GET_LOOP, cur == 0 ? 1 : cur == 1 ? 2 : 0);   // giro Interno → Esterno → Auto
        bg.post(() -> {
            try {
                IBinder a = sub(BIND_HVAC);
                if (a != null) Rpc.call(a, AC, AC_SWITCH_LOOP, Rpc.NONE);
                Diag.put("clima.ricircoloComando", "cambia (era " + cur + ")");
            } catch (Exception e) {
                Diag.error("clima.ricircolo", e.toString());
                forget(BIND_HVAC);
            }
            poll();
        });
    }

    // ---- temperatura e ventola --------------------------------------------------------------------

    /** Imposta la temperatura lato guida (15 = LO … 31 = HI). Il valore richiesto resta visibile per qualche secondo. */
    public void setDriverTemp(int celsius) {
        int t = Math.max(TEMP_MIN, Math.min(TEMP_MAX, celsius));
        heldTemp = t;
        tempHoldUntil = android.os.SystemClock.uptimeMillis() + HOLD_MS;
        pendingTemp = t;
        sendSoon();
    }

    /** Livello ventola: 0 = spegne il clima, 1..11, 15 = automatico. */
    public void setFanLevel(int level) {
        int l = level == FAN_AUTO || level == FAN_OFF ? level : Math.max(FAN_MIN, Math.min(FAN_MAX, level));
        heldFan = l;
        fanHoldUntil = android.os.SystemClock.uptimeMillis() + HOLD_MS;
        // OFF: l'auto non accetta la ventola a 0 (la rimette a 1 dopo un attimo). Come l'app originale, la ventola resta
        // da 1 in su e "OFF" vuol dire spegnere il clima.
        if (l == FAN_OFF) {
            Diag.put("clima.ventolaOff", "ventola OFF → spengo il clima");
            setHvacPower(false);
            return;
        }
        pendingFan = l;
        sendSoon();
    }

    private void sendPending(IBinder a) throws RemoteException {
        int t = pendingTemp;
        if (t > 0) {
            pendingTemp = -1;
            float f = tempToCar(t);
            Rpc.call(a, AC, AC_SET_DRV_TEMP, p -> p.writeFloat(f));
        }
        int fan = pendingFan;
        if (fan >= 0) {
            pendingFan = -1;
            boolean power = Rpc.callBool(a, AC, AC_GET_POWER);
            if (fan == FAN_OFF) {
                if (power) Rpc.call(a, AC, AC_SWITCH_POWER, Rpc.NONE);
            } else if (fan == FAN_AUTO) {
                if (!Rpc.callBool(a, AC, AC_GET_AUTO)) Rpc.call(a, AC, AC_SWITCH_AUTO, Rpc.NONE);
            } else {
                if (!power) Rpc.call(a, AC, AC_SWITCH_POWER, Rpc.NONE);
                Rpc.call(a, AC, AC_SET_FAN, p -> p.writeInt(fan));
            }
        }
    }

    /** Invia il comando subito, senza riscrivere la UI con una lettura immediata. */
    private void sendSoon() {
        if (bg == null) return;
        bg.post(() -> {
            try {
                IBinder a = sub(BIND_HVAC);
                if (a != null) sendPending(a);
            } catch (Exception e) {
                Diag.error("clima.comando", e.toString());
                forget(BIND_HVAC);
            }
        });
    }

    /** Accende o spegne il clima (il servizio SWI69 ha solo il comando "cambia stato"). */
    public void setHvacPower(boolean on) {
        if (bg == null) return;
        bg.post(() -> {
            try {
                IBinder a = sub(BIND_HVAC);
                if (a != null && Rpc.callBool(a, AC, AC_GET_POWER) != on) {
                    Rpc.call(a, AC, AC_SWITCH_POWER, Rpc.NONE);
                }
            } catch (Exception e) {
                Diag.error("clima.accensione", e.toString());
            }
            poll();
        });
    }

    // ---- ricarica: comandi del gestore batteria, come la Gestione ricarica dell'app Energia --------

    /** Limite di ricarica: codice 1..7 (40..100 %). */
    public void setChargeLimitCode(int code) {
        if (code < 1 || code > 7) return;
        chargeCommand(CarBms.CHRG_TRGT_SOC, code, code, "ricarica.limite → " + limitPercent(code) + "%");
    }

    /** Corrente di ricarica AC: codice 1..4 (6 A, 8 A, 16 A, Max). */
    public void setCurrentLimit(int code) {
        if (code < 1 || code > 4) return;
        chargeCommand(CarBms.ALTNG_CHRG_CRNT, code, code, "ricarica.corrente → " + CURRENT_LABELS[code - 1]);
    }

    /** Avvia o ferma la ricarica (cavo collegato). */
    public void setCharging(boolean start) {
        chargeCommand(CarBms.CHRG_CTRL, CTRL_PREPARING, start ? CTRL_START : CTRL_STOP, "ricarica." + (start ? "avvia" : "ferma"));
    }

    /** Avvia o ferma la scarica V2L. */
    public void setDischarging(boolean start) {
        chargeCommand(CarBms.V2X, CTRL_PREPARING, start ? CTRL_START : CTRL_STOP, "v2l." + (start ? "avvia" : "ferma"));
    }

    private void chargeCommand(int prop, int shown, int value, String what) {
        if (bg == null) return;
        hold(prop, shown);
        bg.post(() -> {
            boolean sent = bms.setInt(prop, value);
            Diag.put("ricarica.ultimoComando", what + (sent ? " · inviato" : " · non inviato (gestore batteria non pronto)"));
            poll();
        });
    }
}

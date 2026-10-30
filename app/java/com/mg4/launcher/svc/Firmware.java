package com.mg4.launcher.svc;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Riconosce il firmware dell'auto. Questa versione del launcher è per la SWI69 (Color Radio): sulla SWI68 i servizi
 * e le app originali sono diversi (ci sono i pacchetti com.saicmotor.hmi.* e manca il fornitore di configurazione
 * com.saicmotor.caradapter.featureprovider), quindi lì il launcher non deve tentare nessun collegamento.
 */
public final class Firmware {
    public static final String SWI68_URL = "https://github.com/ernestomicco2026-cell/Mg4-Launcher-SWI68";

    /** App originali della SWI68: se ce n'è anche una sola, è una SWI68. */
    private static final String[] SWI68_APPS = {
            "com.saicmotor.hmi.launcher", "com.saicmotor.hmi.systemsettings", "com.saicmotor.hmi.radio",
            "com.saicmotor.hmi.hvac", "com.saicmotor.hmi.btcall", "com.saicmotor.hmi.aroundview",
            "com.saicmotor.hmi.vehiclesettings"};

    /** Esito: SWI69 (giusta), SWI68 (versione sbagliata), ALTRO (né l'una né l'altra, es. un telefono). */
    public static final int SWI69 = 0, SWI68 = 1, OTHER = 2;
    private static Integer kind;

    private Firmware() {}

    /** True se questa versione non è adatta al dispositivo: non si tenta nessun collegamento. */
    public static boolean wrong(Context c) {
        return kind(c) != SWI69;
    }

    /**
     * Telefono o emulatore invece dell'infotainment: restituisce il motivo, oppure null se è un'auto (anche con il
     * firmware dell'altra versione). Su questi dispositivi il launcher non invia log.
     */
    public static String notACar(Context c) {
        String fp = lower(android.os.Build.FINGERPRINT), hw = lower(android.os.Build.HARDWARE), prod = lower(android.os.Build.PRODUCT),
                model = lower(android.os.Build.MODEL), brand = lower(android.os.Build.BRAND), man = lower(android.os.Build.MANUFACTURER);
        String dev = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        if (fp.startsWith("generic") || fp.contains("emulator") || fp.contains("sdk_gphone") || hw.contains("goldfish")
                || hw.contains("ranchu") || prod.contains("sdk") || prod.contains("emulator") || model.contains("android sdk built for")
                || model.startsWith("generic") || model.contains("emulator"))
            return "emulatore (" + dev + ")";
        if (kind(c) == OTHER) return "firmware non riconosciuto, nessuna app MG (" + dev + ")";
        for (String b : new String[]{"google", "oneplus", "samsung", "xiaomi", "redmi", "huawei", "oppo", "motorola", "sony", "nothing"})
            if (brand.equals(b) || man.equals(b)) return "telefono (" + dev + ")";
        return null;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT);
    }

    public static synchronized int kind(Context c) {
        if (kind != null) return kind;
        PackageManager pm = c.getPackageManager();
        boolean provider = CarFeatures.hasFeatureProvider(c);
        int found = 0;
        StringBuilder sb = new StringBuilder();
        for (String p : SWI68_APPS) {
            try {
                pm.getPackageInfo(p, 0);
                found++;
                sb.append(sb.length() == 0 ? "" : ", ").append(p.substring(p.lastIndexOf('.') + 1));
            } catch (Throwable ignored) {
            }
        }
        boolean mgHome;
        try {
            pm.getPackageInfo(com.mg4.launcher.apps.MgApps.HOME_PKG, 0);
            mgHome = true;
        } catch (Throwable e) {
            mgHome = false;
        }
        kind = found > 0 || (!provider && !mgHome) ? (found > 0 ? SWI68 : OTHER) : SWI69;
        Diag.put("firmware", (kind == SWI68 ? "SWI68 (versione sbagliata)" : kind == OTHER ? "non riconosciuto (nessuna app MG SWI69)" : "SWI69")
                + " · fornitore config " + (provider ? "sì" : "no") + " · home MG SWI69 " + (mgHome ? "sì" : "no")
                + (found > 0 ? " · app SWI68 trovate: " + sb : ""));
        return kind;
    }
}

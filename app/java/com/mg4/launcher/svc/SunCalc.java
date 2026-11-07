package com.mg4.launcher.svc;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 0.4.2 (punto 1d): alba e tramonto calcolati offline (algoritmo NOAA semplificato, precisione di qualche minuto).
 * Serve solo come ultima riserva per il tema giorno/notte.
 */
public final class SunCalc {
    private SunCalc() {}

    /** Riferimento quando la posizione non c'è: centro dell'Italia. */
    public static final double ITALY_LAT = 42.5, ITALY_LON = 12.5;

    /** Minuti dalla mezzanotte locale di alba e tramonto; null nelle notti o nei giorni polari. */
    public static int[] sunriseSunset(double lat, double lon, long nowMs, TimeZone tz) {
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(nowMs);
        int doy = cal.get(Calendar.DAY_OF_YEAR);
        double gamma = 2 * Math.PI / 365.0 * (doy - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma) - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma) - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double latR = Math.toRadians(lat);
        // -0.833°: rifrazione e raggio del disco solare (alba/tramonto "ufficiali")
        double cosHa = (Math.cos(Math.toRadians(90.833)) / (Math.cos(latR) * Math.cos(decl))) - Math.tan(latR) * Math.tan(decl);
        if (cosHa > 1 || cosHa < -1) return null;
        double ha = Math.toDegrees(Math.acos(cosHa));
        double offsetMin = tz.getOffset(nowMs) / 60000.0;
        double sunriseUtc = 720 - 4 * (lon + ha) - eqTime;
        double sunsetUtc = 720 - 4 * (lon - ha) - eqTime;
        return new int[]{norm((int) Math.round(sunriseUtc + offsetMin)), norm((int) Math.round(sunsetUtc + offsetMin))};
    }

    private static int norm(int m) {
        return ((m % 1440) + 1440) % 1440;
    }

    /** true se adesso è giorno (tra alba e tramonto). Nelle notti polari: notte; nei giorni polari: giorno. */
    public static boolean isDay(double lat, double lon, long nowMs, TimeZone tz) {
        int[] rs = sunriseSunset(lat, lon, nowMs, tz);
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(nowMs);
        int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        if (rs == null) {
            int month = cal.get(Calendar.MONTH);
            boolean summerNorth = month >= Calendar.APRIL && month <= Calendar.SEPTEMBER;
            return lat >= 0 ? summerNorth : !summerNorth;
        }
        return rs[0] <= rs[1] ? m >= rs[0] && m < rs[1] : m >= rs[0] || m < rs[1];
    }

    public static String hhmm(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}

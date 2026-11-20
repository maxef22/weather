package com.controlj.addon.weather.util;

/**
 * Psychrometric calculations for the hourly forecast.
 *
 * Enthalpy of moist air per kg of dry air (ASHRAE Fundamentals, IP/SI form):
 *
 *   Pw = Psat(Td)                      - partial vapour pressure from the dew point
 *   W  = 0.621945 * Pw / (P - Pw)      - humidity ratio, kg water / kg dry air
 *   h  = 1.006 * t + W * (2501 + 1.86 * t)   - kJ per kg of dry air
 *
 * Saturation pressure uses the Magnus form (0.1 % accurate between -45 and +60 °C),
 * which is far below the accuracy of a weather forecast.
 *
 * Reference values (P = 1013.25 hPa):
 *   0 °C / 80 %  -> ~7.5 kJ/kg
 *   20 °C / 50 % -> ~38.5 kJ/kg
 *   25 °C / 60 % -> ~55.4 kJ/kg
 *   30 °C / 70 % -> ~78.2 kJ/kg
 */
public final class Psychrometrics {

    /** Magnus coefficients for water vapour over water. */
    private static final double MAGNUS_A = 17.62;
    private static final double MAGNUS_B = 243.12;
    private static final double STANDARD_PRESSURE_HPA = 1013.25;

    private Psychrometrics() {
    }

    /**
     * Saturation vapour pressure over water, hPa.
     *
     * @param tC temperature, °C
     */
    public static double saturationPressureHpa(double tC) {
        return 6.112 * Math.exp(MAGNUS_A * tC / (MAGNUS_B + tC));
    }

    /**
     * Dew point temperature from dry-bulb temperature and relative humidity (inverse Magnus).
     *
     * @param tC        temperature, °C
     * @param rhPercent relative humidity, %
     */
    public static double dewPointC(double tC, double rhPercent) {
        double rh = clamp(rhPercent, 0.1, 100.0);
        double gamma = Math.log(rh / 100.0) + MAGNUS_A * tC / (MAGNUS_B + tC);
        return MAGNUS_B * gamma / (MAGNUS_A - gamma);
    }

    /**
     * Humidity ratio, kg water per kg of dry air.
     *
     * @param dewPointC   dew point temperature, °C
     * @param pressureHpa total (absolute) pressure, hPa
     */
    public static double humidityRatio(double dewPointC, double pressureHpa) {
        double pressure = (pressureHpa > 100.0) ? pressureHpa : STANDARD_PRESSURE_HPA;
        double partial = Math.min(saturationPressureHpa(dewPointC), pressure * 0.99);
        return 0.621945 * partial / (pressure - partial);
    }

    /**
     * Humidity ratio calculated from relative humidity, kg water per kg of dry air.
     */
    public static double humidityRatioFromRh(double tC, double rhPercent, double pressureHpa) {
        return humidityRatio(dewPointC(tC, rhPercent), pressureHpa);
    }

    /**
     * Specific enthalpy of moist air, kJ per kg of dry air.
     *
     * @param tC           temperature, °C
     * @param humidityRatio humidity ratio, kg/kg
     */
    public static double enthalpyKjPerKg(double tC, double humidityRatio) {
        return 1.006 * tC + humidityRatio * (2501.0 + 1.86 * tC);
    }

    /** Convenience: enthalpy from temperature, relative humidity and pressure. */
    public static double enthalpyKjPerKgFromRh(double tC, double rhPercent, double pressureHpa) {
        return enthalpyKjPerKg(tC, humidityRatioFromRh(tC, rhPercent, pressureHpa));
    }

    /** Humidity ratio in g/kg (the unit used for the WebCTRL point). */
    public static double gramsPerKg(double humidityRatio) {
        return humidityRatio * 1000.0;
    }

    /** Round to one decimal place, as used for all written values. */
    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}

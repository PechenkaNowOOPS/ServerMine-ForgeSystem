package ru.servermine.forge;

import java.util.Locale;

/** Visible reserve derived from burn time; it does not consume or migrate fuel. */
record FuelDisplay(long seconds, long coalEquivalent) {
    static FuelDisplay of(double fuelSeconds, double secondsPerCoal) {
        if (!Double.isFinite(fuelSeconds) || fuelSeconds < 0) throw new IllegalArgumentException("Invalid fuel reserve");
        long seconds = (long) Math.ceil(fuelSeconds);
        long coal = Double.isFinite(secondsPerCoal) && secondsPerCoal > 0
                ? (long) Math.ceil(fuelSeconds / secondsPerCoal) : -1;
        return new FuelDisplay(seconds, coal);
    }

    String time() {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    boolean timeOverflow() { return seconds >= 360000; }

    String clockDigits() {
        long visible = Math.min(seconds, 359999);
        return String.format(Locale.ROOT, "%02d%02d%02d", visible / 3600, visible / 60 % 60, visible % 60);
    }

    String coalDigits() {
        if (coalEquivalent < 0) return "----";
        return coalEquivalent > 9999 ? "999+" : String.format(Locale.ROOT, "%4d", coalEquivalent);
    }
}

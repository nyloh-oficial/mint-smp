package net.mintsmp.util;

import java.text.DecimalFormat;

/** Parse shorthand amounts (1k, 2m, 1.5b) and format money/shards. */
public final class Amounts {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");

    private Amounts() {}

    /** Returns a non-negative double, or -1 if the string is invalid. */
    public static double parse(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        raw = raw.trim().toLowerCase().replace(",", "");
        double mult = 1;
        char last = raw.charAt(raw.length() - 1);
        switch (last) {
            case 'k' -> { mult = 1_000d;             raw = raw.substring(0, raw.length() - 1); }
            case 'm' -> { mult = 1_000_000d;         raw = raw.substring(0, raw.length() - 1); }
            case 'b' -> { mult = 1_000_000_000d;     raw = raw.substring(0, raw.length() - 1); }
            case 't' -> { mult = 1_000_000_000_000d; raw = raw.substring(0, raw.length() - 1); }
            default -> { /* plain number */ }
        }
        try {
            double v = Double.parseDouble(raw) * mult;
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) return -1;
            return v;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Parse to a whole (rounded down) long, or -1 if invalid. */
    public static long parseLong(String raw) {
        double v = parse(raw);
        return v < 0 ? -1 : (long) Math.floor(v);
    }

    public static String money(double v)  { return "$" + MONEY.format(v); }
    public static String shards(long v)    { return "\u29eb " + WHOLE.format(v); }
    public static String whole(double v)   { return WHOLE.format(v); }
}

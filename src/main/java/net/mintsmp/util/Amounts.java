package net.mintsmp.util;

import java.text.DecimalFormat;

/** Parse shorthand amounts (1k, 2m, 1.5b, 3t, 5q) and format money/shards compactly. */
public final class Amounts {

    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0");
    private static final String[] SUFFIX = {"", "k", "m", "b", "t", "q"};

    private Amounts() {}

    /** Returns a non-negative double, or -1 if the string is invalid. */
    public static double parse(String raw) {
        if (raw == null || raw.isBlank()) return -1;
        raw = raw.trim().toLowerCase().replace(",", "");
        double mult = 1;
        char last = raw.charAt(raw.length() - 1);
        switch (last) {
            case 'k' -> { mult = 1_000d;                 raw = raw.substring(0, raw.length() - 1); }
            case 'm' -> { mult = 1_000_000d;             raw = raw.substring(0, raw.length() - 1); }
            case 'b' -> { mult = 1_000_000_000d;         raw = raw.substring(0, raw.length() - 1); }
            case 't' -> { mult = 1_000_000_000_000d;     raw = raw.substring(0, raw.length() - 1); }
            case 'q' -> { mult = 1_000_000_000_000_000d; raw = raw.substring(0, raw.length() - 1); }
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

    /** Compact form: 999 -> "999", 1500 -> "1.5k", 2000000 -> "2m", 1.25e9 -> "1.25b". */
    public static String compact(double v) {
        boolean neg = v < 0;
        double abs = Math.abs(v);
        int idx = 0;
        double val = abs;
        while (val >= 1000 && idx < SUFFIX.length - 1) { val /= 1000d; idx++; }
        if (idx == 0) return (neg ? "-" : "") + WHOLE.format(abs);
        String num = String.format("%.2f", val);
        if (num.contains(".")) num = num.replaceAll("0+$", "").replaceAll("\\.$", "");
        return (neg ? "-" : "") + num + SUFFIX[idx];
    }

    public static String money(double v)  { return "$" + compact(v); }
    public static String shards(long v)    { return "\u29eb " + compact(v); }
    public static String whole(double v)   { return compact(v); }
}

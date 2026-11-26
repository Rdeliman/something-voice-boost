package app.something.voiceboost;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final int MAX_GAIN_DB = 24;
    private static final int DEFAULT_GAIN_DB = 9;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("boost", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("enabled", true);
    }

    static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("enabled", on).apply();
    }

    static int gainDb(Context c) {
        return Math.max(0, Math.min(MAX_GAIN_DB, sp(c).getInt("gainDb", DEFAULT_GAIN_DB)));
    }

    static void setGainDb(Context c, int db) {
        sp(c).edit().putInt("gainDb", db).apply();
    }
}

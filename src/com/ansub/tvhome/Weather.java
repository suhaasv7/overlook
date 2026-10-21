package com.ansub.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

// The weather by the clock: the temperature and sky now, from Open-Meteo every 30 minutes. Where
// the TV is comes from its internet address (ipwho.is, looked up once a week), unless the TV's
// settings give a fixed "lat,lon". Both are free and need no account.
final class Weather {
    interface Show { void now(int tempC, int[] icon); }

    private static final long WEATHER_MS = 30 * 60 * 1000, RETRY_MS = 5 * 60 * 1000;
    private static final long PLACE_MS = 7L * 24 * 60 * 60 * 1000;

    private final SharedPreferences prefs;
    private final Show show;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long next; // when to fetch again
    private boolean busy;

    Weather(Context c, Show show) {
        prefs = c.getSharedPreferences("weather", 0);
        this.show = show;
    }

    // Called every minute while Home is in front; it only goes online when the weather is due.
    // place: "lat,lon" from the TV's settings, or null to work it out.
    void refresh(final String place) {
        if (busy || System.currentTimeMillis() < next) return;
        busy = true;
        new Thread(() -> {
            int temp = 0;
            int[] icon = null;
            try {
                String at = place != null && place.matches("-?[0-9.]+,-?[0-9.]+") ? place : located();
                String[] ll = at.split(",");
                JSONObject now = Wallpaper.json("https://api.open-meteo.com/v1/forecast?latitude=" + ll[0]
                        + "&longitude=" + ll[1] + "&current=temperature_2m,weather_code,is_day&timezone=auto")
                        .getJSONObject("current");
                temp = (int) Math.round(now.getDouble("temperature_2m"));
                icon = icon(now.getInt("weather_code"), now.optInt("is_day", 1) == 1);
            } catch (Exception ignored) {}
            final int t = temp;
            final int[] i = icon;
            main.post(() -> {
                busy = false;
                next = System.currentTimeMillis() + (i != null ? WEATHER_MS : RETRY_MS);
                if (i != null) show.now(t, i);
            });
        }).start();
    }

    // Fetch on the next refresh, e.g. right after the weather is switched on.
    void soon() {
        next = 0;
    }

    private String located() throws Exception {
        String at = prefs.getString("at", null);
        if (at != null && System.currentTimeMillis() - prefs.getLong("when", 0) < PLACE_MS) return at;
        JSONObject d = Wallpaper.json("https://ipwho.is/");
        if (!d.optBoolean("success")) {
            if (at != null) return at; // keep the last known place
            throw new Exception("no location");
        }
        at = d.getDouble("latitude") + "," + d.getDouble("longitude");
        prefs.edit().putString("at", at).putLong("when", System.currentTimeMillis()).apply();
        return at;
    }

    // WMO weather codes, as Open-Meteo reports them.
    private static int[] icon(int code, boolean day) {
        if (code <= 1) return day ? LineIcon.SUN : LineIcon.MOON;     // clear, mainly clear
        if (code <= 3) return LineIcon.CLOUD;                         // partly cloudy, overcast
        if (code == 45 || code == 48) return LineIcon.FOG;
        if (code >= 95) return LineIcon.STORM;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return LineIcon.SNOW;
        return LineIcon.RAIN;                                         // drizzle, rain, showers
    }
}

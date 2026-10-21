package com.ansub.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.os.SystemClock;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

// The background: a photo from Wikimedia Commons' featured pictures (free licences, no account), or
// from your own photos. Which photos come up is the mode, picked in the ••• menu:
//   TIME   photos that suit the hour: calm ones in the morning, landscapes by day, sunsets in the evening
//   MIX    every collection, plus your own photos
//   FAVS   the ones you've added to favourites
//   MINE   your own photos, copied to /sdcard/Android/data/com.ansub.tvhome/files/photos
//   or a single collection, like "mountains".
// The same photo stays for the day (for TIME, for that part of the day); then the next one in line.
// A photo is downloaded once and kept, so later starts read it from disk. Until one is in, or with no
// internet, soft colour blobs drift and slowly change colour instead.
final class Wallpaper extends View {
    static final String TIME = "time", MIX = "mix", FAVS = "favourites", MINE = "mine";
    private static final String API = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2";
    private static final String UA = "Overlook/1.0 (https://github.com/suhaasv7/overlook)";
    private static final long RETRY_MS = 10 * 60 * 1000; // after a failed download, wait before trying again
    // Featured-picture collections that suit a TV: about 800 photos fit, so over two years of days
    // before one comes round again.
    static final String[] COLLECTIONS = {"landscapes", "mountains", "lakes", "waterfalls", "coasts",
            "beaches", "forests", "islands", "sunsets", "clouds", "snow"};
    // For TIME: the collections for morning (5–11), day (11–17), evening (17–21) and night.
    private static final String[][] BY_TIME = {
            {"lakes", "clouds", "mountains", "forests", "snow"},
            {"landscapes", "mountains", "beaches", "coasts", "islands", "waterfalls", "lakes", "forests"},
            {"sunsets"},
            {"mountains", "lakes", "snow", "clouds"},
    };
    private static final String LOCAL = "file:"; // a photo of your own, by file name

    private final SharedPreferences prefs; // key and entry of the photo kept, offset, favourites
    private final File file;               // the last photo downloaded
    private final File mine;               // your own photos (null if there's no shared storage)
    private String mode = TIME;
    private String shown;                  // the key of the photo on screen
    private Bitmap photo;
    private final Rect src = new Rect(), dst = new Rect();
    private final Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Map<String, List<String>> titles; // each collection's photos, once fetched this session
    private boolean loading, running;
    private long lastFail = -RETRY_MS;

    Wallpaper(Context c) {
        super(c);
        prefs = c.getSharedPreferences("wallpaper", 0);
        file = new File(c.getFilesDir(), "wallpaper.jpg");
        mine = c.getExternalFilesDir("photos");
    }

    // The home screen is in front.
    void start() {
        running = true;
        check();
        invalidate();
    }

    // Covered by an app: stop the blobs, they only cost time there.
    void stop() {
        running = false;
    }

    void setMode(String m) {
        if (m.equals(mode)) return;
        mode = m;
        if (running) check();
    }

    // On start, every minute and when the mode changes: show the photo for now. A new one is fetched
    // only when the day (or for TIME the part of the day) or the mode has moved on.
    void check() {
        String k = key();
        if (k.equals(shown)) return;
        load(k, offset(), !k.equals(prefs.getString("key", null)), false);
    }

    // The ••• menu's Next wallpaper: the next photo in line. The whole schedule moves up one, so
    // tomorrow brings the one after it and a photo seen early never comes round again soon.
    void next() {
        load(key(), offset() + 1, true, true);
    }

    boolean hasPhoto() {
        return photo != null;
    }

    boolean isFavourite() {
        return photo != null && prefs.getStringSet("favs", Collections.<String>emptySet()).contains(current());
    }

    // Adds the photo on screen to favourites, or takes it out again.
    void toggleFavourite() {
        String e = current();
        if (photo == null || e == null) return;
        Set<String> favs = new HashSet<>(prefs.getStringSet("favs", Collections.<String>emptySet()));
        if (!favs.remove(e)) favs.add(e);
        prefs.edit().putStringSet("favs", favs).apply();
    }

    int mineCount() {
        return minePhotos().size();
    }

    // 10 pm to 6 am: the home screen dims the photo then, if that's switched on.
    static boolean night() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 22 || h < 6;
    }

    private String current() {
        return prefs.getString("entry", null);
    }

    // Day number d shows photo (d + offset) of the mode's list: offset counts the Next presses so far.
    private int offset() {
        return prefs.getInt("offset", 0);
    }

    private static long today() {
        long now = System.currentTimeMillis();
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L;
    }

    private static int partOfDay() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 5 && h < 11 ? 0 : h >= 11 && h < 17 ? 1 : h >= 17 && h < 21 ? 2 : 3;
    }

    private String key() {
        return mode + ":" + today() + (TIME.equals(mode) ? ":" + partOfDay() : "");
    }

    // Off the main thread: pick photo (day + offset) for key k, download it (or take your own file),
    // and swap it in. With fetch false, read back the photo kept for k instead. One at a time. If it
    // fails, whatever is showing stays; with nothing showing yet, the kept photo comes up even if it's
    // an older one. force skips the wait after a failure (a button press).
    private void load(final String k, final int offset, final boolean fetch, final boolean force) {
        if (loading || (fetch && !force && SystemClock.elapsedRealtime() - lastFail < RETRY_MS)) return;
        loading = true;
        final boolean blank = photo == null;
        final int w = Math.max(getWidth(), getResources().getDisplayMetrics().widthPixels);
        new Thread(() -> {
            String entry = prefs.getString("entry", null);
            boolean fetched = false;
            if (fetch) try {
                List<String> pool = pool(k);
                String pick = pool.get((int) ((today() + offset) % pool.size()));
                if (!pick.startsWith(LOCAL)) {
                    JSONObject ii = json(API + "&prop=imageinfo&iiprop=url&iiurlwidth=1920&titles="
                            + URLEncoder.encode(pick, "UTF-8"))
                            .getJSONObject("query").getJSONArray("pages").getJSONObject(0)
                            .getJSONArray("imageinfo").getJSONObject(0);
                    File tmp = new File(file.getPath() + ".part");
                    download(ii.getString("thumburl"), tmp);
                    if (!tmp.renameTo(file)) throw new Exception("couldn't keep the photo");
                }
                entry = pick;
                fetched = true;
            } catch (Exception ignored) {}
            Bitmap b = null;
            if ((fetched || !fetch || blank) && entry != null) try {
                b = decode(entry.startsWith(LOCAL) && mine != null ? new File(mine, entry.substring(LOCAL.length())) : file, w);
            } catch (Throwable ignored) {} // out of memory included: the blobs carry on
            final Bitmap got = b;
            final boolean ok = fetched;
            final String e = entry;
            post(() -> {
                loading = false;
                if (fetch && !ok) lastFail = SystemClock.elapsedRealtime();
                if (got == null) return;
                if (ok) prefs.edit().putString("key", k).putString("entry", e).putInt("offset", offset).apply();
                if (ok || !fetch) shown = k;
                photo = got;
                invalidate();
            });
        }).start();
    }

    // The photos for key k's mode, in a fixed shuffled order, so the same day always picks the same
    // photo and a photographer's series doesn't fill a week. Favourites or your own photos, when
    // there are none, fall back to the mix.
    private List<String> pool(String k) throws Exception {
        String m = k.substring(0, k.indexOf(':'));
        List<String> out = new ArrayList<>();
        if (MINE.equals(m)) out.addAll(minePhotos());
        if (FAVS.equals(m)) out.addAll(prefs.getStringSet("favs", Collections.<String>emptySet()));
        if (out.isEmpty()) {
            if (MINE.equals(m) || FAVS.equals(m)) m = MIX;
            if (titles == null) titles = collections();
            String[] use = TIME.equals(m) ? BY_TIME[partOfDay()] : MIX.equals(m) ? COLLECTIONS : new String[] {m};
            Set<String> seen = new HashSet<>();
            for (String c : use) {
                List<String> l = titles.get(c);
                if (l != null) for (String t : l) if (seen.add(t)) out.add(t);
            }
            if (MIX.equals(m)) out.addAll(minePhotos());
        }
        if (out.isEmpty()) throw new Exception("no photos");
        Collections.sort(out, (a, b) -> mix(a) != mix(b) ? Integer.compare(mix(a), mix(b)) : a.compareTo(b));
        return out;
    }

    private List<String> minePhotos() {
        List<String> out = new ArrayList<>();
        File[] files = mine == null ? null : mine.listFiles();
        if (files != null) for (File f : files)
            if (f.getName().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(jpe?g|png|webp)")) out.add(LOCAL + f.getName());
        return out;
    }

    // Every photo in each collection that fits a TV: wide but not a panorama, and big enough.
    // A collection that's gone or renamed is just empty.
    private static Map<String, List<String>> collections() throws Exception {
        Map<String, List<String>> all = new HashMap<>();
        int found = 0;
        for (String c : COLLECTIONS) {
            List<String> out = new ArrayList<>();
            String more = "";
            int page = 0;
            do {
                JSONObject d = json(API + "&generator=categorymembers&gcmtitle=Category:Featured_pictures_of_" + c
                        + "&gcmtype=file&gcmlimit=500&prop=imageinfo&iiprop=size" + more);
                JSONObject q = d.optJSONObject("query");
                if (q == null) break;
                JSONArray pages = q.getJSONArray("pages");
                for (int i = 0; i < pages.length(); i++) {
                    JSONObject p = pages.getJSONObject(i);
                    JSONArray info = p.optJSONArray("imageinfo");
                    if (info == null) continue;
                    double w = info.getJSONObject(0).getDouble("width"), h = info.getJSONObject(0).getDouble("height");
                    if (w >= 1920 && w / h >= 1.3 && w / h <= 2) out.add(p.getString("title"));
                }
                // Over 500 photos come in pages: pass back what the last page says to continue from.
                JSONObject cont = d.optJSONObject("continue");
                StringBuilder next = new StringBuilder();
                if (cont != null) for (java.util.Iterator<String> it = cont.keys(); it.hasNext(); ) {
                    String key = it.next();
                    next.append('&').append(key).append('=').append(URLEncoder.encode(cont.getString(key), "UTF-8"));
                }
                more = next.toString();
            } while (!more.isEmpty() && ++page < 10); // a cap, in case the paging ever goes round in circles
            all.put(c, out);
            found += out.size();
        }
        if (found == 0) throw new Exception("no photos");
        return all;
    }

    // Reads a photo at about screen size (a camera's 12 MP photo would waste memory), upright.
    private static Bitmap decode(File f, int w) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inJustDecodeBounds = false;
        o.inPreferredConfig = Bitmap.Config.RGB_565; // half the memory; a photo doesn't need alpha
        o.inSampleSize = 1;
        while (Math.max(o.outWidth, o.outHeight) / (o.inSampleSize * 2) >= w) o.inSampleSize *= 2;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        int turn = 0;
        try {
            switch (new ExifInterface(f.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                case ExifInterface.ORIENTATION_ROTATE_90: turn = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: turn = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: turn = 270; break;
            }
        } catch (Exception ignored) {}
        if (turn == 0) return b;
        Matrix m = new Matrix();
        m.postRotate(turn);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    // A well-spread number from a title, for the shuffle. String.hashCode is the same on every device.
    private static int mix(String s) {
        int h = s.hashCode() * 0x9E3779B1;
        return h ^ (h >>> 16);
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", UA); // Wikimedia asks every app to say who it is
        return c;
    }

    static JSONObject json(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            copy(in, out);
        } finally {
            c.disconnect();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (photo != null) {
            // Fill the screen, cropping the photo's edges to fit.
            float s = Math.max((float) getWidth() / photo.getWidth(), (float) getHeight() / photo.getHeight());
            int sw = Math.round(getWidth() / s), sh = Math.round(getHeight() / s);
            src.set((photo.getWidth() - sw) / 2, (photo.getHeight() - sh) / 2,
                    (photo.getWidth() + sw) / 2, (photo.getHeight() + sh) / 2);
            dst.set(0, 0, getWidth(), getHeight());
            c.drawBitmap(photo, src, dst, smooth);
            return;
        }
        drawBlobs(c);
        // Its own clock, not Android's animations, so it moves even with those turned off.
        // About 15 frames a second is plenty for something this soft, and easy on a slow TV.
        if (running) postInvalidateDelayed(66);
    }

    // Four blobs, each drifting back and forth on its own path, while the whole palette turns
    // round the colour wheel every 45 seconds. Drawn at a tenth of the screen size and stretched:
    // they're blurry anyway, so it looks the same and costs next to nothing.
    // Each blob: centre x, y and size (on a 1920x1080 screen), how far it drifts, its size there,
    // seconds per drift. Colours alongside.
    private static final float[][] BLOBS = {
            {358, 280, 1100, 650, 320, 1.2f, 22},
            {1658, 388, 1100, -750, 260, 0.85f, 28},
            {934, 962, 1100, -420, -380, 1.15f, 25},
            {1456, 562, 800, -560, 220, 1.3f, 31},
    };
    private static final int[] BLOB_COLORS = {0xD96E50DC, 0xBF1496AA, 0xB3DC643C, 0x99C83C8C};
    private static final int SMALL_W = 192, SMALL_H = 108;
    private Bitmap small;
    private Canvas smallCanvas;
    private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] hsv = new float[3];

    private void drawBlobs(Canvas c) {
        if (small == null) {
            small = Bitmap.createBitmap(SMALL_W, SMALL_H, Bitmap.Config.ARGB_8888);
            smallCanvas = new Canvas(small);
        }
        long t = SystemClock.uptimeMillis();
        float k = SMALL_W / 1920f, hue = (t % 45000) / 45000f * 360f;
        smallCanvas.drawColor(0xFF0B0B10);
        for (int n = 0; n < BLOBS.length; n++) {
            float[] b = BLOBS[n];
            long period = (long) (b[6] * 1000);
            float p = (t % (2 * period)) / (float) period;
            if (p > 1) p = 2 - p;                                   // there and back
            p = (float) (1 - Math.cos(Math.PI * p)) / 2;            // ease in and out
            float cx = (b[0] + b[3] * p) * k, cy = (b[1] + b[4] * p) * k;
            float r = b[2] * 0.46f * (1 + (b[5] - 1) * p) * k;      // fades out at ~46% of its box
            int base = BLOB_COLORS[n];
            Color.colorToHSV(base, hsv);
            hsv[0] = (hsv[0] + hue) % 360f;
            int col = Color.HSVToColor(Color.alpha(base), hsv);
            blob.setShader(new RadialGradient(cx, cy, r, col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            smallCanvas.drawCircle(cx, cy, r, blob);
        }
        dst.set(0, 0, getWidth(), getHeight());
        c.drawBitmap(small, null, dst, smooth);
    }
}

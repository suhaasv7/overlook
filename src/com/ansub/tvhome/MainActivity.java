package com.ansub.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.ansub.tvhome/.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at six apps, but a chosen list can be any length:
    // apps wrap into rows of six, like Apple TV, and the page scrolls down once they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it, or add another; that saves list 1.
    // A network tile always sits at the top right, level with the clock. Settings shortcuts can join
    // it there, off until picked the same way:
    //   --es quick "bluetooth,sound,picture"
    // (--es quick none  takes them away again; "wifi" and "ethernet" are the network tile now).
    // Folders and the ••• menu's options can be set the same way, so each TV keeps its own setup
    // (install.sh reads them from a TV's settings file in devices/):
    //   --es folders "Anime=com.crunchyroll.crunchyroid+com.sonyliv"   (put folder:Anime in the apps list)
    //   --es options "name=Suhaas;photos=time;clock=12;date=off;weather=on;dim=on;order=mine;location=12.97,77.59"
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String ADD = "add";
    private static final String MORE = "more"; // ••• at the top right: options for the home screen
    private static final String FOLDER = "folder:"; // a folder's key in the app list: folder:<name>
    private static final String SOURCE = "source";  // the TV's inputs: HDMI, AV, the tuner
    private static final int PER_ROW = 6;

    // Settings shortcuts: name, label, icon. Tile keys are QUICK + name.
    private static final String QUICK = "quick:";
    private static final String[] QUICK_NAMES = {"bluetooth", "sound", "picture"};
    private static final String[] QUICK_LABELS = {"Bluetooth", "Sound", "Picture"};
    private static final int[][] QUICK_ICONS = {LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE};
    private static final String NETWORK = "network";
    private static final int OFFLINE = 0, ETHERNET = 4; // network states; 1 to 3 are Wi-Fi bars
    // TCL keeps its own Picture and Sound panels in a service; Android's Sound page is missing there.
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    // Layout is designed at 1920x1080 and scaled to the TV's real UI size by px().
    // Only the top row shows over the photo, low on the screen. Moving down slides the page up, so the
    // top row sits at PAGE_TOP with the others below it.
    private static final int MARGIN = 89, GAP = 40, RADIUS = 16;
    private static final int ROW1_TOP = 830, ROW_STEP = 230, PAGE_TOP = 170;
    private static final int STATUS_TOP = 34, CHIP = 64, CHIP_ICON = 34, CHIP_GAP = 14; // the top right
    private float scale = 1f;

    private static Typeface regular, semibold;

    private FrameLayout root; // the page of tiles, which slides up when you move down past the top row
    private Wallpaper wallpaper;
    private View dim;            // darkens the photo while the lower rows show
    private View nightDim;       // darkens the photo at night, if that's on
    private TextView greeting;   // "Hi <name>", top left
    private TextClock clockTime, clockDate;
    private LinearLayout weatherBox; // icon and temperature, before the date and clock
    private ImageView weatherIcon;
    private TextView weatherTemp;
    private boolean weatherReady;
    private Weather weather;
    private final List<String> ownOrder = new ArrayList<>(); // your app order, under "most used first"
    private LinearLayout status; // the top right: network, shortcuts, •••, Settings, then the clock
    private TextView netName;
    private String netShown;
    private int shownRow = -1;   // the focused tile's row: how far the page has slid
    private String lastGridKey;  // the tile Down goes back to from the top right
    private int tileW, tileH, gridTop; // app grid geometry, set by buildTiles
    private final List<View> tiles = new ArrayList<>();
    private boolean dirty = true;
    private java.util.Locale shownLocale; // the TV's language (and region) when the page was built
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the top row as shown, in order
    private String movingKey;   // the app being moved with left/right, if any
    private TextView moveHint;  // shown above the apps while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open

    // Apps installed, removed or updated. This often lands once Home is already back in front (an app
    // uninstalled from the menu is gone only after Android's prompt has closed), so catch up then.
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            refreshApps();
        }
    };

    // The network tile follows the connection live: Wi-Fi bars, Ethernet or Offline. Only its icon and
    // label change, so nothing is rebuilt.
    private ImageView netIcon;
    private int netState = -1;
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        // Also fires when the Wi-Fi signal gets stronger or weaker.
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            runOnUiThread(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        shownLocale = java.util.Locale.getDefault();

        FrameLayout screen = new FrameLayout(this);
        screen.setClipChildren(false);
        wallpaper = new Wallpaper(this);
        screen.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        // Keeps the clock and the apps readable on any photo: darker at the top and the bottom.
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x66000000, 0x00000000, 0x00000000, 0x8C000000, 0xCC000000}));
        screen.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        nightDim = new View(this);
        nightDim.setBackgroundColor(0x73000000);
        nightDim.setVisibility(View.GONE);
        screen.addView(nightDim, new FrameLayout.LayoutParams(-1, -1));
        dim = new View(this);
        dim.setBackgroundColor(0x9E08080A);
        dim.setAlpha(0f);
        screen.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        root = new FrameLayout(this);
        root.setClipChildren(false);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));

        status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setClipChildren(false);
        // After the chips (addChip puts them in front): the weather, the date, the clock.
        weatherBox = new LinearLayout(this);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherBox.addView(weatherIcon, new LinearLayout.LayoutParams(px(CHIP_ICON), px(CHIP_ICON)));
        weatherTemp = text(px(30), Color.WHITE, semibold);
        weatherTemp.setShadowLayer(px(10), 0, px(2), 0x80000000);
        LinearLayout.LayoutParams wt = new LinearLayout.LayoutParams(-2, -2);
        wt.leftMargin = px(8);
        weatherBox.addView(weatherTemp, wt);
        weatherBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams wb = new LinearLayout.LayoutParams(-2, -2);
        wb.leftMargin = px(26);
        status.addView(weatherBox, wb);
        String day = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEdMMM");
        clockDate = clock(day, day, px(28), 0xD9FFFFFF, regular);
        clockDate.setShadowLayer(px(10), 0, px(2), 0x80000000);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.leftMargin = px(24);
        status.addView(clockDate, dp);
        clockTime = clock("h:mm", "H:mm", px(40), Color.WHITE, semibold);
        clockTime.setFontFeatureSettings("tnum");
        clockTime.setShadowLayer(px(12), 0, px(2), 0x80000000);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = px(16);
        status.addView(clockTime, tp);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.topMargin = px(STATUS_TOP);
        sp.rightMargin = px(MARGIN);
        screen.addView(status, sp);

        greeting = text(px(36), Color.WHITE, semibold);
        greeting.setShadowLayer(px(12), 0, px(2), 0x80000000);
        greeting.setSingleLine(true);
        greeting.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(-2, px(CHIP), Gravity.TOP | Gravity.START);
        gp.topMargin = px(STATUS_TOP);
        gp.leftMargin = px(MARGIN);
        screen.addView(greeting, gp);

        weather = new Weather(this, (t, icon) -> {
            weatherIcon.setImageDrawable(new LineIcon(icon));
            weatherTemp.setText(t + "°");
            weatherReady = true;
            weatherBox.setVisibility(on("weather", true) ? View.VISIBLE : View.GONE);
        });

        moveHint = text(px(24), 0xA8FFFFFF, regular);
        moveHint.setShadowLayer(px(8), 0, px(2), 0x99000000);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(MARGIN), px(ROW1_TOP - 64), -2, -2));

        setContentView(screen);
        applyOptions();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try {
            if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkChanged);
            else cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkChanged);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) {
            buildTiles();
            applyOptions();
        }
    }

    // Saves an app list passed with --es apps "...", shortcuts passed with --es quick "...", and
    // folders and options (see the top of this file). Returns true if it changed.
    // When Android kills the home screen in the background, it can start it again with the same old
    // intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        String folders = intent == null ? null : intent.getStringExtra("folders");
        String options = intent == null ? null : intent.getStringExtra("options");
        if (apps == null && quick == null && folders == null && options == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        if (folders != null) e.putString("o.folders", parseFolders(folders));
        if (options != null) for (String kv : options.split(";")) {
            int eq = kv.indexOf('=');
            if (eq > 0) e.putString("o." + kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split(",") : Apps.LIST;
        Set<String> foldered = inFolders();
        for (String p : wanted) {
            p = p.trim();
            if (p.isEmpty() || out.contains(p)) continue;
            if (isFolder(p) ? !folderApps(p).isEmpty() : !foldered.contains(p) && launchIntent(pm, p) != null) out.add(p);
        }
        // A folder missing from the list still shows, at the end, so its apps never go missing.
        JSONObject f = folders();
        for (Iterator<String> it = f.keys(); it.hasNext(); ) {
            String key = FOLDER + it.next();
            if (!out.contains(key) && !folderApps(key).isEmpty()) out.add(key);
        }
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= PER_ROW) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || foldered.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        wallpaper.start();
        root.removeCallbacks(tick);
        tick.run();
        refreshNetwork();
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    @Override
    protected void onPause() {
        wallpaper.stop();
        root.removeCallbacks(tick);
        super.onPause();
    }

    // Back from a menu or a window on top (like the uninstall prompt): catch up on apps that changed.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshApps();
    }

    // Rebuilds the tiles if apps changed, but only while Home is in front with no menu open and no
    // app being moved; otherwise the next onResume, focus change or end of the move does it.
    private void refreshApps() {
        if (!dirty || !hasWindowFocus() || movingKey != null) return;
        dirty = false;
        buildTiles();
    }

    // The TV's language changed: start over so the labels and the date follow it.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    @Override
    public void onBackPressed() { /* home screen: nothing to go back to */ }

    // The remote's input button, on remotes that have one, opens Source.
    @Override
    public boolean onKeyUp(int code, KeyEvent ev) {
        if (code == KeyEvent.KEYCODE_TV_INPUT && !sources().isEmpty()) {
            showSources();
            return true;
        }
        return super.onKeyUp(code, ev);
    }

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        int wantAt = rowApps.indexOf(want); // where it was, in case the app is gone now
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        status.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS); // the chips are outside root
        for (View t : tiles) {
            View wrap = t.getParent() == status ? t : (View) t.getParent(); // top-right chips sit in status as they are
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        tiles.clear();
        rowApps.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        PackageManager pm = getPackageManager();
        int m = px(MARGIN), g = px(GAP);
        int w = (getResources().getDisplayMetrics().widthPixels - 2 * m - g * (PER_ROW - 1)) / PER_ROW;
        int h = w * 9 / 16;
        tileW = w;
        tileH = h;

        List<String> apps = appList(pm);
        ownOrder.clear();
        ownOrder.addAll(apps);
        if (byUse()) sortByUse(apps);
        // Apps fill rows of six, then Play Store, HDMI inputs and Add app follow on.
        gridTop = px(ROW1_TOP);
        ((FrameLayout.LayoutParams) moveHint.getLayoutParams()).topMargin = gridTop - px(64);

        for (String pkg : apps) {
            if (isFolder(pkg)) {
                int i = rowApps.size();
                rowApps.add(pkg);
                addTile(root, pkg, labelOf(pkg), folderArt(folderApps(pkg), w, h), null, null, gridX(i), gridY(i), w, h);
                continue;
            }
            Intent li = launchIntent(pm, pkg);
            if (li == null) continue;
            Drawable banner = bannerOf(pm, pkg, li);
            String label = pkg;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); } catch (Exception ignored) {}
            // Apps without a TV banner get a plain tile with their normal icon.
            Drawable icon = null;
            if (banner == null) try { icon = pm.getApplicationIcon(pkg); } catch (Exception ignored) {}
            int i = rowApps.size();
            rowApps.add(pkg);
            addTile(root, pkg, label, banner, null, icon, gridX(i), gridY(i), w, h);
        }

        // After the apps: Play Store (when it isn't one of them) and Add app. HDMI and the other
        // inputs are under Source at the top right.
        int n = rowApps.size();
        if (!apps.contains(PLAY_STORE) && !inFolders().contains(PLAY_STORE) && launchIntent(pm, PLAY_STORE) != null)
            addTile(root, PLAY_STORE, "Play Store", null, LineIcon.PLAY, null, gridX(n), gridY(n++), w, h);
        addTile(root, ADD, Strings.t("Add app"), null, LineIcon.PLUS, null, gridX(n), gridY(n), w, h);

        // The top right, before the clock: the network with its name, any settings shortcuts picked,
        // Source (on TVs with inputs), ••• and Settings. Added last, so the first app keeps first focus;
        // Up from the apps reaches them.
        netName = text(px(26), Color.WHITE, semibold);
        netIcon = addChip(NETWORK, Strings.t("Network"), null, netName);
        for (int q : quickList())
            addChip(QUICK + QUICK_NAMES[q], Strings.t(QUICK_LABELS[q]), new LineIcon(QUICK_ICONS[q]), null);
        if (!sources().isEmpty()) addChip(SOURCE, Strings.t("Source"), new LineIcon(LineIcon.HDMI), null);
        addChip(MORE, Strings.t("Options"), new LineIcon(LineIcon.MORE), null);
        addChip(SETTINGS, Strings.t("Settings"), new LineIcon(LineIcon.SETTINGS), null);
        netState = -1;
        netShown = null;
        refreshNetwork();

        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        status.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // An app that was uninstalled hands focus to the one now in its place, as Remove does.
        if (wantAt >= 0 && !rowApps.contains(want))
            want = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(wantAt, rowApps.size() - 1));
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
    }

    private void addTile(ViewGroup parent, final String key, String label, Drawable banner, int[] lineIcon, Drawable appIcon,
                         int x, int y, int w, int h) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        View glow = null;
        if (banner != null) {
            bg.setColor(0xFF1E1A1C);
            ImageView iv = new ImageView(this);
            Drawable art = fit(banner, w, h);
            iv.setImageDrawable(art);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            // A soft glow in the app's own colour behind it while focused, like Apple TV's.
            int c = glowOf(art);
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setColors(new int[] {(c & 0x00FFFFFF) | 0x66000000, c & 0x00FFFFFF});
            g.setGradientRadius(w * 0.9f);
            glow = new View(this);
            glow.setBackground(g);
            glow.setScaleY(0.6f); // a circle squashed to suit a wide tile
            glow.setVisibility(View.INVISIBLE);
        } else {
            // Quiet "glass" tile: icon above label.
            bg.setColor(0x24FFFFFF);
            bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            int size = lineIcon != null ? px(44) : px(64);
            if (lineIcon != null) iv.setImageDrawable(new LineIcon(lineIcon));
            else if (appIcon != null) iv.setImageDrawable(fit(appIcon, size, size));
            inner.addView(iv, new LinearLayout.LayoutParams(size, size));
            TextView t = text(px(24), 0xE0FFFFFF, regular);
            t.setText(label);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(16), 0, px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = px(12);
            inner.addView(t, lp);
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        // A soft shine across the top-left corner while focused, like Apple TV's icons.
        final View shine = new View(this);
        shine.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x52FFFFFF, 0x00FFFFFF, 0x00FFFFFF}));
        shine.setVisibility(View.INVISIBLE);
        card.addView(shine, new FrameLayout.LayoutParams(-1, -1));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setTag(key);

        // Banner tiles show their name underneath when focused.
        final TextView caption = banner == null ? null : text(px(28), Color.WHITE, semibold);
        if (caption != null) {
            caption.setText(label);
            caption.setGravity(Gravity.CENTER_HORIZONTAL);
            caption.setShadowLayer(px(8), 0, px(2), 0x99000000);
            caption.setVisibility(View.INVISIBLE);
        }

        final boolean glass = banner == null;
        final View halo = glow;
        card.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = key;
                lastGridKey = key;
                showRow(rowOf(v));
            }
            if (glass) bg.setColor(has ? 0x38FFFFFF : 0x24FFFFFF);
            shine.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            if (halo != null) halo.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(has ? 1.14f : 1f).scaleY(has ? 1.14f : 1f).translationY(has ? -px(6) : 0)
                    .translationZ(has ? px(26) : 0).setDuration(ms(220)).withLayer().start();
            if (caption != null) caption.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        });
        final boolean editable = rowApps.contains(key);
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) longPressed = false;
                else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else if (!longPressed) {
                open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        if (glow != null) {
            FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(2 * w, 2 * w); // centred on the card
            gp.leftMargin = -w / 2;
            gp.topMargin = h / 2 - w;
            wrap.addView(glow, gp);
        }
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        if (caption != null) {
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, -2);
            cp.topMargin = h + px(26);
            wrap.addView(caption, cp);
        }
        // A set height: rows start below the screen until the page slides up.
        parent.addView(wrap, at(x, y, w, h + px(80)));
        wrap.setAlpha(rowAt(y) == 0 || shownRow > 0 ? 1f : 0f);
        tiles.add(card);
    }

    // A chip at the top right: an icon, with a name beside it if given. Focused, it turns white with
    // dark contents, Apple TV style. Down goes back to the tile you came up from.
    private ImageView addChip(final String key, String label, Drawable icon, final TextView name) {
        final LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(CHIP) / 2f);
        bg.setColor(Color.TRANSPARENT);
        chip.setBackground(bg);
        final ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        chip.addView(iv, new LinearLayout.LayoutParams(px(CHIP_ICON), px(CHIP_ICON)));
        if (name != null) {
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setMaxWidth(px(420));
            name.setShadowLayer(px(8), 0, px(1), 0x8C000000);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
            np.leftMargin = px(12);
            chip.addView(name, np);
            chip.setPadding(px(24), 0, px(24), 0);
        }
        chip.setFocusable(true);
        chip.setTag(key);
        chip.setContentDescription(label);
        chip.setOnFocusChangeListener((v, has) -> {
            if (has) {
                focusedKey = key;
                showRow(0);
            }
            bg.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            if (has) iv.setColorFilter(0xFF111111, PorterDuff.Mode.SRC_IN);
            else iv.clearColorFilter();
            if (name != null) {
                name.setTextColor(has ? 0xFF111111 : Color.WHITE);
                name.setShadowLayer(has ? 0 : px(8), 0, px(1), 0x8C000000);
            }
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).translationZ(has ? px(12) : 0)
                    .setDuration(ms(220)).withLayer().start();
        });
        chip.setOnClickListener(v -> open(key));
        chip.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                View back = tile(lastGridKey);
                (back != null ? back : tiles.get(0)).requestFocus();
                return true;
            }
            // Nothing left of the first chip: don't jump down into the apps.
            return code == KeyEvent.KEYCODE_DPAD_LEFT && status.indexOfChild(v) == 0;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(name != null ? -2 : px(CHIP), px(CHIP));
        lp.leftMargin = px(CHIP_GAP);
        status.addView(chip, status.indexOfChild(weatherBox), lp); // before the weather, date and clock
        tiles.add(chip);
        return iv;
    }

    private void open(String key) {
        Intent i;
        if (ADD.equals(key)) {
            showAddMenu(-1);
            return;
        } else if (SETTINGS.equals(key)) {
            i = new Intent(Settings.ACTION_SETTINGS);
        } else if (isInput(key)) {
            openInput(key);
            return;
        } else if (NETWORK.equals(key)) {
            openNetworkSettings();
            return;
        } else if (MORE.equals(key)) {
            showOptions();
            return;
        } else if (SOURCE.equals(key)) {
            showSources();
            return;
        } else if (isFolder(key)) {
            showFolder(key);
            return;
        } else if (key.startsWith(QUICK)) {
            openQuick(key.substring(QUICK.length()));
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before. The one exception: when the TV itself sends HDMI to
    // Google TV's input player, that goes first, because some Google TVs ship com.android.tv as a stub
    // that starts fine but only says "Hello World!" (issue #5).
    private void openInput(String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        ResolveInfo def = getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(getPackageManager()) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    // The shortcuts picked with --es quick, in that order, as indexes into QUICK_NAMES. A shortcut
    // this TV has no screen for is left out, so it never shows a tile that does nothing.
    private List<Integer> quickList() {
        List<Integer> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("quick", null);
        if (saved == null) return out;
        for (String name : saved.split("[,\\s]+")) {
            int q = Arrays.asList(QUICK_NAMES).indexOf(name.toLowerCase(java.util.Locale.ROOT));
            if (q < 0 || out.contains(q) || quickIntents(QUICK_NAMES[q]).isEmpty()) continue;
            out.add(q);
        }
        return out;
    }

    // OFFLINE, 1 to 3 for Wi-Fi with that many bars, or ETHERNET (any other link, which on a TV is a cable).
    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) { // Android 9 and older: ask the Wi-Fi service
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }

    private void refreshNetwork() {
        if (netIcon == null) return;
        int state = networkState();
        String name = state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : wifiName();
        if (!name.equals(netShown)) {
            netShown = name;
            netName.setText(name);
        }
        if (state == netState) return;
        netState = state;
        if (state == ETHERNET) {
            netIcon.setImageDrawable(new LineIcon(LineIcon.ETHERNET));
        } else {
            // The whole Wi-Fi fan dim, with the bars it has bright on top. Offline is a slash instead.
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = state == OFFLINE ? LineIcon.SLASH : state == 1 ? LineIcon.WIFI_1
                    : state == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            netIcon.setImageDrawable(new LayerDrawable(new Drawable[] {fan, new LineIcon(top)}));
        }
        ((View) netIcon.getParent()).setContentDescription(
                state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : "Wi-Fi");
    }

    // The Wi-Fi network's name. Android 9 tells only apps with location access (install.sh grants it);
    // without that, the saved network with the same id still has the name. Else just "Wi-Fi".
    @SuppressWarnings("deprecation")
    private String wifiName() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            String s = wi.getSSID();
            if (s == null || s.contains("<unknown ssid>"))
                for (WifiConfiguration c : wm.getConfiguredNetworks()) if (c.networkId == wi.getNetworkId()) s = c.SSID;
            if (s != null && !s.isEmpty() && !s.contains("<unknown ssid>")) return s.replaceAll("^\"|\"$", "");
        } catch (Exception ignored) {}
        return "Wi-Fi";
    }

    // Wi-Fi settings (on Android TV that page lists Ethernet too), else the TV's network page, else Settings.
    private void openNetworkSettings() {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // Screens a shortcut can open, best first, keeping only those this TV has.
    private List<Intent> quickIntents(String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        if (tclType != null)
            all.add(new Intent(TCL_SHOW_WINDOW).setClassName("com.tcl.settings", "com.tcl.settings.ShowWindowService")
                    .putExtra("Type", tclType));
        switch (name) {
            case "bluetooth":
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT")); // Android TV's pairing screen
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    // Opens a shortcut's screen: the first one that starts wins.
    private void openQuick(String name) {
        for (Intent i : quickIntents(name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) startService(i);
                else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    interface Pick { void at(int which); }
    interface Labels { String[] get(); }
    interface Choose { boolean at(int which); } // true keeps the menu open
    interface Named { void as(String name); }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. iconPkgs (or null) puts each app's icon by its name.
    // longPick (or null) is what holding OK on a row does; hint (or null) says so under the title.
    private void panel(String title, final List<String> labels, final List<String> iconPkgs, final Pick pick,
                       final Pick longPick, String hint) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), Color.WHITE, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);
        if (hint != null) {
            TextView h = text(px(22), 0xA8FFFFFF, regular);
            h.setText(hint);
            h.setPadding(px(24), px(8), px(24), 0);
            box.addView(h);
        }

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(0x38FFFFFF);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final PackageManager pm = getPackageManager();
        final int size = px(48);
        final Drawable[] icons = new Drawable[labels.size()];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), 0xE6FFFFFF, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                Drawable icon = null;
                if (iconPkgs != null) {
                    if (icons[pos] == null) {
                        try { icons[pos] = fit(pm.getApplicationIcon(iconPkgs.get(pos)), size, size); }
                        catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                        icons[pos].setBounds(0, 0, size, size);
                    }
                    icon = icons[pos];
                }
                t.setCompoundDrawablesRelative(icon, null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        if (longPick != null) list.setOnItemLongClickListener((parent, v, pos, id) -> {
            d.dismiss();
            longPick.at(pos);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0xF7161113));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(0.5f);
        d.show();
    }

    // A small menu that drops down under a tile or chip; the focused row turns white, Apple TV style.
    // Opens above the tile when there's no room below. Back closes it, and so does a choice, unless
    // the choice says to stay open; then the labels are read again, so switches show their new state.
    private void dropdown(View anchor, final Labels labels, final Choose choose) {
        final String[] items = labels.get();
        final TextView[] rows = new TextView[items.length];
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(12), rowH = px(72);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(28));
        bg.setColor(0xF01C1C1E);
        bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
        box.setBackground(bg);
        float widest = 0; // some languages need more room than the tile is wide
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), Color.WHITE, semibold);
            rows[i] = t;
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(18));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? Color.WHITE : Color.TRANSPARENT);
                t.setTextColor(has ? 0xFF111111 : Color.WHITE);
            });
            t.setOnClickListener(v -> {
                if (!choose.at(which)) {
                    d.dismiss();
                    return;
                }
                String[] now = labels.get();
                for (int k = 0; k < rows.length; k++) rows[k].setText(Strings.t(now[k]));
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), below its name.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(anchor.getParent() == status ? 480 : 320)),
                (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(anchor.getParent() == status ? 16 : 64); // a chip has no name under it
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = Math.min(at[0], getResources().getDisplayMetrics().widthPixels - px(MARGIN) - w); // stays on screen
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on a top-row app: move it, swap it for another, put it in a folder, or take it
    // off the home screen. Removing only takes the tile away: the app stays installed and can be added
    // back. Apps the user installed can also be uninstalled; Android asks to confirm, and the tile goes
    // once the app is gone. Apps that came with the TV can't be, so they get App info instead, where
    // Android offers to turn them off. A folder can be moved, renamed or removed (its apps go back to
    // the row). Under "most used first" the order is automatic, so there's no Move.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (!byUse()) {
            labels.add("Move");
            acts.add(() -> startMove(key));
        }
        if (isFolder(key)) {
            labels.add("Rename");
            acts.add(() -> renameFolder(key));
            labels.add("Remove folder");
            acts.add(() -> removeFolder(key));
        } else {
            final boolean user = userInstalled(key);
            labels.add("Replace");
            acts.add(() -> showAddMenu(index));
            labels.add("Add to folder");
            acts.add(() -> addToFolder(key));
            labels.add("Remove from home");
            acts.add(() -> {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            });
            labels.add(user ? "Uninstall" : "App info");
            acts.add(() -> {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            });
        }
        final String[] items = labels.toArray(new String[0]);
        dropdown(tile(key), () -> items, which -> {
            acts.get(which).run();
            return false;
        });
    }

    // ••• at the top right. Switches flip in place and the menu stays open; Back closes it.
    private void showOptions() {
        final List<String> ids = new ArrayList<>(Arrays.asList("next", "fav", "photos", "dim", "weather", "clock", "date", "order"));
        for (String q : QUICK_NAMES) if (!quickIntents(q).isEmpty()) ids.add(QUICK + q);
        dropdown(tile(MORE), () -> optionLabels(ids), which -> option(ids.get(which)));
    }

    private String[] optionLabels(List<String> ids) {
        String[] out = new String[ids.size()];
        for (int i = 0; i < out.length; i++) {
            switch (ids.get(i)) {
                case "next": out[i] = "Next wallpaper"; break;
                case "fav": out[i] = wallpaper.isFavourite() ? "Remove from favourites" : "Add to favourites"; break;
                case "photos": out[i] = "Photos: " + modeLabel(opt("photos", Wallpaper.TIME)); break;
                case "dim": out[i] = "Dim at night: " + onOff(on("dim", true)); break;
                case "weather": out[i] = "Weather: " + onOff(on("weather", true)); break;
                case "clock": out[i] = "Clock: " + clockHours() + "-hour"; break;
                case "date": out[i] = "Date: " + onOff(on("date", false)); break;
                case "order": out[i] = "Apps: " + (byUse() ? "most used first" : "your order"); break;
                default: out[i] = QUICK_LABELS[Arrays.asList(QUICK_NAMES).indexOf(ids.get(i).substring(QUICK.length()))];
            }
        }
        return out;
    }

    // What an option does; true keeps the menu open.
    private boolean option(String id) {
        switch (id) {
            case "next": wallpaper.next(); return true;
            case "fav": wallpaper.toggleFavourite(); return true;
            case "photos": showPhotos(); return false;
            case "dim": flip("dim", true); break;
            case "weather": flip("weather", true); weather.soon(); break;
            case "clock": setOpt("clock", "24".equals(clockHours()) ? "12" : "24"); break;
            case "date": flip("date", false); break;
            case "order": setOpt("order", byUse() ? "mine" : "used"); buildTiles(); break;
            default: openQuick(id.substring(QUICK.length())); return false;
        }
        applyOptions();
        return true;
    }

    // Which photos come up: by time of day, the mix, favourites, your own, or one collection.
    private void showPhotos() {
        final List<String> modes = new ArrayList<>(Arrays.asList(Wallpaper.TIME, Wallpaper.MIX, Wallpaper.FAVS, Wallpaper.MINE));
        modes.addAll(Arrays.asList(Wallpaper.COLLECTIONS));
        String now = opt("photos", Wallpaper.TIME);
        int mine = wallpaper.mineCount();
        List<String> labels = new ArrayList<>();
        for (String m : modes) {
            String l = modeLabel(m);
            if (Wallpaper.MINE.equals(m)) l += mine == 0 ? "  (none yet)" : "  (" + mine + ")";
            labels.add((m.equals(now) ? "✓   " : "      ") + l);
        }
        panel(Strings.t("Photos"), labels, null, which -> {
            setOpt("photos", modes.get(which));
            applyOptions();
        }, null, null);
    }

    private static String modeLabel(String m) {
        switch (m) {
            case Wallpaper.TIME: return "Time of day";
            case Wallpaper.MIX: return "Mix";
            case Wallpaper.FAVS: return "Favourites";
            case Wallpaper.MINE: return "My photos";
            default: return m.isEmpty() ? m : Character.toUpperCase(m.charAt(0)) + m.substring(1);
        }
    }

    // The options (••• menu or the TV's settings file), applied to the screen.
    private void applyOptions() {
        String f = "24".equals(clockHours()) ? "H:mm" : "h:mm";
        clockTime.setFormat12Hour(f);
        clockTime.setFormat24Hour(f);
        clockDate.setVisibility(on("date", false) ? View.VISIBLE : View.GONE);
        weatherBox.setVisibility(on("weather", true) && weatherReady ? View.VISIBLE : View.GONE);
        String name = opt("name", "");
        greeting.setText(name.isEmpty() ? "" : String.format(Strings.t("Hi %s"), name));
        greeting.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
        nightDim.setVisibility(on("dim", true) && Wallpaper.night() ? View.VISIBLE : View.GONE);
        wallpaper.setMode(opt("photos", Wallpaper.TIME));
    }

    // Every minute while Home is in front: a new photo when the part of the day changes, night
    // dimming, and the weather when it's due.
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            wallpaper.check();
            nightDim.setVisibility(on("dim", true) && Wallpaper.night() ? View.VISIBLE : View.GONE);
            if (on("weather", true)) weather.refresh(place());
            root.postDelayed(this, 60_000);
        }
    };

    private String opt(String name, String def) {
        return getSharedPreferences(PREFS, 0).getString("o." + name, def);
    }

    private void setOpt(String name, String value) {
        getSharedPreferences(PREFS, 0).edit().putString("o." + name, value).apply();
    }

    private boolean on(String name, boolean def) {
        return "on".equals(opt(name, def ? "on" : "off"));
    }

    private void flip(String name, boolean def) {
        setOpt(name, on(name, def) ? "off" : "on");
    }

    private static String onOff(boolean on) {
        return on ? "On" : "Off";
    }

    // "12" or "24": the option, else the TV's own setting.
    private String clockHours() {
        return opt("clock", android.text.format.DateFormat.is24HourFormat(this) ? "24" : "12");
    }

    // A fixed "lat,lon" for the weather from the TV's settings, or null to work it out.
    private String place() {
        String p = opt("location", "");
        return p.isEmpty() ? null : p;
    }

    private boolean byUse() {
        return "used".equals(opt("order", "mine"));
    }

    // "Most used first": apps by how long they were open in the last two weeks (a folder counts its
    // apps together). Needs usage access, which install.sh grants; without it the order stays yours.
    private void sortByUse(List<String> apps) {
        if (android.os.Build.VERSION.SDK_INT < 22) return;
        try {
            UsageStatsManager um = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            Map<String, UsageStats> use = um.queryAndAggregateUsageStats(now - 14 * 86_400_000L, now);
            if (use == null || use.isEmpty()) return;
            final Map<String, Long> time = new HashMap<>();
            for (String k : apps) {
                long t = 0;
                for (String p : isFolder(k) ? folderApps(k) : Collections.singletonList(k)) {
                    UsageStats u = use.get(p);
                    if (u != null) t += u.getTotalTimeInForeground();
                }
                time.put(k, t);
            }
            Collections.sort(apps, (a, b) -> Long.compare(time.get(b), time.get(a))); // stable: ties keep your order
        } catch (Exception ignored) {}
    }

    // Folders live in the options as JSON: {"Anime": ["com.crunchyroll.crunchyroid", "com.sonyliv"]}.
    private JSONObject folders() {
        try { return new JSONObject(opt("folders", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }

    private void saveFolders(JSONObject f) {
        setOpt("folders", f.toString());
    }

    // "Anime=pkg1+pkg2;Kids=pkg3", from --es folders, as that JSON.
    private static String parseFolders(String spec) {
        JSONObject f = new JSONObject();
        try {
            for (String part : spec.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                JSONArray a = new JSONArray();
                for (String p : part.substring(eq + 1).split("\\+")) if (!p.trim().isEmpty()) a.put(p.trim());
                f.put(part.substring(0, eq).trim().replace(',', ' '), a);
            }
        } catch (Exception ignored) {}
        return f.toString();
    }

    private static List<String> members(JSONObject f, String name) {
        List<String> out = new ArrayList<>();
        JSONArray a = f.optJSONArray(name);
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private static boolean isFolder(String key) {
        return key != null && key.startsWith(FOLDER);
    }

    // Every app that's in a folder.
    private Set<String> inFolders() {
        Set<String> out = new HashSet<>();
        JSONObject f = folders();
        for (Iterator<String> it = f.keys(); it.hasNext(); ) out.addAll(members(f, it.next()));
        return out;
    }

    // The apps in a folder that can be opened.
    private List<String> folderApps(String key) {
        List<String> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (String p : members(folders(), key.substring(FOLDER.length()))) if (launchIntent(pm, p) != null) out.add(p);
        return out;
    }

    // OK on a folder: its apps. Holding OK on one takes it out, back onto the row after the folder.
    private void showFolder(final String key) {
        final List<String> pkgs = folderApps(key);
        List<String> labels = new ArrayList<>();
        for (String p : pkgs) labels.add(labelOf(p));
        panel(labelOf(key), labels, pkgs, which -> open(pkgs.get(which)), which -> takeOut(key, pkgs.get(which)),
                Strings.t("Hold OK on an app to take it out"));
    }

    private void addToFolder(final String pkg) {
        final List<String> names = new ArrayList<>();
        for (Iterator<String> it = folders().keys(); it.hasNext(); ) names.add(it.next());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        List<String> labels = new ArrayList<>(names);
        labels.add(Strings.t("New folder…"));
        panel(Strings.t("Add to folder"), labels, null, which -> {
            if (which < names.size()) putInFolder(pkg, names.get(which));
            else askName(Strings.t("New folder"), "", name -> putInFolder(pkg, name));
        }, null, null);
    }

    // A new folder takes the app's place on the row; an existing one just gains the app.
    private void putInFolder(String pkg, String name) {
        JSONObject f = folders();
        JSONArray a = f.optJSONArray(name);
        boolean fresh = a == null;
        if (fresh) a = new JSONArray();
        a.put(pkg);
        try { f.put(name, a); } catch (Exception ignored) {}
        saveFolders(f);
        int i = rowApps.indexOf(pkg);
        if (i >= 0 && fresh) rowApps.set(i, FOLDER + name);
        else if (i >= 0) rowApps.remove(i);
        saveRow();
        focusedKey = FOLDER + name;
        buildTiles();
    }

    // Out of the folder, onto the row just after it. The last app out takes the folder's place.
    private void takeOut(String key, String pkg) {
        String name = key.substring(FOLDER.length());
        JSONObject f = folders();
        List<String> left = members(f, name);
        left.remove(pkg);
        if (left.isEmpty()) f.remove(name);
        else try { f.put(name, new JSONArray(left)); } catch (Exception ignored) {}
        saveFolders(f);
        int i = rowApps.indexOf(key);
        if (i < 0) rowApps.add(pkg);
        else if (left.isEmpty()) rowApps.set(i, pkg);
        else rowApps.add(i + 1, pkg);
        saveRow();
        focusedKey = pkg;
        buildTiles();
    }

    private void renameFolder(final String key) {
        final String old = key.substring(FOLDER.length());
        askName(Strings.t("Rename folder"), old, name -> {
            JSONObject f = folders();
            if (name.equals(old) || f.has(name)) return;
            try { f.put(name, f.optJSONArray(old)); } catch (Exception ignored) {}
            f.remove(old);
            saveFolders(f);
            int i = rowApps.indexOf(key);
            if (i >= 0) rowApps.set(i, FOLDER + name);
            saveRow();
            focusedKey = FOLDER + name;
            buildTiles();
        });
    }

    // The folder goes; its apps go back onto the row in its place.
    private void removeFolder(String key) {
        String name = key.substring(FOLDER.length());
        JSONObject f = folders();
        List<String> apps = members(f, name);
        f.remove(name);
        saveFolders(f);
        int i = rowApps.indexOf(key);
        rowApps.remove(i);
        for (String p : apps) if (!rowApps.contains(p)) rowApps.add(i++, p);
        saveRow();
        focusedKey = apps.isEmpty() ? ADD : apps.get(0);
        buildTiles();
    }

    // A box to type a name in, with the TV's keyboard. Done (or Enter) saves it.
    private void askName(String title, String current, final Named named) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(40), px(32), px(40), px(32));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(28));
        bg.setColor(0xF01C1C1E);
        box.setBackground(bg);
        TextView head = text(px(32), Color.WHITE, semibold);
        head.setText(title);
        box.addView(head);
        final EditText e = new EditText(this);
        e.setText(current);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(30));
        e.setTextColor(Color.WHITE);
        e.setTypeface(regular);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener((v, action, ev) -> {
            String n = e.getText().toString().replace(',', ' ').trim(); // commas would split the saved app list
            if (n.isEmpty()) return true;
            d.dismiss();
            named.as(n);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = px(20);
        box.addView(e, lp);
        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setLayout(px(720), -2);
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        d.show();
        e.requestFocus();
    }

    // A folder's tile: up to four of its apps' banners in a grid.
    private Drawable folderArt(List<String> pkgs, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF2A2A2E);
        int pad = px(12), gap = px(8), cw = (w - 2 * pad - gap) / 2, ch = (h - 2 * pad - gap) / 2;
        PackageManager pm = getPackageManager();
        Path clip = new Path();
        for (int i = 0; i < Math.min(4, pkgs.size()); i++) {
            Drawable d = bannerOf(pm, pkgs.get(i), launchIntent(pm, pkgs.get(i)));
            if (d == null) continue;
            int x = pad + (i % 2) * (cw + gap), y = pad + (i / 2) * (ch + gap);
            Drawable art = fit(d, cw, ch);
            clip.reset();
            clip.addRoundRect(new RectF(x, y, x + cw, y + ch), px(6), px(6), Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            art.setBounds(x, y, x + cw, y + ch);
            art.draw(c);
            c.restore();
        }
        return new BitmapDrawable(getResources(), b);
    }

    // An app's TV banner, or null if it has none.
    private static Drawable bannerOf(PackageManager pm, String pkg, Intent launch) {
        try {
            Drawable d = launch == null ? null : pm.getActivityBanner(launch);
            return d != null ? d : pm.getApplicationBanner(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    // A tile's glow colour: its banner's average colour, made more vivid.
    private static int glowOf(Drawable art) {
        if (!(art instanceof BitmapDrawable)) return Color.WHITE;
        int c = Bitmap.createScaledBitmap(((BitmapDrawable) art).getBitmap(), 1, 1, true).getPixel(0, 0);
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.5f);
        hsv[2] = Math.max(hsv[2], 0.85f);
        return Color.HSVToColor(hsv);
    }

    // Every launchable app that isn't on the top row yet, by name. The pick is added at the end,
    // or takes the place of the app at replaceIndex (-1 to add).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> foldered = inFolders();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (rowApps.contains(p) || foldered.contains(p) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        if (byLabel.isEmpty()) {
            panel(Strings.t("Every app is already here"), Collections.singletonList("OK"), null, which -> {}, null, null);
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? Strings.t("Add an app")
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        panel(title, new ArrayList<>(byLabel.keySet()), pkgs, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        }, null, null);
    }

    private String labelOf(String pkg) {
        if (isFolder(pkg)) return pkg.substring(FOLDER.length());
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the top row as the app list, so it no longer follows apps.txt. Under "most used first" the
    // row on screen is sorted, so your own order is what's saved: changes kept, new apps at the end.
    private void saveRow() {
        List<String> keep = rowApps;
        if (byUse()) {
            keep = new ArrayList<>();
            for (String k : ownOrder) if (rowApps.contains(k)) keep.add(k);
            for (String k : rowApps) if (!keep.contains(k)) keep.add(k);
        }
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", keep)).apply();
    }

    // Where app number i sits in the grid.
    private int gridX(int i) {
        return px(MARGIN) + (i % PER_ROW) * (tileW + px(GAP));
    }

    private int gridY(int i) {
        return gridTop + (i / PER_ROW) * px(ROW_STEP);
    }

    // Only the top row shows over the photo. Moving down past it slides the page up (further for rows
    // below the fourth), darkens the photo and shows the other rows; moving back up undoes it.
    private void showRow(int row) {
        shownRow = row;
        boolean down = row > 0;
        float y = down ? -(gridTop - px(PAGE_TOP)) - Math.max(0, row - 3) * px(ROW_STEP) : 0;
        root.animate().translationY(y).setDuration(ms(450)).start();
        dim.animate().alpha(down ? 1 : 0).setDuration(ms(400)).start();
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            if (wrap != null && wrap.getParent() == root && rowOf(t) > 0) wrap.animate().alpha(down ? 1 : 0).setDuration(ms(300)).start();
        }
    }

    private int rowAt(int y) {
        return Math.round((y - gridTop) / (float) px(ROW_STEP));
    }

    private int rowOf(View card) {
        return rowAt(((FrameLayout.LayoutParams) ((View) card.getParent()).getLayoutParams()).topMargin);
    }

    // A duration as designed, in real time, whatever speed this TV runs animations at (0 still turns them off).
    private static long ms(long design) {
        float s = android.os.Build.VERSION.SDK_INT >= 26 ? ValueAnimator.getDurationScale() : 1f;
        return s > 0 ? (long) (design / s) : 0;
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the tile gets an outline, the arrows move it through the grid, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : code == KeyEvent.KEYCODE_DPAD_UP ? -PER_ROW : code == KeyEvent.KEYCODE_DPAD_DOWN ? PER_ROW : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                // Slide the tiles into their new places: no rebuild, so it stays quick on slow TVs.
                rowApps.add(to, rowApps.remove(from));
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    View wrap = (View) tile(rowApps.get(i)).getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridY(i);
                    wrap.setLayoutParams(lp);
                }
                // The page follows it, and rows it moved into or out of show or hide.
                showRow(rowOf(tile(key)));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(null);
                moveHint.setVisibility(View.GONE);
                saveRow();
                refreshApps();
            }
            return true;
        }
        return true; // up/down and everything else stay put while moving
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    // The TV's own inputs for Source (not devices behind them, not hidden ones): HDMI first, then AV
    // and the like, then the tuner. Channel apps that add a "tuner" of their own are left out.
    private List<TvInputInfo> sources() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getParentId() != null || in.isHidden(this)) continue;
            boolean system = (in.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (in.isPassthroughInput() || system) out.add(in);
        }
        Collections.sort(out, (a, b) -> rank(a) != rank(b) ? rank(a) - rank(b) : a.getId().compareTo(b.getId()));
        return out;
    }

    // The names of the devices behind an input: Android lists each device a port reports over
    // HDMI-CEC as an input of its own, with the port as its parent. Empty when there's none (or CEC is off).
    private String connectedTo(String portId) {
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<String> names = new ArrayList<>();
        if (tim != null) for (TvInputInfo in : tim.getTvInputList())
            if (portId.equals(in.getParentId()) && !in.isHidden(this)) names.add(inputLabel(in));
        return android.text.TextUtils.join(", ", names);
    }

    private static int rank(TvInputInfo in) {
        return in.getType() == TvInputInfo.TYPE_HDMI ? 0 : in.getType() == TvInputInfo.TYPE_TUNER ? 2 : 1;
    }

    // Source: pick an input. HDMI and AV open straight away; the tuner opens Live TV on its channels.
    // A device plugged into a port shows by name when it gives one over HDMI-CEC ("HDMI 1 · PlayStation 5");
    // one that doesn't still shows as connected, from the TV sensing it on the cable ("HDMI 1 · connected").
    private void showSources() {
        final List<TvInputInfo> inputs = sources();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        List<String> labels = new ArrayList<>();
        for (TvInputInfo in : inputs) {
            String extra = connectedTo(in.getId());
            if (extra.isEmpty() && in.isPassthroughInput() && tim != null
                    && tim.getInputState(in.getId()) == TvInputManager.INPUT_STATE_CONNECTED) extra = Strings.t("connected");
            labels.add(inputLabel(in) + (extra.isEmpty() ? "" : "  ·  " + extra));
        }
        panel(Strings.t("Source"), labels, null, which -> {
            TvInputInfo in = inputs.get(which);
            if (in.isPassthroughInput()) openInput(in.getId());
            else openLiveTv();
        }, null, null);
    }

    // Live TV for the antenna or cable: the TV's own Live TV app, else whatever launches for it.
    private void openLiveTv() {
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(Intent.ACTION_MAIN).setClassName(live[0], live[1]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
        Intent i = launchIntent(getPackageManager(), "com.droidlogic.droidlivetv");
        if (i != null) try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name, spaced ("HDMI1" →
    // "HDMI 1"). The tuner is just "TV": its own name is often a service's, like "DroidLogicTvInput".
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return withoutDeviceType(custom.toString());
        if (in.getType() == TvInputInfo.TYPE_TUNER) return Strings.t("TV");
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0
                ? withoutDeviceType(label.toString()).replaceAll("^([A-Za-z]+)(\\d+)$", "$1 $2") : "HDMI";
    }

    // Philips stores a name as "name&DEVICE_TYPE" (like "Computer&COMPUTER"), so show the name only.
    // Only on Philips TVs: elsewhere a name like "AUDIO&VIDEO" is the name itself.
    private static String withoutDeviceType(String label) {
        if (!"Philips".equalsIgnoreCase(android.os.Build.MANUFACTURER)) return label;
        int amp = label.lastIndexOf('&');
        return amp > 0 && label.substring(amp + 1).matches("[A-Z][A-Z0-9_]{2,}") ? label.substring(0, amp).trim() : label;
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Inter, bundled in assets/fonts (SIL Open Font License). If it won't load: MiLan Pro, which Xiaomi
    // TVs ship without registering it by name, else Roboto.
    private void loadFonts() {
        if (regular != null) return;
        regular = fromAsset("fonts/Inter-Regular.ttf",
                fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL)));
        semibold = fromAsset("fonts/Inter-SemiBold.ttf",
                fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL)));
    }

    private Typeface fromAsset(String path, Typeface fallback) {
        try { return Typeface.createFromAsset(getAssets(), path); }
        catch (Exception e) { return fallback; }
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}

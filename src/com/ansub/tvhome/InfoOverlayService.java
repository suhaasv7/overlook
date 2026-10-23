package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Optional global shortcut. Never opens an Activity or takes focus from the playing app. */
public final class InfoOverlayService extends AccessibilityService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private FrameLayout overlay;
    private LinearLayout panel;
    private TextView temperature;
    private ImageView sky;
    private Weather weather;
    private Integer lastTemperature;
    private int[] lastSky;
    private int heldKey = -1;
    private boolean registered;
    private boolean connected;
    private volatile int bridgeGeneration;
    private volatile java.lang.Process bridgeProcess;
    private long lastBridgePress;
    private final SharedPreferences.OnSharedPreferenceChangeListener configChanged = (prefs, key) -> {
        if ("o.patchwall_bridge".equals(key)) main.post(this::restartBridge);
    };
    private final Runnable dismiss = this::hide;
    // Shell-only preview, so testing the panel doesn't depend on the vendor's key routing.
    private final BroadcastReceiver preview = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { toggle(); }
    };

    @Override protected void onServiceConnected() {
        connected = true;
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        weather = new Weather(this, (temp, icon) -> {
            lastTemperature = temp;
            lastSky = icon;
            if (temperature != null) {
                temperature.setText(temp + "°C");
                sky.setImageDrawable(new LineIcon(icon));
            }
        });
        if (!registered) {
            IntentFilter filter = new IntentFilter("com.ansub.tvhome.TOGGLE_INFO");
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(preview, filter, "android.permission.DUMP", main, Context.RECEIVER_EXPORTED);
            else registerReceiver(preview, filter, "android.permission.DUMP", main);
            registered = true;
            getSharedPreferences("config", 0).registerOnSharedPreferenceChangeListener(configChanged);
        }
        restartBridge();
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        // Xiaomi handles these global keys before accessibility. Its log bridge is the sole trigger.
        if (bridgeEnabled() && (code == KeyEvent.KEYCODE_BUTTON_2 || code == KeyEvent.KEYCODE_BUTTON_5))
            return false;
        // Keep consuming the matching up event even if configuration changes while held.
        if (code == heldKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) heldKey = -1;
            return true;
        }
        int configured;
        try { configured = Integer.parseInt(option("patchwall_key", "0")); }
        catch (NumberFormatException e) { return false; }
        // Do not allow the experimental shortcut to steal essential remote controls.
        if (!allowed(configured) || code != configured || event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() != 0) return false;
        heldKey = code;
        toggle();
        return true;
    }

    private boolean bridgeEnabled() {
        return "mi-tv".equals(option("patchwall_bridge", "off"))
                && checkPermission("android.permission.READ_LOGS", android.os.Process.myPid(),
                    android.os.Process.myUid()) == PackageManager.PERMISSION_GRANTED;
    }

    // Android 9 Mi TV sends PatchWall to Xiaomi's explicit GlobalKeyReceiver before key filters.
    // Read only that tag, from now onward, and react to its one key-up message. No logs are kept.
    private void restartBridge() {
        final int generation = ++bridgeGeneration;
        java.lang.Process previous = bridgeProcess;
        bridgeProcess = null;
        if (previous != null) previous.destroy();
        if (!connected || !bridgeEnabled()) return;
        Thread reader = new Thread(() -> {
            while (generation == bridgeGeneration) {
                java.lang.Process process = null;
                try {
                    String now = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
                    process = new ProcessBuilder("logcat", "-b", "main", "-b", "system", "-v", "raw", "-T", now,
                            "GlobalKey:V", "*:S").redirectErrorStream(true).start();
                    if (generation != bridgeGeneration) { process.destroy(); return; }
                    bridgeProcess = process;
                    try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while (generation == bridgeGeneration && (line = lines.readLine()) != null) {
                            if (!"receiving KeyEvent.KEYCODE_BUTTON_2 start tvhome".equals(line.trim())) continue;
                            main.post(() -> {
                                if (!connected || generation != bridgeGeneration || !bridgeEnabled()) return;
                                long at = SystemClock.uptimeMillis();
                                if (at - lastBridgePress < 350) return;
                                lastBridgePress = at;
                                toggle();
                            });
                        }
                    }
                } catch (Exception e) {
                    if (generation == bridgeGeneration) Log.w("OverlookOverlay", "Mi TV key bridge stopped", e);
                } finally {
                    if (process != null) process.destroy();
                }
                // A stopped log daemon is unusual; avoid a hot retry loop if it happens.
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            }
        }, "overlook-mi-key");
        reader.setDaemon(true);
        reader.start();
    }

    private static boolean allowed(int key) {
        return key > KeyEvent.KEYCODE_UNKNOWN && key <= KeyEvent.getMaxKeyCode()
                && key != KeyEvent.KEYCODE_HOME && key != KeyEvent.KEYCODE_BACK
                && key != KeyEvent.KEYCODE_POWER && key != KeyEvent.KEYCODE_SLEEP
                && key != KeyEvent.KEYCODE_WAKEUP && key != KeyEvent.KEYCODE_VOLUME_UP
                && key != KeyEvent.KEYCODE_VOLUME_DOWN && key != KeyEvent.KEYCODE_VOLUME_MUTE
                && key != KeyEvent.KEYCODE_DPAD_CENTER && key != KeyEvent.KEYCODE_ENTER
                && key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN
                && key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private String option(String key, String fallback) {
        return getSharedPreferences("config", 0).getString("o." + key, fallback);
    }

    private int size(int design) {
        return Math.round(design * getResources().getDisplayMetrics().widthPixels / 1920f);
    }

    private void toggle() {
        if (panel != null) { hide(); return; }
        if (windows == null) return;
        overlay = new FrameLayout(this);
        // One translucent grey veil across the entire display; video remains visible underneath.
        overlay.setBackgroundColor(0x995B6064);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(size(48), size(32), size(48), size(32));
        overlay.addView(panel, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextClock clock = new TextClock(this);
        boolean h24 = "24".equals(option("clock",
                android.text.format.DateFormat.is24HourFormat(this) ? "24" : "12"));
        clock.setFormat12Hour(h24 ? "HH:mm" : "h:mm a");
        clock.setFormat24Hour(h24 ? "HH:mm" : "h:mm a");
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size(112));
        clock.setTextColor(0xFFFFFFFF);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setShadowLayer(size(12), 0, size(3), 0x55000000);
        clock.setSingleLine(true);
        clock.setGravity(Gravity.CENTER);
        panel.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout conditions = new LinearLayout(this);
        conditions.setGravity(Gravity.CENTER);
        sky = new ImageView(this);
        conditions.addView(sky, new LinearLayout.LayoutParams(size(52), size(52)));
        temperature = new TextView(this);
        temperature.setText(lastTemperature == null ? "Weather loading…" : lastTemperature + "°C");
        if (lastSky != null) sky.setImageDrawable(new LineIcon(lastSky));
        temperature.setTextColor(0xEBFFFFFF);
        temperature.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size(40));
        temperature.setPadding(size(12), 0, 0, 0);
        conditions.addView(temperature);
        LinearLayout.LayoutParams weatherLayout = new LinearLayout.LayoutParams(-2, -2);
        weatherLayout.topMargin = size(18);
        panel.addView(conditions, weatherLayout);
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.LEFT;
        try { windows.addView(overlay, layout); }
        catch (RuntimeException e) {
            Log.w("OverlookOverlay", "Cannot show info overlay", e);
            overlay = null; panel = null; temperature = null; sky = null;
            return;
        }
        String location = option("location", "");
        weather.refresh(location.isEmpty() ? null : location);
        // No activity focus, input interception or audio focus: playback continues underneath.
        main.postDelayed(dismiss, 8000);
        main.postDelayed(unavailable, 7000);
    }

    private final Runnable unavailable = () -> {
        if (temperature != null && "Weather loading…".contentEquals(temperature.getText()))
            temperature.setText("Weather unavailable");
    };

    private void hide() {
        main.removeCallbacks(dismiss);
        main.removeCallbacks(unavailable);
        if (overlay != null) windows.removeView(overlay);
        overlay = null; panel = null; temperature = null; sky = null;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { hide(); heldKey = -1; }
    @Override public void onDestroy() {
        connected = false;
        restartBridge();
        getSharedPreferences("config", 0).unregisterOnSharedPreferenceChangeListener(configChanged);
        hide();
        if (registered) unregisterReceiver(preview);
        super.onDestroy();
    }
}

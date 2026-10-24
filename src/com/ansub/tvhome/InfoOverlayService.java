package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private boolean exiting;
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
        // A full-screen translucent grey veil keeps the live picture visible around the glass.
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x80535B65, 0x66323B46, 0x88545B65}));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        // Floating typography: no card, capsule or container background behind the information.
        panel.setGravity(Gravity.RIGHT);
        FrameLayout.LayoutParams information = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        information.topMargin = size(120);
        information.rightMargin = size(89);
        overlay.addView(panel, information);
        TextClock clock = new TextClock(this);
        boolean h24 = "24".equals(option("clock",
                android.text.format.DateFormat.is24HourFormat(this) ? "24" : "12"));
        clock.setFormat12Hour(h24 ? "HH:mm" : "h:mm a");
        clock.setFormat24Hour(h24 ? "HH:mm" : "h:mm a");
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size(108));
        clock.setTextColor(0xFFFFFFFF);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setFontFeatureSettings("tnum");
        clock.setShadowLayer(size(10), 0, size(2), 0x40000000);
        clock.setSingleLine(true);
        clock.setGravity(Gravity.RIGHT);
        panel.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout conditions = new LinearLayout(this);
        conditions.setGravity(Gravity.CENTER);

        sky = new ImageView(this);
        conditions.addView(sky, new LinearLayout.LayoutParams(size(38), size(38)));
        temperature = new TextView(this);
        temperature.setText(lastTemperature == null ? "Weather loading…" : lastTemperature + "°C");
        if (lastSky != null) sky.setImageDrawable(new LineIcon(lastSky));
        temperature.setTextColor(0xEBFFFFFF);
        temperature.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size(32));
        temperature.setPadding(size(12), 0, 0, 0);
        conditions.addView(temperature);
        LinearLayout.LayoutParams weatherLayout = new LinearLayout.LayoutParams(-2, -2);
        weatherLayout.topMargin = size(12);
        weatherLayout.gravity = Gravity.RIGHT;
        panel.addView(conditions, weatherLayout);
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.LEFT;
        long entranceMs = animationDuration(380);
        if (entranceMs > 0) {
            overlay.setTranslationX(getResources().getDisplayMetrics().widthPixels);
            overlay.setAlpha(.65f);
        }
        try { windows.addView(overlay, layout); }
        catch (RuntimeException e) {
            Log.w("OverlookOverlay", "Cannot show info overlay", e);
            overlay = null; panel = null; temperature = null; sky = null;
            return;
        }
        if (entranceMs > 0) overlay.animate().translationX(0f).alpha(1f)
                .setDuration(entranceMs).setInterpolator(new DecelerateInterpolator(1.8f)).withLayer().start();
        String location = option("location", "");
        weather.refresh(location.isEmpty() ? null : location);
        // No activity focus, input interception or audio focus: playback continues underneath.
        main.postDelayed(dismiss, 8000);
        main.postDelayed(unavailable, 7000);
    }

    // Android applies animator scale again; retain the intended speed and honour animations off.
    private long animationDuration(long designMs) {
        float scale = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale <= 0 ? 0 : Math.round(designMs / scale);
    }

    private final Runnable unavailable = () -> {
        if (temperature != null && "Weather loading…".contentEquals(temperature.getText()))
            temperature.setText("Weather unavailable");
    };

    private void hide() {
        main.removeCallbacks(dismiss);
        main.removeCallbacks(unavailable);
        if (overlay == null || exiting) return;
        exiting = true;
        FrameLayout departing = overlay;
        departing.animate().cancel();
        long exitMs = animationDuration(300);
        if (exitMs == 0) { removeOverlay(); return; }
        departing.animate().translationX(getResources().getDisplayMetrics().widthPixels).alpha(.65f)
                .setDuration(exitMs).setInterpolator(new AccelerateInterpolator(1.4f))
                .withLayer().withEndAction(() -> {
                    if (overlay == departing) removeOverlay();
                }).start();
    }

    // Service teardown must remove the window immediately, without a pending animation callback.
    private void removeOverlay() {
        main.removeCallbacks(dismiss);
        main.removeCallbacks(unavailable);
        if (overlay != null) {
            overlay.animate().withEndAction(null).cancel();
            windows.removeView(overlay);
        }
        overlay = null; panel = null; temperature = null; sky = null;
        exiting = false;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { removeOverlay(); heldKey = -1; }
    @Override public void onDestroy() {
        connected = false;
        restartBridge();
        getSharedPreferences("config", 0).unregisterOnSharedPreferenceChangeListener(configChanged);
        removeOverlay();
        if (registered) unregisterReceiver(preview);
        super.onDestroy();
    }
}

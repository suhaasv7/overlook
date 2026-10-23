package com.ansub.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** Optional global shortcut. Never opens an Activity or takes focus from the playing app. */
public final class InfoOverlayService extends AccessibilityService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private LinearLayout panel;
    private TextView temperature;
    private ImageView sky;
    private Weather weather;
    private Integer lastTemperature;
    private int[] lastSky;
    private int heldKey = -1;
    private boolean registered;
    private final Runnable dismiss = this::hide;
    // Shell-only preview, so testing the panel doesn't depend on the vendor's key routing.
    private final BroadcastReceiver preview = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { toggle(); }
    };

    @Override protected void onServiceConnected() {
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
        }
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
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
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(size(36), size(24), size(36), size(24));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xB820242B);
        background.setCornerRadius(size(24));
        background.setStroke(size(1), 0x40FFFFFF);
        panel.setBackground(background);
        TextClock clock = new TextClock(this);
        boolean h24 = "24".equals(option("clock",
                android.text.format.DateFormat.is24HourFormat(this) ? "24" : "12"));
        clock.setFormat12Hour(h24 ? "HH:mm" : "h:mm a");
        clock.setFormat24Hour(h24 ? "HH:mm" : "h:mm a");
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size(64));
        clock.setTextColor(0xFFFFFFFF);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        panel.addView(clock);
        LinearLayout conditions = new LinearLayout(this);
        conditions.setGravity(Gravity.CENTER_VERTICAL);
        sky = new ImageView(this);
        conditions.addView(sky, new LinearLayout.LayoutParams(size(38), size(38)));
        temperature = new TextView(this);
        temperature.setText(lastTemperature == null ? "Weather loading…" : lastTemperature + "°C");
        if (lastSky != null) sky.setImageDrawable(new LineIcon(lastSky));
        temperature.setTextColor(0xEBFFFFFF);
        temperature.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size(28));
        temperature.setPadding(size(12), 0, 0, 0);
        conditions.addView(temperature);
        panel.addView(conditions);
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                -2, -2, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.RIGHT;
        layout.x = size(70);
        layout.y = size(60);
        try { windows.addView(panel, layout); }
        catch (RuntimeException e) {
            Log.w("OverlookOverlay", "Cannot show info overlay", e);
            panel = null; temperature = null; sky = null;
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
        if (panel != null) windows.removeView(panel);
        panel = null; temperature = null; sky = null;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { hide(); heldKey = -1; }
    @Override public void onDestroy() {
        hide();
        if (registered) unregisterReceiver(preview);
        super.onDestroy();
    }
}

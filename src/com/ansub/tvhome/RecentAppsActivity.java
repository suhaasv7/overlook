package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-first recent tasks; the optional local ADB helper owns task removal. */
public final class RecentAppsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<View> openButtons = new ArrayList<>(), clearButtons = new ArrayList<>();
    private LinearLayout cards;
    private TextView subtitle, empty, clearAll, retry, hint;
    private HorizontalScrollView scroll;
    private Typeface regular, semibold;
    private float scale;
    private boolean busy;
    private int selected;
    private String selectedPackage;

    private int px(int value) { return Math.round(scale * value); }
    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        view.setTypeface(bold ? semibold : regular);
        return view;
    }
    private GradientDrawable surface(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(px(16));
        if (stroke != 0) shape.setStroke(px(3), stroke);
        return shape;
    }
    private TextView button(String title, int width) {
        TextView button = text(title, 25, true);
        button.setGravity(Gravity.CENTER); button.setFocusable(true);
        button.setPadding(px(22), 0, px(22), 0);
        button.setBackground(surface(0xFF242C38, 0));
        button.setOnFocusChangeListener((v, has) -> {
            button.setBackground(surface(has ? Color.WHITE : 0xFF242C38, 0));
            button.setTextColor(has ? 0xFF10151E : Color.WHITE);
        });
        button.setLayoutParams(new LinearLayout.LayoutParams(px(width), px(64)));
        return button;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        regular = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.ttf");
        semibold = Typeface.createFromAsset(getAssets(), "fonts/Inter-SemiBold.ttf");
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF182230, 0xFF0E141E, 0xFF080C12}));
        setContentView(root);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(96), px(110), px(96), 0);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Recent apps", 60, true), new LinearLayout.LayoutParams(0, -2, 1));
        clearAll = button("Clear all", 210);
        header.addView(clearAll);
        clearAll.setOnClickListener(v -> request("CLEAR_ALL"));
        clearAll.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusCard(selected, false); return true;
            }
            return false;
        });
        content.addView(header);
        subtitle = text("Loading recent apps…", 26, false);
        subtitle.setTextColor(0xFFC4D0DF);
        LinearLayout.LayoutParams subPosition = new LinearLayout.LayoutParams(-1, -2);
        subPosition.topMargin = px(20); content.addView(subtitle, subPosition);
        scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(px(8), px(16), px(8), px(16));
        cards = new LinearLayout(this);
        cards.setClipChildren(false); scroll.addView(cards);
        LinearLayout.LayoutParams rowPosition = new LinearLayout.LayoutParams(-1, px(440));
        rowPosition.topMargin = px(66); content.addView(scroll, rowPosition);
        empty = text("", 32, true);
        empty.setPadding(0, px(100), 0, px(32));
        content.addView(empty);
        retry = button("Retry", 180);
        retry.setOnClickListener(v -> request("LIST"));
        content.addView(retry);
        hint = text("Up: Clear all     Down: Clear app     OK: Select     Back: Close", 23, false);
        hint.setTextColor(0xFFAABBD0);
        FrameLayout.LayoutParams hintPosition = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        hintPosition.leftMargin = px(96); hintPosition.bottomMargin = px(70);
        root.addView(hint, hintPosition);
    }

    @Override protected void onResume() { super.onResume(); request("LIST"); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }

    private void request(String command) {
        if (busy) return;
        busy = true;
        if (command.startsWith("CLEAR")) InfoOverlayService.taskProgress(true);
        clearAll.setText(command.equals("CLEAR_ALL") ? "Clearing…" : "Clear all");
        subtitle.setText(command.startsWith("CLEAR") ? "Clearing recent tasks…" : "Checking recent apps…");
        worker.execute(() -> {
            JSONObject result = null;
            String error = null;
            try {
                result = new JSONObject(RecentAppsBridge.request(command));
                if (result.has("error")) error = "The TV could not update recent apps. Try again.";
            } catch (Exception e) {
                error = "Recent-apps helper is offline. Restart it over ADB after the TV restarts.";
            }
            final JSONObject response = result;
            final String failure = error;
            runOnUiThread(() -> {
                if (command.startsWith("CLEAR")) InfoOverlayService.taskProgress(false);
                if (isFinishing() || isDestroyed()) return;
                busy = false; clearAll.setText("Clear all");
                if (failure != null) {
                    subtitle.setText(failure);
                    scroll.setVisibility(View.GONE);
                    hint.setText("OK: Retry     Back: Close");
                    empty.setText("Recent apps unavailable"); empty.setVisibility(View.VISIBLE);
                    retry.setText("Retry"); retry.setOnClickListener(v -> request("LIST"));
                    retry.setVisibility(View.VISIBLE); clearAll.setVisibility(View.GONE);
                    cards.removeAllViews(); openButtons.clear(); clearButtons.clear();
                    retry.requestFocus(); return;
                }
                render(response.optJSONArray("apps"));
                if (command.startsWith("CLEAR")) {
                    int removed = response.optInt("removed"), failed = response.optInt("failed");
                    Toast.makeText(this, failed > 0 ? "Some recent tasks could not be cleared. Try again."
                            : removed > 0 ? "Recent apps cleared" : "Already cleared", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void render(JSONArray apps) {
        int count = apps == null ? 0 : apps.length();
        String previous = selectedPackage;
        cards.setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
        cards.removeAllViews(); openButtons.clear(); clearButtons.clear();
        scroll.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        hint.setText(count > 0 ? "Up: Clear all     Down: Clear app     OK: Select     Back: Close" : "OK: Home     Back: Close");
        retry.setVisibility(View.GONE); empty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        empty.setText("All clear. Your apps are ready when you need them.");
        clearAll.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        subtitle.setText(count == 0 ? "No recent apps" : count + (count == 1 ? " app" : " apps")
                + "  ·  Clear removes recent tasks and keeps app data.");
        int nextSelection = Math.min(selected, Math.max(0, count - 1));
        for (int i = 0; i < count; i++) {
            JSONObject app = apps.optJSONObject(i);
            if (app == null) continue;
            String pkg = app.optString("package");
            if (pkg.equals(previous)) nextSelection = i;
            addApp(pkg, i);
        }
        cards.setDescendantFocusability(LinearLayout.FOCUS_AFTER_DESCENDANTS);
        if (count > 0) focusCard(nextSelection, false);
        else { retry.setText("Home"); retry.setOnClickListener(v -> finish()); retry.setVisibility(View.VISIBLE); retry.requestFocus(); }
    }

    private void focusCard(int index, boolean clear) {
        List<View> buttons = clear ? clearButtons : openButtons;
        if (!buttons.isEmpty()) buttons.get(Math.max(0, Math.min(index, buttons.size() - 1))).requestFocus();
    }

    private void addApp(String pkg, int index) {
        String name = pkg;
        Drawable artwork = null;
        boolean banner = false;
        try {
            name = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString();
            artwork = getPackageManager().getApplicationBanner(pkg); banner = artwork != null;
            if (artwork == null) artwork = getPackageManager().getApplicationIcon(pkg);
        } catch (Exception ignored) { }
        final String label = name;
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true); tile.setClipToOutline(true);
        tile.setBackground(surface(0xFF202A38, 0));
        tile.setContentDescription("Open " + name);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(artwork);
        icon.setScaleType(banner ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_INSIDE);
        int inset = px(banner ? 12 : 44);
        icon.setPadding(inset, inset, inset, inset);
        tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        column.addView(tile, new LinearLayout.LayoutParams(-1, px(220)));
        TextView title = text(name, 28, true);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titlePosition = new LinearLayout.LayoutParams(-1, -2);
        titlePosition.topMargin = px(22); titlePosition.bottomMargin = px(20);
        column.addView(title, titlePosition);
        TextView clear = button("Clear app", 364);
        clear.setContentDescription("Clear " + name + " from recent apps");
        column.addView(clear);
        tile.setOnFocusChangeListener((v, has) -> {
            tile.setBackground(surface(0xFF202A38, has ? Color.WHITE : 0));
            if (has) { selected = index; selectedPackage = pkg; }
        });
        clear.setOnFocusChangeListener((v, has) -> {
            clear.setBackground(surface(has ? Color.WHITE : 0xFF242C38, 0));
            clear.setTextColor(has ? 0xFF10151E : Color.WHITE);
            if (has) { selected = index; selectedPackage = pkg; }
        });
        tile.setOnClickListener(v -> {
            if (busy) return;
            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
            try {
                if (launch == null) throw new IllegalStateException();
                launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launch); finish();
            } catch (Exception e) { Toast.makeText(this, "Cannot open " + label, Toast.LENGTH_SHORT).show(); }
        });
        clear.setOnClickListener(v -> request("CLEAR " + pkg));
        tile.setOnKeyListener((v, key, event) -> navigate(key, event, index, false));
        clear.setOnKeyListener((v, key, event) -> navigate(key, event, index, true));
        openButtons.add(tile); clearButtons.add(clear);
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(px(364), -2);
        position.rightMargin = px(36); cards.addView(column, position);
    }

    private boolean navigate(int key, KeyEvent event, int index, boolean clear) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (key == KeyEvent.KEYCODE_DPAD_UP) {
            if (clear) focusCard(index, false); else clearAll.requestFocus();
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) focusCard(index, true);
        else if (key == KeyEvent.KEYCODE_DPAD_LEFT) focusCard(index - 1, clear);
        else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) focusCard(index + 1, clear);
        else return false;
        return true;
    }
}

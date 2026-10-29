package com.ansub.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.animation.ValueAnimator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

/** Optional Android TV feed. Provider access and contributions are controlled by the TV/apps. */
final class WatchNextShelf extends LinearLayout {
    private final Activity activity;
    private final float scale;
    private final Runnable up, down, focused;
    private final TextView message, heading;
    private final LinearLayout cards;
    private int generation;
    private boolean active;
    private boolean classic;
    private final ThreadPoolExecutor artwork = new ThreadPoolExecutor(2, 2, 15, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(40), r -> {
                Thread t = new Thread(r, "overlook-resume-art"); t.setDaemon(true); return t;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final Uri FEED = Uri.parse("content://android.media.tv/watch_next_program");

    WatchNextShelf(Activity activity, float scale, Runnable up, Runnable down, Runnable focused) {
        super(activity);
        artwork.allowCoreThreadTimeOut(true);
        this.activity = activity;
        this.scale = scale;
        this.up = up;
        this.down = down;
        this.focused = focused;
        setOrientation(VERTICAL);
        heading = label("Continue watching", 36, Typeface.BOLD);
        addView(heading);
        message = label("", 22, Typeface.NORMAL);
        message.setPadding(0, px(12), 0, px(16));
        message.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 26 && activity.checkSelfPermission(
                    "android.permission.READ_TV_LISTINGS") != PackageManager.PERMISSION_GRANTED)
                activity.requestPermissions(new String[] {"android.permission.READ_TV_LISTINGS"}, 204);
        });
        message.setOnFocusChangeListener((v, has) -> {
            message.setBackground(background(has));
            if (has) focused.run();
        });
        message.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_UP) { up.run(); return true; }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) { down.run(); return true; }
            return false;
        });
        addView(message);
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(px(8), px(10), px(8), px(10));
        cards = new LinearLayout(activity);
        scroll.addView(cards);
        addView(scroll, new LayoutParams(-1, -2));
    }

    void configure(boolean classic) {
        this.classic = classic;
        heading.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(30));
    }

    private String resumeLabel(Item item) {
        String resume = item.type == 1 ? "Next episode" : item.type == 2 ? "New episode" : "Resume";
        if (item.position > 0) resume += " · " + item.position / 60000 + " min watched";
        if (item.duration > 0) resume += (item.position > 0 ? " of " : " · ") + item.duration / 60000 + " min";
        return resume;
    }

    private void open(Item item) {
        try { activity.startActivity(item.intent); }
        catch (Exception e) { Toast.makeText(activity, "This title is unavailable. Open " + item.service + " below.", Toast.LENGTH_LONG).show(); }
    }

    private int px(int value) { return Math.round(value * scale); }
    private TextView label(String value, int size, int style) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTypeface(Typeface.create("sans-serif", style));
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        return text;
    }

    void stop() { active = false; generation++; artwork.getQueue().clear(); }
    boolean focusFirst() {
        if (getVisibility() != View.VISIBLE) return false;
        if (message.isFocusable()) return message.requestFocus();
        return cards.getChildCount() > 0 && cards.getChildAt(0).requestFocus();
    }

    void refresh() {
        active = true;
        final int request = ++generation;
        boolean needsPermission = Build.VERSION.SDK_INT >= 26 && activity.checkSelfPermission(
                "android.permission.READ_TV_LISTINGS") != PackageManager.PERMISSION_GRANTED;
        message.setFocusable(needsPermission);
        message.setClickable(needsPermission);
        if (needsPermission) {
            message.setVisibility(View.VISIBLE);
            cards.removeAllViews();
            message.setText("Allow TV listings access to show Continue watching. Select here to allow.");
            return;
        }
        message.setBackground(null);
        if (cards.getChildCount() == 0) {
            message.setVisibility(View.VISIBLE);
            message.setText("Looking for recently watched videos and movies…");
        }
        new Thread(() -> {
            List<Item> items = new ArrayList<>();
            String error = null;
            if (Build.VERSION.SDK_INT < 26) {
                error = "Continue watching needs Android TV 8 or newer. Your apps are below.";
            } else {
                String[] columns = {"package_name", "title", "intent_uri", "last_playback_position_millis",
                        "duration_millis", "watch_next_type", "browsable", "poster_art_uri", "thumbnail_uri"};
                try (Cursor cursor = activity.getContentResolver().query(FEED, columns, null, null,
                        "last_engagement_time_utc_millis DESC")) {
                    Set<String> seen = new HashSet<>();
                    Map<String, Integer> shared = new LinkedHashMap<>();
                    Map<String, Integer> omitted = new LinkedHashMap<>();
                    Map<String, List<Item>> byApp = new LinkedHashMap<>();
                    if (cursor == null) error = "This TV does not provide a Continue watching feed. Open an app below.";
                    else while (cursor.moveToNext()) {
                        String owner = cursor.getString(0);
                        shared.put(owner, shared.containsKey(owner) ? shared.get(owner) + 1 : 1);
                        int type = cursor.getInt(5);
                        if (type < 0 || type > 2 || cursor.getInt(6) == 0) {
                            count(omitted, owner + ":hidden-or-watchlist"); continue;
                        }
                        String pkg = cursor.getString(0), title = cursor.getString(1), uri = cursor.getString(2);
                        if (pkg == null || title == null || uri == null || !seen.add(pkg + "\n" + uri)) continue;
                        long position = cursor.getLong(3), duration = cursor.getLong(4);
                        if (duration > 0 && position >= duration) continue;
                        try {
                            String service = activity.getPackageManager().getApplicationLabel(
                                    activity.getPackageManager().getApplicationInfo(pkg, 0)).toString();
                            Intent supplied = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                            // Keep only the playback URI, never provider-supplied actions, extras or grants.
                            Intent intent = new Intent(Intent.ACTION_VIEW, supplied.getData());
                            intent.setPackage(pkg);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            String scheme = intent.getData() == null ? null : intent.getData().getScheme();
                            if (scheme == null || "file".equalsIgnoreCase(scheme)
                                    || "content".equalsIgnoreCase(scheme)) continue;
                            // Native TV playback activities need not be browser entry points.
                            if (supplied.getComponent() != null
                                    && pkg.equals(supplied.getComponent().getPackageName()))
                                intent.setComponent(supplied.getComponent());
                            if (intent.resolveActivity(activity.getPackageManager()) == null) {
                                count(omitted, pkg + ":unresolved-link"); continue;
                            }
                            List<Item> appItems = byApp.get(pkg);
                            if (appItems == null) { appItems = new ArrayList<>(); byApp.put(pkg, appItems); }
                            if (appItems.size() < 10) appItems.add(new Item(title, service, intent, position,
                                    duration, type, cursor.getString(7), cursor.getString(8)));
                        } catch (Exception ignored) { count(omitted, pkg + ":invalid-or-missing-app"); }
                    }
                    // Interleave apps so one prolific service cannot fill the entire shelf.
                    for (int index = 0; index < 10 && items.size() < 30; index++)
                        for (List<Item> appItems : byApp.values())
                            if (index < appItems.size() && items.size() < 30) items.add(appItems.get(index));
                    android.util.Log.i("OverlookWatchNext", "Shared rows by app: " + shared
                            + "; playable cards=" + items.size() + "; omitted=" + omitted);
                } catch (SecurityException e) {
                    error = "This TV limits Continue watching to its system launcher. Open a streaming app below to resume.";
                } catch (Exception e) {
                    error = "Continue watching is unavailable. Return Home to retry, or open an app below.";
                }
            }
            final String result = error;
            activity.runOnUiThread(() -> {
                if (!active || request != generation || activity.isFinishing()) return;
                int selected = -1;
                for (int i = 0; i < cards.getChildCount(); i++)
                    if (cards.getChildAt(i).hasFocus()) selected = i;
                // Removing a focused card must not hand focus to a hidden app row.
                setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
                cards.removeAllViews();
                message.setText(result != null ? result : items.isEmpty()
                        ? "No shared resume entries available. Only apps that publish viewing activity can appear."
                        : "From apps that share activity with Android TV. Some services may not appear.");
                boolean populated = result == null && !items.isEmpty();
                message.setVisibility(populated ? View.GONE : View.VISIBLE);
                if (result == null) for (Item item : items) addCard(item);
                setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
                if (selected >= 0) {
                    if (cards.getChildCount() > 0)
                        cards.getChildAt(Math.min(selected, cards.getChildCount() - 1)).requestFocus();
                    else down.run();
                }
            });
        }, "overlook-watch-next").start();
    }

    private static void count(Map<String, Integer> counts, String key) {
        counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
    }

    private void addCard(Item item) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(VERTICAL);
        card.setPadding(px(9), px(7), px(9), px(7));
        FrameLayout imageBox = new FrameLayout(activity);
        GradientDrawable imageBackground = new GradientDrawable();
        imageBackground.setColor(0xFF20232B);
        imageBackground.setCornerRadius(px(16));
        imageBox.setBackground(imageBackground);
        imageBox.setClipToOutline(true);
        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // The owning app icon is a real fallback, never an invented thumbnail.
        try { image.setImageDrawable(activity.getPackageManager().getApplicationIcon(item.intent.getPackage())); }
        catch (Exception ignored) {}
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageBox.addView(image, new FrameLayout.LayoutParams(-1, -1));
        card.addView(imageBox, new LayoutParams(-1, px(classic ? 180 : 198)));
        final int request = generation;
        artwork.execute(() -> {
            Bitmap result = ResumeArtwork.load(activity.getApplicationContext(), item.thumbnail);
            if (result == null) result = ResumeArtwork.load(activity.getApplicationContext(), item.poster);
            final Bitmap bitmap = result;
            if (bitmap != null) activity.runOnUiThread(() -> {
                if (!active || request != generation || activity.isFinishing()) return;
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(bitmap);
            });
        });
        TextView service = label(item.service, 18, Typeface.NORMAL);
        service.setSingleLine(true);
        service.setEllipsize(TextUtils.TruncateAt.END);
        service.setPadding(0, px(5), 0, 0);
        card.addView(service);
        TextView title = label(item.title, classic ? 22 : 24, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams titlePosition = new LayoutParams(-1, px(30));
        titlePosition.topMargin = px(3);
        card.addView(title, titlePosition);
        String resume = resumeLabel(item);
        TextView detail = label(resume, 18, Typeface.NORMAL);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        // Playback position remains in the accessibility description; keep the visual row quiet.
        detail.setVisibility(View.GONE);
        card.addView(detail);
        card.setFocusable(true);
        card.setContentDescription(item.title + ", " + item.service + ", " + resume);
        card.setBackground(background(false));
        card.setOnFocusChangeListener((view, has) -> {
            card.setBackground(background(has));
            float zoom = has ? 1.035f : 1f;
            float animationScale = Build.VERSION.SDK_INT >= 26 ? ValueAnimator.getDurationScale() : 1;
            card.animate().scaleX(zoom).scaleY(zoom).translationZ(has ? px(10) : 0)
                    .setDuration(animationScale == 0 ? 0 : 180).start();
            if (has) focused.run();
        });
        card.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_UP) {
                up.run();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_DOWN) { down.run(); return true; }
            return (key == KeyEvent.KEYCODE_DPAD_LEFT && cards.indexOfChild(v) == 0)
                    || (key == KeyEvent.KEYCODE_DPAD_RIGHT && cards.indexOfChild(v) == cards.getChildCount() - 1);
        });
        card.setOnClickListener(v -> open(item));
        LayoutParams position = new LayoutParams(px(classic ? 340 : 376),
                px(classic ? 260 : 280));
        position.rightMargin = px(22);
        cards.addView(card, position);
    }

    private GradientDrawable background(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(focused ? 0x192A303D : 0x00000000);
        shape.setCornerRadius(px(16));
        shape.setStroke(px(focused ? 4 : 1), focused ? Color.WHITE : 0x00000000);
        return shape;
    }

    private static final class Item {
        final String title, service, poster, thumbnail;
        final Intent intent;
        final long position, duration;
        final int type;
        Item(String title, String service, Intent intent, long position, long duration, int type, String poster, String thumbnail) {
            this.title = title; this.service = service; this.intent = intent;
            this.position = position; this.duration = duration; this.type = type;
            this.poster = poster; this.thumbnail = thumbnail;
        }
    }
}

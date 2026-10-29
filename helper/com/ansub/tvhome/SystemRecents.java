package com.ansub.tvhome;

import android.app.UiAutomation;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.util.HashSet;
import java.util.Set;

/** Clicks the system's actual Dismiss controls, without suppressing accessibility services. */
final class SystemRecents implements AutoCloseable {
    private final HandlerThread thread;
    private final UiAutomation ui;
    SystemRecents() throws Exception {
        thread = new HandlerThread("overlook-system-recents"); thread.start();
        ui = (UiAutomation) UiAutomation.class.getConstructor(Looper.class,
                Class.forName("android.app.IUiAutomationConnection")).newInstance(thread.getLooper(),
                Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance());
        try {
            UiAutomation.class.getMethod("connect", int.class).invoke(ui, 1);
            AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.packageNames = new String[] {"com.android.systemui"};
            ui.setServiceInfo(info);
        } catch (Exception e) {
            try { UiAutomation.class.getMethod("disconnect").invoke(ui); } catch (Exception ignored) { }
            thread.quitSafely(); throw e;
        }
    }

    void open() throws Exception {
        long at = SystemClock.uptimeMillis();
        if (!ui.injectInputEvent(new KeyEvent(at, at, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_APP_SWITCH, 0), true)
                || !ui.injectInputEvent(new KeyEvent(at, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_APP_SWITCH, 0), true)) throw new IllegalStateException("Cannot open system Recents");
        for (int i = 0; i < 40; i++) {
            AccessibilityNodeInfo root = root();
            if (root != null) { root.recycle(); return; }
            Thread.sleep(100);
        }
        throw new IllegalStateException("System Recents unavailable");
    }

    private AccessibilityNodeInfo root() {
        for (AccessibilityWindowInfo window : ui.getWindows()) {
            AccessibilityNodeInfo node = window.getRoot();
            if (node == null) continue;
            if ("com.android.systemui".contentEquals(node.getPackageName() == null ? "" : node.getPackageName())) {
                java.util.List<AccessibilityNodeInfo> matches = node.findAccessibilityNodeInfosByViewId("com.android.systemui:id/recents_view");
                boolean recents = !matches.isEmpty();
                for (AccessibilityNodeInfo match : matches) match.recycle();
                if (recents) return node;
            }
            node.recycle();
        }
        return null;
    }

    /** Exact task-title description matches only. Never guesses from screen coordinates. */
    boolean dismissOne(Set<String> titles, long deadline) throws Exception {
        Set<String> seen = new HashSet<>();
        for (int attempt = 0; attempt < 45 && SystemClock.uptimeMillis() < deadline; attempt++) {
            AccessibilityNodeInfo root = root();
            if (root == null) return false;
            try {
                java.util.List<AccessibilityNodeInfo> labels = root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/title");
                AccessibilityNodeInfo target = null;
                StringBuilder page = new StringBuilder();
                for (AccessibilityNodeInfo label : labels) {
                    String description = String.valueOf(label.getContentDescription());
                    page.append(description).append('\n');
                    if (target == null && titles.contains(description)) {
                        AccessibilityNodeInfo bar = label.getParent();
                        if (bar != null) {
                            java.util.List<AccessibilityNodeInfo> closes = bar.findAccessibilityNodeInfosByViewId("com.android.systemui:id/dismiss_task");
                            for (AccessibilityNodeInfo close : closes) {
                                if (target == null && close.isVisibleToUser()) target = AccessibilityNodeInfo.obtain(close);
                                close.recycle();
                            }
                            bar.recycle();
                        }
                    }
                    label.recycle();
                }
                if (target != null) {
                    boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    target.recycle();
                    if (clicked) Thread.sleep(180);
                    return clicked;
                }
                if (!seen.add(page.toString())) return false;
                if (!scroll(root)) return false;
            } finally { root.recycle(); }
            Thread.sleep(120);
        }
        return false;
    }

    private boolean scroll(AccessibilityNodeInfo node) {
        // Recents initially shows the newest tasks at the bottom; older tasks are above them.
        if (node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean moved = scroll(child); child.recycle(); if (moved) return true;
        }
        return false;
    }

    @Override public void close() throws Exception {
        try { UiAutomation.class.getMethod("disconnect").invoke(ui); }
        finally { thread.quitSafely(); }
    }
}

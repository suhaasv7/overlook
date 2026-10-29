package com.ansub.tvhome;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ADB-started Android 9 adapter. No network port, shell execution, force-stop or data deletion. */
public final class RecentsHelper {
    private static final String TOKEN = "com.ansub.tvhome.RECENTS_V1";
    private static final String APP = "com.ansub.tvhome";
    private final Object manager;
    private final Method getTasks;
    private final Class<?> api;
    private final int user;
    private static android.content.Context context;
    private volatile boolean running = true;
    private volatile CountDownLatch appDied;

    private RecentsHelper(int user) throws Exception {
        this.user = user;
        manager = ActivityManager.class.getDeclaredMethod("getService").invoke(null);
        api = Class.forName("android.app.IActivityManager");
        getTasks = api.getMethod("getRecentTasks", int.class, int.class, int.class);
    }

    private int appUid() throws Exception {
        Object pm = Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
        return (Integer) Class.forName("android.content.pm.IPackageManager")
                .getMethod("getPackageUid", String.class, int.class, int.class).invoke(pm, APP, 0, user);
    }

    @SuppressWarnings("unchecked") private List<ActivityManager.RecentTaskInfo> tasks() throws Exception {
        Object slice = getTasks.invoke(manager, 80, 2, user);
        List<ActivityManager.RecentTaskInfo> all = (List<ActivityManager.RecentTaskInfo>)
                slice.getClass().getMethod("getList").invoke(slice);
        List<ActivityManager.RecentTaskInfo> eligible = new ArrayList<>();
        for (ActivityManager.RecentTaskInfo task : all) {
            String pkg = owner(task);
            if (pkg == null || protectedPackage(pkg) || task.persistentId < 0) continue;
            if (task.baseIntent != null && task.baseIntent.hasCategory(Intent.CATEGORY_HOME)) continue;
            eligible.add(task);
        }
        return eligible;
    }

    private static String owner(ActivityManager.RecentTaskInfo task) {
        ComponentName component = task.baseIntent == null ? null : task.baseIntent.getComponent();
        if (component == null) component = task.origActivity;
        return component == null ? null : component.getPackageName();
    }

    private static boolean protectedPackage(String pkg) {
        // Preserve Home, System UI and the VPN carrying the remote TV connection.
        return APP.equals(pkg) || "com.android.systemui".equals(pkg)
                || "com.tailscale.ipn".equals(pkg) || "android".equals(pkg);
    }

    private synchronized JSONObject response(String command) throws Exception {
        int removed = 0, failed = 0;
        if (!"LIST".equals(command)) {
            String pkg = command.startsWith("CLEAR ") ? command.substring(6) : null;
            if (!"CLEAR_ALL".equals(command) && (pkg == null || !pkg.matches("[a-zA-Z0-9_.]+")))
                throw new IllegalArgumentException("Unsupported command");
            List<ActivityManager.RecentTaskInfo> selected = new ArrayList<>();
            for (ActivityManager.RecentTaskInfo task : tasks())
                if (pkg == null || pkg.equals(owner(task))) selected.add(task);
            if (!selected.isEmpty()) {
                Object focused = api.getMethod("getFocusedStackInfo").invoke(manager);
                int originalStack = focused.getClass().getField("stackId").getInt(focused);
                try (SystemRecents recents = new SystemRecents()) {
                    recents.open();
                    long deadline = android.os.SystemClock.uptimeMillis() + 25000;
                    for (ActivityManager.RecentTaskInfo task : selected) {
                        // One package can own several tasks with the same accessible title.
                        // Keep dismissing that package until this selected task is actually gone.
                        while (hasTask(task.persistentId) && android.os.SystemClock.uptimeMillis() < deadline) {
                            int before = appTaskCount(owner(task));
                            if (!recents.dismissOne(titles(task), deadline)) break;
                            for (int poll = 0; poll < 25 && android.os.SystemClock.uptimeMillis() < deadline
                                    && appTaskCount(owner(task)) >= before; poll++) Thread.sleep(100);
                            if (appTaskCount(owner(task)) >= before) break;
                        }
                    }
                } finally {
                    api.getMethod("setFocusedStack", int.class).invoke(manager, originalStack);
                }
                for (ActivityManager.RecentTaskInfo task : selected) {
                    if (hasTask(task.persistentId)) failed++; else removed++;
                }
            }
        }
        if (!"LIST".equals(command)) System.err.println("Clear completed: removed=" + removed + "; failed=" + failed);
        Map<String, Integer> packages = new LinkedHashMap<>();
        for (ActivityManager.RecentTaskInfo task : tasks()) {
            String pkg = owner(task);
            packages.put(pkg, packages.containsKey(pkg) ? packages.get(pkg) + 1 : 1);
        }
        JSONArray apps = new JSONArray();
        for (Map.Entry<String, Integer> entry : packages.entrySet())
            apps.put(new JSONObject().put("package", entry.getKey()).put("tasks", entry.getValue()));
        return new JSONObject().put("apps", apps).put("removed", removed).put("failed", failed);
    }

    private int appTaskCount(String pkg) throws Exception {
        int count = 0;
        for (ActivityManager.RecentTaskInfo task : tasks()) if (pkg.equals(owner(task))) count++;
        return count;
    }

    private boolean hasTask(int id) throws Exception {
        for (ActivityManager.RecentTaskInfo task : tasks()) if (task.persistentId == id) return true;
        return false;
    }

    private java.util.Set<String> titles(ActivityManager.RecentTaskInfo task) throws Exception {
        android.content.pm.PackageManager pm = context.getPackageManager();
        String label = pm.getApplicationLabel(pm.getApplicationInfo(owner(task), 0)).toString();
        for (ActivityManager.RecentTaskInfo other : tasks()) {
            String otherPackage = owner(other);
            if (!owner(task).equals(otherPackage) && label.equals(pm.getApplicationLabel(pm.getApplicationInfo(otherPackage, 0)).toString()))
                throw new IllegalStateException("Ambiguous app labels; preserving tasks");
        }
        String title = task.taskDescription == null ? null : task.taskDescription.getLabel();
        if (title == null && task.baseIntent != null) {
            android.content.pm.ActivityInfo info = task.baseIntent.resolveActivityInfo(pm, 0);
            if (info != null) title = info.loadLabel(pm).toString();
        }
        java.util.Set<String> descriptions = new java.util.HashSet<>();
        descriptions.add(label);
        if (title != null) descriptions.add(label + " " + title);
        if ("Home".equals(label) || "Tailscale".equals(label))
            throw new IllegalStateException("Ambiguous protected app label");
        return descriptions;
    }

    private final Binder service = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            int uid = Binder.getCallingUid();
            try {
                if (uid != 2000 && uid != appUid()) throw new SecurityException("Overlook only");
                data.enforceInterface(TOKEN);
                if (code == 2 && uid == 2000) {
                    reply.writeNoException();
                    running = false;
                    CountDownLatch latch = appDied;
                    if (latch != null) latch.countDown();
                    return true;
                }
                if (code != 1) return false;
                String command = data.readString();
                if (command == null || command.length() > 256) throw new IllegalArgumentException();
                long identity = Binder.clearCallingIdentity();
                JSONObject result;
                try { result = response(command); }
                catch (Exception e) { System.err.println("Task operation failed: " + (e.getCause() == null ? e : e.getCause())); result = new JSONObject().put("error", "Recent apps operation failed"); }
                finally { Binder.restoreCallingIdentity(identity); }
                reply.writeNoException(); reply.writeString(result.toString()); return true;
            } catch (Exception e) { reply.writeException(e); return true; }
        }
    };

    private static Bundle providerCall(String method, Bundle extras, int user) throws Exception {
        Object manager = ActivityManager.class.getDeclaredMethod("getService").invoke(null);
        Class<?> api = Class.forName("android.app.IActivityManager");
        Binder token = new Binder();
        Object holder = api.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class)
                .invoke(manager, "com.ansub.tvhome.recents", user, token);
        if (holder == null) throw new IllegalStateException("Overlook provider unavailable");
        try {
            Object provider = holder.getClass().getField("provider").get(holder);
            return (Bundle) Class.forName("android.content.IContentProvider")
                    .getMethod("call", String.class, String.class, String.class, Bundle.class)
                    .invoke(provider, "com.android.shell", method, null, extras);
        } finally {
            api.getMethod("removeContentProviderExternal", String.class, IBinder.class)
                    .invoke(manager, "com.ansub.tvhome.recents", token);
        }
    }

    private void serve() throws Exception {
        boolean reported = false;
        while (running) {
            try {
                Bundle extras = new Bundle(); extras.putBinder("service", service);
                Bundle result = providerCall("attach", extras, user);
                IBinder life = result == null ? null : result.getBinder("life");
                if (life == null) throw new IllegalStateException("App unavailable");
                reported = false;
                CountDownLatch died = new CountDownLatch(1);
                appDied = died;
                life.linkToDeath(died::countDown, 0);
                died.await();
            } catch (Exception e) {
                if (!reported) System.err.println("Waiting for Overlook recent-apps provider: " + e.toString());
                reported = true;
                if (running) Thread.sleep(1500);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Object thread = Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        context = (android.content.Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
        int user = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        if (args.length > 0 && "stop".equals(args[0])) {
            try {
                Bundle result = providerCall("get", null, user);
                IBinder binder = result == null ? null : result.getBinder("service");
                if (binder != null) {
                    Parcel data = Parcel.obtain(), reply = Parcel.obtain();
                    try { data.writeInterfaceToken(TOKEN); binder.transact(2, data, reply, 0); }
                    finally { data.recycle(); reply.recycle(); }
                }
            } catch (Exception ignored) { }
            System.exit(0);
        }
        RecentsHelper helper = new RecentsHelper(user);
        if (args.length > 0 && "probe".equals(args[0])) System.out.println(helper.response("LIST"));
        else helper.serve();
        System.exit(0);
    }
}

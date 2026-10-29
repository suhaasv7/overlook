package com.ansub.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** ADB may attach the narrowly scoped task helper; other apps cannot retrieve its Binder. */
public final class RecentAppsProvider extends ContentProvider {
    private final Binder life = new Binder();
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != 2000 && uid != android.os.Process.myUid()) throw new SecurityException("Shell or Overlook only");
        if ("attach".equals(method)) {
            if (uid != 2000) throw new SecurityException("Shell attachment only");
            RecentAppsBridge.service = extras == null ? null : extras.getBinder("service");
            Bundle result = new Bundle(); result.putBinder("life", life); return result;
        }
        if ("get".equals(method)) {
            Bundle result = new Bundle(); result.putBinder("service", RecentAppsBridge.service); return result;
        }
        throw new IllegalArgumentException("Unsupported method");
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw new UnsupportedOperationException(); }
}

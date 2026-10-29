package com.ansub.tvhome;

import android.os.IBinder;
import android.os.Parcel;

final class RecentAppsBridge {
    static volatile IBinder service;
    static String request(String command) throws Exception {
        IBinder binder = service;
        // A helper or app update briefly replaces the Binder. Wait off the UI thread for attachment.
        for (int retry = 0; retry < 20 && (binder == null || !binder.isBinderAlive()); retry++) {
            Thread.sleep(150);
            binder = service;
        }
        if (binder == null || !binder.isBinderAlive()) throw new IllegalStateException("Helper offline");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("com.ansub.tvhome.RECENTS_V1"); data.writeString(command);
            if (!binder.transact(1, data, reply, 0)) throw new IllegalStateException("Helper incompatible");
            reply.readException();
            return reply.readString();
        } finally { data.recycle(); reply.recycle(); }
    }
}

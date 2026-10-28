package com.ansub.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small, bounded artwork decoder. Called only by the shelf's background workers. */
final class ResumeArtwork {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    static Bitmap load(Context context, String address) {
        if (address == null || address.isEmpty()) return null;
        Bitmap cached = CACHE.get(address);
        if (cached != null) return cached;
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse(address);
            String scheme = uri.getScheme();
            InputStream stream;
            if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                if (connection.getContentLengthLong() > MAX_BYTES) return null;
                stream = connection.getInputStream();
            } else if ("content".equals(scheme) || "android.resource".equals(scheme)) {
                stream = context.getContentResolver().openInputStream(uri);
            } else return null;
            if (stream == null) return null;
            byte[] bytes;
            try (InputStream input = stream; ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = input.read(chunk)) != -1) {
                    if (data.size() + count > MAX_BYTES) return null;
                    data.write(chunk, 0, count);
                }
                bytes = data.toByteArray();
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 768
                    || options.outHeight / options.inSampleSize > 432) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap != null) CACHE.put(address, bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null; // The title and app remain available if artwork cannot load.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

package app.revanced.extension.music.stats;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Loads artwork thumbnails for the report: a small in-memory cache, a disk cache in
 * the app cache directory, and a background download. Images are decoded downscaled
 * so a long report never holds full-size bitmaps.
 */
final class Thumbnails {
    private Thumbnails() {}

    private static final int MAX_DIMENSION = 320;
    private static final LruCache<String, Bitmap> MEMORY =
            new LruCache<String, Bitmap>(6 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    /** Shows the artwork for {@code url} in {@code view}, if there is one. */
    static void load(ImageView view, String url) {
        load(view, url, null);
    }

    /** Loads artwork and hands the bitmap to the callback as well. */
    static void load(ImageView view, String url, Callback callback) {
        if (url == null || url.isEmpty()) return;
        if (url.contains("//1000x1000") || url.endsWith("/")) return; // malformed

        view.setTag(url);

        Bitmap cached = MEMORY.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            if (callback != null) callback.onBitmap(cached);
            return;
        }

        final String target = url;
        final android.widget.ImageView targetView = view;
        EXECUTOR.execute(() -> {
            // Artwork captured from the player is a local file: decode it directly.
            Bitmap bitmap = ArtworkStore.isLocal(target) ? ArtworkStore.read(target) : null;
            if (bitmap == null && !ArtworkStore.isLocal(target)) {
                bitmap = readFromDisk(target);
                if (bitmap == null) {
                    bitmap = download(target);
                    if (bitmap != null) writeToDisk(target, bitmap);
                }
            }
            if (bitmap == null) return;
            MEMORY.put(target, bitmap);
            final Bitmap result = bitmap;
            Utils.runOnMainThread(() -> {
                if (targetView.getTag() != null && target.equals(targetView.getTag())) {
                    targetView.setImageBitmap(result);
                }
                if (callback != null) callback.onBitmap(result);
            });
        });
    }

    interface Callback {
        void onBitmap(Bitmap bitmap);
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "ReVanced-Wrapped/1.0");
            connection.setRequestProperty("Accept", "image/*");
            if (connection.getResponseCode() / 100 != 2) return null;

            try (InputStream in = connection.getInputStream()) {
                byte[] data = readAll(in);
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                return scale(bitmap);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Thumbnail download failed", ex);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static Bitmap scale(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= MAX_DIMENSION) return bitmap;
        float factor = (float) MAX_DIMENSION / largest;
        return Bitmap.createScaledBitmap(bitmap,
                Math.round(width * factor), Math.round(height * factor), true);
    }

    // --- disk cache ---

    private static File cacheFile(String url) {
        File dir = new File(Utils.getContext().getCacheDir(), "thumbs");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, hash(url) + ".jpg");
    }

    private static String hash(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(url.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception ex) {
            return String.valueOf(url.hashCode());
        }
    }

    private static Bitmap readFromDisk(String url) {
        File file = cacheFile(url);
        if (!file.exists() || file.length() == 0) return null;
        try {
            return scale(BitmapFactory.decodeFile(file.getAbsolutePath()));
        } catch (Exception ex) {
            return null;
        }
    }

    private static void writeToDisk(String url, Bitmap bitmap) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(url))) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
        } catch (Exception ex) {
            Logger.printException(() -> "Could not cache thumbnail", ex);
        }
    }
}

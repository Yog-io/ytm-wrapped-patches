package app.revanced.extension.music.stats;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Stores the artwork YouTube Music already hands us for the playing track.
 *
 * <p>The media-session metadata carries the real cover as a Bitmap, so the exact
 * image is saved to the app cache and remembered by path. No network, no name
 * matching, nothing to guess.
 */
final class ArtworkStore {
    private ArtworkStore() {}

    private static final int MAX_DIMENSION = 320;

    /** Saves a bitmap and returns its file path, or null on failure. */
    static String save(Bitmap bitmap, String key) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        try {
            File dir = new File(Utils.getContext().getCacheDir(), "art");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, hash(key) + ".jpg");
            Bitmap scaled = scale(bitmap);
            try (FileOutputStream out = new FileOutputStream(file)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            return file.getAbsolutePath();
        } catch (Exception ex) {
            Logger.printInfo(() -> "Could not store artwork: " + ex.getMessage());
            return null;
        }
    }

    /** Reads a saved artwork file. */
    static Bitmap read(String path) {
        try {
            File file = new File(path);
            if (!file.exists() || file.length() == 0) return null;
            return BitmapFactory.decodeFile(path);
        } catch (Exception ex) {
            return null;
        }
    }

    /** True when a stored value is an image file written by this app. */
    static boolean isLocal(String value) {
        return value != null && value.startsWith("/");
    }

    private static Bitmap scale(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= MAX_DIMENSION) return bitmap;
        float factor = (float) MAX_DIMENSION / largest;
        return Bitmap.createScaledBitmap(bitmap,
                Math.round(width * factor), Math.round(height * factor), true);
    }

    private static String hash(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(key.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 10; i++) builder.append(String.format("%02x", bytes[i]));
            return builder.toString();
        } catch (Exception ex) {
            return String.valueOf(key.hashCode());
        }
    }
}

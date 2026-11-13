package app.revanced.extension.music.stats;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts the dominant colours of an artwork, the way Apple Music Replay does:
 * a handful of colours sampled from the cover, then used to build the moving
 * mesh gradient behind every screen.
 */
final class Palette {
    private Palette() {}

    /** The sampled colours, most prominent first. */
    static List<Integer> fromBitmap(Bitmap bitmap) {
        List<Integer> colors = new ArrayList<>();
        if (bitmap == null || bitmap.isRecycled()) return colors;

        int size = 24;
        Bitmap small = Bitmap.createScaledBitmap(bitmap, size, size, true);
        try {
            Map<Integer, int[]> buckets = new HashMap<>();
            int[] pixels = new int[size * size];
            small.getPixels(pixels, 0, size, 0, 0, size, size);

            for (int pixel : pixels) {
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                // Ignore near-black and near-white pixels: they carry no hue.
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                if (max < 40 || min > 225) continue;

                int key = ((r >> 5) << 6) | ((g >> 5) << 3) | (b >> 5);
                int[] bucket = buckets.get(key);
                if (bucket == null) {
                    bucket = new int[4];
                    buckets.put(key, bucket);
                }
                bucket[0] += r;
                bucket[1] += g;
                bucket[2] += b;
                bucket[3]++;
            }

            buckets.entrySet().stream()
                    .sorted((a, b) -> b.getValue()[3] - a.getValue()[3])
                    .limit(4)
                    .forEach(entry -> {
                        int[] bucket = entry.getValue();
                        int count = bucket[3];
                        colors.add(Color.rgb(bucket[0] / count, bucket[1] / count, bucket[2] / count));
                    });
        } catch (Exception ex) {
            // fall through: an empty palette is fine
        } finally {
            if (small != bitmap) small.recycle();
        }
        return colors;
    }

    /** A colour pushed towards black, for gradient floors. */
    static int darken(int color, float factor) {
        return Color.rgb(
                Math.round(Color.red(color) * factor),
                Math.round(Color.green(color) * factor),
                Math.round(Color.blue(color) * factor));
    }

    /** A colour pushed towards white, for highlights. */
    static int lighten(int color, float factor) {
        return Color.rgb(
                Math.round(Color.red(color) + (255 - Color.red(color)) * factor),
                Math.round(Color.green(color) + (255 - Color.green(color)) * factor),
                Math.round(Color.blue(color) + (255 - Color.blue(color)) * factor));
    }
}

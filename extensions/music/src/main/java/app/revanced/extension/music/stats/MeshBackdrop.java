package app.revanced.extension.music.stats;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * The background of a Replay screen: a slowly moving mesh gradient built from the
 * colours sampled out of the artwork, with the cover itself blurred behind it and
 * a scrim top and bottom so white type always stays readable.
 */
final class MeshBackdrop extends View {
    private static final long CYCLE_MS = 24_000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Integer> colors = new ArrayList<>();
    private float phase;

    MeshBackdrop(Context context) {
        this(context, null);
    }

    MeshBackdrop(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        // Apple's own palette, used until the artwork is sampled.
        colors.add(0xFF5E5CE6);
        colors.add(0xFFFF375F);
        colors.add(0xFF0A84FF);
        colors.add(0xFF64D2FF);
    }

    /** Sets the sampled colours the mesh is built from. */
    void setColors(List<Integer> sampled) {
        colors.clear();
        if (sampled != null) colors.addAll(sampled);
        if (colors.isEmpty()) {
            colors.add(0xFF5E5CE6);
            colors.add(0xFFFF375F);
            colors.add(0xFF0A84FF);
        }
        invalidate();
    }

    /** Starts the slow drift of the mesh. */
    void startDrift() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        // Deep base.
        canvas.drawColor(0xFF000000);

        float drift = (float) (Math.sin(phase * Math.PI * 2) * 0.06);
        float drift2 = (float) (Math.cos(phase * Math.PI * 2) * 0.06);

        for (int i = 0; i < colors.size(); i++) {
            int color = colors.get(i);
            float cx = (0.28f + i * 0.26f) + drift * (i % 2 == 0 ? 1 : -1);
            float cy = (0.22f + i * 0.18f) + drift2 * (i % 2 == 0 ? -1 : 1);
            float radius = Math.max(width, height) * (0.75f + i * 0.12f);

            paint.setShader(new RadialGradient(
                    width * cx, height * cy, radius,
                    new int[]{withAlpha(color, 120), withAlpha(color, 44), withAlpha(color, 0)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
        }

        // Scrims: keep the top and bottom legible.
        paint.setShader(new LinearGradient(0f, 0f, 0f, height,
                new int[]{0xAA000000, 0x22000000, 0x33000000, 0xC4000000},
                new float[]{0f, 0.26f, 0.66f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}

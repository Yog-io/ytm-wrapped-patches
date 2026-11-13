package app.revanced.extension.music.stats;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The Wrapped report body.
 *
 * <p>Modelled on Apple Music Replay: a single dark canvas that flows through
 * sections, artwork-style tiles for every artist and song, large figures for the
 * headline numbers, and a quiet grey voice for everything else.
 */
final class WrappedStory {
    private WrappedStory() {}

    static final int BG = 0xFF000000;
    static final int SURFACE = 0xF21C1C1E;
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT2 = 0xFF98989D;
    static final int TEXT3 = 0xFF6E6E73;

    // Apple's system palette.
    static final int RED = 0xFFFF453A;
    static final int ORANGE = 0xFFFF9F0A;
    static final int YELLOW = 0xFFFFD60A;
    static final int GREEN = 0xFF32D74B;
    static final int MINT = 0xFF63E6E2;
    static final int TEAL = 0xFF40C8E0;
    static final int CYAN = 0xFF64D2FF;
    static final int BLUE = 0xFF0A84FF;
    static final int INDIGO = 0xFF5E5CE6;
    static final int PURPLE = 0xFFBF5AF2;
    static final int PINK = 0xFFFF375F;
    static final int BROWN = 0xFFAC8E68;

    /**
     * Order used to tint artwork. Green sits last so it only ever shows up as one
     * tile among many, never as the voice of the whole report.
     */
    static final int[] ACCENTS = {
            INDIGO, PINK, PURPLE, BLUE, CYAN, TEAL, MINT, ORANGE, YELLOW, RED, GREEN, BROWN
    };

    static final String[] DAY_NAMES = {
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    };

    /** Builds the report as a list of full-screen cards. */
    static java.util.List<View> buildCards(Activity activity, WrappedReport.Data data) {
        Context context = activity;
        java.util.List<View> cards = new java.util.ArrayList<>();
        cards.add(WrappedSections.heroCard(context, data));
        cards.add(WrappedSections.artistsCard(context, data));
        cards.add(WrappedSections.songsCard(context, data));
        cards.add(WrappedSections.statsCard(context, data));
        cards.add(WrappedSections.clockCard(context, data));
        cards.add(WrappedSections.weekCard(context, data));
        cards.add(WrappedSections.outroCard(context, data));
        return cards;
    }

    // --------------------------------------------------------------- helpers

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static int shade(int color, float factor) {
        int r = (int) Math.min(255, ((color >> 16) & 0xFF) * factor);
        int g = (int) Math.min(255, ((color >> 8) & 0xFF) * factor);
        int b = (int) Math.min(255, (color & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int blend(int from, int to, float amount) {
        float a = 1f - amount;
        int r = (int) (((from >> 16) & 0xFF) * a + ((to >> 16) & 0xFF) * amount);
        int g = (int) (((from >> 8) & 0xFF) * a + ((to >> 8) & 0xFF) * amount);
        int b = (int) ((from & 0xFF) * a + (to & 0xFF) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Stable accent for a name, so every artist or song keeps its own colour. */
    static int tintFor(String name) {
        int hash = 0;
        if (name != null) {
            for (int i = 0; i < name.length(); i++) hash = hash * 31 + name.charAt(i);
        }
        return ACCENTS[Math.abs(hash) % ACCENTS.length];
    }

    static TextView text(Context context, String value, float sp, int color, Typeface face) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTypeface(face);
        view.setIncludeFontPadding(false);
        return view;
    }

    static TextView title(Context context, String value) {
        return text(context, value, 30, TEXT, Typeface.create("sans-serif", Typeface.BOLD));
    }

    static TextView sub(Context context, String value) {
        return text(context, value, 17, TEXT2, Typeface.create("sans-serif", Typeface.NORMAL));
    }

    static TextView caption(Context context, String value) {
        return text(context, value, 13, TEXT2, Typeface.create("sans-serif", Typeface.NORMAL));
    }

    static TextView big(Context context, String value, float sp) {
        return text(context, value, sp, TEXT, Typeface.create("sans-serif", Typeface.BOLD));
    }

    static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable glass(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), 0x1AFFFFFF);
        return drawable;
    }

    /** Artwork stand-in: a saturated gradient tile with the artwork's shape. */
    /**
     * Frosted glass: a translucent sheet with a soft top highlight and a hairline
     * edge. The aurora backdrop shows through, which is what makes it read as
     * glass rather than a flat card.
     */
    static GradientDrawable frost(Context context, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x30FFFFFF, 0x12FFFFFF, 0x1AFFFFFF});
        drawable.setCornerRadius(dp(context, radiusDp));
        drawable.setStroke(dp(context, 1), 0x3DFFFFFF);
        return drawable;
    }

    static GradientDrawable artwork(Context context, int accent, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{blend(accent, 0xFFFFFFFF, 0.20f), shade(accent, 0.55f)});
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static LinearLayout surface(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(frost(context, 22));
        view.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        return view;
    }

    /** Fixed-width gap for horizontal rows. */
    static View gap(Context context, int widthDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                dp(context, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    static View space(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        return view;
    }

    static View meter(Context context, int accent, float share) {
        LinearLayout host = new LinearLayout(context);
        host.setOrientation(LinearLayout.HORIZONTAL);
        host.setBackground(rounded(context, 0x24FFFFFF, 999));

        int fill = Math.max(1, Math.round(Math.min(1f, share) * 1000));
        View fillView = new View(context);
        GradientDrawable fillDrawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{blend(accent, 0xFFFFFFFF, 0.22f), accent});
        fillDrawable.setCornerRadius(dp(context, 999));
        fillView.setBackground(fillDrawable);
        host.addView(fillView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, fill));
        host.addView(new View(context), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, 1000 - fill)));
        return host;
    }

    /**
     * The hero panel is glass too: a tinted, translucent sheet with a lit top edge
     * and a hairline outline, so the blurred colour behind it shows through.
     */
    static GradientDrawable heroGradient(Context context, int accent) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(blend(accent, 0xFFFFFFFF, 0.22f), 0x5C),
                        withAlpha(accent, 0x30),
                        withAlpha(shade(accent, 0.6f), 0x26)});
        drawable.setCornerRadius(dp(context, 24));
        drawable.setStroke(dp(context, 1), 0x40FFFFFF);
        return drawable;
    }

    /** A soft coloured glow painted behind a panel. */
    static GradientDrawable glow(Context context, int accent, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(accent, 0x66), withAlpha(accent, 0x0D)});
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static String hourLabel(int hour) {
        String suffix = hour < 12 ? "AM" : "PM";
        int display = hour % 12;
        if (display == 0) display = 12;
        return display + " " + suffix;
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * The canvas backdrop: a deep tinted wash with two out-of-focus colour blobs,
     * the way blurred artwork bleeds behind Apple's Replay.
     */
    static final class AuroraDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint();
        private final int primary;
        private final int secondary;
        private final int tertiary;

        AuroraDrawable(int primary, int secondary, int tertiary) {
            this.primary = primary;
            this.secondary = secondary;
            this.tertiary = tertiary;
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float width = Math.max(bounds.width(), 800);
            float height = Math.max(bounds.height(), 800);

            paint.setShader(new LinearGradient(0f, 0f, width * 0.3f, height,
                    new int[]{shade(primary, 0.30f), shade(tertiary, 0.16f), 0xFF000000},
                    new float[]{0f, 0.30f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);

            paint.setShader(new RadialGradient(width * 0.86f, height * 0.04f, width * 0.95f,
                    new int[]{withAlpha(primary, 165), withAlpha(primary, 52), withAlpha(primary, 0)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);

            paint.setShader(new RadialGradient(width * 0.05f, height * 0.45f, width * 0.95f,
                    new int[]{withAlpha(secondary, 140), withAlpha(secondary, 44), withAlpha(secondary, 0)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);

            paint.setShader(new RadialGradient(width * 0.75f, height * 0.92f, width * 0.8f,
                    new int[]{withAlpha(tertiary, 110), withAlpha(tertiary, 30), withAlpha(tertiary, 0)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    /**
     * Artwork tile: a coloured gradient with a monogram, replaced by the real
     * thumbnail once it has been downloaded.
     */
    static FrameLayout artworkView(Context context, String url, String artist, String title,
                                   String monogram, int accent, int sizeDp, boolean circle) {
        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(artwork(context, accent, circle ? 999 : 14));

        TextView letter = text(context, monogram, circle ? 30 : 17, TEXT,
                Typeface.create("sans-serif", Typeface.BOLD));
        letter.setGravity(Gravity.CENTER);
        frame.addView(letter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        android.widget.ImageView image = new android.widget.ImageView(context);
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        final float radius = dp(context, 14);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                if (circle) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                } else {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            }
        });
        image.setClipToOutline(true);
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (url != null && !url.isEmpty()) {
            Thumbnails.load(image, url);
        } else if (artist != null && !artist.isEmpty()) {
            // No artwork known yet: look it up once, show it, and keep it locally.
            final String trackTitle = title;
            ArtworkResolver.Callback callback = resolved -> {
                Thumbnails.load(image, resolved);
                ListeningDatabase database = ListeningDatabase.getInstance();
                if (trackTitle != null && !trackTitle.isEmpty()) {
                    database.updateArtworkByName(trackTitle, artist, resolved);
                } else {
                    database.setArtistArtwork(artist, resolved);
                }
            };
            if (title != null && !title.isEmpty()) {
                ArtworkResolver.resolveTrack(artist, title, callback);
            } else {
                ArtworkResolver.resolveArtist(artist, callback);
            }
        }

        frame.setLayoutParams(new LinearLayout.LayoutParams(
                dp(context, sizeDp), dp(context, sizeDp)));
        return frame;
    }

    /** First letters of a name, used as artwork monograms. */
    static String monogram(String name) {
        if (name == null || name.isEmpty()) return "?";
        String trimmed = name.trim();
        String[] parts = trimmed.split("[\\s&,+]+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return ("" + trimmed.charAt(0)).toUpperCase();
    }

    /** Draws a shader-filled rectangle (gradients are not drawables). */
    static final class GradientView extends View {
        private final Shader shader;

        GradientView(Context context, Shader shader) {
            super(context);
            this.shader = shader;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Paint paint = new Paint();
            paint.setShader(shader);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }
}

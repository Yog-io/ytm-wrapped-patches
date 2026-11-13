package app.revanced.extension.music.stats;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * The Replay screens.
 *
 * <p>Structure follows Apple Music Replay: a moving mesh gradient sampled from the
 * artwork, one hero square of cover art, an oversized number that counts up, and
 * a quiet line of copy anchored to the bottom.
 */
final class WrappedSections {
    private WrappedSections() {}

    /** A screen that animates itself when it is shown. */
    interface Entry {
        void onEnter();
    }

    /** Notified once the artwork of a tile is known. */
    interface Art {
        void onBitmap(Bitmap bitmap);
    }

    static void setEntry(View card, Entry entry) {
        card.setTag(entry);
    }

    // --------------------------------------------------------------- chrome

    /** A screen: mesh backdrop, and the three anchored planes of the layout. */
    static FrameLayout card(Context context) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0xFF000000);

        MeshBackdrop mesh = new MeshBackdrop(context);
        root.addView(mesh, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    /** Small uppercase label, wide tracking, quiet white. */
    private static TextView eyebrow(Context context, String value) {
        TextView view = WrappedStory.text(context, value.toUpperCase(Locale.US), 13,
                0x99FFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.08f);
        return view;
    }

    /** A data label inside the poster card: caps, wide tracking, dim. */
    private static TextView dataLabel(Context context, String value) {
        TextView view = WrappedStory.text(context, value.toUpperCase(Locale.US), 11,
                0x8CFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.14f);
        return view;
    }

    /** The eyebrow, anchored to the top of the screen. */
    private static void topLabel(FrameLayout root, Context context, String value) {
        TextView view = eyebrow(context, value);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        params.setMargins(dp(context, 24), dp(context, 46), dp(context, 24), 0);
        root.addView(view, params);
    }

    /** The body of a screen, centred in the space between the label and the copy. */
    private static LinearLayout body(FrameLayout root, Context context, boolean top) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                top ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        params.setMargins(dp(context, 24), top ? dp(context, 84) : dp(context, 76),
                dp(context, 24), dp(context, 34));
        root.addView(body, params);
        return body;
    }

    /** The closing line, anchored to the bottom of the screen. */
    private static void bottomCopy(FrameLayout root, Context context, String value) {
        TextView view = WrappedStory.text(context, value, 17, 0xB3FFFFFF,
                Typeface.create("sans-serif", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        params.setMargins(dp(context, 24), 0, dp(context, 24), dp(context, 34));
        root.addView(view, params);
    }

    private static void applyPalette(FrameLayout root, Bitmap bitmap) {
        if (bitmap == null) return;
        List<Integer> colors = Palette.fromBitmap(bitmap);
        if (colors.isEmpty()) return;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof MeshBackdrop) ((MeshBackdrop) child).setColors(colors);
        }
    }

    private static void startDrift(FrameLayout root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof MeshBackdrop) ((MeshBackdrop) child).startDrift();
        }
    }

    /**
     * The hero square of cover art: 1:1, rounded, floating on the mesh, with the
     * real cover replacing the monogram as soon as it is known.
     */
    private static FrameLayout artTile(Context context, String url, String artist, String title,
                                       String monogram, int accent, int sizePx, Art art) {
        FrameLayout frame = new FrameLayout(context);
        // Until the real cover arrives: a dim glass tile, never a flat colour block.
        frame.setBackground(WrappedStory.placeholder(context, accent, 16));

        TextView letter = WrappedStory.text(context, monogram, sizePx > dp(context, 120) ? 34 : 18,
                accent, Typeface.create("sans-serif", Typeface.BOLD));
        letter.setGravity(Gravity.CENTER);
        frame.addView(letter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(context, 16));
            }
        });
        image.setClipToOutline(true);
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (url != null && !url.isEmpty()) {
            Thumbnails.load(image, url, bitmap -> {
                if (art != null) art.onBitmap(bitmap);
            });
        } else if (artist != null && !artist.isEmpty()) {
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

        frame.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        frame.setElevation(dp(context, 18));
        return frame;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** The best known song to put on the cover: the top one that has artwork. */
    private static WrappedReport.Track coverTrack(WrappedReport.Data data) {
        for (WrappedReport.Track track : data.topTracks) {
            if (track.artworkUrl != null && !track.artworkUrl.isEmpty()) return track;
        }
        return data.topTracks.isEmpty() ? null : data.topTracks.get(0);
    }

    private static int screenWidth(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    // ------------------------------------------------------------ 01 · intro

    static View introCard(Context context, WrappedReport.Data data) {
        FrameLayout root = card(context);
        topLabel(root, context, "Your year in music");
        LinearLayout body = body(root, context, false);

        WrappedReport.Track cover = coverTrack(data);
        String artUrl = cover == null ? null : cover.artworkUrl;
        String artArtist = cover == null ? null : cover.artist;
        String artTitle = cover == null ? null : cover.title;
        String monogram = cover == null ? "♪" : WrappedStory.monogram(cover.title);
        int size = (int) (screenWidth(context) * 0.58f);

        FrameLayout art = artTile(context, artUrl, artArtist, artTitle, monogram,
                WrappedStory.INDIGO, size, bitmap -> applyPalette(root, bitmap));
        body.addView(art);

        body.addView(WrappedStory.space(context, 26));
        TextView total = WrappedStory.text(context, "0", 76, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.BOLD));
        total.setLetterSpacing(-0.02f);
        total.setGravity(Gravity.CENTER);
        body.addView(total);

        body.addView(WrappedStory.space(context, 4));
        TextView caption = WrappedStory.text(context, "minutes listened", 20, 0x99FFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        caption.setGravity(Gravity.CENTER);
        body.addView(caption);

        bottomCopy(root, context, "Your year in music started with "
                + WrappedStory.group(data.totalMs / 60_000) + " total minutes.");

        startDrift(root);
        setEntry(root, () -> {
            WrappedStory.reveal(art);
            WrappedStory.kenBurns(art);
            WrappedStory.countUpNumber(total, data.totalMs / 60_000, "", true);
        });
        return root;
    }

    // ------------------------------------------------------ 02 · month by month

    static View monthsCard(Context context, WrappedReport.Data data) {
        FrameLayout root = card(context);
        topLabel(root, context, "Your year, month by month");
        LinearLayout body = body(root, context, true);

        if (data.monthlyArtists.isEmpty()) {
            body.addView(WrappedStory.big(context, "Not enough data yet", 30));
            return root;
        }

        int shown = Math.min(6, data.monthlyArtists.size());
        for (int i = data.monthlyArtists.size() - shown; i < data.monthlyArtists.size(); i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
            row.setBackground(WrappedStory.frost(context, 16));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(context, 8);
            body.addView(row, rowParams);

            TextView label = WrappedStory.text(context, monthLabel(data.monthlyArtists.get(i)), 13,
                    0x99FFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(context, 64),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.rightMargin = dp(context, 14);
            row.addView(label, labelParams);

            TextView name = WrappedStory.text(context, data.monthlyArtistNames.get(i), 16, 0xFFFFFFFF,
                    Typeface.create("sans-serif", Typeface.BOLD));
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        String lead;
        if (data.topArtistMonths > 1) {
            lead = data.topArtists.isEmpty()
                    ? "Your top artist led for " + data.topArtistMonths + " months."
                    : data.topArtists.get(0).name + " was your top artist for "
                    + data.topArtistMonths + " months.";
        } else {
            lead = "Your listening changed shape as the year went on.";
        }
        bottomCopy(root, context, lead);

        startDrift(root);
        return root;
    }

    // --------------------------------------------------------- 03 · top artist

    static View artistCard(Context context, WrappedReport.Data data) {
        FrameLayout root = card(context);
        topLabel(root, context, "Your top artist");
        LinearLayout body = body(root, context, false);

        if (data.topArtists.isEmpty()) {
            body.addView(WrappedStory.big(context, "No artists yet", 34));
            return root;
        }

        WrappedReport.Artist top = data.topArtists.get(0);
        int size = (int) (screenWidth(context) * 0.5f);
        FrameLayout art = artTile(context, top.artworkUrl, top.name, null,
                WrappedStory.monogram(top.name), WrappedStory.tintFor(top.name), size,
                bitmap -> applyPalette(root, bitmap));
        body.addView(art);

        body.addView(WrappedStory.space(context, 24));
        TextView name = WrappedStory.text(context, top.name, 36, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.BOLD));
        name.setLetterSpacing(-0.01f);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.addView(name);

        body.addView(WrappedStory.space(context, 6));
        TextView listened = WrappedStory.text(context,
                "You listened to them for " + WrappedReport.minutes(top.listenedMs) + ".",
                17, 0x99FFFFFF, Typeface.create("sans-serif", Typeface.NORMAL));
        listened.setGravity(Gravity.CENTER);
        body.addView(listened);

        bottomCopy(root, context, data.topArtistMonths > 1
                ? "They led your listening for " + data.topArtistMonths + " months."
                : "You consistently returned to " + top.name + ".");

        startDrift(root);
        setEntry(root, () -> {
            WrappedStory.reveal(art);
            WrappedStory.kenBurns(art);
        });
        return root;
    }

    // ---------------------------------------------------------- 04 · top song

    static View songCard(Context context, WrappedReport.Data data) {
        FrameLayout root = card(context);
        topLabel(root, context, "You played " + WrappedStory.group(data.plays) + " songs");
        LinearLayout body = body(root, context, false);

        if (data.topTracks.isEmpty()) {
            body.addView(WrappedStory.big(context, "No tracks yet", 34));
            return root;
        }

        WrappedReport.Track top = coverTrack(data);
        int size = (int) (screenWidth(context) * 0.5f);
        FrameLayout art = artTile(context, top.artworkUrl, top.artist, top.title,
                WrappedStory.monogram(top.title), WrappedStory.tintFor(top.title), size,
                bitmap -> applyPalette(root, bitmap));
        body.addView(art);

        body.addView(WrappedStory.space(context, 24));
        TextView title = WrappedStory.text(context, top.title, 32, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(-0.01f);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.addView(title);

        body.addView(WrappedStory.space(context, 6));
        TextView artist = WrappedStory.text(context,
                top.artist == null ? "" : top.artist, 20, 0x99FFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        artist.setGravity(Gravity.CENTER);
        body.addView(artist);

        body.addView(WrappedStory.space(context, 18));
        TextView plays = WrappedStory.text(context, "0", 52, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.BOLD));
        plays.setLetterSpacing(-0.02f);
        plays.setGravity(Gravity.CENTER);
        body.addView(plays);

        body.addView(WrappedStory.space(context, 2));
        TextView playsCaption = WrappedStory.text(context,
                top.plays == 1 ? "play" : "plays", 15, 0x99FFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        playsCaption.setGravity(Gravity.CENTER);
        body.addView(playsCaption);

        bottomCopy(root, context, "Your top song was " + top.title + " by "
                + (top.artist == null ? "—" : top.artist) + ".");

        startDrift(root);
        setEntry(root, () -> {
            WrappedStory.reveal(art);
            WrappedStory.kenBurns(art);
            WrappedStory.countUpNumber(plays, top.plays, "", true);
        });
        return root;
    }

    // -------------------------------------------------------- 05 · discovery

    static View discoveryCard(Context context, WrappedReport.Data data) {
        FrameLayout root = card(context);
        topLabel(root, context, "New to you");
        LinearLayout body = body(root, context, false);

        TextView count = WrappedStory.text(context, "0", 76, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.BOLD));
        count.setLetterSpacing(-0.02f);
        count.setGravity(Gravity.CENTER);
        body.addView(count);

        body.addView(WrappedStory.space(context, 2));
        TextView caption = WrappedStory.text(context,
                data.newArtistsThisYear == 1 ? "new artist this year" : "new artists this year",
                20, 0x99FFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        caption.setGravity(Gravity.CENTER);
        body.addView(caption);

        body.addView(WrappedStory.space(context, 22));

        int shown = Math.min(4, data.newArtists.size());
        for (int i = 0; i < shown; i++) {
            WrappedReport.Artist artist = data.newArtists.get(i);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(context, 12), dp(context, 10), dp(context, 16), dp(context, 10));
            row.setBackground(WrappedStory.frost(context, 16));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(context, 8);
            body.addView(row, rowParams);

            int tile = dp(context, 44);
            FrameLayout thumb = artTile(context, artist.artworkUrl, artist.name, null,
                    WrappedStory.monogram(artist.name), WrappedStory.tintFor(artist.name), tile,
                    i == 0 ? bitmap -> applyPalette(root, bitmap) : null);
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(tile, tile);
            tileParams.rightMargin = dp(context, 14);
            row.addView(thumb, tileParams);

            TextView name = WrappedStory.text(context, artist.name, 16, 0xFFFFFFFF,
                    Typeface.create("sans-serif", Typeface.BOLD));
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        String lead;
        if (!data.comebacks.isEmpty()) {
            lead = "You came back to " + data.comebacks.get(0) + ".";
        } else if (data.newArtistsThisYear > 0) {
            lead = "You discovered " + data.newArtistsThisYear + " new artists this year.";
        } else {
            lead = "Your next favourite artist is still out there.";
        }
        bottomCopy(root, context, lead);

        startDrift(root);
        setEntry(root, () ->
                WrappedStory.countUpNumber(count, data.newArtistsThisYear, "", true));
        return root;
    }

    // ----------------------------------------------------------- 06 · rhythm

    static View rhythmCard(Context context, WrappedReport.Data data) {
        FrameLayout root = card(context);
        topLabel(root, context, "Your rhythm");
        LinearLayout body = body(root, context, false);

        TextView hour = WrappedStory.text(context, WrappedStory.hourLabel(data.peakHour), 72,
                0xFFFFFFFF, Typeface.create("sans-serif", Typeface.BOLD));
        hour.setLetterSpacing(-0.02f);
        hour.setGravity(Gravity.CENTER);
        body.addView(hour);

        body.addView(WrappedStory.space(context, 4));
        TextView hourCaption = WrappedStory.text(context, "your music peaks here", 20, 0x99FFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hourCaption.setGravity(Gravity.CENTER);
        body.addView(hourCaption);

        body.addView(WrappedStory.space(context, 22));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 16));
        row.setBackground(WrappedStory.frost(context, 18));
        body.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView streak = WrappedStory.text(context,
                data.longestStreak + (data.longestStreak == 1 ? " day" : " days"), 26, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.BOLD));
        row.addView(streak);

        TextView streakCaption = WrappedStory.text(context, "in a row", 15, 0x99FFFFFF,
                Typeface.create("sans-serif", Typeface.NORMAL));
        streakCaption.setGravity(Gravity.CENTER);
        row.addView(streakCaption, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bottomCopy(root, context, data.archetypeLine.isEmpty()
                ? "Your listening has a rhythm of its own."
                : data.archetypeLine);

        startDrift(root);
        setEntry(root, () -> WrappedStory.reveal(hour));
        return root;
    }

    // ---------------------------------------------------------- 07 · poster

    static View posterCard(Context context, WrappedReport.Data data) {
        FrameLayout root = card(context);
        topLabel(root, context, "Your year in one card");
        LinearLayout body = body(root, context, true);

        LinearLayout poster = new LinearLayout(context);
        poster.setOrientation(LinearLayout.VERTICAL);
        poster.setPadding(dp(context, 22), dp(context, 22), dp(context, 22), dp(context, 22));
        poster.setBackground(WrappedStory.posterGlass(context));
        body.addView(poster, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView heading = WrappedStory.text(context, "MY YEAR", 12, 0x99FFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setLetterSpacing(0.2f);
        poster.addView(heading);

        poster.addView(WrappedStory.space(context, 14));

        poster.addView(figure(context, "Minutes", WrappedStory.group(data.totalMs / 60_000)));
        poster.addView(WrappedStory.space(context, 10));
        poster.addView(figure(context, "Songs", WrappedStory.group(data.plays)));
        poster.addView(WrappedStory.space(context, 10));
        poster.addView(figure(context, "Artists", WrappedStory.group(data.artists)));

        poster.addView(WrappedStory.space(context, 18));
        poster.addView(dataLabel(context, "Top artists"));

        int count = Math.min(5, data.topArtists.size());
        for (int i = 0; i < count; i++) {
            poster.addView(posterRow(context, String.valueOf(i + 1),
                    data.topArtists.get(i).name));
        }

        if (!data.topTracks.isEmpty()) {
            poster.addView(WrappedStory.space(context, 14));
            poster.addView(dataLabel(context, "Top songs"));
            for (int i = 0; i < Math.min(3, data.topTracks.size()); i++) {
                poster.addView(posterRow(context, String.valueOf(i + 1),
                        data.topTracks.get(i).title));
            }
        }

        body.addView(WrappedStory.space(context, 16));

        TextView share = WrappedStory.text(context, "Copy summary", 15, 0xFFFFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        share.setGravity(Gravity.CENTER);
        share.setPadding(dp(context, 24), dp(context, 12), dp(context, 24), dp(context, 12));
        share.setBackground(WrappedStory.frost(context, 24));
        share.setOnClickListener(view -> {
            try {
                ClipboardManager clipboard = (ClipboardManager)
                        context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("Wrapped", summary(data)));
                    Toast.makeText(context, "Summary copied", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) {
            }
        });
        body.addView(share);

        bottomCopy(root, context, "That's your year in music.");

        startDrift(root);
        setEntry(root, () -> WrappedStory.reveal(poster));
        return root;
    }

    private static LinearLayout figure(Context context, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(dataLabel(context, label), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView number = WrappedStory.text(context, value, 26, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.BOLD));
        number.setLetterSpacing(-0.01f);
        row.addView(number);

        return row;
    }

    private static LinearLayout posterRow(Context context, String rank, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 5), 0, dp(context, 5));

        TextView index = WrappedStory.text(context, rank, 13, 0x66FFFFFF,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(index, new LinearLayout.LayoutParams(dp(context, 22),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = WrappedStory.text(context, value, 16, 0xFFFFFFFF,
                Typeface.create("sans-serif", Typeface.NORMAL));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return row;
    }

    private static String summary(WrappedReport.Data data) {
        StringBuilder text = new StringBuilder();
        text.append("My year in music\n");
        text.append(WrappedStory.group(data.totalMs / 60_000)).append(" minutes\n");
        text.append(WrappedStory.group(data.plays)).append(" songs\n");
        text.append(WrappedStory.group(data.artists)).append(" artists\n");
        for (WrappedReport.Artist artist : data.topArtists) {
            text.append("• ").append(artist.name).append('\n');
        }
        for (WrappedReport.Track track : data.topTracks) {
            text.append("• ").append(track.title).append('\n');
        }
        return text.toString();
    }

    private static String monthLabel(String month) {
        try {
            String[] parts = month.split("-");
            String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            int index = Integer.parseInt(parts[1]) - 1;
            if (index < 0 || index > 11) return month;
            return names[index] + " " + parts[0];
        } catch (Exception ex) {
            return month;
        }
    }
}

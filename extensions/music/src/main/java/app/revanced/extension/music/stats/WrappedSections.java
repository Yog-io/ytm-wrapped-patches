package app.revanced.extension.music.stats;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The report cards. Each card is a full-screen surface with its own artwork-like
 * backdrop, in the style of Apple Music Replay: bold headline, quiet grey
 * supporting copy, tinted artwork tiles and large figures.
 */
final class WrappedSections {
    private WrappedSections() {}

    /** A full-screen card: aurora backdrop plus a padded content column. */
    static FrameLayout card(Context context, int primary, int secondary, int tertiary) {
        FrameLayout root = new FrameLayout(context);
        WrappedStory.AuroraDrawable aurora =
                new WrappedStory.AuroraDrawable(primary, secondary, tertiary);
        root.setBackground(aurora);

        // A blurred copy of the artwork sits under the panels: that is what gives
        // the cards their frosted depth instead of a flat colour.
        View blurred = new View(context);
        blurred.setBackground(aurora);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            blurred.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    WrappedStory.dp(context, 46), WrappedStory.dp(context, 46),
                    android.graphics.Shader.TileMode.CLAMP));
        }
        root.addView(blurred, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(WrappedStory.dp(context, 22), WrappedStory.dp(context, 26),
                WrappedStory.dp(context, 22), WrappedStory.dp(context, 34));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    /** Child 0 is the blurred backdrop, child 1 is the content column. */
    private static LinearLayout columnOf(FrameLayout card) {
        return (LinearLayout) card.getChildAt(1);
    }

    private static void heading(LinearLayout column, String title, String subtitle) {
        column.addView(WrappedStory.title(column.getContext(), title));
        column.addView(WrappedStory.space(column.getContext(), 6));
        column.addView(WrappedStory.sub(column.getContext(), subtitle));
        column.addView(WrappedStory.space(column.getContext(), 22));
    }

    // ------------------------------------------------------------------ hero

    static View heroCard(Context context, WrappedReport.Data data) {
        FrameLayout card = card(context, WrappedStory.INDIGO, WrappedStory.PINK,
                WrappedStory.BLUE);
        LinearLayout column = columnOf(card);

        heading(column, "Your Music Wrapped", "Everything you played, in one place.");

        LinearLayout hero = WrappedStory.surface(context);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(WrappedStory.dp(context, 22), WrappedStory.dp(context, 38),
                WrappedStory.dp(context, 22), WrappedStory.dp(context, 38));
        hero.setBackground(WrappedStory.heroGradient(context, WrappedStory.PURPLE));
        hero.addView(WrappedStory.big(context, WrappedReport.minutes(data.totalMs), 52));
        hero.addView(WrappedStory.space(context, 8));
        hero.addView(WrappedStory.text(context, "of music, start to finish", 18,
                0xE6FFFFFF, Typeface.create("sans-serif", Typeface.NORMAL)));
        column.addView(hero);

        column.addView(WrappedStory.space(context, 14));
        LinearLayout tiles = new LinearLayout(context);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(statTile(context, String.valueOf(data.plays), "times you\nplayed",
                WrappedStory.PINK), weight());
        tiles.addView(WrappedStory.gap(context, 12));
        tiles.addView(statTile(context, String.valueOf(data.tracks), "songs you\nheard",
                WrappedStory.BLUE), weight());
        tiles.addView(WrappedStory.gap(context, 12));
        tiles.addView(statTile(context, String.valueOf(data.activeDays), "days you\nlistened",
                WrappedStory.TEAL), weight());
        column.addView(tiles, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(WrappedStory.space(context, 14));
        column.addView(keyValue(context, "On average, you listened for",
                WrappedReport.compact(data.avgSessionMs)));

        return card;
    }

    // --------------------------------------------------------------- artists

    static View artistsCard(Context context, WrappedReport.Data data) {
        FrameLayout card = card(context, WrappedStory.PINK, WrappedStory.INDIGO,
                WrappedStory.PURPLE);
        LinearLayout column = columnOf(card);

        heading(column, "Your top artists", "The voices you kept coming back to.");

        if (data.topArtists.isEmpty()) {
            column.addView(WrappedStory.sub(context, "Nothing yet."));
            return card;
        }

        WrappedReport.Artist top = data.topArtists.get(0);
        int accent = WrappedStory.tintFor(top.name);

        LinearLayout hero = WrappedStory.surface(context);
        hero.setBackground(WrappedStory.heroGradient(context, accent));
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(WrappedStory.dp(context, 18), WrappedStory.dp(context, 22),
                WrappedStory.dp(context, 18), WrappedStory.dp(context, 22));
        hero.addView(WrappedStory.text(context, "You had one artist on repeat", 15,
                0xE6FFFFFF, Typeface.create("sans-serif", Typeface.NORMAL)));
        hero.addView(WrappedStory.space(context, 8));
        hero.addView(WrappedStory.big(context, top.name, 34));
        hero.addView(WrappedStory.space(context, 8));
        hero.addView(WrappedStory.text(context, Math.round(data.topArtistShare * 100)
                + "% of your listening went to them", 15, 0xCCFFFFFF,
                Typeface.create("sans-serif", Typeface.NORMAL)));
        column.addView(hero);

        column.addView(WrappedStory.space(context, 20));
        HorizontalScrollView rail = new HorizontalScrollView(context);
        rail.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        for (WrappedReport.Artist artist : data.topArtists) {
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);

            tile.addView(WrappedStory.artworkView(context, artist.artworkUrl,
                    artist.name, null, WrappedStory.monogram(artist.name),
                    WrappedStory.tintFor(artist.name), 104, true));

            tile.addView(WrappedStory.space(context, 10));
            TextView name = WrappedStory.text(context, artist.name, 14, WrappedStory.TEXT,
                    Typeface.create("sans-serif", Typeface.BOLD));
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(name);

            TextView amount = WrappedStory.caption(context, WrappedReport.minutes(artist.listenedMs));
            amount.setGravity(Gravity.CENTER);
            tile.addView(amount);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    WrappedStory.dp(context, 124), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(WrappedStory.dp(context, 12));
            row.addView(tile, params);
        }

        rail.addView(row);
        column.addView(rail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(WrappedStory.space(context, 20));
        if (data.topArtistTrack != null) {
            column.addView(keyValue(context, "Their song you kept on", data.topArtistTrack));
        }
        column.addView(WrappedStory.space(context, 10));
        column.addView(keyValue(context, "Then came",
                data.topArtists.size() > 1 ? data.topArtists.get(1).name : "—"));

        return card;
    }

    // ----------------------------------------------------------------- songs

    static View songsCard(Context context, WrappedReport.Data data) {
        FrameLayout card = card(context, WrappedStory.BLUE, WrappedStory.PURPLE,
                WrappedStory.INDIGO);
        LinearLayout column = columnOf(card);

        heading(column, "Your top songs", "The ones you never skipped.");

        if (data.topTracks.isEmpty()) {
            column.addView(WrappedStory.sub(context, "Nothing yet."));
            return card;
        }

        long maxPlays = Math.max(1, data.topTracks.get(0).plays);
        for (WrappedReport.Track track : data.topTracks) {
            int accent = WrappedStory.tintFor(track.title);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, WrappedStory.dp(context, 8), 0, WrappedStory.dp(context, 8));

            row.addView(WrappedStory.artworkView(context, track.artworkUrl,
                    track.artist, track.title, WrappedStory.monogram(track.title),
                    accent, 52, false));

            LinearLayout names = new LinearLayout(context);
            names.setOrientation(LinearLayout.VERTICAL);
            names.setPadding(WrappedStory.dp(context, 14), 0, 0, 0);

            TextView name = WrappedStory.text(context, track.title, 16, WrappedStory.TEXT,
                    Typeface.create("sans-serif", Typeface.BOLD));
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            names.addView(name);

            String detail = (track.artist == null ? "" : track.artist + " · ")
                    + "played " + track.plays + (track.plays == 1 ? " time" : " times");
            TextView detailView = WrappedStory.caption(context, detail);
            detailView.setMaxLines(1);
            detailView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            names.addView(WrappedStory.space(context, 4));
            names.addView(detailView);

            row.addView(names, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            column.addView(row);

            column.addView(WrappedStory.meter(context, accent, track.plays / (float) maxPlays),
                    new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            WrappedStory.dp(context, 3)));
            column.addView(WrappedStory.space(context, 12));
        }

        column.addView(WrappedStory.space(context, 4));
        column.addView(keyValue(context, "Together, your top songs got you",
                data.topListensPlays + " plays"));

        return card;
    }

    // ----------------------------------------------------------------- stats

    static View statsCard(Context context, WrappedReport.Data data) {
        FrameLayout card = card(context, WrappedStory.INDIGO, WrappedStory.CYAN,
                WrappedStory.BLUE);
        LinearLayout column = columnOf(card);

        heading(column, "Your listening stats", "The numbers behind the music.");

        LinearLayout first = new LinearLayout(context);
        first.setOrientation(LinearLayout.HORIZONTAL);
        first.addView(statTile(context, String.valueOf(data.plays), "plays",
                WrappedStory.PINK), weight());
        first.addView(WrappedStory.gap(context, 12));
        first.addView(statTile(context, String.valueOf(data.tracks), "songs",
                WrappedStory.BLUE), weight());
        column.addView(first, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(WrappedStory.space(context, 12));
        LinearLayout second = new LinearLayout(context);
        second.setOrientation(LinearLayout.HORIZONTAL);
        second.addView(statTile(context, String.valueOf(data.artists), "artists",
                WrappedStory.PURPLE), weight());
        second.addView(WrappedStory.gap(context, 12));
        second.addView(statTile(context, String.valueOf(data.activeDays), "days",
                WrappedStory.TEAL), weight());
        column.addView(second, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        column.addView(WrappedStory.space(context, 14));
        column.addView(keyValue(context, "On a typical day you listened for",
                WrappedReport.compact(data.perDayMs)));
        column.addView(WrappedStory.space(context, 10));
        column.addView(keyValue(context, "New songs you found this month",
                String.valueOf(data.discoveries30d)));
        column.addView(WrappedStory.space(context, 10));
        column.addView(keyValue(context, "Your longest streak",
                data.longestStreak + " days"));

        return card;
    }

    // ----------------------------------------------------------------- clock

    static View clockCard(Context context, WrappedReport.Data data) {
        FrameLayout card = card(context, WrappedStory.PURPLE, WrappedStory.PINK,
                WrappedStory.INDIGO);
        LinearLayout column = columnOf(card);

        heading(column, "You're a " + WrappedStory.hourLabel(data.peakHour) + " person",
                "That's when you press play the most.");

        LinearLayout surface = WrappedStory.surface(context);
        surface.addView(WrappedStory.big(context, WrappedStory.hourLabel(data.peakHour), 40));
        surface.addView(WrappedStory.space(context, 4));
        surface.addView(WrappedStory.caption(context, "your busiest hour"));

        long max = 1;
        for (long value : data.listensByHour) max = Math.max(max, value);

        int chartHeight = WrappedStory.dp(context, 110);
        LinearLayout chart = new LinearLayout(context);
        chart.setOrientation(LinearLayout.HORIZONTAL);
        chart.setGravity(Gravity.BOTTOM);
        for (int hour = 0; hour < 24; hour++) {
            int height = Math.max(WrappedStory.dp(context, 4),
                    Math.round(chartHeight * (data.listensByHour[hour] / (float) max)));
            View bar = new View(context);
            bar.setBackground(WrappedStory.rounded(context,
                    hour == data.peakHour ? WrappedStory.PINK : 0x2AFFFFFF, 3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, 1f);
            params.setMarginEnd(WrappedStory.dp(context, 3));
            chart.addView(bar, params);
        }
        surface.addView(WrappedStory.space(context, 20));
        surface.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, chartHeight));

        LinearLayout axis = new LinearLayout(context);
        axis.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"12a", "6a", "12p", "6p", "12a"};
        for (String label : labels) {
            axis.addView(WrappedStory.text(context, label, 11, WrappedStory.TEXT3,
                    Typeface.create("sans-serif", Typeface.NORMAL)),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        surface.addView(WrappedStory.space(context, 8));
        surface.addView(axis);
        column.addView(surface);

        column.addView(WrappedStory.space(context, 12));
        column.addView(keyValue(context, "That makes you a", data.archetype));

        return card;
    }

    // ------------------------------------------------------------------ week

    static View weekCard(Context context, WrappedReport.Data data) {
        FrameLayout card = card(context, WrappedStory.CYAN, WrappedStory.TEAL,
                WrappedStory.BLUE);
        LinearLayout column = columnOf(card);

        heading(column, WrappedStory.DAY_NAMES[data.peakDay] + " is your day",
                "You listen more on this day than any other.");

        LinearLayout surface = WrappedStory.surface(context);
        surface.addView(WrappedStory.big(context, WrappedStory.DAY_NAMES[data.peakDay], 34));
        surface.addView(WrappedStory.space(context, 4));
        surface.addView(WrappedStory.caption(context, "your strongest day"));
        surface.addView(WrappedStory.space(context, 18));

        long max = 1;
        for (long value : data.listensByDayOfWeek) max = Math.max(max, value);

        for (int i = 0; i < 7; i++) {
            long value = data.listensByDayOfWeek[i];
            boolean peak = i == data.peakDay;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(WrappedStory.text(context, WrappedStory.DAY_NAMES[i].substring(0, 3), 14,
                            peak ? WrappedStory.TEXT : WrappedStory.TEXT2,
                            Typeface.create("sans-serif", Typeface.NORMAL)),
                    new LinearLayout.LayoutParams(WrappedStory.dp(context, 38),
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(WrappedStory.meter(context,
                            peak ? WrappedStory.INDIGO : 0x2AFFFFFF, value / (float) max),
                    new LinearLayout.LayoutParams(0, WrappedStory.dp(context, 4), 1f));
            row.addView(WrappedStory.text(context, String.valueOf(value), 12, WrappedStory.TEXT3,
                            Typeface.create("sans-serif", Typeface.NORMAL)),
                    new LinearLayout.LayoutParams(WrappedStory.dp(context, 32),
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            surface.addView(row);
            surface.addView(WrappedStory.space(context, 12));
        }
        column.addView(surface);

        column.addView(WrappedStory.space(context, 12));
        column.addView(keyValue(context, "You listened this many days in a row",
                data.longestStreak + " days"));

        return card;
    }

    // ---------------------------------------------------------------- outro

    static View outroCard(Context context, WrappedReport.Data data) {
        FrameLayout card = card(context, WrappedStory.INDIGO, WrappedStory.PURPLE,
                WrappedStory.PINK);
        LinearLayout column = columnOf(card);

        heading(column, "That's your year", "Here's the whole thing.");

        LinearLayout hero = WrappedStory.surface(context);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(WrappedStory.heroGradient(context, WrappedStory.INDIGO));
        hero.setPadding(WrappedStory.dp(context, 22), WrappedStory.dp(context, 34),
                WrappedStory.dp(context, 22), WrappedStory.dp(context, 34));
        hero.addView(WrappedStory.big(context, data.plays + " plays", 44));
        hero.addView(WrappedStory.space(context, 6));
        hero.addView(WrappedStory.text(context, data.tracks + " songs · " + data.artists
                + " artists · " + data.activeDays + " days", 16, 0xE6FFFFFF,
                Typeface.create("sans-serif", Typeface.NORMAL)));
        column.addView(hero);

        column.addView(WrappedStory.space(context, 18));
        column.addView(WrappedStory.text(context,
                "All of this came from this phone. Nothing was uploaded. Made with ReVanced.",
                12, WrappedStory.TEXT3, Typeface.create("sans-serif", Typeface.NORMAL)));

        return card;
    }

    // --------------------------------------------------------------- helpers

    private static LinearLayout statTile(Context context, String value, String label, int accent) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setBackground(WrappedStory.glass(context, WrappedStory.withAlpha(accent, 0x3C), 20));
        cell.setPadding(WrappedStory.dp(context, 16), WrappedStory.dp(context, 16),
                WrappedStory.dp(context, 16), WrappedStory.dp(context, 16));
        cell.addView(WrappedStory.big(context, value, 25));
        cell.addView(WrappedStory.space(context, 4));
        cell.addView(WrappedStory.caption(context, label));
        return cell;
    }

    /** A label on the left, a value on the right, on a glass row. */
    private static LinearLayout keyValue(Context context, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(WrappedStory.frost(context, 18));
        row.setPadding(WrappedStory.dp(context, 16), WrappedStory.dp(context, 14),
                WrappedStory.dp(context, 16), WrappedStory.dp(context, 14));
        row.addView(WrappedStory.caption(context, label), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(WrappedStory.big(context, value, 18));
        return row;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}

package app.revanced.extension.music.stats;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Full-screen, fully native Music Wrapped report: native app bar with a back arrow,
 * native cards, lists and bar charts (no WebView).
 */
@SuppressWarnings("unused")
public final class WrappedReportActivity extends Activity {
    private static final int BACKGROUND = Color.parseColor("#12081F");
    private static final int CARD = Color.parseColor("#1E1230");
    private static final int CARD_TRACK = Color.parseColor("#2A1B3D");
    private static final int ACCENT = Color.parseColor("#1DB954");
    private static final int ACCENT_ALT = Color.parseColor("#A855F7");
    private static final int TEXT_DIM = Color.parseColor("#B3B3B3");
    private static final int TEXT_FAINT = Color.parseColor("#777777");

    private FrameLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setBackgroundDrawable(new ColorDrawable(BACKGROUND));
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        content = new FrameLayout(this);
        content.setBackgroundColor(BACKGROUND);
        content.addView(new ProgressBar(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);
        root.addView(buildAppBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        setLightSystemBarIcons(false);

        Utils.runOnBackgroundThread(() -> {
            WrappedReport.Data data;
            try {
                data = WrappedReport.load();
            } catch (Exception ex) {
                Logger.printException(() -> "WrappedReport load failure", ex);
                data = null;
            }
            WrappedReport.Data report = data;
            Utils.runOnMainThread(() -> showReport(report));
        });
    }

    private View buildAppBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BACKGROUND);

        View back = new BackArrowView();
        back.setContentDescription("Back");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(this);
        title.setText("Music Wrapped");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMarginStart(dp(4));
        bar.addView(title, titleParams);

        return bar;
    }

    private void showReport(WrappedReport.Data data) {
        if (isFinishing() || isDestroyed()) return;

        content.removeAllViews();

        if (data == null) {
            content.addView(centeredMessage("Music Wrapped",
                    "Could not build the report."), matchParent());
            return;
        }
        if (data.isEmpty()) {
            content.addView(centeredMessage("Nothing here yet",
                    "Play some music and come back."), matchParent());
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(14), dp(18), dp(32));
        scroll.addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addTotalCard(column, data);
        addStatsCard(column, data);
        addSectionTitle(column, "Top tracks");
        addTrackList(column, data);
        addSectionTitle(column, "Top artists");
        addArtistList(column, data);
        addSectionTitle(column, "Your listening clock");
        addHourChart(column, data.listensByHour);
        addSectionTitle(column, "Your week");
        addWeekChart(column, data.listensByDayOfWeek);
        addFooter(column);

        content.addView(scroll, matchParent());
    }

    // --- Sections ---

    private void addTotalCard(LinearLayout column, WrappedReport.Data data) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(ACCENT, 12));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView total = new TextView(this);
        total.setText(WrappedReport.minutes(data.totalMs));
        total.setTextColor(Color.WHITE);
        total.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        total.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(total);

        TextView label = dimText("total listening time", 13);
        card.addView(label);

        column.addView(card, topMargin(matchWidth(), dp(8)));
    }

    private void addStatsCard(LinearLayout column, WrappedReport.Data data) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(CARD, 10));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView plays = new TextView(this);
        plays.setText(data.plays + (data.plays == 1 ? " session played" : " sessions played"));
        plays.setTextColor(Color.WHITE);
        plays.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        plays.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(plays);

        card.addView(dimText(data.tracks + " tracks \u2022 " + data.artists + " artists \u2022 "
                + data.activeDays + " active days", 12));

        column.addView(card, topMargin(matchWidth(), dp(8)));
    }

    private void addSectionTitle(LinearLayout column, String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(ACCENT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        column.addView(view, topMargin(matchWidth(), dp(16)));
    }

    private void addTrackList(LinearLayout column, WrappedReport.Data data) {
        int rank = 1;
        for (WrappedReport.Track track : data.topTracks) {
            StringBuilder sub = new StringBuilder();
            if (track.artist != null && !track.artist.isEmpty()) {
                sub.append(track.artist).append(" \u2022 ");
            }
            sub.append(WrappedReport.minutes(track.listenedMs))
                    .append(" \u2022 ").append(track.plays)
                    .append(track.plays == 1 ? " play" : " plays");
            if (track.durationMs > 0) {
                sub.append(" \u2022 song ").append(WrappedReport.minutes(track.durationMs));
            }

            column.addView(addRow(rank + ". " + track.title, sub.toString()),
                    topMargin(matchWidth(), dp(6)));
            rank++;
        }
    }

    private void addArtistList(LinearLayout column, WrappedReport.Data data) {
        int rank = 1;
        for (WrappedReport.Artist artist : data.topArtists) {
            column.addView(addRow(rank + ". " + artist.name, WrappedReport.minutes(artist.listenedMs)),
                    topMargin(matchWidth(), dp(6)));
            rank++;
        }
    }

    private void addHourChart(LinearLayout column, long[] hours) {
        long max = 1;
        for (long value : hours) max = Math.max(max, value);

        int chartHeight = dp(70);
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        bars.setGravity(Gravity.BOTTOM);
        for (long value : hours) {
            int percent = (int) (value * 100 / max);
            View bar = new View(this);
            bar.setBackground(rounded(ACCENT_ALT, 3));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, Math.max(dp(2), chartHeight * percent / 100), 1f);
            params.setMarginEnd(dp(2));
            bars.addView(bar, params);
        }
        column.addView(bars, topMargin(matchWidth(), dp(6)));

        String[] labels = {"0h", "6h", "12h", "18h", "24h"};
        LinearLayout axis = new LinearLayout(this);
        axis.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            TextView view = dimText(label, 11);
            axis.addView(view, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        column.addView(axis, topMargin(matchWidth(), dp(4)));
    }

    private void addWeekChart(LinearLayout column, long[] days) {
        String[] names = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        long max = 1;
        for (long value : days) max = Math.max(max, value);

        for (int i = 0; i < 7; i++) {
            int percent = (int) (days[i] * 100 / max);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = dimText(names[i], 11);
            row.addView(label, new LinearLayout.LayoutParams(dp(38),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout track = new LinearLayout(this);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setBackground(rounded(CARD_TRACK, 6));

            View fill = new View(this);
            fill.setBackground(rounded(ACCENT, 6));
            int fillWeight = Math.max(percent, 1);
            track.addView(fill, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, fillWeight));
            if (fillWeight < 100) {
                track.addView(new View(this), new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 100 - fillWeight));
            }

            LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                    0, dp(12), 1f);
            trackParams.setMarginStart(dp(6));
            row.addView(track, trackParams);

            column.addView(row, topMargin(matchWidth(), dp(5)));
        }
    }

    private void addFooter(LinearLayout column) {
        TextView footer = new TextView(this);
        footer.setText("Music Wrapped \u2022 generated locally by ReVanced");
        footer.setTextColor(TEXT_FAINT);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        column.addView(footer, topMargin(matchWidth(), dp(18)));
    }

    // --- Helpers ---

    private View addRow(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(rounded(CARD, 10));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(titleView);

        row.addView(dimText(subtitle, 12));
        return row;
    }

    private TextView dimText(String text, int sizeSp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setMaxLines(2);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private View centeredMessage(String title, String message) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        box.addView(titleView);

        TextView messageView = dimText(message, 14);
        messageView.setGravity(Gravity.CENTER);
        box.addView(messageView);
        return box;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchParent() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int topPx) {
        params.setMargins(0, topPx, 0, 0);
        return params;
    }

    private void setLightSystemBarIcons(boolean light) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(light ? mask : 0, mask);
                }
            } else {
                int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                getWindow().getDecorView().setSystemUiVisibility(light ? flags : 0);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not set system bar appearance", ex);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Material-style back arrow, drawn so no app resources or libraries are required. */
    private final class BackArrowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        BackArrowView() {
            super(WrappedReportActivity.this);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float arm = dp(6);
            float shaft = dp(8);

            path.reset();
            path.moveTo(cx + arm, cy - shaft);
            path.lineTo(cx - arm, cy);
            path.lineTo(cx + arm, cy + shaft);
            path.moveTo(cx - arm, cy);
            path.lineTo(cx + arm, cy);
            canvas.drawPath(path, paint);
        }
    }
}

package app.revanced.extension.music.stats;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Full-screen Music Wrapped report. The screen header is the platform ActionBar
 * (native title + up arrow, handled by the framework); the report body below it
 * is rendered with native views.
 */
@SuppressWarnings("unused")
public final class WrappedReportActivity extends Activity {
    private static final int BACKGROUND = Color.parseColor("#0A0A0C");
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
        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
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

    /**
     * Header matching the app's own screen header metrics:
     * 48dp tall, 56dp back touch area with a 24dp framework up arrow,
     * 20sp sans-serif-medium title starting at 72dp.
     */
    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(BACKGROUND);

        FrameLayout back = new FrameLayout(this);
        back.setContentDescription("Navigate up");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> finish());

        ImageView arrow = new ImageView(this);
        Drawable icon = resolveUpArrow();
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(Color.WHITE);
            arrow.setImageDrawable(icon);
        }
        back.addView(arrow, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        bar.addView(back, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Music Wrapped");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        titleParams.setMarginStart(dp(16));
        bar.addView(title, titleParams);

        return bar;
    }

    private Drawable resolveUpArrow() {
        try {
            TypedValue value = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.homeAsUpIndicator, value, true)) {
                return getDrawable(value.resourceId);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Could not resolve up arrow", ex);
        }
        return null;
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

        WrappedPager pager = new WrappedPager(this, WrappedStory.buildCards(this, data));
        content.addView(pager, matchParent());
    }

    // --- Helpers ---

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
}

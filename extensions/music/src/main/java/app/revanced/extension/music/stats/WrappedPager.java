package app.revanced.extension.music.stats;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Card host: one full-screen card at a time, with a thin progress bar and a
 * page counter. Tap the right side to advance, the left to go back, or swipe.
 */
final class WrappedPager extends FrameLayout {
    private static final int TAP_SLOP_DP = 12;
    private static final int SWIPE_DP = 44;

    private final List<View> cards;
    private final LinearLayout segments;
    private final TextView counter;

    private int index;
    private float downX;
    private float downY;

    WrappedPager(Context context, List<View> cards) {
        super(context);
        this.cards = cards;

        segments = new LinearLayout(context);
        segments.setOrientation(LinearLayout.HORIZONTAL);

        for (View card : cards) {
            card.setVisibility(cards.indexOf(card) == 0 ? VISIBLE : GONE);
            addView(card, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        FrameLayout.LayoutParams segmentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        segmentParams.setMargins(dp(18), dp(12), dp(18), 0);
        addView(segments, segmentParams);

        counter = new TextView(context);
        counter.setTextColor(Color.parseColor("#8E8E93"));
        counter.setTextSize(11);
        counter.setLetterSpacing(0.14f);
        FrameLayout.LayoutParams counterParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        counterParams.setMargins(0, dp(24), dp(20), 0);
        addView(counter, counterParams);

        for (int i = 0; i < cards.size(); i++) {
            View segment = new View(context);
            segment.setBackgroundColor(Color.parseColor("#3A3A3C"));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(3), 1f);
            params.setMarginEnd(dp(4));
            segments.addView(segment, params);
        }
        updateChrome();
    }

    private void updateChrome() {
        for (int i = 0; i < segments.getChildCount(); i++) {
            segments.getChildAt(i).setBackgroundColor(
                    i <= index ? Color.WHITE : Color.parseColor("#3A3A3C"));
        }
        counter.setText(String.format(java.util.Locale.US, "%02d / %02d",
                index + 1, cards.size()));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > dp(SWIPE_DP) && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0) next(); else previous();
                } else if (Math.abs(dx) < dp(TAP_SLOP_DP) && Math.abs(dy) < dp(TAP_SLOP_DP)) {
                    if (event.getX() < getWidth() * 0.3f) previous(); else next();
                }
                return true;
            }
            default:
                return true;
        }
    }

    private void next() {
        if (index < cards.size() - 1) show(index + 1, true);
    }

    private void previous() {
        if (index > 0) show(index - 1, false);
    }

    private void show(int target, boolean forward) {
        View outgoing = cards.get(index);
        View incoming = cards.get(target);
        int width = getWidth() > 0 ? getWidth() : dp(360);
        int sign = forward ? 1 : -1;

        incoming.setVisibility(VISIBLE);
        incoming.setTranslationX(sign * width);
        incoming.setAlpha(0.85f);
        incoming.animate().translationX(0f).alpha(1f).setDuration(240).start();

        outgoing.animate().translationX(-sign * width).alpha(0f).setDuration(240)
                .withEndAction(() -> {
                    outgoing.setVisibility(GONE);
                    outgoing.setTranslationX(0f);
                    outgoing.setAlpha(1f);
                }).start();

        index = target;
        updateChrome();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

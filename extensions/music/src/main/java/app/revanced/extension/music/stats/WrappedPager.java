package app.revanced.extension.music.stats;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
 * page counter.
 *
 * <p>Paging works like Apple Replay: taps only, at the edges. Horizontal
 * swiping is deliberately ignored so a stray drag never scrubs the screen.
 */
final class WrappedPager extends FrameLayout {
    private static final int TAP_SLOP_DP = 12;

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

        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i);
            card.setVisibility(i == 0 ? VISIBLE : GONE);
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
            segment.setBackground(segmentDrawable(false));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(3), 1f);
            params.setMarginEnd(dp(2));
            segments.addView(segment, params);
        }
        updateChrome();

        // The first screen plays its reveal as soon as it is on screen.
        post(() -> {
            Object tag = cards.get(0).getTag();
            if (tag instanceof WrappedSections.Entry) {
                ((WrappedSections.Entry) tag).onEnter();
            }
        });
    }

    /** A progress segment: 3pt tall, fully rounded, white or a dimmed white. */
    private GradientDrawable segmentDrawable(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(active ? Color.WHITE : Color.argb(77, 255, 255, 255));
        drawable.setCornerRadius(dp(2));
        return drawable;
    }

    private void updateChrome() {
        for (int i = 0; i < segments.getChildCount(); i++) {
            segments.getChildAt(i).setBackground(segmentDrawable(i <= index));
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
                if (Math.abs(dx) < dp(TAP_SLOP_DP) && Math.abs(dy) < dp(TAP_SLOP_DP)) {
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
        incoming.setTranslationX(sign * width * 0.18f);
        incoming.setAlpha(0f);
        // The mesh gradient cross-fades over half a second.
        incoming.animate().translationX(0f).alpha(1f).setDuration(500)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();

        outgoing.animate().translationX(-sign * width * 0.12f).alpha(0f).setDuration(500)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f))
                .withEndAction(() -> {
                    outgoing.setVisibility(GONE);
                    outgoing.setTranslationX(0f);
                    outgoing.setAlpha(1f);
                }).start();

        index = target;
        updateChrome();

        // A light tap marks the moment the screen changes.
        try {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        } catch (Throwable ignored) {
        }

        Object tag = incoming.getTag();
        if (tag instanceof WrappedSections.Entry) {
            ((WrappedSections.Entry) tag).onEnter();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

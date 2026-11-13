package app.revanced.extension.music.settings.preference;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.music.stats.WrappedReportActivity;

/**
 * Settings preference that opens the local Music Wrapped report screen.
 */
@SuppressWarnings("unused")
public class WrappedReportPreference extends Preference implements Preference.OnPreferenceClickListener {

    private void init() {
        setSelectable(true);
        setPersistent(false);
        setOnPreferenceClickListener(this);
    }

    public WrappedReportPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public WrappedReportPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public WrappedReportPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WrappedReportPreference(Context context) {
        super(context);
        init();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        try {
            Context context = getContext();
            context.startActivity(new Intent(context, WrappedReportActivity.class));
        } catch (Exception ex) {
            Logger.printException(() -> "WrappedReport launch failure", ex);
        }
        return true;
    }
}

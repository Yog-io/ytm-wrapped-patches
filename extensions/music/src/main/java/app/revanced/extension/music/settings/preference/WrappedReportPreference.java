package app.revanced.extension.music.settings.preference;

import static app.revanced.extension.music.stats.WrappedReport.showReport;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import app.revanced.extension.shared.Logger;

/**
 * Settings preference that opens the local Music Wrapped report.
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
            showReport(getContext());
        } catch (Exception ex) {
            Logger.printException(() -> "WrappedReport show failure", ex);
        }
        return true;
    }
}

package app.revanced.extension.music.stats;

import static app.revanced.extension.shared.Utils.runOnBackgroundThread;
import static app.revanced.extension.shared.Utils.runOnMainThread;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;

import java.util.Calendar;
import java.util.Locale;

import app.revanced.extension.shared.Logger;

/**
 * Builds and shows a Spotify-Wrapped style report from the local listening database.
 * All data comes from {@link ListeningDatabase}; nothing is sent anywhere.
 */
@SuppressWarnings("unused")
public final class WrappedReport {
    private WrappedReport() {}

    private static final int MAX_LIST_ITEMS = 5;

    public static void showReport(Context context) {
        runOnBackgroundThread(() -> {
            try {
                ListeningDatabase db = ListeningDatabase.getInstance();
                String html = buildHtml(db);
                runOnMainThread(() -> showDialog(context, html));
            } catch (Exception ex) {
                Logger.printException(() -> "WrappedReport build failure", ex);
            }
        });
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String minutes(long ms) {
        long m = ms / 60_000;
        if (m >= 60) {
            return String.format(Locale.US, "%dh %02dm", m / 60, m % 60);
        }
        return m + " min";
    }

    private static String buildHtml(ListeningDatabase db) {
        StringBuilder sb = new StringBuilder(16_000);
        long totalMs = db.totalListenedMs();
        int plays = db.totalPlayCount();
        int tracks = db.distinctTracks();
        int artists = db.distinctArtists();
        int days = db.activeDays();

        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'>")
          .append("<style>")
          .append("body{background:#12081f;color:#fff;font-family:sans-serif;margin:0;padding:16px;}")
          .append("h1{font-size:28px;margin:4px 0;}")
          .append(".sub{color:#b3b3b3;font-size:13px;margin-bottom:12px;}")
          .append(".big{background:linear-gradient(135deg,#1db954,#191414);border-radius:12px;padding:14px;margin:8px 0;}")
          .append(".big .num{font-size:32px;font-weight:bold;}")
          .append(".row{background:#1e1230;border-radius:10px;padding:10px 12px;margin:6px 0;}")
          .append(".row .t{font-size:15px;font-weight:bold;}")
          .append(".row .a{color:#b3b3b3;font-size:12px;}")
          .append("h2{font-size:16px;color:#1db954;margin:14px 0 4px;}")
          .append(".bar{height:12px;background:linear-gradient(90deg,#1db954,#a855f7);border-radius:6px;min-width:2px;}")
          .append(".lbl{font-size:11px;color:#b3b3b3;display:inline-block;width:38px;}")
          .append(".hrs{display:flex;align-items:flex-end;gap:2px;height:70px;margin-top:6px;}")
          .append(".hrb{flex:1;background:#a855f7;border-radius:3px 3px 0 0;}")
          .append(".foot{color:#777;font-size:11px;margin-top:14px;}")
          .append("</style></head><body>");

        if (totalMs <= 0 && plays == 0) {
            sb.append("<h1>Nothing here yet</h1><div class='sub'>Play some music and come back.</div></body></html>");
            return sb.toString();
        }

        sb.append("<h1>Your Music Wrapped</h1><div class='sub'>From listening data stored on this device only</div>");

        sb.append("<div class='big'><div class='num'>").append(minutes(totalMs))
          .append("</div><div class='sub'>total listening time</div></div>");

        sb.append("<div class='row'><div class='t'>").append(plays).append(" sessions played</div>")
          .append("<div class='a'>").append(tracks).append(" tracks &bull; ").append(artists)
          .append(" artists &bull; ").append(days).append(" active days</div></div>");

        // Top tracks by listened time.
        sb.append("<h2>Top tracks</h2>");
        try (var c = db.topTracksByListened(MAX_LIST_ITEMS)) {
            int rank = 1;
            while (c.moveToNext()) {
                long listened = c.getLong(3);
                long played = c.getLong(2);
                long duration = c.getLong(4);
                sb.append("<div class='row'><div class='t'>").append(rank).append(". ")
                  .append(esc(c.getString(0))).append("</div><div class='a'>")
                  .append(esc(c.getString(1))).append(" &bull; ").append(minutes(listened))
                  .append(" &bull; ").append(played).append(play(played))
                  .append(duration > 0 ? " &bull; song " + minutes(duration) : "")
                  .append("</div></div>");
                rank++;
            }
        }

        // Top artists.
        sb.append("<h2>Top artists</h2>");
        try (var c = db.topArtistsByListened(MAX_LIST_ITEMS)) {
            int rank = 1;
            while (c.moveToNext()) {
                sb.append("<div class='row'><div class='t'>").append(rank).append(". ")
                  .append(esc(c.getString(0))).append("</div><div class='a'>")
                  .append(minutes(c.getLong(1))).append("</div></div>");
                rank++;
            }
        }

        // Listening clock (24h bars).
        sb.append("<h2>Your listening clock</h2><div class='hrs'>");
        long maxHour = 1;
        long[] hours = new long[24];
        try (var c = db.listensByHour()) {
            while (c.moveToNext()) {
                int hour = c.getInt(0);
                if (hour >= 0 && hour < 24) {
                    hours[hour] = c.getLong(1);
                    maxHour = Math.max(maxHour, hours[hour]);
                }
            }
        }
        for (long h : hours) {
            int pct = (int) (h * 100 / maxHour);
            sb.append("<div class='hrb' style='height:").append(Math.max(pct, 2)).append("%'></div>");
        }
        sb.append("</div><div><span class='lbl'>0h</span><span class='lbl'>6h</span>")
          .append("<span class='lbl'>12h</span><span class='lbl'>18h</span><span class='lbl'>24h</span></div>");

        // Day of week bars.
        String[] dowNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        long[] dow = new long[7];
        long maxDow = 1;
        try (var c = db.listensByDayOfWeek()) {
            while (c.moveToNext()) {
                int d = c.getInt(0);
                if (d >= 0 && d < 7) {
                    dow[d] = c.getLong(1);
                    maxDow = Math.max(maxDow, dow[d]);
                }
            }
        }
        sb.append("<h2>Your week</h2>");
        for (int i = 0; i < 7; i++) {
            int pct = (int) (dow[i] * 100 / maxDow);
            sb.append("<div style='margin:3px 0'><span class='lbl'>").append(dowNames[i]).append("</span>")
              .append("<div class='bar' style='width:").append(Math.max(pct, 1)).append("%'></div></div>");
        }

        sb.append("<div class='foot'>Music Wrapped &bull; generated locally by ReVanced</div></body></html>");
        return sb.toString();
    }

    private static String play(long n) {
        return n == 1 ? " play" : " plays";
    }

    private static void showDialog(Context context, String html) {
        WebView webView = new WebView(context);
        webView.setBackgroundColor(Color.parseColor("#12081f"));
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#12081f"));
        container.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Music Wrapped")
                .setView(container)
                .setPositiveButton(android.R.string.ok, null)
                .show();

        // Near-fullscreen dialog.
        var window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (context.getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
    }
}

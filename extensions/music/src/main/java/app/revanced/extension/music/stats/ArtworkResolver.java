package app.revanced.extension.music.stats;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;

/**
 * Resolves artwork for a track or an artist from public music catalogues.
 *
 * <p>Matches are strict: the catalogue entry must carry the same track title and
 * the same artist as the report shows, so a cover is never borrowed from a
 * different song. When nothing matches exactly, no image is used at all.
 */
final class ArtworkResolver {
    private ArtworkResolver() {}

    interface Callback {
        void onArtwork(String url);
    }

    /**
     * One worker, at the lowest priority: artwork lookups must never compete with
     * audio playback for CPU.
     */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "wrapped-artwork");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> MISSES = new ConcurrentHashMap<>();

    /** Resolves the cover of a track. */
    static void resolveTrack(String artist, String title, Callback callback) {
        resolve(artist, title, false, callback);
    }

    /** Resolves the picture of an artist. */
    static void resolveArtist(String artist, Callback callback) {
        resolve(artist, null, true, callback);
    }

    private static void resolve(String artist, String title, boolean artistOnly,
                                Callback callback) {
        final String key = (artistOnly ? "a|" : "t|") + artist + "|" + (title == null ? "" : title);
        String cached = CACHE.get(key);
        if (cached != null) {
            deliver(callback, cached);
            return;
        }
        if (MISSES.containsKey(key)) return; // already looked, nothing matched

        EXECUTOR.execute(() -> {
            String url = null;
            try {
                if (artistOnly) {
                    url = itunesArtist(artist);
                    if (url == null) url = deezerArtist(artist);
                    if (url == null) url = musicBrainzArtist(artist);
                } else {
                    url = itunesTrack(artist, title);
                    if (url == null) url = deezerTrack(artist, title);
                }
            } catch (Exception ex) {
                Logger.printInfo(() -> "Artwork lookup failed: " + ex.getMessage());
            }
            if (url != null) {
                CACHE.put(key, url);
                deliver(callback, url);
            } else {
                MISSES.put(key, Boolean.TRUE);
            }
        });
    }

    private static void deliver(Callback callback, String url) {
        if (callback == null) return;
        Utils.runOnMainThread(() -> callback.onArtwork(url));
    }

    // --- iTunes ---

    private static String itunesTrack(String artist, String title) throws JSONException {
        JSONArray results = search("https://itunes.apple.com/search?term="
                + encode((artist == null ? "" : artist + " ") + (title == null ? "" : title))
                + "&entity=song&limit=25");
        if (results == null) return null;

        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (!titleMatches(item.optString("trackName", ""), title)) continue;
            if (!artistMatches(item.optString("artistName", ""), artist)) continue;
            String artwork = item.optString("artworkUrl100", "");
            if (!artwork.isEmpty()) return artwork.replace("100x100", "600x600");
        }
        return null;
    }

    private static String itunesArtist(String artist) throws JSONException {
        JSONArray results = search("https://itunes.apple.com/search?term=" + encode(artist)
                + "&entity=musicArtist&limit=10");
        if (results == null) return null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (!artistMatches(item.optString("artistName", ""), artist)) continue;
            String artwork = item.optString("artworkUrl100", "");
            if (!artwork.isEmpty()) return artwork.replace("100x100", "600x600");
        }
        return null;
    }

    // --- Deezer ---

    private static String deezerTrack(String artist, String title) throws JSONException {
        JSONObject json = getJson("https://api.deezer.com/search?q="
                + encode((artist == null ? "" : artist + " ") + (title == null ? "" : title))
                + "&limit=25");
        if (json == null) return null;
        JSONArray data = json.optJSONArray("data");
        if (data == null) return null;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            JSONObject itemArtist = item.optJSONObject("artist");
            if (!titleMatches(item.optString("title", ""), title)) continue;
            if (!artistMatches(itemArtist == null ? "" : itemArtist.optString("name", ""),
                    artist)) continue;
            JSONObject album = item.optJSONObject("album");
            if (album == null) continue;
            String cover = pickCover(album);
            if (cover != null) return cover;
        }
        return null;
    }

    private static String deezerArtist(String artist) throws JSONException {
        JSONObject json = getJson("https://api.deezer.com/search/artist?q="
                + encode(artist == null ? "" : artist) + "&limit=10");
        if (json == null) return null;
        JSONArray data = json.optJSONArray("data");
        if (data == null) return null;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.getJSONObject(i);
            if (!artistMatches(item.optString("name", ""), artist)) continue;
            String picture = item.optString("picture_xl", "");
            if (picture.isEmpty()) picture = item.optString("picture_medium", "");
            if (!picture.isEmpty()) return picture;
        }
        return null;
    }

    private static String pickCover(JSONObject album) {
        String cover = album.optString("cover_xl", "");
        if (cover.isEmpty()) cover = album.optString("cover_big", "");
        if (cover.isEmpty()) cover = album.optString("cover_medium", "");
        return cover.isEmpty() ? null : cover;
    }

    // --- MusicBrainz ---

    private static String musicBrainzArtist(String artist) throws JSONException {
        JSONObject json = getJson("https://musicbrainz.org/ws/2/artist?query="
                + encode(artist == null ? "" : artist) + "&fmt=json&limit=10");
        if (json == null) return null;
        JSONArray artists = json.optJSONArray("artists");
        if (artists == null) return null;
        for (int i = 0; i < artists.length(); i++) {
            JSONObject item = artists.getJSONObject(i);
            if (!artistMatches(item.optString("name", ""), artist)) continue;
            String id = item.optString("id", "");
            if (!id.isEmpty()) return "https://coverartarchive.org/artist/" + id + "/front-500";
        }
        return null;
    }

    // --- strict matching ---

    /** Lower-case, without bracketed extras or punctuation. */
    private static String normalise(String value) {
        if (value == null) return "";
        String text = value.toLowerCase();
        text = text.replaceAll("\\([^)]*\\)", " ");
        text = text.replaceAll("\\[[^]]*\\]", " ");
        text = text.replaceAll("[^a-z0-9\\u00c0-\\u024f ]", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private static List<String> tokens(String value) {
        List<String> words = new ArrayList<>();
        for (String word : normalise(value).split(" ")) {
            if (word.length() > 2 && !word.equals("and") && !word.equals("feat")
                    && !word.equals("the") && !word.equals("with")) {
                words.add(word);
            }
        }
        return words;
    }

    /** The catalogue title must be the same song we show in the report. */
    private static boolean titleMatches(String candidate, String target) {
        String a = normalise(candidate);
        String b = normalise(target);
        if (b.isEmpty()) return false;
        if (a.equals(b)) return true;
        List<String> want = tokens(target);
        List<String> have = tokens(candidate);
        if (want.isEmpty()) return false;
        int hits = 0;
        for (String word : want) {
            if (have.contains(word)) hits++;
        }
        return hits == want.size();
    }

    /** The catalogue artist must be the same artist we show in the report. */
    private static boolean artistMatches(String candidate, String target) {
        String a = normalise(candidate);
        String b = normalise(target);
        if (b.isEmpty()) return false;
        if (a.equals(b)) return true;

        List<String> want = tokens(target);
        List<String> have = tokens(candidate);
        if (want.isEmpty()) return false;
        int hits = 0;
        for (String word : want) {
            if (have.contains(word)) hits++;
        }
        if (hits == want.size()) return true;

        // A collaboration ("Bonobo & Pink Floyd") is still that artist: accept the
        // catalogue entry when it names one of the artists involved.
        for (String part : target.split("[&,+]| feat | ft ")) {
            if (part.trim().isEmpty()) continue;
            if (normalise(part).equals(a)) return true;
            List<String> partTokens = tokens(part);
            if (partTokens.isEmpty()) continue;
            int partHits = 0;
            for (String word : partTokens) {
                if (have.contains(word)) partHits++;
            }
            if (partHits == partTokens.size()) return true;
        }
        return false;
    }

    // --- plumbing ---

    private static JSONArray search(String url) throws JSONException {
        JSONObject json = getJson(url);
        if (json == null) return null;
        return json.optJSONArray("results");
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ex) {
            return value.replace(' ', '+');
        }
    }

    private static JSONObject getJson(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ReVanced-Wrapped/1.0");
            if (connection.getResponseCode() / 100 != 2) return null;
            try (InputStream in = connection.getInputStream()) {
                StringBuilder builder = new StringBuilder();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    builder.append(new String(buffer, 0, read));
                }
                return new JSONObject(builder.toString());
            }
        } catch (Exception ex) {
            Logger.printInfo(() -> "Artwork catalogue unreachable: " + ex.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

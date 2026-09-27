package app.coffeebreak.anaesthesia;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps the local archive of issues and syncs it with the public feed on GitHub.
 * The archive lives in the app's private storage, so every issue ever received
 * stays readable offline even after it drops out of the feed.
 */
public final class FeedStore {

    public static final String FEED_URL =
            "https://raw.githubusercontent.com/SCGHanaesthesia/coffee-break-anaesthesia/main/feed.json";
    public static final String REPO_URL =
            "https://github.com/SCGHanaesthesia/coffee-break-anaesthesia";

    static final String PREFS = "coffeebreak";
    private static final String ARCHIVE_FILE = "archive.json";

    private FeedStore() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Returns the archive JSON: {"app":{...},"issues":[...newest first]}. Seeds from the bundled issue on first run. */
    public static synchronized String readArchive(Context c) {
        File f = new File(c.getFilesDir(), ARCHIVE_FILE);
        try {
            if (f.exists()) {
                return readAll(new FileInputStream(f));
            }
            String seed = readAll(c.getAssets().open("seed.json"));
            JSONObject seedObj = new JSONObject(seed);
            writeArchive(c, seedObj);
            // Don't notify for the issue that ships with the app.
            JSONArray issues = seedObj.optJSONArray("issues");
            if (issues != null && issues.length() > 0) {
                prefs(c).edit().putString("notified_id", issues.getJSONObject(0).optString("id")).apply();
            }
            return seedObj.toString();
        } catch (Exception e) {
            return "{\"issues\":[]}";
        }
    }

    private static void writeArchive(Context c, JSONObject archive) throws Exception {
        File tmp = new File(c.getFilesDir(), ARCHIVE_FILE + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(archive.toString().getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
        File dest = new File(c.getFilesDir(), ARCHIVE_FILE);
        if (!tmp.renameTo(dest)) {
            throw new Exception("Could not save archive");
        }
    }

    /**
     * Downloads the feed and merges it into the archive.
     * Returns {"ok":bool,"newIssues":[ids],"latestId":..,"error":..,"checkedAt":millis}.
     */
    public static synchronized JSONObject sync(Context c) {
        JSONObject result = new JSONObject();
        long now = System.currentTimeMillis();
        try {
            result.put("checkedAt", now);
            JSONObject archive = new JSONObject(readArchive(c));
            JSONArray existing = archive.optJSONArray("issues");
            if (existing == null) existing = new JSONArray();

            String body = httpGet(FEED_URL + "?t=" + (now / 60000L));
            JSONObject feed = new JSONObject(body);
            JSONArray incoming = feed.optJSONArray("issues");
            if (incoming == null) incoming = new JSONArray();

            List<JSONObject> merged = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < existing.length(); i++) {
                JSONObject is = existing.getJSONObject(i);
                merged.add(is);
                ids.add(is.optString("id"));
            }
            JSONArray newIds = new JSONArray();
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject is = incoming.getJSONObject(i);
                String id = is.optString("id");
                if (id.isEmpty()) continue;
                int at = ids.indexOf(id);
                if (at >= 0) {
                    merged.set(at, is); // corrections to an existing issue replace it
                } else {
                    merged.add(is);
                    ids.add(id);
                    newIds.put(id);
                }
            }
            Collections.sort(merged, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return b.optString("id").compareTo(a.optString("id"));
                }
            });
            JSONArray out = new JSONArray();
            for (JSONObject is : merged) out.put(is);
            archive.put("issues", out);
            if (feed.has("app")) archive.put("app", feed.getJSONObject("app"));
            writeArchive(c, archive);

            prefs(c).edit()
                    .putLong("last_check", now)
                    .putLong("last_success", now)
                    .remove("last_error")
                    .apply();

            result.put("ok", true);
            result.put("newIssues", newIds);
            result.put("latestId", out.length() > 0 ? out.getJSONObject(0).optString("id") : "");
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
            prefs(c).edit().putLong("last_check", now).putString("last_error", msg).apply();
            try {
                result.put("ok", false);
                result.put("error", msg);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    static JSONObject latestIssue(Context c) {
        try {
            JSONArray issues = new JSONObject(readArchive(c)).optJSONArray("issues");
            if (issues != null && issues.length() > 0) return issues.getJSONObject(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String httpGet(String u) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setUseCaches(false);
        try {
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            return readAll(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    static String readAll(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}

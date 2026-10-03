package com.annetv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChannelRepository {
    public interface Callback {
        void onChannels(List<Channel> channels);
    }

    private static final String PREFS = "anne_tv_channels";
    private static final String KEY_CACHE = "manifest_cache";
    private final Context context;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public ChannelRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public void load(Callback callback) {
        io.execute(() -> {
            List<Channel> initial = loadCached();
            if (initial.isEmpty()) initial = loadBundled();
            List<Channel> first = initial;
            main.post(() -> callback.onChannels(first));

            String remote = BuildConfig.CHANNELS_MANIFEST_URL.trim();
            if (TextUtils.isEmpty(remote)) {
                remote = context.getString(R.string.channels_manifest_url).trim();
            }
            if (TextUtils.isEmpty(remote)) return;

            String body = fetch(remote);
            if (body == null) return;
            List<Channel> fresh = parse(body);
            if (fresh.isEmpty()) return;

            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_CACHE, body).apply();
            main.post(() -> callback.onChannels(fresh));
        });
    }

    private List<Channel> loadCached() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return parse(prefs.getString(KEY_CACHE, ""));
    }

    private List<Channel> loadBundled() {
        try (InputStream in = context.getAssets().open("channels.json")) {
            return parse(readAll(in));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private String fetch(String urlString) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlString).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("User-Agent", "AnneTV/" + BuildConfig.VERSION_NAME);
            if (c.getResponseCode() / 100 != 2) return null;
            return readAll(c.getInputStream());
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String readAll(InputStream input) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) out.append(line).append('\n');
        return out.toString();
    }

    private static List<String> readStreams(JSONObject o) {
        List<String> streams = new ArrayList<>();
        JSONArray urls = o.optJSONArray("streamUrls");
        if (urls != null) {
            for (int i = 0; i < urls.length(); i++) {
                String value = urls.optString(i, "").trim();
                if (!value.isEmpty() && !streams.contains(value)) streams.add(value);
            }
        }
        String legacy = o.optString("streamUrl", "").trim();
        if (!legacy.isEmpty() && !streams.contains(legacy)) streams.add(legacy);
        return streams;
    }

    private static List<Channel> parse(String json) {
        if (TextUtils.isEmpty(json)) return Collections.emptyList();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray array = root.getJSONArray("channels");
            List<Channel> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                if (!o.optBoolean("enabled", true)) continue;
                Channel c = new Channel(
                        o.optString("id", "ch-" + i),
                        o.optInt("number", i + 1),
                        o.optString("name", "Kanal " + (i + 1)),
                        o.optString("group", "TV"),
                        o.optBoolean("favorite", false),
                        readStreams(o)
                );
                result.add(c);
            }
            Collections.sort(result, (left, right) -> Integer.compare(left.number, right.number));
            return result;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}

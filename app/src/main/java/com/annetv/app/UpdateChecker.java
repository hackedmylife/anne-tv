package com.annetv.app;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateChecker {
    private final MainActivity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private UpdateInfo pendingAfterPermission;
    private boolean waitingForInstallPermission;

    public UpdateChecker(MainActivity activity) {
        this.activity = activity;
    }

    public void check() {
        String manifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim();
        if (TextUtils.isEmpty(manifestUrl)) {
            manifestUrl = activity.getString(R.string.update_manifest_url).trim();
        }
        if (TextUtils.isEmpty(manifestUrl)) return;

        final String url = manifestUrl;
        io.execute(() -> {
            UpdateInfo info = fetch(url);
            if (info == null || info.versionCode <= BuildConfig.VERSION_CODE) return;
            if (TextUtils.isEmpty(info.apkUrl)) return;
            activity.runOnUiThread(() -> showPrompt(info));
        });
    }

    public void onHostResume() {
        if (!waitingForInstallPermission || pendingAfterPermission == null) return;
        waitingForInstallPermission = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls()) {
            UpdateInfo info = pendingAfterPermission;
            pendingAfterPermission = null;
            download(info);
        } else {
            pendingAfterPermission = null;
        }
    }

    private void showPrompt(UpdateInfo info) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available)
                .setMessage(activity.getString(R.string.update_message) + "\n\n" + info.versionName)
                .setPositiveButton(R.string.update_now, (d, w) -> download(info))
                .setNegativeButton(R.string.later, null)
                .show();
    }

    private void download(UpdateInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            pendingAfterPermission = info;
            waitingForInstallPermission = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return;
        }

        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        String fileName = "AnneTV-" + info.versionName + ".apk";
        File apk = new File(dir, fileName);
        if (apk.exists()) apk.delete();

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("Anne TV " + info.versionName)
                .setDescription(activity.getString(R.string.update_downloading))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);
        long id = dm.enqueue(req);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                long done = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (done != id) return;
                try { activity.unregisterReceiver(this); } catch (Exception ignored) {}

                if (!downloadSucceeded(dm, id) || !apk.isFile()) {
                    Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                    return;
                }

                io.execute(() -> {
                    if (!TextUtils.isEmpty(info.sha256)) {
                        String actual = sha256(apk);
                        if (!info.sha256.equalsIgnoreCase(actual)) {
                            apk.delete();
                            activity.runOnUiThread(() -> Toast.makeText(activity,
                                    R.string.update_hash_failed, Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                    activity.runOnUiThread(() -> install(apk));
                });
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
    }

    private boolean downloadSucceeded(DownloadManager dm, long id) {
        try (Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor != null && cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                return statusIndex >= 0 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void install(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".files", apk);
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
        } catch (Exception ignored) {
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show();
        }
    }

    private UpdateInfo fetch(String urlString) {
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
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) s.append(line);
            JSONObject o = new JSONObject(s.toString());
            return new UpdateInfo(o.getInt("versionCode"), o.optString("versionName", ""),
                    o.optString("apkUrl", ""), o.optString("sha256", ""));
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private String sha256(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) d.update(b, 0, n);
            StringBuilder out = new StringBuilder();
            for (byte x : d.digest()) out.append(String.format(Locale.US, "%02x", x));
            return out.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String sha256;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String sha256) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }
}

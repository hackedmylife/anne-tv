package com.annetv.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public final class AdminActivity extends AppCompatActivity {
    private static final String ANDROID_SETTINGS_PACKAGE = "com.android.settings";

    private SharedPreferences preferences;
    private LinearLayout actions;
    private TextView status;
    private TextView autoStartAction;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(BootReceiver.PREFS, Context.MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0A0A0A);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(48), dp(36), dp(48), dp(48));
        scroll.addView(actions, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText(R.string.admin_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        actions.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.admin_subtitle);
        subtitle.setTextColor(0xB3FFFFFF);
        subtitle.setTextSize(17);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        actions.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextColor(0xFFE6E6E6);
        status.setTextSize(17);
        status.setPadding(0, 0, 0, dp(18));
        actions.addView(status, new LinearLayout.LayoutParams(-1, -2));

        autoStartAction = addAction("", v -> toggleAutoStart());
        TextView homeAction = addAction(getString(R.string.admin_home_settings), v -> openHomeSettings());
        addAction(getString(R.string.admin_app_settings), v -> openApplicationSettings());
        addAction(getString(R.string.admin_unknown_sources), v -> openUnknownSources());
        addAction(getString(R.string.admin_android_settings), v -> openAndroidSettings());
        addAction(getString(R.string.admin_return_to_tv), v -> finish());

        setContentView(scroll);
        refreshStatus();
        homeAction.requestFocus();
    }

    private TextView addAction(String label, View.OnClickListener listener) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setTextColor(Color.WHITE);
        row.setTextSize(20);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(20), dp(16), dp(20), dp(16));
        row.setBackgroundColor(0xFF1B1B1B);
        row.setOnClickListener(listener);
        row.setOnFocusChangeListener((view, focused) -> {
            TextView text = (TextView) view;
            if (focused) {
                text.setBackgroundColor(Color.WHITE);
                text.setTextColor(Color.BLACK);
            } else {
                text.setBackgroundColor(0xFF1B1B1B);
                text.setTextColor(Color.WHITE);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(64));
        params.setMargins(0, 0, 0, dp(10));
        actions.addView(row, params);
        return row;
    }

    private void refreshStatus() {
        if (status == null || autoStartAction == null) return;
        boolean autoStart = preferences.getBoolean(BootReceiver.KEY_AUTO_START, true);
        boolean isHome = isAnneTvDefaultHome();
        boolean canInstall = canRequestPackageInstalls();

        status.setText(getString(
                R.string.admin_status,
                autoStart ? getString(R.string.admin_enabled) : getString(R.string.admin_disabled),
                isHome ? getString(R.string.admin_home_anne_tv) : getString(R.string.admin_home_other),
                canInstall ? getString(R.string.admin_permission_granted) : getString(R.string.admin_permission_not_granted)));

        autoStartAction.setText(autoStart
                ? getString(R.string.admin_disable_autostart)
                : getString(R.string.admin_enable_autostart));
    }

    private boolean isAnneTvDefaultHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return resolved != null
                    && resolved.activityInfo != null
                    && getPackageName().equals(resolved.activityInfo.packageName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean canRequestPackageInstalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        try {
            return getPackageManager().canRequestPackageInstalls();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void toggleAutoStart() {
        boolean current = preferences.getBoolean(BootReceiver.KEY_AUTO_START, true);
        preferences.edit().putBoolean(BootReceiver.KEY_AUTO_START, !current).apply();
        refreshStatus();
        Toast.makeText(this,
                !current ? R.string.admin_autostart_enabled_toast : R.string.admin_autostart_disabled_toast,
                Toast.LENGTH_SHORT).show();
    }

    private void openHomeSettings() {
        Intent direct = new Intent(Settings.ACTION_HOME_SETTINGS);
        direct.setPackage(ANDROID_SETTINGS_PACKAGE);
        if (tryStart(direct)) return;

        if (tryStart(new Intent(Settings.ACTION_HOME_SETTINGS))) return;

        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        Intent chooser = Intent.createChooser(home, getString(R.string.admin_home_chooser_title));
        if (tryStart(chooser)) return;

        showUnavailable();
    }

    private void openApplicationSettings() {
        Uri packageUri = Uri.parse("package:" + getPackageName());

        Intent direct = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
        direct.setPackage(ANDROID_SETTINGS_PACKAGE);
        if (tryStart(direct)) return;

        if (tryExplicitSettings(
                "com.android.settings.Settings$AppInfoDashboardActivity",
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                packageUri)) return;

        if (tryExplicitSettings(
                "com.android.settings.Settings$ManageApplicationsActivity",
                Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                null)) return;

        Intent standard = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri);
        if (tryStart(standard)) return;

        showUnavailable();
    }

    private void openUnknownSources() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            openApplicationSettings();
            return;
        }

        Uri packageUri = Uri.parse("package:" + getPackageName());

        Intent direct = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri);
        direct.setPackage(ANDROID_SETTINGS_PACKAGE);
        if (tryStart(direct)) return;

        if (tryExplicitSettings(
                "com.android.settings.Settings$ManageExternalSourcesActivity",
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                packageUri)) return;

        Intent standard = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri);
        if (tryStart(standard)) return;

        showUnavailable();
    }

    private void openAndroidSettings() {
        Intent direct = new Intent(Settings.ACTION_SETTINGS);
        direct.setPackage(ANDROID_SETTINGS_PACKAGE);
        if (tryStart(direct)) return;

        if (tryExplicitSettings("com.android.settings.Settings", Settings.ACTION_SETTINGS, null)) return;

        if (!tryStart(new Intent(Settings.ACTION_SETTINGS))) showUnavailable();
    }

    private boolean tryExplicitSettings(String className, String action, Uri data) {
        Intent intent = new Intent(action);
        intent.setComponent(new ComponentName(ANDROID_SETTINGS_PACKAGE, className));
        if (data != null) intent.setData(data);
        return tryStart(intent);
    }

    private void showUnavailable() {
        Toast.makeText(this, R.string.admin_settings_unavailable, Toast.LENGTH_LONG).show();
    }

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

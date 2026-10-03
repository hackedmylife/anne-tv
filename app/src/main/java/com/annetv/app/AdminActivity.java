package com.annetv.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public final class AdminActivity extends AppCompatActivity {
    private static final String PREFS = "anne_tv_admin";
    private static final String KEY_AUTO_START = "auto_start_enabled";

    private TextView homeStatus;
    private Button autoStartButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(56), dp(42), dp(56), dp(42));
        root.setBackgroundColor(Color.rgb(12, 12, 12));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText(R.string.admin_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText(R.string.admin_hint);
        hint.setTextColor(0xFFBDBDBD);
        hint.setTextSize(17);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(0, dp(12), 0, dp(28));
        root.addView(hint, hintParams);

        homeStatus = new TextView(this);
        homeStatus.setTextColor(Color.WHITE);
        homeStatus.setTextSize(18);
        homeStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, 0, 0, dp(20));
        root.addView(homeStatus, statusParams);

        Button chooseHome = createButton(R.string.admin_choose_home);
        chooseHome.setOnClickListener(v -> openHomeSettings());
        root.addView(chooseHome, buttonParams());

        autoStartButton = createButton(R.string.admin_auto_start_on);
        autoStartButton.setOnClickListener(v -> toggleAutoStart());
        root.addView(autoStartButton, buttonParams());

        Button changeHome = createButton(R.string.admin_restore_home);
        changeHome.setOnClickListener(v -> openHomeSettings());
        root.addView(changeHome, buttonParams());

        Button back = createButton(R.string.admin_back_to_tv);
        back.setOnClickListener(v -> finish());
        root.addView(back, buttonParams());

        chooseHome.requestFocus();
    }

    private Button createButton(int textRes) {
        Button button = new Button(this);
        button.setText(textRes);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setMinHeight(dp(56));
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(560), dp(64));
        params.setMargins(0, dp(7), 0, dp(7));
        return params;
    }

    private void refreshState() {
        if (homeStatus == null || autoStartButton == null) return;
        boolean isHome = isAnneTvDefaultHome();
        homeStatus.setText(isHome ? R.string.admin_home_active : R.string.admin_home_inactive);

        boolean autoStart = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_START, true);
        autoStartButton.setText(autoStart ? R.string.admin_auto_start_on : R.string.admin_auto_start_off);
    }

    private boolean isAnneTvDefaultHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && getPackageName().equals(info.activityInfo.packageName);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void toggleAutoStart() {
        boolean current = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_START, true);
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_AUTO_START, !current).apply();
        refreshState();
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            return;
        } catch (ActivityNotFoundException ignored) {
            // Some TV firmwares hide the dedicated Home settings page.
        }

        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            startActivity(Intent.createChooser(home, getString(R.string.admin_choose_home)));
        } catch (Exception ignored) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignoredAgain) {
                // Firmware exposes no compatible settings surface.
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

package com.annetv.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public final class BootReceiver extends BroadcastReceiver {
    static final String PREFS = "anne_tv_admin";
    static final String KEY_AUTO_START = "auto_start_enabled";

    private static final String ACTION_BOOT_RETRY = "com.annetv.app.action.BOOT_RETRY";
    private static final int[] RETRY_REQUESTS = {4101, 4102, 4103, 4104};
    private static final long[] RETRY_DELAYS_MS = {5000L, 15000L, 30000L, 45000L};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        boolean bootEvent = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action))
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && Intent.ACTION_USER_UNLOCKED.equals(action))
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
        boolean retryEvent = ACTION_BOOT_RETRY.equals(action);
        if (!bootEvent && !retryEvent) return;

        if (!isAutoStartEnabled(context)) {
            cancelRetries(context);
            return;
        }

        launchAnneTv(context);

        // Onvo-style TV firmware may force its own launcher after the first
        // boot broadcast. Schedule several bounded retries so Anne TV can take
        // over after the OEM launcher has finished its cold-start sequence.
        if (bootEvent) {
            for (int i = 0; i < RETRY_REQUESTS.length; i++) {
                scheduleRetry(context, RETRY_REQUESTS[i], RETRY_DELAYS_MS[i]);
            }
        }
    }

    private static boolean isAutoStartEnabled(Context context) {
        // Direct-boot broadcasts can arrive before credential-encrypted storage
        // is available. AdminActivity mirrors the preference into device-
        // protected storage so the receiver can read it during cold boot.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Context directBoot = context.createDeviceProtectedStorageContext();
                if (directBoot.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .contains(KEY_AUTO_START)) {
                    return directBoot.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .getBoolean(KEY_AUTO_START, true);
                }
            } catch (RuntimeException ignored) {
                // Fall back to the normal preference/default below.
            }
        }

        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_AUTO_START, true);
        } catch (RuntimeException ignored) {
            // During locked boot credential storage may be unavailable. Auto-
            // start defaults to enabled, matching the product's intended mode.
            return true;
        }
    }

    private static void launchAnneTv(Context context) {
        try {
            Intent launch = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(launch);
        } catch (RuntimeException ignored) {
            // The TV may temporarily reject activity launches while early boot
            // is still progressing. AlarmManager retries handle that window.
        }
    }

    private static void scheduleRetry(Context context, int requestCode, long delayMs) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent retry = new Intent(context, BootReceiver.class).setAction(ACTION_BOOT_RETRY);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                retry,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt = SystemClock.elapsedRealtime() + delayMs;
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
        } catch (RuntimeException ignored) {
            try {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent);
            } catch (RuntimeException ignoredAgain) {
                // HOME role and the other boot broadcasts remain as fallbacks.
            }
        }
    }

    static void cancelRetries(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        for (int requestCode : RETRY_REQUESTS) {
            cancelRetry(context, alarmManager, requestCode);
        }
    }

    private static void cancelRetry(Context context, AlarmManager alarmManager, int requestCode) {
        Intent retry = new Intent(context, BootReceiver.class).setAction(ACTION_BOOT_RETRY);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                retry,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) alarmManager.cancel(pendingIntent);
    }
}

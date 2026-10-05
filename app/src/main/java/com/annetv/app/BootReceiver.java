package com.annetv.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public final class BootReceiver extends BroadcastReceiver {
    static final String PREFS = "anne_tv_admin";
    static final String KEY_AUTO_START = "auto_start_enabled";

    private static final String ACTION_BOOT_RETRY = "com.annetv.app.action.BOOT_RETRY";
    private static final int RETRY_ONE_REQUEST = 4101;
    private static final int RETRY_TWO_REQUEST = 4102;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        boolean bootEvent = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);
        boolean retryEvent = ACTION_BOOT_RETRY.equals(action);
        if (!bootEvent && !retryEvent) return;

        boolean enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_AUTO_START, true);
        if (!enabled) {
            cancelRetries(context);
            return;
        }

        launchAnneTv(context);

        // Some TV firmwares start their own launcher after BOOT_COMPLETED and
        // cover third-party HOME apps. Retry after the OEM launcher has settled.
        if (bootEvent) {
            scheduleRetry(context, RETRY_ONE_REQUEST, 8000L);
            scheduleRetry(context, RETRY_TWO_REQUEST, 18000L);
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
            // OEM firmware may temporarily reject launches while boot is still
            // progressing. Delayed AlarmManager retries provide the fallback.
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
                // The immediate launch and HOME role remain as fallbacks.
            }
        }
    }

    static void cancelRetries(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        cancelRetry(context, alarmManager, RETRY_ONE_REQUEST);
        cancelRetry(context, alarmManager, RETRY_TWO_REQUEST);
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

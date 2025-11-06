package com.android.httpserver.util;

import static android.content.Context.NOTIFICATION_SERVICE;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.android.httpserver.R;

public class NotificationHelper {

    private Context context;
    private static final String PREFS_NAME = "prefs";
    private static final String KEY_FIRST_LAUNCH = "isFirstLaunch";

    public NotificationHelper(Context context) {
        this.context = context;

        if (isFirstLaunch()) {
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(KEY_FIRST_LAUNCH, false);
            editor.apply();
            showNoticeDialog();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NotificationChannelProperties.DEFAULT_DOWNLOAD_CHANNEL_ID,
                    NotificationChannelProperties.DEFAULT_DOWNLOAD_CHANNEL_NAME,
                    NotificationChannelProperties.DEFAULT_DOWNLOAD_CHANNEL_IMPORTANCE
            );

            channel.setDescription(NotificationChannelProperties.DEFAULT_DOWNLOAD_CHANNEL_DESCRIPTION);
            android.app.NotificationManager notificationManager = context.getSystemService(android.app.NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private boolean isFirstLaunch() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    private void showNoticeDialog() {
        new AlertDialog.Builder(context)
                .setTitle("Notification Permission")
                .setMessage("App requires notification permission to send download notifications")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }


    public void notifyDownloadStartedDefault(String fileName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannelProperties.DEFAULT_DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_language)
                .setContentTitle("Download Started")
                .setContentText(fileName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }

    public void notifyDownloadStartedSilent(String fileName) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannelProperties.SILENT_DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_language)
                .setContentTitle("Download Started")
                .setContentText(fileName)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }
}

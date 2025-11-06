package com.android.httpserver.util;

import android.app.NotificationManager;

public final class NotificationChannelProperties {

    // DEFAULT Channel
    public static final String DEFAULT_DOWNLOAD_CHANNEL_ID = "download_channel_default";
    public static final int DEFAULT_DOWNLOAD_CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT;
    public static final CharSequence DEFAULT_DOWNLOAD_CHANNEL_NAME = "Download Notifications";
    public static final String DEFAULT_DOWNLOAD_CHANNEL_DESCRIPTION = "Notify when file download begins";

    // SILENT channel
    public static final String SILENT_DOWNLOAD_CHANNEL_ID = "download_channel_silent";
    public static final int SILENT_DOWNLOAD_CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_LOW;
    public static final CharSequence SILENT_DOWNLOAD_CHANNEL_NAME = "Silent Download Notifications";
    public static final String SILENT_DOWNLOAD_CHANNEL_DESCRIPTION = "Notify when file download begins";

}

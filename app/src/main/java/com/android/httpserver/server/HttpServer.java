package com.android.httpserver.server;

import static android.content.ContentValues.TAG;
import static com.android.httpserver.MainActivity.fileMap;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.android.httpserver.Constants;
import com.android.httpserver.MainActivity;
import com.android.httpserver.R;
import com.android.httpserver.component.HistoryViewModel;
import com.android.httpserver.model.FileInfo;
import com.android.httpserver.model.History;
import com.android.httpserver.response.AcceptPost;
import com.android.httpserver.response.BadRequest;
import com.android.httpserver.response.InternalServerError;
import com.android.httpserver.response.NoContent;
import com.android.httpserver.response.NotFound;
import com.android.httpserver.response.Accept;
import com.android.httpserver.util.NotificationHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public final class HttpServer extends NanoHTTPD {

    private Context context;
    private final ContentResolver contentResolver;
    private HistoryViewModel historyViewModel;
    private NotificationHelper notificationHelper;
    private static HttpServer INSTANCE;

    private HttpServer(Context context, int port, ContentResolver contentResolver, HistoryViewModel historyViewModel, NotificationHelper notificationHelper) {
        super(port);
        this.context = context;
        this.contentResolver = contentResolver;
        this.historyViewModel = historyViewModel;
        this.notificationHelper = notificationHelper;
    }

    public static HttpServer getInstance(Context context, int port, ContentResolver contentResolver, HistoryViewModel historyViewModel, NotificationHelper notificationHelper) {
        if (INSTANCE == null) {
            INSTANCE = new HttpServer(
                    context,
                    port,
                    contentResolver,
                    historyViewModel,
                    notificationHelper
            );
        }
        return INSTANCE;
    }


    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri().substring(0, session.getUri().lastIndexOf('/')+1);

        InputStream notFoundStream = null;
        InputStream noContentStream = null;
        InputStream serverErrorStream = null;
        InputStream badRequestStream = null;
        InputStream okStream = null;
        InputStream okPostStream =  null;

        try {
            okStream = context.getAssets().open("200");
            okPostStream = context.getAssets().open("post");
            notFoundStream = context.getAssets().open("404");
            noContentStream = context.getAssets().open("204");
            serverErrorStream = context.getAssets().open("500");
            badRequestStream = context.getAssets().open("400");
        } catch (IOException e) {
            return new InternalServerError(e.getMessage(), MimeTypes.TEXT_PLAIN).build();
        }

        if(Method.GET.equals(method) && RequestPath.ROOT.equals(uri)) {

            if(fileMap.isEmpty() && !MainActivity.RECEIVE_MODE) {
                return new NoContent("Requested resource is not available", MimeTypes.TEXT_HTML).build(noContentStream);
            }

            try {

                if (MainActivity.RECEIVE_MODE) {
                    String okPostStringStream = stringStream(okPostStream);
                    return new AcceptPost(okPostStringStream, MimeTypes.TEXT_HTML).build();
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(okStream, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                String html = sb.toString();
                FileInfo fileInfo = null;
                String uid = "";
                for (Map.Entry<String, FileInfo> entry: fileMap.entrySet()) {
                    fileInfo = entry.getValue();
                    uid = entry.getKey();
                }

                if(fileInfo != null && uid.length() > 0) {
                    String fileName = fileInfo.getFileName();
                    String downloadUrl = RequestPath.DOWNLOAD+uid;
                    html = html.replace("{{filename}}", fileName);
                    html = html.replace("{{url}}", downloadUrl);
                    return new Accept(html, MimeTypes.TEXT_HTML).build();
                }

                return new NoContent("", MimeTypes.TEXT_HTML).build(noContentStream);

            } catch (IOException e) {
                return new InternalServerError(e.getMessage(), MimeTypes.TEXT_PLAIN).build(serverErrorStream, e.getClass().getSimpleName());
            }
        }

        if(Method.GET.equals(method) && RequestPath.DOWNLOAD.equals(uri)) {
            String fileUId = session.getUri().substring(session.getUri().lastIndexOf('/')+1);

            if(fileUId.trim().isEmpty()) {
                return new BadRequest("Missing fileUId", MimeTypes.TEXT_PLAIN).build(badRequestStream);
            }

            // check if fileMap is not empty
            if(fileMap.isEmpty() || !fileMap.containsKey(fileUId.trim())) {
                return new NotFound(MimeTypes.TEXT_HTML, "").build(notFoundStream);
            }

            FileInfo info = fileMap.get(fileUId.trim());
            Uri fileUri = info != null ? info.getUri() : null;
            String fileName = info != null ? info.getFileName() : "unknown";
            String fileSize = info != null ? info.getFileSize() : "0B";

            if(fileUri != null) {

                try {
                    InputStream inputStream = contentResolver.openInputStream(fileUri);
                    String mimeType = contentResolver.getType(fileUri);
                    if(mimeType == null) {
                        mimeType = MimeTypes.APPLICATION_OCTET_STREAM;
                    }
                    // remove entry from map
                    fileMap.clear();
                    notificationHelper.notifyDownloadStartedDefault(fileName);
                    saveHistory(fileName, fileSize, mimeType);
                    Response response = new Accept(null, mimeType).build(inputStream);
                    response.addHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                    return response;
                } catch (Exception e) {
                    return new InternalServerError(e.getMessage(), MimeTypes.TEXT_PLAIN).build(serverErrorStream, e.getClass().getSimpleName());
                }

            } else {
                return new NoContent("", MimeTypes.TEXT_HTML).build(noContentStream);
            }
        }

        if (Method.POST.equals(method) && RequestPath.UPLOAD.equals(uri)) {

            try {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                Map<String, List<String>> params = session.getParameters();
                String tempPath = files.get("file");
                if (tempPath == null) {
                    Log.e(TAG, ":$$: tempPath null");
                    return null;
                }
                File uploadedFile = new File(tempPath);
                SharedPreferences preferences = context.getSharedPreferences(Constants.SHARED_PREFERENCES, Context.MODE_PRIVATE);
                String uriString = preferences.getString(Constants.KEY_EXT_PATH_URI, null);
                if (uriString == null) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MimeTypes.TEXT_PLAIN, "ext_uri_null");
                }

                Uri locationUri = Uri.parse(uriString);
                DocumentFile pickedDir=DocumentFile.fromTreeUri(context, locationUri);
                String fileName = "unknown";
                if (params.containsKey("file")) {
                    fileName = params.get("file").get(0);
                }

                if (pickedDir == null) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MimeTypes.TEXT_PLAIN, "ext_uri_null");
                }

                DocumentFile newFile = pickedDir.createFile(
                        MimeTypes.APPLICATION_OCTET_STREAM,
                        fileName
                );
                try (
                        InputStream in = new FileInputStream(uploadedFile);
                        OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())
                ) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }

                saveHistory(fileName, "", MimeTypes.APPLICATION_OCTET_STREAM);
                notificationHelper.notifyUploadCompletedDefault(fileName);
                return newFixedLengthResponse(Response.Status.OK, MimeTypes.TEXT_PLAIN, "ok");

            } catch (Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MimeTypes.TEXT_PLAIN, e.getClass().getSimpleName());
            }
        }

        return new NotFound(MimeTypes.TEXT_HTML, "").build(notFoundStream);
    }

    private void saveHistory(String fileName, String fileSize, String mimeType) {
        int resId = -1;
        if(mimeType.equals(MimeTypes.TEXT_HTML) || mimeType.equals(MimeTypes.TEXT_PLAIN) || mimeType.equals(MimeTypes.TEXT_XML) || mimeType.equals(MimeTypes.APPLICATION_JSON)) {
            resId = R.drawable.ic_file_plain;
        } else if (mimeType.equals(MimeTypes.IMAGE_JPEG) || mimeType.equals(MimeTypes.IMAGE_PNG) || mimeType.equals(MimeTypes.IMAGE_GIF)) {
            resId = R.drawable.ic_file_image;
        } else if(mimeType.equals(MimeTypes.AUDIO_MPEG) || mimeType.equals(MimeTypes.AUDIO_WAV) || mimeType.equals(MimeTypes.AUDIO_OGG)) {
            resId = R.drawable.ic_file_audio;
        } else if(mimeType.equals(MimeTypes.VIDEO_MP4) || mimeType.equals(MimeTypes.VIDEO_MPEG) || mimeType.equals(MimeTypes.VIDEO_WEBM)) {
            resId = R.drawable.ic_file_video;
        } else if(mimeType.equals(MimeTypes.ANDROID_PACKAGE)) {
            resId = R.drawable.ic_file_android_package;
        } else {
            resId = R.drawable.ic_file_unknown;
        }

        String date = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date());
        History history = new History(fileName, fileSize, date, resId);
        this.historyViewModel.insert(history);
    }


    public String getIPAddress() {
        try {
            for(Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface inf = en.nextElement();
                for(Enumeration<InetAddress> enumIpAdd = inf.getInetAddresses(); enumIpAdd.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAdd.nextElement();
                    if(!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String stringStream(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
}

package com.android.httpserver.server;

import static com.android.httpserver.MainActivity.fileMap;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.android.httpserver.R;
import com.android.httpserver.component.HistoryViewModel;
import com.android.httpserver.model.FileInfo;
import com.android.httpserver.model.History;
import com.android.httpserver.response.BadRequest;
import com.android.httpserver.response.InternalServerError;
import com.android.httpserver.response.NoContent;
import com.android.httpserver.response.NotFound;
import com.android.httpserver.response.Accept;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {

    private Context context;
    private final ContentResolver contentResolver;
    private HistoryViewModel historyViewModel;

    public HttpServer(Context context, int port, ContentResolver contentResolver, HistoryViewModel historyViewModel) {
        super(port);
        this.context = context;
        this.contentResolver = contentResolver;
        this.historyViewModel = historyViewModel;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();
        // load the assets
        InputStream notFoundStream = null;
        InputStream noContentStream = null;
        InputStream serverErrorStream = null;
        InputStream badRequestStream = null;

        try {
            notFoundStream = context.getAssets().open("404.html");
            noContentStream = context.getAssets().open("204.html");
            serverErrorStream = context.getAssets().open("500.html");
            badRequestStream = context.getAssets().open("400.html");
        } catch (IOException e) {
            return new InternalServerError(e.getMessage(), MimeTypes.TEXT_PLAIN).build();
        }

        if(Method.GET.equals(method) && "/".equals(uri)) {

            if(fileMap.isEmpty()) {
                return new NoContent("Requested resource is not available", MimeTypes.TEXT_HTML).build(noContentStream);
            }

            try {
                InputStream okStream = context.getAssets().open("200.html");
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
                    String downloadUrl = "/download?id=" + uid;
                    html = html.replace("{{filename}}", fileName);
                    html = html.replace("{{url}}", downloadUrl);
                    return new Accept(html, MimeTypes.TEXT_HTML).build();
                }

                return new NoContent("Requested resource is not available", MimeTypes.TEXT_HTML).build(noContentStream);

            } catch (IOException e) {
                // 500 error
                return new InternalServerError(e.getMessage(), MimeTypes.TEXT_PLAIN).build(serverErrorStream, e.getClass().getSimpleName());
            }
        }
        if(Method.GET.equals(method) && "/download".equals(uri)) {
            Map<String, List<String>> params = session.getParameters();
            List<String> ids = params.get("id");

            if(ids == null || ids.isEmpty()) {
                return new BadRequest("Missing parameter id", MimeTypes.TEXT_PLAIN).build(badRequestStream);
            }

            String id=ids.get(0);
            // check if fileMap is not empty
            if(fileMap.isEmpty()) {
                return new NoContent("Requested resource is not available", MimeTypes.TEXT_HTML).build(noContentStream);
            }

            Uri fileUri = null;
            String idFromMap = "", fileName = "unknown";
            String fileSize = "";
            for(Map.Entry<String, FileInfo> infoEntry: fileMap.entrySet()) {
                fileUri = infoEntry.getValue().getUri();
                fileName = infoEntry.getValue().getFileName();
                idFromMap = infoEntry.getKey();
                fileSize = infoEntry.getValue().getFileSize();
            }

            if(fileUri != null && idFromMap.equals(id)) {

                try {
                    InputStream inputStream = contentResolver.openInputStream(fileUri);
                    String mimeType = contentResolver.getType(fileUri);
                    if(mimeType == null) {
                        mimeType = MimeTypes.APPLICATION_OCTET_STREAM;
                    }
                    // remove entry from map
                    fileMap.clear();
                    saveHistory(fileName, fileSize, mimeType);
                    Response response = new Accept(null, mimeType).build(inputStream);
                    response.addHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                    return response;
                } catch (Exception e) {
                    return new InternalServerError(e.getMessage(), MimeTypes.TEXT_PLAIN).build(serverErrorStream, e.getClass().getSimpleName());
                }

            } else {
                return new NoContent("Requested resource is not available", MimeTypes.TEXT_HTML).build(noContentStream);
            }
        }

        String errorMsg = "Path: " + uri + " was not found";
        return new NotFound(errorMsg, MimeTypes.TEXT_HTML).build(notFoundStream);
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
}

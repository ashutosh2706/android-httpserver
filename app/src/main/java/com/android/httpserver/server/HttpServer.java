package com.android.httpserver.server;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.android.httpserver.Constants;
import com.android.httpserver.R;
import com.android.httpserver.component.HistoryViewModel;
import com.android.httpserver.model.History;
import com.android.httpserver.util.NotificationHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public final class HttpServer extends NanoHTTPD {

    private final Context context;
    private final ContentResolver contentResolver;
    private final HistoryViewModel historyViewModel;
    private final NotificationHelper notificationHelper;
    private static HttpServer INSTANCE;

    private HttpServer(Context context, int port, ContentResolver contentResolver, HistoryViewModel historyViewModel,
            NotificationHelper notificationHelper) {
        super(port);
        this.context = context;
        this.contentResolver = contentResolver;
        this.historyViewModel = historyViewModel;
        this.notificationHelper = notificationHelper;
    }

    public static HttpServer getInstance(Context context, int port, ContentResolver contentResolver,
            HistoryViewModel historyViewModel, NotificationHelper notificationHelper) {
        if (INSTANCE == null) {
            INSTANCE = new HttpServer(context, port, contentResolver, historyViewModel, notificationHelper);
        }
        return INSTANCE;
    }

    private DocumentFile getRootDir() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.SHARED_PREFERENCES, Context.MODE_PRIVATE);
        String uriString = prefs.getString(Constants.KEY_EXT_PATH_URI, null);
        if (uriString == null)
            return null;
        return DocumentFile.fromTreeUri(context, Uri.parse(uriString));
    }

    private DocumentFile navigateToPath(String path) {
        DocumentFile root = getRootDir();
        if (root == null)
            return null;
        if (path == null || path.isEmpty() || path.equals("/"))
            return root;

        String[] segments = path.split("/");
        DocumentFile current = root;
        for (String segment : segments) {
            if (segment.isEmpty())
                continue;
            DocumentFile child = current.findFile(segment);
            if (child == null)
                return null;
            current = child;
        }
        return current;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        try {
            if (Method.GET.equals(method)) {
                if (uri.equals("/") || uri.startsWith("/browse")) {
                    return handleBrowse(uri);
                } else if (uri.startsWith("/download/")) {
                    return handleDownload(uri);
                }
            } else if (Method.POST.equals(method)) {
                if (uri.startsWith("/upload")) {
                    return handleUpload(session, uri);
                } else if (uri.startsWith("/mkdir")) {
                    return handleMkdir(session, uri);
                }
            }
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_ERROR, "500", e.getClass().getSimpleName());
        }

        return errorResponse(Response.Status.NOT_FOUND, "404", "");
    }

    private Response handleBrowse(String uri) {
        String path = "";
        if (uri.startsWith("/browse/")) {
            path = uri.substring("/browse/".length());
        } else if (uri.equals("/browse")) {
            path = "";
        }

        try {
            path = URLDecoder.decode(path, "UTF-8");
        } catch (Exception ignored) {
        }

        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        DocumentFile folder = navigateToPath(path);
        if (folder == null || !folder.isDirectory()) {
            return errorResponse(Response.Status.NOT_FOUND, "404", "");
        }

        List<Map<String, String>> items = new ArrayList<>();
        DocumentFile[] children = folder.listFiles();
        if (children != null) {
            List<DocumentFile> sorted = new ArrayList<>();
            for (DocumentFile child : children) {
                sorted.add(child);
            }
            Collections.sort(sorted, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory())
                    return -1;
                if (!a.isDirectory() && b.isDirectory())
                    return 1;
                String nameA = a.getName() != null ? a.getName() : "";
                String nameB = b.getName() != null ? b.getName() : "";
                return nameA.compareToIgnoreCase(nameB);
            });

            for (DocumentFile child : sorted) {
                Map<String, String> item = new HashMap<>();
                String name = child.getName() != null ? child.getName() : "unknown";
                String rel = path.isEmpty() ? name : path + "/" + name;
                item.put("name", name);
                item.put("rel", rel);
                item.put("is_dir", String.valueOf(child.isDirectory()));
                item.put("size", child.isDirectory() ? "" : humanSize(child.length()));
                items.add(item);
            }
        }

        String parent = "";
        if (!path.isEmpty()) {
            int lastSlash = path.lastIndexOf('/');
            parent = lastSlash > 0 ? path.substring(0, lastSlash) : "";
        }

        String html = buildBrowseHtml(items, path, parent);
        return newFixedLengthResponse(Response.Status.OK, MimeTypes.TEXT_HTML, html);
    }

    private Response handleDownload(String uri) {
        String path = uri.substring("/download/".length());
        try {
            path = URLDecoder.decode(path, "UTF-8");
        } catch (Exception ignored) {
        }

        DocumentFile file = navigateToPath(path);
        if (file == null || !file.isFile()) {
            return errorResponse(Response.Status.NOT_FOUND, "404", "");
        }

        try {
            InputStream inputStream = contentResolver.openInputStream(file.getUri());
            String mimeType = file.getType();
            if (mimeType == null)
                mimeType = MimeTypes.APPLICATION_OCTET_STREAM;
            String fileName = file.getName() != null ? file.getName() : "download";
            long fileSize = file.length();

            notificationHelper.notifyDownloadStartedDefault(fileName);
            saveHistory(fileName, humanSize(fileSize), mimeType);

            Response response = newChunkedResponse(Response.Status.OK, mimeType, inputStream);
            response.addHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            if (fileSize > 0) {
                response.addHeader("Content-Length", String.valueOf(fileSize));
            }
            return response;
        } catch (Exception e) {
            return errorResponse(Response.Status.INTERNAL_ERROR, "500", e.getClass().getSimpleName());
        }
    }

    private Response handleUpload(IHTTPSession session, String uri) throws IOException, ResponseException {
        String path = "";
        if (uri.startsWith("/upload/")) {
            path = uri.substring("/upload/".length());
        }
        try {
            path = URLDecoder.decode(path, "UTF-8");
        } catch (Exception ignored) {
        }
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        DocumentFile folder = navigateToPath(path);
        if (folder == null || !folder.isDirectory()) {
            return errorResponse(Response.Status.NOT_FOUND, "404", "");
        }

        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        Map<String, List<String>> params = session.getParameters();

        List<String> fileKeys = new ArrayList<>();
        for (String key : files.keySet()) {
            if (key.startsWith("files")) {
                fileKeys.add(key);
            }
        }

        for (String key : fileKeys) {
            String tempPath = files.get(key);
            if (tempPath == null)
                continue;

            String originalName = "uploaded_file";
            if (params.containsKey(key)) {
                List<String> names = params.get(key);
                if (names != null && !names.isEmpty()) {
                    originalName = names.get(0);
                }
            }

            String fileName = originalName;
            if (folder.findFile(fileName) != null) {
                String stem = fileName;
                String suffix = "";
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    stem = fileName.substring(0, dotIndex);
                    suffix = fileName.substring(dotIndex);
                }
                int i = 1;
                while (folder.findFile(fileName) != null) {
                    fileName = stem + "_" + i + suffix;
                    i++;
                }
            }

            DocumentFile newFile = folder.createFile(MimeTypes.APPLICATION_OCTET_STREAM, fileName);
            if (newFile == null)
                continue;

            File tempFile = new File(tempPath);
            try (InputStream in = new FileInputStream(tempFile);
                    OutputStream out = contentResolver.openOutputStream(newFile.getUri())) {
                if (out != null) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }

            notificationHelper.notifyUploadCompletedDefault(fileName);
            saveHistory(fileName, "", MimeTypes.APPLICATION_OCTET_STREAM);
        }

        String redirectPath = path.isEmpty() ? "/browse" : "/browse/" + path;
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, MimeTypes.TEXT_HTML, "");
        response.addHeader("Location", redirectPath);
        return response;
    }

    private Response handleMkdir(IHTTPSession session, String uri) throws IOException, ResponseException {
        String path = "";
        if (uri.startsWith("/mkdir/")) {
            path = uri.substring("/mkdir/".length());
        }
        try {
            path = URLDecoder.decode(path, "UTF-8");
        } catch (Exception ignored) {
        }
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        DocumentFile folder = navigateToPath(path);
        if (folder == null || !folder.isDirectory()) {
            return errorResponse(Response.Status.NOT_FOUND, "404", "");
        }

        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        Map<String, List<String>> params = session.getParameters();

        String dirname = "";
        if (params.containsKey("dirname")) {
            List<String> values = params.get("dirname");
            if (values != null && !values.isEmpty()) {
                dirname = values.get(0).trim();
            }
        }

        if (!dirname.isEmpty()) {
            dirname = dirname.replace("/", "").replace("\\", "").replace("\0", "");
            if (!dirname.isEmpty()) {
                DocumentFile existingDir = folder.findFile(dirname);
                if (existingDir == null || !existingDir.isDirectory()) {
                    folder.createDirectory(dirname);
                }
            }
        }

        String redirectPath = path.isEmpty() ? "/browse" : "/browse/" + path;
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, MimeTypes.TEXT_HTML, "");
        response.addHeader("Location", redirectPath);
        return response;
    }

    private String buildBrowseHtml(List<Map<String, String>> items, String path, String parent) {
        // Load template from assets
        String template;
        try {
            InputStream is = context.getAssets().open("browse");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            template = new String(buffer, "UTF-8");
        } catch (IOException e) {
            return "<h1>Error loading template</h1>";
        }

        // Build parent link
        String parentLink = "";
        if (!path.isEmpty()) {
            String parentUrl = parent.isEmpty() ? "/browse" : "/browse/" + encodePathSegments(parent);
            parentLink = "<p><a href=\"" + parentUrl + "\">&#11013; Parent</a></p>";
        }

        // Build table rows
        StringBuilder rows = new StringBuilder();
        for (Map<String, String> item : items) {
            rows.append("<tr><td>");
            boolean isDir = "true".equals(item.get("is_dir"));
            String name = escapeHtml(item.get("name"));
            String rel = item.get("rel");

            if (isDir) {
                rows.append("&#128193; <a href=\"/browse/").append(encodePathSegments(rel)).append("\">").append(name)
                        .append("</a>");
            } else {
                rows.append("&#128196; <a href=\"/download/").append(encodePathSegments(rel)).append("\">").append(name)
                        .append("</a>");
            }

            rows.append("</td><td>").append(isDir ? "Folder" : "File").append("</td>");
            rows.append("<td>").append(item.get("size")).append("</td></tr>\n");
        }

        // Build action URLs
        String uploadAction = path.isEmpty() ? "/upload" : "/upload/" + encodePathSegments(path);
        String mkdirAction = path.isEmpty() ? "/mkdir" : "/mkdir/" + encodePathSegments(path);

        // Replace placeholders
        String html = template;
        html = html.replace("{{current_path}}", escapeHtml(path));
        html = html.replace("{{parent_link}}", parentLink);
        html = html.replace("{{table_rows}}", rows.toString());
        html = html.replace("{{upload_action}}", uploadAction);
        html = html.replace("{{mkdir_action}}", mkdirAction);

        return html;
    }

    private String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String encodePathSegments(String path) {
        if (path == null)
            return "";
        String[] segments = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0)
                sb.append("/");
            try {
                sb.append(java.net.URLEncoder.encode(segments[i], "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                sb.append(segments[i]);
            }
        }
        return sb.toString();
    }

    private Response errorResponse(Response.Status status, String assetName, String errorCode) {
        try {
            InputStream is = context.getAssets().open(assetName);
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            String html = new String(buffer, "UTF-8");
            if (errorCode != null && !errorCode.isEmpty()) {
                html = html.replace("{{error_code}}", escapeHtml(errorCode));
            } else {
                html = html.replace("{{error_code}}", "");
            }
            return newFixedLengthResponse(status, MimeTypes.TEXT_HTML, html);
        } catch (IOException e) {
            return newFixedLengthResponse(status, MimeTypes.TEXT_PLAIN, "Error: " + status.getDescription());
        }
    }

    private String humanSize(long size) {
        String[] units = { "B", "KB", "MB", "GB", "TB" };
        double s = size;
        for (String unit : units) {
            if (s < 1024) {
                return String.format(Locale.US, "%.1f %s", s, unit);
            }
            s /= 1024;
        }
        return String.format(Locale.US, "%.1f PB", s);
    }

    private void saveHistory(String fileName, String fileSize, String mimeType) {
        int resId;
        if (mimeType.equals(MimeTypes.TEXT_HTML) || mimeType.equals(MimeTypes.TEXT_PLAIN)
                || mimeType.equals(MimeTypes.TEXT_XML) || mimeType.equals(MimeTypes.APPLICATION_JSON)) {
            resId = R.drawable.ic_file_plain;
        } else if (mimeType.equals(MimeTypes.IMAGE_JPEG) || mimeType.equals(MimeTypes.IMAGE_PNG)
                || mimeType.equals(MimeTypes.IMAGE_GIF)) {
            resId = R.drawable.ic_file_image;
        } else if (mimeType.equals(MimeTypes.AUDIO_MPEG) || mimeType.equals(MimeTypes.AUDIO_WAV)
                || mimeType.equals(MimeTypes.AUDIO_OGG)) {
            resId = R.drawable.ic_file_audio;
        } else if (mimeType.equals(MimeTypes.VIDEO_MP4) || mimeType.equals(MimeTypes.VIDEO_MPEG)
                || mimeType.equals(MimeTypes.VIDEO_WEBM)) {
            resId = R.drawable.ic_file_video;
        } else if (mimeType.equals(MimeTypes.ANDROID_PACKAGE)) {
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
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface inf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAdd = inf.getInetAddresses(); enumIpAdd.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAdd.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
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

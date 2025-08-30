package com.android.httpserver.response;

import com.android.httpserver.server.MimeTypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import fi.iki.elonen.NanoHTTPD;

public class BadRequest implements NanoHttpResponse {

    private final String content;
    private final String mimeType;
    private final String code = "400 BAD REQUEST: ";

    public BadRequest(String content, String mimeType) {
        this.content = content;
        this.mimeType = mimeType;
    }

    @Override
    public NanoHTTPD.Response build() {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                content
        );
    }

    public NanoHTTPD.Response build(InputStream inputStream) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            bufferedReader.close();
            String html = sb.toString();
            html = html.replace("{{error_code}}", content);
            // this.content = html;
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    MimeTypes.TEXT_HTML,
                    html
            );
        } catch (IOException e) {
            // if exception occurs then return plain text
            return this.build();
        }
    }
}

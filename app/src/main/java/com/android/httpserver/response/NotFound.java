package com.android.httpserver.response;

import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

public class NotFound implements NanoHttpResponse {

    private final String content;
    private final String mimeType;
    private final String code = "404 NOT FOUND: ";

    public NotFound(String mimeType, String content) {
        this.mimeType = mimeType;
        this.content = content;
    }


    @Override
    public NanoHTTPD.Response build() {
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                code+content
        );
    }

    public NanoHTTPD.Response build(InputStream inputStream) {
        return NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                inputStream
        );
    }
}

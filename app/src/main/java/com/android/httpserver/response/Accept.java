package com.android.httpserver.response;

import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

public class Accept implements NanoHttpResponse {

    private final String content;
    private final String mimeType;
    private final String code = "200 OK: ";

    public Accept(String content, String mimeType) {
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
        return NanoHTTPD.newChunkedResponse(
                NanoHTTPD.Response.Status.OK,
                mimeType,
                inputStream
        );
    }
}

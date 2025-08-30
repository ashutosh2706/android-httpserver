package com.android.httpserver.response;

import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

public class NoContent implements NanoHttpResponse {

    private final String content;
    private final String mimeType;
    private final String code = "204 NO CONTENT: ";

    public NoContent(String content, String mimeType) {
        this.mimeType = mimeType;
        this.content = content;
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

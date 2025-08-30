package com.android.httpserver.response;

import fi.iki.elonen.NanoHTTPD;

public interface NanoHttpResponse {
    NanoHTTPD.Response build();
}

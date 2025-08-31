package com.android.httpserver.model;

import android.net.Uri;
import java.time.LocalDateTime;

public class FileInfo {
    Uri uri;
    String uid;
    String fileName;
    String fileSize;
    LocalDateTime createdAt;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public FileInfo() {
    }

    public FileInfo(Uri uri, String uid, LocalDateTime createdAt, String fileName, String fileSize) {
        this.uri = uri;
        this.uid = uid;
        this.createdAt = createdAt;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }
}

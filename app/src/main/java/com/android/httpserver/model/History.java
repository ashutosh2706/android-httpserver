package com.android.httpserver.model;

public class History {

    private String fileName;
    private String fileSize;
    private String extra;
    private int imageResId;

    public History(String fileName, String fileSize, String extra, int imageResId) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.extra = extra;
        this.imageResId = imageResId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public String getExtra() {
        return extra;
    }

    public int getImageResId() {
        return imageResId;
    }
}

package com.android.httpserver.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "history")
public class History {

    @PrimaryKey(autoGenerate = true)
    private long id;
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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public int getImageResId() {
        return imageResId;
    }

    public void setImageResId(int imageResId) {
        this.imageResId = imageResId;
    }
}

package com.example.penyakitan;

public class CameraImage {

    private String fileName;
    private String imageUrl;
    private String updated;
    private String label;
    private String source;

    public CameraImage(String fileName, String imageUrl, String updated) {
        this.fileName = fileName;
        this.imageUrl = imageUrl;
        this.updated = updated;
        this.label = "";
        this.source = "";
    }

    public CameraImage(String fileName, String imageUrl, String updated, String label, String source) {
        this.fileName = fileName;
        this.imageUrl = imageUrl;
        this.updated = updated;
        this.label = label;
        this.source = source;
    }

    public String getFileName() {
        return fileName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getUpdated() {
        return updated;
    }

    public String getLabel() {
        return label;
    }

    public String getSource() {
        return source;
    }
}
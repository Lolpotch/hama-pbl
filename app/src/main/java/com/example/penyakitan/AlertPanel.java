package com.example.penyakitan;

public class AlertPanel {

    public String cameraID;
    public String diseaseName;
    public String imageUrl;
    public String date;
    public String description;
    public String solution;

    public AlertPanel(){}

    public AlertPanel(String cameraID, String diseaseName, String imageUrl,
                      String date, String description, String solution) {

        this.cameraID = cameraID;
        this.diseaseName = diseaseName;
        this.imageUrl = imageUrl;
        this.date = date;
        this.description = description;
        this.solution = solution;
    }
}
package com.notesapp;

import android.location.Location;


public class Note {
    private String text;
    private double latitude;
    private double longitude;

    Note(String text, Location location) {
        this.text = text;
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();
    }

    Note(String text, double latitude, double longitude) {
        this.text = text;
        this.latitude = longitude;
        this.longitude = longitude;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return text + " (" + locationToString() + ")";
    }

    public String locationToString() {
        return "lat: " + latitude + ", lon: " + longitude;
    }

}

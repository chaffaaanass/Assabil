/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.projects.gmm.assabil;

/**
 *
 * @author Chaffaa Anass
 */
public class UnassignedInstance {
    private final String partnerName;
    private final double latitude;
    private final double longitude;
    private final double weight;

    public UnassignedInstance(String partnerName, double latitude, double longitude, double weight) {
        this.partnerName = partnerName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.weight = weight;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getWeight() {
        return weight;
    }
}

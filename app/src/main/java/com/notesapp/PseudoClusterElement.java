package com.notesapp;


import android.location.Location;

import java.util.ArrayList;
import java.util.List;


/**
 * Represents a single cluster of locations, part of a cluster list in the PseudoCluster class.
 */
public class PseudoClusterElement {
    private List<Location> locationCluster;
    private Location meanLocation;


    PseudoClusterElement(Location location){
        locationCluster = new ArrayList<>();
        locationCluster.add(location);
        meanLocation = new Location("");
        meanLocation.setLatitude(location.getLatitude());
        meanLocation.setLongitude(location.getLongitude());
    }


    /**
     * Adds a new location to the cluster and calculates a new mean location, based on the new list
     * of cluster elements.
     * @param location the Location to be added to the cluster.
     */
    void add(Location location) {
        locationCluster.add(location);
        meanLocation = findMeanLocation(meanLocation, location);
    }


    /**
     * Returns the number of elements in the cluster.
     * @return the number of elements.
     */
    int size() {
        return locationCluster.size();
    }


    /**
     * Returns the list of locations currently part of the cluster.
     * @return the list of cluster locations.
     */
    List<Location> getLocations() {
        return locationCluster;
    }


    /**
     * Returns the mean of locations in the cluster (already calculated).
     * @return the Location representing the mean of all current cluster locations.
     */
    Location getMeanLocation() {
        return meanLocation;
    }


    /**
     * Finds a mean of two locations.
     * @param location1 the first location.
     * @param location2 the first location.
     * @return the mean of the two locations.
     */
    private static Location findMeanLocation(Location location1, Location location2) {
        Location location = new Location("");
        location.setLatitude((location1.getLatitude() + location2.getLatitude()) / 2.0);
        location.setLongitude((location1.getLongitude() + location2.getLongitude()) / 2.0);
        return location;
    }


    /**
     * Returns all locations in the current cluster in string format.
     * @return a string representation of all cluster locations.
     */
    String locationsToString() {
        int size = locationCluster.size();
        if (size == 0) return "";
        else {
            StringBuilder sb = new StringBuilder();
            for(Location location : locationCluster) {
                sb.append(location.getLatitude())
                        .append(" ")
                        .append(location.getLongitude())
                        .append("\n");
            }
            return sb.toString();
        }
    }

}

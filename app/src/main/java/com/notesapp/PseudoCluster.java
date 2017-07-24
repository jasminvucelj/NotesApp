package com.notesapp;


import android.location.Location;

import java.util.ArrayList;
import java.util.List;

public class PseudoCluster {
    private List<ArrayList<Location>> clusterList;
    private List<Double> latitudeMeans;
    private List<Double> longitudeMeans;

    double distanceThreshold;


    PseudoCluster(double distanceThreshold) {
        this.distanceThreshold = distanceThreshold;
        clusterList = new ArrayList<>();
        latitudeMeans = new ArrayList<>();
        longitudeMeans = new ArrayList<>();
    }


    int getTotalElementCount() {
        int size = clusterList.size();
        if (size == 0) {
            return 0;
        }
        else {
            int sum = 0;
            for (ArrayList<Location> cluster : clusterList) {
                sum += cluster.size();
            }
            return sum;
        }
    }


    void add(Location location) {
        int clusterCount = clusterList.size();
        // first location - create first cluster and add the location to it
        if(clusterCount == 0) {
            addCluster(location);
        }

        else {
            double newLat = location.getLatitude();
            double newLon = location.getLongitude();

            // try to place the location in an existing cluster
            for(int i = 0; i < clusterCount; i++) {
                // if distance to the mean of i-th cluster < threshold -> place the location
                // in that cluster
                if(euclideanDistance(latitudeMeans.get(i), longitudeMeans.get(i), newLat, newLon)
                        < distanceThreshold) {
                    clusterList.get(i).add(location); // ???
                    latitudeMeans.set(i, (latitudeMeans.get(i)+location.getLatitude()) / 2.0);
                    longitudeMeans.set(i, (longitudeMeans.get(i)+location.getLongitude()) / 2.0);
                    return;

                }
            }

            // if not placed -> requires a new cluster
            addCluster(location);

        }

    }


    private void addCluster(Location location) {
        ArrayList<Location> tempList = new ArrayList<>();;
        tempList.add(location);
        clusterList.add(tempList);

        latitudeMeans.add(location.getLatitude());
        longitudeMeans.add(location.getLongitude());
    }


    // TODO location distance
    private double euclideanDistance(double lat1, double lon1, double lat2, double lon2) {
        return Math.sqrt(Math.pow(lat2 - lat1, 2) + Math.pow(lon2 - lon1, 2));
    }


    List<Location> getLargestCluster() {
        int size = clusterList.size();
        if (size == 0) {
            return null;
        }
        else if (size == 1) {
            return clusterList.get(0);
        }
        else {
            int maxIndex = 0;
            int maxSize = clusterList.get(0).size();

            for (int i = 1; i < size; i++) {
                int tempSize = clusterList.get(i).size();
                if (tempSize > maxSize) {
                    maxIndex = i;
                    maxSize = tempSize;
                }
            }
            return clusterList.get(maxIndex);
        }
    }
}

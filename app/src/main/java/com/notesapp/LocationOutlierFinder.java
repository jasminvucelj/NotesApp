package com.notesapp;

import android.location.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class LocationOutlierFinder {
    private static final int LATITUDE_INDEX = 0;
    private static final int LONGITUDE_INDEX = 1;
    private static final double IQR_COEF = 1.5;

    private static List<Double> latitudeList;
    private static List<Double> longitudeList;


    /**
     * Runs the algorithm for outlier detection/removal. Does not directly modify the original
     * dataset.
     * @param dataset initial list of locations (with outliers).
     * @return list of locations, outliers removed.
     */
    static List<Location> removeOutliers(List<Location> dataset) {

        if (dataset == null || dataset.isEmpty()) {
            return dataset;
        }

        double median[] = new double[2];
        double[][] Q = new double[2][2]; // [lat=0/lon=1][Q1=0/Q3=1]
        double[] IQR = new double[2];

        makeSortedLists(dataset);

        median[LATITUDE_INDEX] = findMedian(latitudeList);
        median[LONGITUDE_INDEX] = findMedian(longitudeList);

        Q[LATITUDE_INDEX] = findQValues(latitudeList, median[LATITUDE_INDEX]);
        Q[LONGITUDE_INDEX] = findQValues(longitudeList, median[LONGITUDE_INDEX]);

        // interquartile range (IQR) = Q3 - Q1
        IQR[LATITUDE_INDEX] = Q[LATITUDE_INDEX][1] - Q[LATITUDE_INDEX][0];
        IQR[LONGITUDE_INDEX] = Q[LONGITUDE_INDEX][1] - Q[LONGITUDE_INDEX][0];

        return eliminateOutliers(dataset, Q, IQR);
    }


    /**
     * Creates and sorts the lists of latitudes and longitudes for finding the medians, Q1s,
     * and Q3s.
     * @param dataset initial list of locations (with outliers).
     */
    private static void makeSortedLists(List<Location> dataset) {
        latitudeList = new ArrayList<>();
        longitudeList = new ArrayList<>();

        for (Location l : dataset) {
            latitudeList.add(l.getLatitude());
            longitudeList.add(l.getLongitude());
        }

        Collections.sort(latitudeList);
        Collections.sort(longitudeList);
    }


    /**
     * Finds the median (value separating the upper 50% of datafrom the lower 50%)
     * of a list of values.
     * @param dataset a sorted list of double values.
     * @return median value of the dataset.
     */
    private static double findMedian(List<Double> dataset) {
        int size = dataset.size();
        if (size == 0) {
            return 0;
        }
        else if (size % 2 == 1) {
            return dataset.get(size/2);
        }
        else {
            return (dataset.get(size/2) + dataset.get(size/2 - 1)) / 2.0;
        }
    }


    /**
     * Finds Q1 (value separating the upper 75% of data from the lower 25%) and Q3 (value
     * separating the upper 25% of data from the lower 75%) of a list of values. The values are
     * found as medians of the lower and upper 50% of data, respectively.
     * @param dataset a sorted list of double values.
     * @param median median value of the given dataset.
     * @return a double array of two values, Q1 and Q3 of the given dataset.
     */
    private static double[] findQValues(List<Double> dataset, double median) {

        List<Double> lowerHalf = new ArrayList<>();
        List<Double> upperHalf = new ArrayList<>();

        for (Double d : dataset) {
            if (d < median) {
                lowerHalf.add(d);
            }
            else if (d > median) {
                upperHalf.add(d);
            }
            else {
                lowerHalf.add(d);
                upperHalf.add(d);
            }
        }

        return new double[] {findMedian(lowerHalf), findMedian(upperHalf)};
    }


    /**
     * Finds and removes outliers from a given dataset based on its Q and IQR values. A location is
     * considered an outlier if either its latitude or longitude falls outside the bounds defined by
     * the quartiles, IQR, and the constant IQR_COEF (1.5 by default).
     * @param oldDataset list of locations (with outliers)
     * @param Q 2D array (2x2 elements) of quartile values (first index determining latitude
     *          or longitude, the second the quartile).
     * @param IQR array (2 elements) of interquartile range values (for latitude and longitude).
     * @return list of locations, outliers removed.
     */
    private static List<Location> eliminateOutliers(List<Location> oldDataset, double[][] Q, double[] IQR) {
        List<Location> newDataset = new ArrayList<>();

        double[][] bounds = new double[2][2]; // [lat=0/lon=1][lower=0/upper=1]

        /**
         * Bounds for acceptable locations:
         * - lower: Q1 - IQR_COEF * IQR
         * - upper: Q3 + IQR_COEF * IQR
         * (for both latitude and longitude)
         */
        bounds[LATITUDE_INDEX][0] = Q[LATITUDE_INDEX][0] - IQR_COEF * IQR[LATITUDE_INDEX];
        bounds[LATITUDE_INDEX][1] = Q[LATITUDE_INDEX][1] + IQR_COEF * IQR[LATITUDE_INDEX];
        bounds[LONGITUDE_INDEX][0] = Q[LONGITUDE_INDEX][0] - IQR_COEF * IQR[LONGITUDE_INDEX];
        bounds[LONGITUDE_INDEX][1] = Q[LONGITUDE_INDEX][1] + IQR_COEF * IQR[LONGITUDE_INDEX];

        // add only the acceptable locations to the new dataset
        for (Location l : oldDataset) {
            double lat = l.getLatitude();
            if (lat < bounds[LATITUDE_INDEX][0] || lat > bounds[LATITUDE_INDEX][1]) {
                continue;
            }

            double lon = l.getLongitude();
            if (lon < bounds[LONGITUDE_INDEX][0] || lon > bounds[LONGITUDE_INDEX][1]) {
                continue;
            }

            newDataset.add(l);
        }

        return newDataset;
    }

}


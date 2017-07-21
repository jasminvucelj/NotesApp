package com.notesapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;


public class MainActivity extends Activity implements OnMapReadyCallback {

    final int LONG_REFRESH_TIME = 5 * 60 * 1000; // 5 min => ms
    final int SHORT_REFRESH_TIME = 6 * 1000; // 15 s => ms
    final int REFRESH_DISTANCE = 100;
    final int OUTLIER_LOCATION_BUFFER_SIZE = 6; // 10
    final int TIMER_INTERVAL = 5 * 1000;
    final float ZOOM_LEVEL = 9;
    final float ACCURACY_THRESHOLD = 100;

    static long nextUpdateTime;
    double currentDistance = 0;

    DatabaseHandler dbHandler;

    CountDownTimer countDownTimer;

    GoogleMap googleMap;
    MapFragment mapFragment;

    Button btnStartDay, btnSendNote;
    EditText noteText;
    TextView textViewDistance, textViewDB;

    boolean dayStarted = false;

    List<Location> constantLocationBuffer = new ArrayList<>();

    Location lastLocationPeriodic = null, lastLocationConstant = null;
    LocationManager locationManagerConstant;
    LocationManager locationManagerPeriodic;
    LocationListener locationListenerConstant;
    LocationListener locationListenerPeriodic;


/*    static class TimeChangedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Calendar.getInstance().getTimeInMillis() > nextUpdateTime) { // time changed => request update
                requestGPSUpdatePeriodic(locationManagerPeriodic, locationListenerPeriodic);
            }
        }
    }*/


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initMap();
        initViews();
        initDatabaseHandler();


        // ask permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        }
                        , 10);
            }
            return;
        }


        initCountDownTimer();
        initLocations();
        btnConfig();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 10:
                initCountDownTimer();
                initLocations();
                btnConfig();
                break;
            default:
                break;
        }
    }


    /**
     * Initializes the map fragment.
     */
    private void initMap() {
        mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);
    }


    /**
     * Assigns views on the layout to variables.
     */
    private void initViews() {
        btnStartDay = (Button) findViewById(R.id.btnStartDay);
        btnSendNote = (Button) findViewById(R.id.btnSendNote);
        noteText = (EditText) findViewById(R.id.noteText);
        textViewDB = (TextView) findViewById(R.id.textViewDB);
        textViewDistance = (TextView) findViewById(R.id.textViewDistance);
    }


    /**
     * Initializes the DatabaseHandler.
     */
    private void initDatabaseHandler() {
        dbHandler = new DatabaseHandler(this);
        dbHandler.deleteAll(); // TEST
    }


    /**
     * Sets up the countdown timer.
     */
    private void initCountDownTimer() {
        countDownTimer = new CountDownTimer(LONG_REFRESH_TIME, TIMER_INTERVAL) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (Calendar.getInstance().getTimeInMillis() > nextUpdateTime) { // time changed => request update
                    requestGPSUpdatePeriodic(locationManagerPeriodic, locationListenerPeriodic);
                }
            }

            @Override
            public void onFinish() { // timer expires => request update
                requestGPSUpdatePeriodic(locationManagerPeriodic, locationListenerPeriodic);
            }
        };
    };


    /**
     * Sets up the locationManagers and locationListeners.
     */
    private void initLocations() {

        locationManagerPeriodic = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListenerPeriodic = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                periodicLocationChanged(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        };

        locationManagerConstant = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListenerConstant = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                constantLocationChanged(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        };
    }


    /**
     * Requests a single GPS update.
     * @param lm location manager
     * @param ll location listener
     */
    static private void requestGPSUpdatePeriodic(LocationManager lm, LocationListener ll) {
        //noinspection MissingPermission
        lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, ll, null);
    }


    /**
     * Requests constant GPS updates with a given refresh time/distance.
     * @param lm location manager
     * @param ll location listener
     * @param refreshTime min time interval (ms)
     * @param refreshDistance min distance interval (m)
     */
    static private void requestGPSUpdateConstant(LocationManager lm, LocationListener ll, int refreshTime, int refreshDistance) {
        //noinspection MissingPermission
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, refreshTime, refreshDistance, ll);
    }


    /**
     * Stores location if acceptable (and restarts the timer), otherwise requests a new one.
     * @param location last received periodic location.
     */
    private void periodicLocationChanged(Location location) {
        if (location.getAccuracy() < ACCURACY_THRESHOLD) {
            btnSendNote.setEnabled(true);
            lastLocationPeriodic = location;
            nextUpdateTime = Calendar.getInstance().getTimeInMillis() + LONG_REFRESH_TIME;
            countDownTimer.start();

            Toast.makeText(this, "Received location: " +
                            location.getLatitude() +
                            ", " +
                            location.getLongitude(),
                    Toast.LENGTH_SHORT).show();
        } else {
            requestGPSUpdatePeriodic(locationManagerPeriodic, locationListenerPeriodic);
        }
    }


    /**
     * Updates total distance based on locations received through constant tracking.
     * Uses an IQR method-based algorithm to detect and remove outlier ("false") locations.
     * @param location last received constant location.
     */
    private void constantLocationChanged(Location location) {
        // add new location to buffer
        constantLocationBuffer.add(location);

        //Toast.makeText(this, String.valueOf(constantLocationBuffer.size()), Toast.LENGTH_SHORT).show();

        if (constantLocationBuffer.size() >= OUTLIER_LOCATION_BUFFER_SIZE) {
            // remove outlier points from buffer
            constantLocationBuffer = LocationOutlierFinder.removeOutliers(constantLocationBuffer);

            if(constantLocationBuffer.size() > 0 && constantLocationBuffer.contains(location)) {
                // update distance
                if (currentDistance == 0) {
                    // distance of the entire list
                    currentDistance = locationListDistance(constantLocationBuffer);
                } else {
                    // distance from last saved location to the current one
                    currentDistance += locationDistance(lastLocationConstant, location);
                }

                // make room in the buffer - remove oldest location
                constantLocationBuffer.remove(0);

                textViewDistance.setText(getString(R.string.distance) +
                        "\t" +
                        String.format(Locale.getDefault(), "%.2f", currentDistance) +
                        " m");

                // update last saved location
                lastLocationConstant = location;

            }
            else {
                Toast.makeText(this,
                        "Location rejected: " +
                                location.getLatitude() +
                                ", " +
                                location.getLongitude(),
                        Toast.LENGTH_SHORT).show();
            }
        }
        else if (currentDistance == 0) {
            textViewDistance.setText(getString(R.string.not_enough_locations));
        }
    }


    /**
     * Checks whether or not a location is acceptable. A location is considered acceptable
     * if it's not null, and the accuracy is below a defined threshold.
     * @param location the location to be checked.
     * @return true if location is acceptable, false if not.
     */
    private boolean locationIsAcceptable(Location location) { // acceptable = not null & accuracy > 100 m
        if (location == null || location.getAccuracy() > ACCURACY_THRESHOLD) return false;
        return true;
    }


    /**
     * Returns the distance between two locations.
     * @param loc1 first location.
     * @param loc2 second location.
     * @return 0 if either location is undefined, otherwise the distance between them.
     */
    private double locationDistance(Location loc1, Location loc2) {
        if(loc1 == null || loc2 == null) {
            return 0;
        }
        else {
            return (double) loc2.distanceTo(loc1);
        }
    }


    /**
     * Returns the total distance between locations in a list.
     * @param locList list of locations.
     * @return 0 if the list has less than 2 elements, otherwise the total distance.
     */
    private double locationListDistance(List<Location> locList) {
        int size = locList.size();
        if(size <= 1) {
            return 0;
        }
        else {
            double result = 0;
            for(int i = 0; i < size-1; i++) {
                result += locationDistance(locList.get(i), locList.get(i+1));
            }
            return result;
        }

    }


    /**
     * Sets up the onClickListeners for btnStartDay (toggle tracking) and btnSendNote
     * (sends/saves to DB the note with the last periodic location).
     */
    private void btnConfig(){
        // btnStartDay - toggle tracking
        btnStartDay.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                dayStartedToggle();
            }
        });

        // btnSendNote - sends (saves to db) note with the last periodic location
        btnSendNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendNote(noteText.getText().toString(), lastLocationPeriodic);
            }
        });
    }


    /**
     * Toggles both constant and periodic tracking.
     */
    private void dayStartedToggle() {
        if (dayStarted) { // turn off
            dayStarted = false;

            btnSendNote.setEnabled(false);
            btnStartDay.setText(getString(R.string.start_day));

            countDownTimer.cancel();

            lastLocationPeriodic = null;
            lastLocationConstant = null;

            locationManagerPeriodic.removeUpdates(locationListenerPeriodic);
            locationManagerConstant.removeUpdates(locationListenerConstant);
        }

        else { // turn on
            dayStarted = true;

            btnStartDay.setText(getString(R.string.stop_day));

            currentDistance = 0;
            constantLocationBuffer = new ArrayList<>();

            requestGPSUpdatePeriodic(locationManagerPeriodic, locationListenerPeriodic);
            requestGPSUpdateConstant(locationManagerConstant, locationListenerConstant, SHORT_REFRESH_TIME, REFRESH_DISTANCE);
        }
    }


    /**
     * Creates a Note from a given text and location, and sends it (saves to DB) if the location is
     * acceptable.
     * @param text note text.
     * @param location note location.
     */
    private void sendNote(String text, Location location) {
        // location ok?
        if (!locationIsAcceptable(location)) {
            Toast.makeText(this,
                    R.string.location_bad,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note(text, location);

        if(dbHandler.addNote(note)) {
            Toast.makeText(this,
                    "Successfully inserted: " +
                            note.toString(),
                    Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this,
                    "Insertion failed" +
                            note.toString(),
                    Toast.LENGTH_SHORT).show();
        }

        // display data
        textViewDB.setText(dbHandler.databaseToString());

        // place marker on map
        setMarker(note);
    }


    /**
     * Places a marker for a location of a note on the map.
     * @param note Note with a location to be marked.
     */
    private void setMarker(Note note) {
        LatLng locationAsLatLng = new LatLng(note.getLatitude(), note.getLongitude());

        googleMap.addMarker(new MarkerOptions().position(locationAsLatLng)
                .title(note.getText()));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(locationAsLatLng, ZOOM_LEVEL));
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
    }
}




package com.notesapp;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
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
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;


public class MainActivity extends Activity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // parameters for receiving location updates
    final int LONG_REFRESH_TIME = 5 * 60 * 1000; // 5 min => ms
    final int SHORT_REFRESH_TIME = 6 * 1000; // 15 s => ms
    final int REFRESH_DISTANCE = 0; // 100
    // number of locations in the buffer (for IQR algorithm) and number of initial locations (for
    // which the pseudo-clustering algorithm will be used)
    final int OUTLIER_BUFFER_SIZE = 4; // 10
    final int INITIAL_LOCATION_COUNT = OUTLIER_BUFFER_SIZE - 1;
    // countdown timer tick interval
    final int TIMER_INTERVAL = 5 * 1000;
    // refresh time for activity recognition
    final int ACTIVITY_REFRESH_TIME = 6 * 1000; // 5s
    // threshold of activity confidence for activity recognition
    final int CONFIDENCE_THRESHOLD = 75;
    // zoom for GoogleMap
    final float ZOOM_LEVEL = 10;
    // threshold of desired location accuracy
    final float ACCURACY_THRESHOLD = 100;
    // for pseudo-clustering
    final double DISTANCE_THRESHOLD = 100 * SHORT_REFRESH_TIME / 1000; // m

    // filename for logging
    static final String LOG_FILENAME = "log_";
    static final String LOG_EXTENSION = ".txt";

    int constantLocationCount;
    static long nextUpdateTime;
    double currentDistance = 0;

    DatabaseHandler dbHandler = null;

    CountDownTimer countDownTimer;

    GoogleMap googleMap;
    MapFragment mapFragment;

    // layout elements
    Button btnStartDay, btnSendNote;
    EditText noteText;
    TextView textViewDistance, textViewDB;

    static boolean logging = true;

    boolean dayStarted = false, trackingEnabled = false, activityReceiverActive = false;

    PseudoClusterList pseudoClusterList;

    List<Location> constantLocationBuffer = new ArrayList<>();

    Location lastLocationPeriodic = null, lastLocationConstant = null;
    LocationManager locationManagerConstant;
    LocationManager locationManagerPeriodic;
    LocationListener locationListenerConstant;
    LocationListener locationListenerPeriodic;

    GoogleApiClient mApiClient;

    // for logging
    SparseArray<String> activityNames;


    /**
     * For receiving user activity updates from {@link ActivityRecognitionService} and turning
     * constant tracking on and off as neccessary.
     */
    private BroadcastReceiver activityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int type = intent.getIntExtra("type", DetectedActivity.STILL);
            int confidence = intent.getIntExtra("confidence", 0);

            if(MainActivity.logging) {
                MainActivity.writeLog(MainActivity.getCurrentDateTime().split("_")[1] +
                        ": " +
                        "Most probable activity: " +
                        activityNames.get(type, "") +
                        " (" +
                        String.valueOf(type) +
                        ") (" +
                        String.valueOf(confidence) +
                        "%)\n");
            }

            // if type of activity is STILL & confidence is above threshold, turn off tracking
            // (as neccessary)
            if(type == DetectedActivity.STILL && confidence >= CONFIDENCE_THRESHOLD) {
                if (trackingEnabled) {
                    trackingEnabled = false;
                    locationManagerConstant.removeUpdates(locationListenerConstant);

                    if(logging) {
                        writeLog(getCurrentDateTime().split("_")[1] + ": Tracking = OFF.\n");
                    }
                }
            }

            // else the user is moving (or it is not certain that he isn't), so turn on tracking
            // (as neccessary)
            else {
                if (!trackingEnabled) {
                    trackingEnabled = true;
                    requestGPSUpdateConstant(locationManagerConstant,
                            locationListenerConstant,
                            SHORT_REFRESH_TIME,
                            REFRESH_DISTANCE);

                    if(logging) {
                        writeLog(getCurrentDateTime().split("_")[1] + ": Tracking = ON.\n");
                    }
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initMap();
        initViews();
        initDatabaseHandler();
        initActivityRecognition();
        initSparseArray();


        // ask permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        10);
            }
            return;
        }

        initCountDownTimer();
        initLocations();
        btnConfig();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
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


    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Intent intent = new Intent(this, ActivityRecognitionService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mApiClient,
                ACTIVITY_REFRESH_TIME,
                pendingIntent);
    }


    @Override
    public void onConnectionSuspended(int i) {

    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }


    @Override
    protected void onDestroy() {
        stopActivityUpdates();
        super.onDestroy();
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
        dbHandler.deleteAll();
    }


    /**
     * Initializes the API client for activity recognition.
     */
    private void initActivityRecognition() {
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }


    /**
     * Initializes the sparse array that maps the activity values to their names.
     */
    private void initSparseArray() {
        activityNames = new SparseArray<>();
        activityNames.append(0, "IN_VEHICLE");
        activityNames.append(1, "ON_BICYCLE");
        activityNames.append(2, "ON_FOOT");
        activityNames.append(8, "RUNNING");
        activityNames.append(3, "STILL");
        activityNames.append(5, "TILTING");
        activityNames.append(4, "UNKNOWN");
        activityNames.append(7, "WALKING");
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
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

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
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        };
    }


    /**
     * Registers the receiver for updates from {@link ActivityRecognitionService}.
     */
    private void registerActivityReceiver() {
        activityReceiverActive = true;
        LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(
                activityReceiver, new IntentFilter("activityRecognitionIntent"));
    }


    /**
     * Unregisters the receiver for updates from {@link ActivityRecognitionService}.
     */
    private void unregisterReceiver() {
        activityReceiverActive = false;
        LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(activityReceiver);
    }


    /**
     * Stops activity recognition updates.
     */
    private void stopActivityUpdates() {
        if (mApiClient.isConnected()) {
            Intent intent = new Intent(this, ActivityRecognitionService.class);
            PendingIntent pendingIntent = PendingIntent.getService(this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mApiClient, pendingIntent);
        }
    }


    /**
     * Requests a single GPS update.
     * @param locationManager location manager
     * @param locationListener location listener
     */
    static private void requestGPSUpdatePeriodic(LocationManager locationManager,
                                                 LocationListener locationListener) {
        //noinspection MissingPermission
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER,
                locationListener, null);
    }


    /**
     * Requests constant GPS updates with a given refresh time/distance.
     * @param locationManager location manager
     * @param locationListener location listener
     * @param refreshTime min time interval (ms)
     * @param refreshDistance min distance interval (m)
     */
    static private void requestGPSUpdateConstant(LocationManager locationManager,
                                                 LocationListener locationListener,
                                                 int refreshTime,
                                                 int refreshDistance) {
        //noinspection MissingPermission
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                refreshTime,
                refreshDistance,
                locationListener);
    }


    /**
     * Stores location if acceptable (and restarts the timer), otherwise requests a new one.
     * @param location last received periodic location.
     */
    private void periodicLocationChanged(Location location) {
        if (locationIsAcceptable(location)){
            btnSendNote.setEnabled(true);
            lastLocationPeriodic = location;
            nextUpdateTime = Calendar.getInstance().getTimeInMillis() + LONG_REFRESH_TIME;
            countDownTimer.start();

            if (logging) {
                writeLog(getCurrentDateTime().split("_")[1] +
                        ": " +
                        "Location [" +
                        location.getLatitude() +
                        ", " +
                        location.getLongitude() +
                        "] (accuracy =  " +
                        location.getAccuracy() +
                        ") accepted as new periodic location.\n");
            }

        } else {
            requestGPSUpdatePeriodic(locationManagerPeriodic, locationListenerPeriodic);

            if (logging) {
                writeLog(getCurrentDateTime().split("_")[1] +
                        ": " +
                        "Location [" +
                        location.getLatitude() +
                        ", " +
                        location.getLongitude() +
                        "] (accuracy =  " +
                        location.getAccuracy() +
                        "rejected (insufficient accuracy).\n");
            }
        }
    }


    /**
     * Updates total distance based on locations received through constant tracking.
     * Uses a "pseudo-cluster" algorithm (for initial locations) and an IQR method-based algorithm
     * (for later points) to detect and remove outlier ("false") locations.
     * @param location last received constant location.
     */
    private void constantLocationChanged(Location location) {
        if(logging) {
            writeLog(getCurrentDateTime().split("_")[1] +
                    ": " +
                    "Received location [" +
                    location.getLatitude() +
                    ", " +
                    location.getLongitude() +
                    "].\n");
        }

        // no need to count all locations, only to know if enough have been received yet or not
        if (constantLocationCount < INITIAL_LOCATION_COUNT + 1) {
            constantLocationCount++;
//            Toast.makeText(this,
//                    String.valueOf(constantLocationCount),
//                    Toast.LENGTH_SHORT).show();
        }

        ////////////////////////////////////////////////////////////////////////////////////////////

        // not enough locations yet - add new location to pseudo-cluster
        if (constantLocationCount < INITIAL_LOCATION_COUNT) {
            pseudoClusterList.add(location);
            textViewDistance.setText(getString(R.string.not_enough_locations));

            if(logging) {
                writeLog(getCurrentDateTime().split("_")[1] +
                        ": " +
                        "Location [" +
                        location.getLatitude() +
                        ", " +
                        location.getLongitude() +
                        "] added to cluster, new largest cluster mean = [" +
                        pseudoClusterList.getLargestCluster().getMeanLocation().getLatitude() +
                        ", " +
                        pseudoClusterList.getLargestCluster().getMeanLocation().getLongitude() +
                        "].\n");
            }
        }

        // enough locations - get largest pseudo-cluster and use it for the IQR method algorithm
        // in the future
        else if (constantLocationCount == INITIAL_LOCATION_COUNT) {
            pseudoClusterList.add(location);
            constantLocationBuffer = pseudoClusterList.getLargestCluster().getLocations();
            textViewDistance.setText(getString(R.string.not_enough_locations));

            if(logging) {
                writeLog(getCurrentDateTime().split("_")[1] +
                        ": " +
                        "Location [" +
                        location.getLatitude() +
                        ", " +
                        location.getLongitude() +
                        "] added to cluster, getting largest cluster (mean = [" +
                        pseudoClusterList.getLargestCluster().getMeanLocation().getLatitude() +
                        ", " +
                        pseudoClusterList.getLargestCluster().getMeanLocation().getLongitude() +
                        "]).\n");
            }

        }

        // more locations - the pseudo-cluster has already been used - use the IQR method algorithm
        else {
            // add new location to buffer
            constantLocationBuffer.add(location);

            if(logging) {
                writeLog(getCurrentDateTime().split("_")[1] +
                        ": " +
                        "Location [" +
                        location.getLatitude() +
                        ", " +
                        location.getLongitude() +
                        "] added to buffer.\n");
            }

            if (constantLocationBuffer.size() >= OUTLIER_BUFFER_SIZE) {
                // remove outlier points from buffer
                constantLocationBuffer = IQROutlierFinder.removeOutliers(constantLocationBuffer);

                if (constantLocationBuffer.size() > 0 && constantLocationBuffer.contains(location)) {
                    // update distance

                    if (currentDistance == 0) {
                        // distance of the entire list
                        currentDistance = locationListDistance(constantLocationBuffer);
                    } else {
                        // distance from last saved location to the current one
                        currentDistance += locationDistance(lastLocationConstant, location);
                    }

                    if(logging) {
                        writeLog(getCurrentDateTime().split("_")[1] +
                                ": " +
                                "Updating distance - new value: " +
                                String.valueOf(currentDistance) +
                                ".\n");
                    }

                    // make room in the buffer - remove oldest location
                    constantLocationBuffer.remove(0);

                    textViewDistance.setText(getString(R.string.distance) +
                            "\t" +
                            String.format(Locale.getDefault(), "%.2f", currentDistance) +
                            " m");

                    // update last saved location
                    lastLocationConstant = location;

                    // turn on activity receiver (if not already active)
                    if (!activityReceiverActive) {
                        registerActivityReceiver();
                        if(logging) {
                            writeLog(getCurrentDateTime().split("_")[1] + ": " + "Activity receiver registered.\n");
                        }
                    }

                } else {
                    // location is an outlier - rejected
                    Toast.makeText(this,
                            "Location rejected: " +
                                    location.getLatitude() +
                                    ", " +
                                    location.getLongitude(),
                            Toast.LENGTH_SHORT).show();

                    if(logging) {
                        writeLog(getCurrentDateTime().split("_")[1] +
                                "Location rejected: " +
                                location.getLatitude() +
                                ", " +
                                location.getLongitude() +
                                "].\n");
                    }
                }
            } else if (currentDistance == 0) {
                textViewDistance.setText(getString(R.string.not_enough_locations));
            }
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
            trackingEnabled = false;

            unregisterReceiver();

            btnSendNote.setEnabled(false);
            btnStartDay.setText(getString(R.string.start_day));

            initDatabaseHandler();

            countDownTimer.cancel();

            lastLocationPeriodic = null;
            lastLocationConstant = null;

            locationManagerPeriodic.removeUpdates(locationListenerPeriodic);
            locationManagerConstant.removeUpdates(locationListenerConstant);

            if(logging) {
                writeLog(getCurrentDateTime().split("_")[1] + ": Tracking = OFF (day stopped).\n");
            }

            mApiClient.disconnect();
        }

        else { // turn on
            dayStarted = true;
            trackingEnabled = true;

            btnStartDay.setText(getString(R.string.stop_day));

            currentDistance = 0;

            constantLocationCount = 0;
            pseudoClusterList = new PseudoClusterList(DISTANCE_THRESHOLD);

            constantLocationBuffer = new ArrayList<>();

            requestGPSUpdatePeriodic(locationManagerPeriodic,
                    locationListenerPeriodic);
            requestGPSUpdateConstant(locationManagerConstant,
                    locationListenerConstant,
                    SHORT_REFRESH_TIME,
                    REFRESH_DISTANCE);

            if(logging) {
                writeLog(getCurrentDateTime().split("_")[1] + ": Tracking = ON (day started).\n");
            }

            mApiClient.connect();
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

            // place marker on map
            setMarker(note);
        }
        else {
            Toast.makeText(this,
                    "Insertion failed" +
                            note.toString(),
                    Toast.LENGTH_SHORT).show();
        }

        // display data
        textViewDB.setText(dbHandler.databaseToString());

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


    /**
     * Checks if external storage is available for read and write
     */
    static public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }


    /**
     * Writes data to a log file on the external memory of the device.
     * @param data data to be logged.
     */
    public static void writeLog(String data) {
        if(isExternalStorageWritable()) {
            String filename = LOG_FILENAME +
                    getCurrentDateTime().split("_")[0] +
                    LOG_EXTENSION;

            File file = new File(Environment.getExternalStorageDirectory(), filename);

            if(!file.exists()) {
                try {
                    file.createNewFile();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try {
                FileOutputStream fos = new FileOutputStream(file, true);
                fos.write(data.getBytes());
                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Gets the current date and time, formats them and returns them as string.
     * @return the current date and time as string.
     */
    public static String getCurrentDateTime() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        return(df.format(c.getTime()));
    }

}




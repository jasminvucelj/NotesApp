# Notes App

Notes App is an Android app designed to showcase features such as:

* constant and periodic GPS tracking
* algorithms for handling inaccurate GPS locations
* storing notes with the user's location to a database 
* marking locations on a GoogleMap 
* user activity recogntion based on Google's ActivityRecognitionApi
* logging various app activities into a file on the external memory

Compatible with API 15+ (Android 4.0.3). Uses Google Play Services.

## Features

## GPS tracking

Required classes for location tracking are LocationManager, which provides the access to the system location services, and LocationListener, which receives location updates from the service. In constant tracking, updates are requested regularly, with a given refresh time (and optionally distance), while periodic tracking (in this implementation) uses a repeating CountDownTimer, and requests a single update once the timer expires. This is used to ensure the location is requested even in case of the change of system time, as the timer checks for a change on every pre-defined tick. For locations obtained through periodic tracking, a simple non-null and accuracy value check is performed, while those obtained through constant tracking utilize custom, more elaborate algorithms.

## Algorithms for handling inaccurate GPS locations

For various reasons, the GPS location fixes received on an Android device may be significantly inaccurate. This can pose a significant issue with constant tracking, where even a single inaccurate location, if unhandled, can render the distance metric completely worthless. There are various ways to try and make an app as robust as possible against such issues, and two custom algorithms have been implemented as a means of handling them: a "pseudo-clustering" algorithm for the initial set of locations, and an IQR-based algorithm for later locations.

### "Pseudo-clustering" algorithm

This algorithm, named for the relative similarity to the machine learning concept of clustering, is used for eliminating bad locations from the first received set of locations, where the number of inaccurate ones can be expected to be significant. It relies on one assumption: the number of accurate locations in the initial set will be larger than any single group ("cluster") of inacurate ones. 

The main data structure used in the algorithm is represented by the PseudoClusterList class, and is a list containing all the location clusters. Each of the clusters is represented by the PseudoCluster class, which contains a list of locations that belong to the particular cluster, and an additional Location variable used to store the mean of all locations currently in the cluster. The criterion for adding a location to a cluster is whether or not the distance between that location and the mean of the cluster is less than the distance threshold (defined for the entire cluster list). If the location is too far from all the clusters, a new cluster will be created (and added to the list) to accomodate it. After a location is added to a cluster, its mean is updated accordingly.

Once a sufficient number of points has been added to the list, the algorithm will end by returning all the locations found in the largest cluster, which (according to the initial assumption) should be the list of accurate locations. This algorithm is useful for cases where the accurate locations cannot be expected to be a large majority of all received locations (note that it doesn't even have to be the absolute majority).

### IQR-based algorithm

This algorithm is based on descriptive statistics, depending on the interquartile range (IQR) to detect and remove outlier locations. It works on a buffer of locations with a certain maximum size, which is initialized from the largest cluster from the "pseudo-clustering" algorithm, and new locations are added to it as they are received. Once the maximum size has been reached, it removes outlier locations from the buffer as neccessary, and recalculates the total difference from the points in the buffer. After each recalculation, the oldest location in the buffer is removed, so the buffer can effectively be considered a FIFO queue. The criterion for finding outliers is whether or not their coordinates (latitude and longitude, altitude is not considered) are outside the boundaries defined as:

* Q1 - IQR_COEF * IQR (the lower boundary)

* Q3 + IQR_COEF * IQR (the upper boundary)

where Q1 and Q3 are the 1st and 3rd quartiles of the buffer dataset, IQR is the interquartile range, calculated as the difference Q3-Q1, and IQR_COEF is a constant with a value defined as 1.5. Locations with at least one coordinate outside the defined range are considered outliers and are removed from the buffer, not considered in the total distance calculations.

## Storing notes to a database

A Note is a class containing some data (String inputted through an EditText), with a location obtained through constant tracking. A user can store a note with the desired text to an SQLite database, but only if an accurate location is available: a Note cannot be sent to a database without a location. Adding a Note to the database is done via a DatabaseHandler class, which defines the methods neccessary for reading and writing to the database, as well as deleting entries from it, using the required SQL queries.

## Marking locations on a GoogleMap

Once a Note has been added to the database, a marker for it is added to the GoogleMap, at the coordinates specified in the note. A GoogleMap can be added to the layout as a MapFragment inside a FrameLayout. It also requires an API key (can be obtained from the Google API Console) to be added as metadata in the AndroidManifest file, and a dependency on Google Play Services to be specified in the module's build.gradle file. Finally, the Main Activity should implement the OnMapReadyCallback interface, by overriding the OnMapReady method, which will supply the activity with a GoogleMap as soon as it's ready. Once it's ready, and the Location is converted to a LatLng format the map requires, adding a marker on the coordinates with the text of the note, as well as changing the camera position and zoom, should be a straightforward matter.

## User activity recognition

Google's Activity Recognition API can be used to detect a user's activity (walking, running, in a vehicle, standing still etc.) and have the app adapt accordingly. In this case, it is used to check if the user is currently standing still, and disable the constant GPS tracking if that's the case. That way the low power sensors used for activity recognition can help reduce the power consumption of listening for GPS updates every couple of seconds.

Activity recognition requires a member variable of type GoogleApiClient in the main activity for Google Play Services integration, and will require that activity to implement the GoogleApiClient.ConnectionCallbacks and GoogleApiClient.OnConnectionFailedListener. Once the GoogleApiClient is connected, activity updates can be requested with a certain period, similar to how GPS location updates are requested. Detection of user activity and the sending of that data to the main activity is handled in a special ActivityRecognitionService service, which detects the most probable activity and sends its type and the confidence with which it was detected to the main activity via a LocalBroadcastManager broadcast. Once the data is received in a BroadcastReceiver in the main activity, the constant GPS tracking can be turned on and off based on the type and confidence of the detected activity; if the user is standing still with a confidence higher than a selected threshold, tracking is turned off (as neccessary), else it's turned on.

## Logging app activity

The app logs some of its activity to a file on the external storage (world-readable and editable outside the app). The events logged are start and stop of tracking, receiving an activity from the recognition service, receiving a GPS location fix, accepting or rejecting a location, adding a location to a pseudo-cluster or an IQR algorithm buffer., and distance updates. Each line in the log is also timestamped. The method for writing to a file first checks if external storage is available for writing, and then either creates a log file with the desired text, or appends to an already existing one.

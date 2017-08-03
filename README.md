# Notes App

Notes App is an Android app designed to showcase features such as:

* constant and periodic GPS tracking
* algorithms for detection and removal of inaccurate GPS locations
* storing notes with the user's location to a database 
* marking locations on a GoogleMap 
* user activity recogntion based on Google's ActivityRecognitionApi
* logging various app activities into a file on the external memory

Compatible with API 15+ (Android 4.0.3). Uses Google Play Services.

## Features

## GPS tracking

Required classes for location tracking are LocationManager, which provides the access to the system location services, and LocationListener, which receives location updates from the service. In constant tracking, updates are requested regularly, with a given refresh time (and optionally distance), while periodic tracking (in this implementation) uses a repeating CountDownTimer, and requests a single update once the timer expires. This is used to ensure the location is requested even in case of the change of system time, as the timer checks for a change on every pre-defined tick. For locations obtained through periodic tracking, a simple non-null and accuracy value check is performed, while those obtained through constant tracking utilize custom, more elaborate algorithms.

## Algorithms for detection and removal of inaccurate GPS locations

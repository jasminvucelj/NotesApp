package com.notesapp;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;


public class ActivityRecognitionService extends IntentService {

    public ActivityRecognitionService() {
        super("ActivityRecognitionService");
    }

    public ActivityRecognitionService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if(ActivityRecognitionResult.hasResult(intent)) {
            sendToMainActivity(ActivityRecognitionResult.extractResult(intent));
        }
    }


    private void sendToMainActivity(ActivityRecognitionResult result) {
        DetectedActivity mostProbableActivity = result.getMostProbableActivity();
        int type = mostProbableActivity.getType();
        int confidence = mostProbableActivity.getConfidence();

        Intent intent = new Intent("activityRecognitionIntent");
        intent.putExtra("type", type);
        intent.putExtra("confidence", confidence);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}

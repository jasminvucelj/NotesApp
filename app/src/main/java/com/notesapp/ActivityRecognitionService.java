package com.notesapp;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.List;


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
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            DetectedActivity mostProbableActivity = result.getMostProbableActivity();
            sendToMainActivity(mostProbableActivity.getType(),
                    mostProbableActivity.getConfidence());
        }
    }


    private void sendToMainActivity(int type, int confidence) {
        Intent intent = new Intent("activityRecognitionIntent");
        intent.putExtra("type", type);
        intent.putExtra("confidence", confidence);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}

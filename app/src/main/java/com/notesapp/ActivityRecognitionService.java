package com.notesapp;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
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
            sendToMainActivity(ActivityRecognitionResult.extractResult(intent));
        }
    }


    private void sendToMainActivity(ActivityRecognitionResult result) {
        List<DetectedActivity> probableActivities = result.getProbableActivities();
        ArrayList<Integer> typeList = new ArrayList<>();
        ArrayList<Integer> confidenceList = new ArrayList<>();

        for(DetectedActivity activity : probableActivities) {
            typeList.add(activity.getType());
            confidenceList.add(activity.getConfidence());

            // logging
            if(MainActivity.logging) {
                MainActivity.writeLog(MainActivity.getCurrentTime() +
                        ": " +
                        String.valueOf(activity.getType()) +
                        "(" +
                        String.valueOf(activity.getConfidence()) +
                        "%)\n");
            }
        }

        Intent intent = new Intent("activityRecognitionIntent");
        intent.putExtra("type", typeList);
        intent.putExtra("confidence", confidenceList);

        Toast.makeText(this, String.valueOf(result.getMostProbableActivity().getType()) + " (" + String.valueOf(result.getMostProbableActivity().getConfidence()) + "%)", Toast.LENGTH_LONG).show();
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

}

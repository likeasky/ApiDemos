package com.example.android.apis;

import android.app.Application;
import android.util.Log;

/**
 * Created by nobody on 2015-01-18.
 *
 */
public class ApiDemosApplication extends Application {
    private String TAG = "ApiDemosApplication";

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
    }
}

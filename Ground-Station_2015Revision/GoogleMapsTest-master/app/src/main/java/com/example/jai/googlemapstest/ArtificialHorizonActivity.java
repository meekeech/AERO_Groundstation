package com.example.jai.googlemapstest;

import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class ArtificialHorizonActivity extends AppCompatActivity {

    private AttitudeIndicator mAttitudeIndicator;
    private Telemetry telemetry;

    //might have to change to float
    private float pitch,roll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artificial_horizon);
        mAttitudeIndicator = (AttitudeIndicator) findViewById(R.id.attitude_indicator);

        //sets orientation to landscape
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);


        if(telemetry == null){
            telemetry = new Telemetry(this);
        }


        //set up USB and have it start recording values within that class
        telemetry.setUpUsbIfNeeded();

        //get the values you need for pitch and roll
        //pitch = telemetry.getPitch();
        //roll = telemetry.getRoll();
        pitch = 30;
        roll = 30;

        //send those values to the attitude indicator class to display it in the new activity
        mAttitudeIndicator.setAttitude(pitch, roll); //must change these to float or double consistently


    }


}

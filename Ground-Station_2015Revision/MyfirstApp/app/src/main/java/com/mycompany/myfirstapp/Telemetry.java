package com.mycompany.myfirstapp;


import com.google.android.gms.maps.model.Marker;

public class Telemetry extends MyActivity {
    // Telemetry Variables
    public Marker mTarget;
    public Marker mPlane;
    float planeAirSpeed = 0;
    float planeBaro = 0;
    float planeHeading = 0;
    float planeGroundSpeed = 0;
    float targetAlt = 0;
    float targetLat,targetLng;
    float planeLat,planeLng;
    float planeAlt = 0;

    public void updateTelemetry(String airspeed, String heading, String baroH, String lngGPS, String latGPS, String altGPS, String horizon, String groundSpeed) {
        //Read in and update all telemetry data
        planeAirSpeed = Float.parseFloat(airspeed);
        planeHeading = Float.parseFloat(heading);
        planeBaro = Float.parseFloat(baroH);
        planeLat = Float.parseFloat(latGPS);
        planeLng = Float.parseFloat(lngGPS);
        planeAlt = Float.parseFloat(altGPS);
        planeGroundSpeed = Float.parseFloat(groundSpeed);

        //update corresponding fields
        tvAlt.setText(String.valueOf((int)planeBaro + "ft"));

        //if gps zero don't update
        if (planeLat == 0 && planeLng == 0){
            return;
        }

        //updatePlanePosition(planeLat,planeLng,planeAlt);

    }


}

package com.mycompany.myfirstapp;


import com.google.android.gms.maps.model.Marker;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;

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
    int mReadSize=0;
    static final int READBUF_SIZE  = 256;
    byte[] rbuf  = new byte[READBUF_SIZE];
    char[] rchar = new char[READBUF_SIZE];
    Handler mHandler = new Handler();
    private boolean timer = false;         //drop timer active

    //Setup Buttons for Interface
    private Button bDrop;
    private Button bComm;
    private Button bTarget;
    private Button bTimer;
    private TextView tvAlt;
    private TextView tvRelease;

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

    private void setTimerToDrop(){
        double timeToTarget = 0;
        double currentHeight = planeAlt - targetAlt;

        if (timer){
            //calculate distance to target
            double latitudeFromTarget =  Math.abs(targetLat - planeLat)*110819;  //convert to m
            double longitudeFromTarget =  Math.abs(targetLng - planeLng)*98361; //convert to m
            double distanceToTarget = Math.sqrt(Math.pow(latitudeFromTarget,2)+Math.pow(longitudeFromTarget,2));

            //Calculate time to target
            if (planeGroundSpeed != 0){
                timeToTarget = distanceToTarget/(double)planeGroundSpeed;
            }

            //include delay for drop response ~0.4s
            //include delay for height of release

            //Trouble shooting for altitude differences

            bTimer.setText(String.format("%.2fs",timeToTarget));
            bTarget.setText(String.format("%.1fft",currentHeight));


        }else {
            bTimer.setText("TIMER");
        }
    }

    public Runnable mLoop = new Runnable() {
        @Override
        public void run() {
            int i;
            int readSize;
            mThreadIsStopped = false;
            while(true) {
                if(mThreadIsStopped) {
                    break;
                }
                synchronized (ftDev) {
                    readSize = ftDev.getQueueStatus();
                    if(readSize>55) {
                        mReadSize = readSize;
                        if(mReadSize > READBUF_SIZE) {
                            mReadSize = READBUF_SIZE;
                        }
                        ftDev.read(rbuf,mReadSize);

                        for(i=0; i<mReadSize; i++) {
                            rchar[i] = (char)rbuf[i];
                        }


                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {

                                //This is some important shit right here!!
                                //All telelmetry data is coming in here
                                //splice string here
                                String telemetryBuffer = String.copyValueOf(rchar, 0, mReadSize);
                                String filteredBuffer = telemetryBuffer.substring(telemetryBuffer.lastIndexOf("$") + 1);

                                String telemetry[] = filteredBuffer.split("[,]");

                                //check size of telemetry
                                if (telemetry.length >= 8 ) {

                                    updateTelemetry(
                                            (telemetry[0]),      //airspeed
                                            (telemetry[1]),      //heading
                                            (telemetry[2]),      //baroAlt
                                            (telemetry[3]),      //lon
                                            (telemetry[4]),      //lat
                                            (telemetry[5]),     //alt
                                            (telemetry[6]),     //horizon
                                            (telemetry[7]));     //groundspeed
                                }

                                setTimerToDrop();


                            }
                        });

                    } // end of if(readSize>0)
                } // end of synchronized
            }
        }
    };
}

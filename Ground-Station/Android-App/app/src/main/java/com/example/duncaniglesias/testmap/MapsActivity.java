/*UWO AERO DESIGN - Predictive Time to Drop Estimator for release of payload
* on a predetermined target. SAE Aero Design Competition EAST, Lakelan, FL
* Developed by Duncan Iglesias & Matthew Stokes (March 2015)
* */

package com.example.duncaniglesias.testmap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.usb.UsbManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

public class MapsActivity extends FragmentActivity {

    //Setup Maps & variables needed for calculations
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private double marker_lat = 0;
    private double marker_long = 0;
    private double marker_alt = 0;

    //Telemetry
    private Marker mTarget;
    private Marker mPlane;
    float planeAirSpeed = 0;
    float planeBaro = 0;
    float planeHeading = 0;
    float planeGroundSpeed = 0;
    float targetAlt = 0;
    float targetLat,targetLng;
    float planeLat,planeLng;
    float planeAlt = 0;

    double currentTime = 10;
    double previousTime = 0;
    private double previousLat = 0;
    private double previousLng = 0;

    //Setup Buttons for Interface
    private Button bDrop;
    private Button bComm;
    private Button bTarget;
    private Button bTimer;
    private TextView tvAlt;
    private TextView tvRelease;

    //Misc
    FT_Device ftDev;
    private boolean payload = true;
    private boolean timer = false;
    public int READ_BUFFER_SIZE = 56; //
    public int port = 0;
    public Context global_context;
    public static D2xxManager ftD2xx = null; //
    byte[] rbuf  = new byte[READBUF_SIZE];
    char[] rchar = new char[READBUF_SIZE];
    byte[] drop = {(byte) '0'};
    byte[] load = {(byte) '1'};
    byte[] USBBuffer; //
    final byte XON = 0x11;    /* Resume transmission */
    final byte XOFF = 0x13;    /* Pause transmission */
    static final int READBUF_SIZE  = 256;
    int mReadSize=0;
    boolean mThreadIsStopped = true;
    Handler mHandler = new Handler();

    //hardcode the baudrate
    int baud = 9600;

    //Debugging string
    private String TAG = "TAG";





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            ftD2xx = D2xxManager.getInstance(this);
        }
        catch (D2xxManager.D2xxException e) {Log.e("FTDI_HT","getInstance fail!!");}
        USBBuffer = new byte[READ_BUFFER_SIZE];

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //Log.d(TAG, "...In onCreate()...");
        global_context = this;

        setUpMapIfNeeded();
        //setUpUsbIfNeeded();

        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        //Set up buttons/textview
        bTimer =    (Button) findViewById(R.id.button_timer);
        bDrop =     (Button) findViewById(R.id.button_drop);
        bTarget =   (Button) findViewById(R.id.button_target);
        bComm =     (Button) findViewById(R.id.button_commsync);
        tvAlt =     (TextView) findViewById(R.id.tvHeight);
        tvRelease = (TextView) findViewById(R.id.tvRelease);


        setUpMapClickListener();
        setUpButtonsListeners();

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mThreadIsStopped = true;
        unregisterReceiver(mUsbReceiver);
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        //Setup the map fragment in the activity
        mMap.setMyLocationEnabled(true);                // enable seeing your location
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);     // see satellite view
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, true);
        Location myLocation = locationManager.getLastKnownLocation(provider);

        //fail safe incase location is null
        double lat, lng, alt;

        if (myLocation == null) {
            lat = 0;
            lng = 0;
            alt = 0;
        } else {
            lat = myLocation.getLatitude();
            lng = myLocation.getLongitude();
            alt = myLocation.getAltitude();
        }

        marker_lat = lat;
        marker_long = lng;
        marker_alt = alt;

        planeAirSpeed = 0;
        planeHeading = 0;
        planeBaro = 0;
        planeLat = 0;
        planeLng = 0;
        planeAlt = 0;

        LatLng coordinate = new LatLng(lat, lng);
        CameraUpdate yourLocation = CameraUpdateFactory.newLatLngZoom(coordinate, 18);
        mMap.animateCamera(yourLocation);

        //add default marker locations on start up
        mTarget = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .title("Target")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_target))
                .visible(false));

        mPlane = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(0, 0))
                .title("Plane")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_plane))
                .visible(false));
    }

    private void getTargetLocation() {
        //Insert logic here for printing the marker logic out
        if (marker_lat == 0 || marker_long == 0) {
            Log.v(TAG, "Point on map not selected");
            Toast.makeText(getBaseContext(), "Please select a point", Toast.LENGTH_SHORT).show();
        } else {
            Log.d("ButtonPress", "lat: " + marker_lat + " long: " + marker_long + " alt: " + marker_alt);
            targetLat = (float)marker_lat;
            targetLng = (float)marker_long;
            targetAlt = planeAlt;
            Toast.makeText(getBaseContext(), "Locked on target", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePlanePosition(float Lat, float Lng, float Alt) {
        //Update the position of the marker from the plane
        mPlane.remove();
        mPlane = mMap.addMarker(new MarkerOptions()
                .position(new LatLng(Lat,Lng))
                .title("Plane")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_plane))
                .anchor((float)0.5,(float)0.5)
                .rotation(planeHeading)
                .visible(true));
    }

    void payloadToggle()
    {
        if(ftDev == null){
            return;
        }

        if (!ftDev.isOpen()) {
            Toast.makeText(global_context, "Device not open!", Toast.LENGTH_SHORT).show();
            return;
        }


        synchronized (ftDev) {
            if(!ftDev.isOpen()) {
                Log.e(TAG, "onClickWrite : Device is not open");
                return;
            }

            ftDev.setLatencyTimer((byte)16);

            //payload toggle command logic
            if (payload) {
                ftDev.write(drop, drop.length);
                payload = false;
                bDrop.setText("LOAD");

                //Record the drop height
                tvRelease.setText((int) (planeAlt - targetAlt) + "ft");
            }
            else {
                ftDev.write(load, load.length);
                payload = true;
                bDrop.setText("DROP");
            }


//            Toast.makeText(global_context, "You found me!!!", Toast.LENGTH_SHORT).show();
        }
    }

    private void setUpUsbIfNeeded() {
        // if already connected
        if (ftDev != null && ftDev.isOpen()) {
            Toast.makeText(global_context,"Port("+port+") is already opened.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ftD2xx.createDeviceInfoList(global_context) < 1) {
            Toast.makeText(getBaseContext(), "Connect cable", Toast.LENGTH_LONG).show();
            return;
        }

        ftDev = ftD2xx.openByIndex(global_context, 0);

        if (ftDev == null) {
            Toast.makeText(getBaseContext(), "Connect cable", Toast.LENGTH_LONG).show();
            return;
        }

        setConfig();
        Toast.makeText(getBaseContext(), "Connected", Toast.LENGTH_LONG).show();

    }

    private void setUpButtonsListeners() {
        //Set on click for each button
        bDrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "payload toggle");
                payloadToggle();
            }
        });
        bTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toggle timer state
                if(timer){
                    timer = false;
                }else {
                    timer = true;
                }
            }
        });
        bTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "bTarget");
                getTargetLocation();
            }
        });
        bComm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "Communication Request");
                setUpUsbIfNeeded();
                openDevice();
            }
        });


    }

    private void setUpMapClickListener() {
        // map
        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        GoogleMap googleMap = supportMapFragment.getMap();
        googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng latLng) {
                mTarget.remove();
                MarkerOptions markerOptions = new MarkerOptions();
                        markerOptions.position(latLng);
                        markerOptions.title(latLng.latitude + " : " + latLng.longitude);
                        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_target));
                        //mMap.clear();  // clear old marker
                //mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                mTarget = mMap.addMarker(markerOptions
                        .anchor((float) 0.5, (float) 0.5)
                        .visible(true));

                mMap.setMyLocationEnabled(true);                // enable seeing your location

                LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                Criteria criteria = new Criteria();
                String provider = locationManager.getBestProvider(criteria, true);
                Location myLocation = locationManager.getLastKnownLocation(provider);

                //get lat and long
                marker_lat  = latLng.latitude;
                marker_long = latLng.longitude;
                marker_alt  = planeAlt;


            }
        });

        setUpMapIfNeeded();
    }

    void setConfig()
    {
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET); // reset to UART mode for 232 devices
        ftDev.setBaudRate(baud);
        ftDev.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8, D2xxManager.FT_STOP_BITS_1,
                D2xxManager.FT_PARITY_NONE);
        ftDev.setFlowControl(D2xxManager.FT_FLOW_RTS_CTS, XON, XOFF);
    }

    private void closeDevice() {
        //mThreadIsStopped = true;
        //updateView(false);
        if(ftDev != null) {
            ftDev.close();
        }
    }


    // done when ACTION_USB_DEVICE_ATTACHED
    @Override
    protected void onNewIntent(Intent intent) {
        openDevice();
    };

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // never come here(when attached, go to onNewIntent)
                openDevice();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                closeDevice();
            }
        }
    };

    private void openDevice() {
        if(ftDev != null) {
            if(ftDev.isOpen()) {
                if(mThreadIsStopped) {
                    //updateView(true);
                    setConfig();
                    ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                    ftDev.restartInTask();
                    new Thread(mLoop).start();

                }
                return;
            }
        }

        int devCount = 0;
        devCount = ftD2xx.createDeviceInfoList(this);

        Log.d(TAG, "Device number : "+ Integer.toString(devCount));

        D2xxManager.FtDeviceInfoListNode[] deviceList = new D2xxManager.FtDeviceInfoListNode[devCount];
        ftD2xx.getDeviceInfoList(devCount, deviceList);

        if(devCount <= 0) {
            return;
        }

        if(ftDev == null) {
            ftDev = ftD2xx.openByIndex(this, 0);
        } else {
            synchronized (ftDev) {
                ftDev = ftD2xx.openByIndex(this, 0);
            }
        }

        if(ftDev.isOpen()) {
            if(mThreadIsStopped) {
                //updateView(true);
                setConfig();
                ftDev.purge((byte) (D2xxManager.FT_PURGE_TX | D2xxManager.FT_PURGE_RX));
                ftDev.restartInTask();
                new Thread(mLoop).start();
            }
        }
    }

    private Runnable mLoop = new Runnable() {
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


    private void updateTelemetry(String airspeed, String heading, String baroH, String lngGPS, String latGPS, String altGPS, String horizon, String groundSpeed) {
        //Read in and update all telemetry data
        planeAirSpeed = Float.parseFloat(airspeed);
        planeHeading = Float.parseFloat(heading);
        planeBaro = Float.parseFloat(baroH);
        planeLat = Float.parseFloat(latGPS);
        planeLng = Float.parseFloat(lngGPS);
        planeAlt = Float.parseFloat(altGPS);
        planeGroundSpeed = Float.parseFloat(groundSpeed);

     /*   //Update relative position
        double tmpLng = planeLng - previousLng;
        double tmpLat = planeLat - previousLat;
*/
/*        if (tmpLat >= 0 && tmpLng >= 0){
            //NE
            planeHeading = (float) ((float)(Math.tan(planeLng/planeLat))*(180/3.14));
        }else if (tmpLat <= 0 && tmpLng >= 0){
            //SE
            planeHeading = (float) ((float)(Math.tan(planeLat/planeLng))*(180/3.14)) + 90;
        }else if (tmpLat <= 0 && tmpLng <= 0){
            //SW
            planeHeading = (float) ((float)(Math.tan(planeLng/planeLat))*(180/3.14)) + 180;
        }else{
            //NW
            planeHeading = (float) ((float)(Math.tan(planeLat/planeLng))*(180/3.14)) + 270;
        }*/

/*
        //Calculate current speed (m/s)
        currentTime = (double)System.currentTimeMillis()/1000;
        planeAirSpeed = (float) Math.sqrt(Math.pow(Math.abs(tmpLat)*110819,2)+Math.pow(Math.abs(tmpLng)*98361,2));///(float)(currentTime-previousTime);
*/

//        Toast.makeText(getBaseContext(), String.valueOf(planeHeading), Toast.LENGTH_LONG).show();

/*
        //update position cue
        previousLat = planeLat;
        previousLng = planeLng;
        previousTime = currentTime;

*/

        //bTarget.setText(String.valueOf((int)planeGroundSpeed));

        //update corresponding fields
        tvAlt.setText(String.valueOf((int)planeBaro + "ft"));

        //if gps zero don't update
        if (planeLat == 0 && planeLng == 0){
            return;
        }

        updatePlanePosition(planeLat,planeLng,planeAlt);

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


}

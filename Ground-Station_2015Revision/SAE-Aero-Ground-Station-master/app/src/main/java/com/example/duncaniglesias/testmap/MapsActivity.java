//////////////////////////////////////////////////////////////////////////////////
/////                  SAE AERO DESIGN GROUND STATION                        /////
//////////////////////////////////////////////////////////////////////////////////
/*UWO AERO DESIGN - Predictive Time to Drop Estimator for release of payload
* on a predetermined target. SAE Aero Design Competition EAST, Lakeland, FL
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

// Google Maps support
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

// USB support
import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

//////////////////////////////////////////////////////////////////////////////////
/////                             MAIN ACTIVITY                              /////
//////////////////////////////////////////////////////////////////////////////////
public class MapsActivity extends FragmentActivity {

    // Setup Maps & variables needed for calculations
    private GoogleMap mMap; // null if Google Play services APK is unavailable
    private double marker_lat   = 0;
    private double marker_long  = 0;
    private double marker_alt   = 0;

    // Telemetry
    private Marker mTarget;
    private Marker mPlane;
    private float  planeAirSpeed    = 0;
    private float  planeBaro        = 0;
    private float  planeHeading     = 0;
    private float  planeGroundSpeed = 0;
    private float  targetAlt        = 0;
    private float  planeAlt         = 0;
    private float  targetLat        = 0;
    private float  targetLng        = 0;
    private float  planeLat         = 0;
    private float  planeLng         = 0;
    private float  horizon          = 0;

    // Setup Buttons for Interface
    private Button      bDrop;
    private Button      bComm;
    private Button      bTarget;
    private Button      bTimer;
    private TextView    tvAlt;
    private TextView    tvRelease;

    // USB port
    FT_Device ftDev;
    public static D2xxManager ftD2xx = null;

    // Payload Drop & Time to Release
    private boolean payload         = true;
    private boolean timer           = false;

    // Buffer Read
    public int READ_BUFFER_SIZE = 56;
    public int port = 0;
    public byte[] USBBuffer;
    public final byte XON = 0x11;    /* Resume transmission */
    public final byte XOFF = 0x13;    /* Pause transmission */
    public static final int READBUF_SIZE  = 256;
    public int mReadSize = 0;
    public boolean mThreadIsStopped = true;

    // Thread for handling data
    Handler mHandler = new Handler();

    // Serial communication (usb radio)
    Context global_context;
    private final int BAUD = 57600;
    private final byte[] drop = {(byte) '1'};
    private final byte[] load = {(byte) '0'};
    private final byte[] request = {(byte) 'r'};

    // Initialize buffers for incoming transmission
    private byte[] rbuf  = new byte[READBUF_SIZE];
    private char[] rchar = new char[READBUF_SIZE];

    // Debugging string
    private String TAG = "TAG";
    private String TEL = "TEL";
    private String COM = "COM";
    private String PAY = "PAY";

    /**
     * Creates all necessary buttons and items for this current screen.
     * Calls the map fragment to begin and markers necessary for tracking
     * the plane in real time.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Initialize USB socket
        try {
            ftD2xx = D2xxManager.getInstance(this);
        }
        catch (D2xxManager.D2xxException e) {
            Log.e("FTDI_HT","getInstance fail!!");
        }

        // Establish USB buffer
        USBBuffer = new byte[READ_BUFFER_SIZE];
        super.onCreate(savedInstanceState);

        // Set view to current activity
        setContentView(R.layout.activity_maps);
        global_context = this;

        // Set screen orientation and maps type
        setUpMapIfNeeded();
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Set up buttons/textview
        bTimer =    (Button) findViewById(R.id.button_timer);
        bDrop =     (Button) findViewById(R.id.button_drop);
        bTarget =   (Button) findViewById(R.id.button_target);
        bComm =     (Button) findViewById(R.id.button_commsync);
        tvAlt =     (TextView) findViewById(R.id.tvHeight);
        tvRelease = (TextView) findViewById(R.id.tvRelease);

        // Start onclick listeners
        setUpMapClickListener();
        setUpButtonsListeners();

        // Stuff for the USB socket
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    /**
     * Sets up the map if the screen goes to sleep during the activity.
     * This is not ideal and has not be tested. Future work!!!
     */
    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if the activity is paused. This is not ideal
     * and has not be tested. Future work!!!
     */
    @Override
    public void onPause() {
        super.onPause();
    }

    /**
     * Destroy all humans. Close the activity and the USB socket.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mThreadIsStopped = true;
        unregisterReceiver(mUsbReceiver);
    }

    /**
     * Sets up the map fragment if not already done so. Zooms to location,
     * indicates the location of the ground station, and will populate the
     * position of the plane if there is an appropriate connection.
     */
    private void setUpMapIfNeeded() {
        // If not already initialized
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

    /**
     * Sets up the map fragment if not already done so. Zooms to location,
     * indicates the location of the ground station, and will populate the
     * position of the plane if there is an appropriate connection.
     */
    private void setUpMap() {
        // Setup the map fragment in the activity
        mMap.setMyLocationEnabled(true);                // enable seeing your location
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);     // see satellite view
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, true);
        Location myLocation = locationManager.getLastKnownLocation(provider);

        double currentLatitude = myLocation.getLatitude();
        double currentLongitude = myLocation.getLongitude();
        LatLng latLng = new LatLng(currentLatitude, currentLongitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

        if (myLocation == null) {
            marker_lat = 0;
            marker_long = 0;
            marker_alt = 0;
        } else {
            marker_lat = myLocation.getLatitude();
            marker_long = myLocation.getLongitude();
            marker_alt = myLocation.getAltitude();
        }

        // Update current location on map fragment
        LatLng coordinate = new LatLng(marker_lat, marker_long);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinate, 16));

        // Add default marker locations on start up
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
        if (marker_lat == 0 || marker_long == 0) {
            Log.v(TAG, "null target selection");
            Toast.makeText(getBaseContext(), "Select a Target", Toast.LENGTH_SHORT).show();
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
            String msg = "Device not open!";
            Toast.makeText(global_context, msg, Toast.LENGTH_SHORT).show();
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
                ftDev.write(request, request.length);
                ftDev.write(drop, drop.length);
                payload = false;
                bDrop.setText("LOAD");

                //Record the drop height
                //tvRelease.setText((int) (planeAlt - targetAlt) + "ft");
            }
            else {
                ftDev.write(request, request.length);
                ftDev.write(load, load.length);
                payload = true;
                bDrop.setText("DROP");
            }

            // print to screen telemetry data
            //Toast.makeText(global_context,"...", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sets up the USB socket if not already opened. Checks for connectivity
     * and then opens 'port 0' since there is only one USB port on the
     * device. This step is critical for communication with the plane.
     */
    private void setUpUsbIfNeeded() {
        // Check if already connected
        if (ftDev != null && ftDev.isOpen()) {
            String msg = "Port("+port+") is already opened.";
            Toast.makeText(global_context, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check whether there is a device plugged in
        if (ftD2xx.createDeviceInfoList(global_context) < 1) {
            String msg = "Connect the USB radio";
            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
            return;
        }

        // Open the device on port 0 (USB radio by default)
        ftDev = ftD2xx.openByIndex(global_context, 0);

        // Check for successful connection
        if (ftDev == null) {
            String msg = "Connect the USB radio";
            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG).show();
            return;
        }

        setConfig();
        Toast.makeText(getBaseContext(), "Connected", Toast.LENGTH_LONG).show();

    }

    /**
     * Sets up the buttons on the front user interface and sets the
     * onclick listener to active.
     */
    private void setUpButtonsListeners() {
        //Set on click for each button
        bDrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {payloadToggle();}
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

    /**
     * Sets up the map onclick listeners for the Google Maps fragment.
     * This allows the user to add/update the position of the target
     * waypoint.
     */
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

                mTarget = mMap.addMarker(markerOptions
                        .anchor((float)0.5,(float)0.5)
                        .visible(true));

                // Enable device location
                mMap.setMyLocationEnabled(true);

                LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
                Criteria criteria = new Criteria();
                String provider = locationManager.getBestProvider(criteria, true);
                //Location myLocation = locationManager.getLastKnownLocation(provider);

                // Update current latitude and longitude
                marker_lat  = latLng.latitude;
                marker_long = latLng.longitude;
                marker_alt  = planeAlt;
            }
        });

        setUpMapIfNeeded();
    }

    /**
     * FTDI USB communication configuration to set the rate of communication
     * and attributes associated with the port.
     */
    void setConfig()
    {
        ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET); // reset to UART mode for 232 devices
        ftDev.setBaudRate(BAUD);
        ftDev.setDataCharacteristics(D2xxManager.FT_DATA_BITS_8, D2xxManager.FT_STOP_BITS_1,
                D2xxManager.FT_PARITY_NONE);
        ftDev.setFlowControl(D2xxManager.FT_FLOW_RTS_CTS, XON, XOFF);
    }

    /**
     * Closes the USB port
     */
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

    /**
     * Request for telemetry. Spawns a thread to request telemetry from plane
     * and listens for a response.
     */
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
                    // Request telemetry from plane
                    if(!ftDev.isOpen()) {
                        Log.e(TAG, "onClickWrite : Device is not open");
                        return;
                    }

                    ftDev.setLatencyTimer((byte) 16);
                    ftDev.write(request, request.length);

                    try{
                        Thread.sleep(500);
                    }catch(Exception e)
                    {
                        Log.e(TEL, "Error trying to sleep");
                    }

                    // Size of buffer
                    readSize = ftDev.getQueueStatus();

                    // If elements are present in the buffer
                    if(readSize > 40) {
                        mReadSize = readSize;

                        if(mReadSize > READBUF_SIZE) {
                            mReadSize = READBUF_SIZE;
                        }

                        ftDev.read(rbuf, mReadSize);

                        for(i=0; i<mReadSize; i++) {
                            rchar[i] = (char)rbuf[i];
                        }

                        // Parse incoming string and set telemetry coordinates
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {

                                // Copy in buffer to string
                                String telemetryBuffer = String.copyValueOf(rchar, 0, mReadSize);

                                // Remove beginning/end carriages '$' and '#'
                                String filteredBuffer = telemetryBuffer.substring(telemetryBuffer.lastIndexOf("$") + 1);
                                String[] splitBuffer  = filteredBuffer.split("#");
                                filteredBuffer = splitBuffer[0];
                                //Toast.makeText(global_context,filteredBuffer, Toast.LENGTH_SHORT).show();

                                // Parse incoming data by ',' delimiter and update
                                String telemetry[] = filteredBuffer.split("[,]");
                                if (telemetry.length >= 8) {updateTelemetry(telemetry);}

                                setTimerToDrop();
                                bComm.setText((telemetry[6]));
                            }
                        });

                    } // end of if(readSize>0)
                } // end of synchronized
            }
        }
    };


    // private void updateTelemetry(String airspeed, String heading, String baroH, String lngGPS, String latGPS, String altGPS, String horizon, String groundSpeed)
    private void updateTelemetry(String[] telemetry)
     {
        //Read in and update all telemetry data
        planeAirSpeed =     Float.parseFloat(telemetry[0]);
        planeHeading =      Float.parseFloat(telemetry[1]);
        planeBaro =         Float.parseFloat(telemetry[2]);
        planeLat =          Float.parseFloat(telemetry[3]);
        planeLng =          Float.parseFloat(telemetry[4]);
        planeAlt =          Float.parseFloat(telemetry[5]);
        horizon =           Float.parseFloat(telemetry[6]);
        planeGroundSpeed =  Float.parseFloat(telemetry[7]);

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

        // Update current height of the plane
        tvAlt.setText(String.valueOf((int)planeBaro + "ft"));

        // if gps zero don't update
        if (!(planeLat == 0 && planeLng == 0)){
            updatePlanePosition(planeLat,planeLng,planeAlt);
        }
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

//////////////////////////////////////////////////////////////////////////////////
/////                                END                                     /////
//////////////////////////////////////////////////////////////////////////////////

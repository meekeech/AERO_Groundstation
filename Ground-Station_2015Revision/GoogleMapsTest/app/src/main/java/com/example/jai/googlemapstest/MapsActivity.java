package com.example.jai.googlemapstest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.support.design.widget.Snackbar;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Marker;
import android.location.LocationManager;
import android.database.sqlite.SQLiteDatabase;
import android.support.design.widget.FloatingActionButton;

import java.util.jar.Manifest;
import java.util.logging.Handler;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;


public class MapsActivity extends AppCompatActivity {

    private Telemetry telemetry;

    private GoogleMap mMap;
    private SQLiteDatabase pointDb;

    // Target Variables
    public Marker targetMarker;
    public LatLng targetPoint;
    public String targetName;

    // Variables for saving target location
    protected boolean savingMode = false;

    // Plane location variables to draw marker
    protected double plane_lat = 0;
    protected double plane_long = 0;
    protected float plane_rotation = 0;

    // Logging variables
    protected String TAG = "TAG";

    //Ugly USB stuff
    // USB port
    FT_Device ftDev;
    public static D2xxManager ftD2xx = null;

    // Payload Drop & Time to Release
    private boolean payload = true;
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
    //Handler mHandler = new Handler();

    // Serial communication (usb radio)
    Context global_context;
    private final int BAUD = 57600;
    private final byte[] drop = {(byte) '1'};
    private final byte[] load = {(byte) '0'};
    private final byte[] request = {(byte) 'r'};

    // Initialize buffers for incoming transmission
    private byte[] rbuf  = new byte[READBUF_SIZE];
    private char[] rchar = new char[READBUF_SIZE];

    //floating button
    protected FloatingActionButton fab;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Initialize USB socket
        try {
            ftD2xx = D2xxManager.getInstance(this);
        }
        catch (D2xxManager.D2xxException e) {
            Log.e("FTDI_HT", "getInstance fail!!");
        }

        // Establish USB buffer
        USBBuffer = new byte[READ_BUFFER_SIZE];


        // Set current view
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        global_context = this;

        // what is the right contecxt for the USB to work??? qu'est que fuck!
        Log.v(TAG, this.toString());


        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        setUpFloatingButton();
        setUpInternalStorage();
        setUpMapIfNeeded();
        updateMarkers();

        telemetry = new Telemetry(this);

        // Stuff for the USB socket
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }


    public void setUpFloatingButton() {
        //FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "Drop/Load Action", Snackbar.LENGTH_LONG)
                        //.setAction("Action", null).show();

                if (payload) {
                    fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_refresh, global_context.getTheme()));
                    payload = false;
                }
                else {
                    fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_drop_icon, global_context.getTheme()));
                    payload = true;
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu customMenu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, customMenu);
        return true;
    }

    public void setUpInternalStorage() {
        pointDb = this.openOrCreateDatabase("PointDatabase", MODE_PRIVATE, null);
        pointDb.execSQL("CREATE TABLE IF NOT EXISTS points (id INTEGER PRIMARY KEY AUTOINCREMENT, lat REAL NOT NULL, long REAL NOT NULL, name BLOB NOT NULL);");
        pointDb.close();
    }

    @Override
    protected void onResume(){
        super.onResume();
        setUpMapIfNeeded();
        updateMarkers();
    }

    private void setUpMapIfNeeded() {
        // Confirm map is not already instantiated
        if(mMap == null) {
            // Attempt to obtain map from SupportMapFragment
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            if (mMap != null) {
            setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(true);
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        zoomCameraToLocation();

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener(){
            @Override
            public void onMapClick(LatLng point) {
                if (savingMode) {
                    targetPoint = point;
                    updateMarkers();
                }
            }
        });

        // Add marker to plane
        LatLng plane = new LatLng(plane_lat, plane_long);

        mMap.addMarker(new MarkerOptions()
                .position(plane)
                .title("Plane Location")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_plane))
                .anchor(0.5f, 0.5f)
                .rotation(plane_rotation));
    }

    public void zoomCameraToLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, false);
        Location myLocation = locationManager.getLastKnownLocation(provider);

        double latitude;
        double longitude;

        if (myLocation == null) {
            //locationManager.requestLocationUpdates(provider, 1000, 0, );
            latitude = 0;
            longitude = 0;
        } else {
            latitude = myLocation.getLatitude();
            longitude = myLocation.getLongitude();
        }

        LatLng currentLocation = new LatLng(latitude, longitude);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocation));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.setTarget:
                if (!savingMode){
                    Toast.makeText(this, "Tap to Set Target Location", Toast.LENGTH_SHORT).show();
                    savingMode = true;
                } else {
                    Toast.makeText(this, "Set Target Location Disabled", Toast.LENGTH_SHORT).show();
                    savingMode = false;
                }
                break;
            case R.id.saveTarget:
                if(targetPoint != null) {
                    promptInput();
                }
                break;
            case R.id.targetAtCurrentLocation:
                Location myLocation = mMap.getMyLocation();
                targetPoint = new LatLng(myLocation.getLatitude(), myLocation.getLongitude());
                updateMarkers();
                savingMode = false;
                break;
            case R.id.loadLocation:
                savingMode = false;
                Intent intent = new Intent(MapsActivity.this, LoadTargetActivity.class);
                pointDb.close();
                startActivityForResult(intent, 1);
                break;
            case R.id.connect:
                Log.v(TAG, "Communication Request");
                setUpUsbIfNeeded();
                openDevice();
                //telemetry.Connect();
                //Toast.makeText(this, "Conenction Button", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == 1) {
                Double returnedPoint[] = (Double[]) data.getSerializableExtra("1");
                targetPoint = new LatLng(returnedPoint[0], returnedPoint[1]);
                updateMarkers();
                Toast.makeText(this, "Received Data", Toast.LENGTH_LONG).show();
            } else {
                return;
            }
        }
    }

    public void promptInput() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Name of Target");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                targetName = input.getText().toString();
                savingMode = false;
                savePoint();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void updateMarkers() {
        //remove previously placed Marker
        if (targetMarker != null) {
            targetMarker.remove();
        }

        //place marker where user just clicked
        if(targetPoint != null) {
            targetMarker = mMap.addMarker(new MarkerOptions()
                    .position(targetPoint)
                    .title("Target")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_target))
                    .anchor(0.5f, 0.5f));
        }
    }

    public void savePoint() {
        pointDb = this.openOrCreateDatabase("PointDatabase", MODE_PRIVATE, null);
        pointDb.execSQL("INSERT INTO points (lat, long, name) VALUES ( "
                + Double.toString(targetPoint.latitude)
                + " , " + Double.toString(targetPoint.longitude)
                + " , " + "'" + targetName + "'" + " );");
        pointDb.close();
        Toast.makeText(this, "Saving Target Location: " + Double.toString(targetPoint.latitude) + " " + Double.toString(targetPoint.longitude), Toast.LENGTH_SHORT).show();
    }


/////////////////////////////////////////////////////////////////////////////
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
                    //new Thread(mLoop).start();

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
                //new Thread(mLoop).start();
            }
        }
    }
























/////////////////////////////////////////////////////////////////////

}

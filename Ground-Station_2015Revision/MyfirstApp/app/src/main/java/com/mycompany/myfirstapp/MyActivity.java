package com.mycompany.myfirstapp;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
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

public class MyActivity extends AppCompatActivity {

    //Setup Buttons for Interface
    private Button bDrop;
    private Button bComm;
    private Button bTarget;
    private Button bTimer;
    public TextView tvAlt;
    private TextView tvRelease;

    //Misc
    public static D2xxManager ftD2xx = null;
    byte[] USBBuffer;
    public int READ_BUFFER_SIZE = 56;
    private boolean timer = false;
    FT_Device ftDev;
    public Context global_context;
    private boolean payload = true;
    byte[] drop = {(byte) '0'};
    byte[] load = {(byte) '1'};
    final byte XON = 0x11;    /* Resume transmission */
    final byte XOFF = 0x13;    /* Pause transmission */
    public int port = 0;        //default usb port for radio
    boolean mThreadIsStopped = true;
    //hardcode the baudrate
    int baud = 9600;

    public MapsActivity map;
    public Telemetry tele;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        global_context = this;

        try {
            ftD2xx = D2xxManager.getInstance(this);
        }
        catch (D2xxManager.D2xxException e) {Log.e("FTDI_HT","getInstance fail!!");}
        USBBuffer = new byte[READ_BUFFER_SIZE];

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        map.setUpMapIfNeeded();

        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        map.mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        map.mMap.getUiSettings().setMyLocationButtonEnabled(true);

        /*
        //Set up buttons/textview
        bTimer =    (Button) findViewById(R.id.button_timer);
        bDrop =     (Button) findViewById(R.id.button_drop);
        bTarget =   (Button) findViewById(R.id.button_target);
        bComm =     (Button) findViewById(R.id.button_commsync);
        tvAlt =     (TextView) findViewById(R.id.tvHeight);
        tvRelease = (TextView) findViewById(R.id.tvRelease);
*/
        map.setUpMapClickListener();
        setUpButtonsListeners();



        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_my, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void setUpButtonsListeners() {
        //Set on click for each button
        bDrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(map.TAG, "payload toggle");
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
                Log.v(map.TAG, "bTarget");
                map.getTargetLocation();
            }
        });
        bComm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(map.TAG, "Communication Request");
                setUpUsbIfNeeded();
                openDevice();
            }
        });


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
                Log.e(map.TAG, "onClickWrite : Device is not open");
                return;
            }

            ftDev.setLatencyTimer((byte)16);

            //payload toggle command logic
            if (payload) {
                ftDev.write(drop, drop.length);
                payload = false;
                bDrop.setText("LOAD");

                //Record the drop height
                tvRelease.setText((int) (tele.planeAlt - tele.targetAlt) + "ft");
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
                    new Thread(tele.mLoop).start();

                }
                return;
            }
        }

        int devCount = 0;
        devCount = ftD2xx.createDeviceInfoList(this);

        Log.d(map.TAG, "Device number : "+ Integer.toString(devCount));

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
                new Thread(tele.mLoop).start();
            }
        }
    }




}


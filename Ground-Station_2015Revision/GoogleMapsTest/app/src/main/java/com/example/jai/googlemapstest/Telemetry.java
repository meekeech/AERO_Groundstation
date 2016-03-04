package com.example.jai.googlemapstest;

/**
 * Created by Jai on 2/21/2016.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

public class Telemetry extends FragmentActivity {

    FT_Device ftDev;
    public static D2xxManager ftD2xx = null;
    private boolean USBConnected = false;

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
    private final int BAUD = 57600;
    private final byte[] drop = {(byte) '1'};
    private final byte[] load = {(byte) '0'};
    private final byte[] request = {(byte) 'r'};

    // Initialize buffers for incoming transmission
    private byte[] rbuf  = new byte[READBUF_SIZE];
    private char[] rchar = new char[READBUF_SIZE];

    // Read variables
    protected double planeLong;
    protected double planeLat;
    protected double planeAlt;
    protected double planeSpeed;
    protected double planeHeading;
    protected double planeYaw;
    protected double planePitch;
    protected double planeRoll;

    Context global_context;
    protected String TAG = "TAG";
    protected IntentFilter filter;
    protected BroadcastReceiver mUsbReceiver;

    public Telemetry(Context context) {
        this.planeLong = 0;
        this.planeLat = 0;
        this.planeAlt = 0;
        this.planeSpeed = 0;
        this.planeHeading = 0;
        this.planeYaw = 0;
        this.planePitch = 0;
        this.planeRoll = 0;

        this.global_context = context;

        Log.v(TAG, global_context.toString());

       /* filter = new IntentFilter();

        mUsbReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    // never come here(when attached, go to onNewIntent)
                    //openDevice();
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    //closeDevice();
                }
            }
        };*/

        Log.v("TELE", "Telemetry established" );

    }

    protected void Connect() {
        // try statement to attempt to connect to USB
        // if successful then print 'connected'
        //ftD2xx = D2xxManager.getInstance(this);

       if (USBConnected) return;

        try {
            ftD2xx = D2xxManager.getInstance(global_context);

            if (ftD2xx == null) {
                Toast.makeText(global_context, "Instance NULL", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(global_context, "Connection established", Toast.LENGTH_SHORT).show();
        }
        catch (D2xxManager.D2xxException e) {
            Log.e("FTDI_HT","getInstance fail!!");
            Toast.makeText(global_context, "Connection failed", Toast.LENGTH_SHORT).show();
            return;
        }

        // Establish USB buffer
        USBBuffer = new byte[READ_BUFFER_SIZE];
        // Stuff for the USB socket
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //openDevice();
    };

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

    /*
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
            }*/

    protected int Update() {
        // send 'r' to usb
        // will receive telemetry

        return 0;
    }

    protected void Drop () {
        // will receive either a '0' or a '1' from the system
    }
}

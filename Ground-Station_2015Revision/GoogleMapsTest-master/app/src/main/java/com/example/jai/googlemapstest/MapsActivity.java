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
    protected boolean planeVisible = false;


    //floating button
    protected FloatingActionButton fab;
    boolean payload = false;

    Context global_context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Set current view
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

        setUpFloatingButton();
        setUpInternalStorage();
        setUpMapIfNeeded();
        updateMarkers();

        if(telemetry == null){
            telemetry = new Telemetry(this);
        }

        global_context = this;
    }

    public void setUpFloatingButton() {
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Snackbar.make(view, "Drop/Load Action", Snackbar.LENGTH_LONG)
                       // .setAction("Action", null).show();

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

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng point) {
                if (savingMode) {
                    targetPoint = point;
                    updateMarkers();
                }
            }
        });
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
                telemetry.setUpUsbIfNeeded();
                if(!planeVisible) {
                    planeVisible = false;
                } else {
                    planeVisible = true;
                }
                break;
            case R.id.artificialHorizon:
                Toast.makeText(this, "Artificial Horizon", Toast.LENGTH_SHORT).show();
                savingMode = false;
                Intent intent2 = new Intent(MapsActivity.this, ArtificialHorizonActivity.class);
                pointDb.close();
                startActivityForResult(intent2, 2);
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

        // Add marker to plane
        LatLng plane = new LatLng(plane_lat, plane_long);

        mMap.addMarker(new MarkerOptions()
                .position(plane)
                .title("Plane Location")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_plane))
                .anchor(0.5f, 0.5f)
                .rotation(plane_rotation)
                .visible(planeVisible));

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
///////////////////////////////////////////////////////
}

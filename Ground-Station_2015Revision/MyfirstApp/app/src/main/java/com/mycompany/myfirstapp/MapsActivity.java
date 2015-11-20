// This Activity initiates the map activity and shit

package com.mycompany.myfirstapp;

import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapsActivity extends Telemetry {
    public GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private double marker_lat = 0;
    private double marker_long = 0;
    private double marker_alt = 0;

    //Debugging string
    public String TAG = "TAG";

    public void setUpMapIfNeeded() {
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


    public void setUpMapClickListener() {
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
                marker_lat = latLng.latitude;
                marker_long = latLng.longitude;
                marker_alt = planeAlt;


            }
        });

        setUpMapIfNeeded();
    }

    public void getTargetLocation() {
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

}

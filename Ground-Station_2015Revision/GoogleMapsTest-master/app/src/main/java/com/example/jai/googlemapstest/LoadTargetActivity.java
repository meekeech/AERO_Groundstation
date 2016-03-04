package com.example.jai.googlemapstest;

import android.database.Cursor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.database.sqlite.SQLiteDatabase;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class LoadTargetActivity extends AppCompatActivity {

    private SQLiteDatabase pointDb;
    private ArrayList<Integer> ids = new ArrayList<Integer>() ;
    private ArrayList<Double> lat = new ArrayList<Double>();
    private ArrayList<Double> lon = new ArrayList<Double>();
    private ArrayList<String> names = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_target);

        getData();
        populateListView();
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    public void getData() {
        pointDb = this.openOrCreateDatabase("PointDatabase", MODE_PRIVATE, null);
        Cursor c = pointDb.rawQuery("SELECT * FROM points", null);
        c.moveToFirst();

        if(c.getCount() != 0) {
            do {
                int id = c.getInt(0);
                Double latitude = c.getDouble(1);
                Double longitude = c.getDouble(2);
                String name = c.getString(3);

                ids.add(id);
                lat.add(latitude);
                lon.add(longitude);
                names.add(name);
            } while (c.moveToNext());
        }

        pointDb.close();
    }

    public void populateListView() {
        //SimpleCursorAdapter myCursorAdapter = new SimpleCursorAdapter(getBaseContext(), , )
        ListView lv = (ListView)findViewById(R.id.listView);
        ArrayAdapter<String> myAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names);
        lv.setAdapter(myAdapter);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedItem = names.get(position);
                Double targetPoint[] = new Double[]{lat.get(position), lon.get(position)};
                getIntent().putExtra("1", targetPoint);
                Toast.makeText(getApplicationContext(), "Loading Target: " + selectedItem + " at " + targetPoint[0].toString() + " " + targetPoint[1].toString(), Toast.LENGTH_LONG).show();
                setResult(RESULT_OK, getIntent());
                finish();
            }
        });
    }
}

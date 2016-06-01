package org.noise_planet.noisecapture;


import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;


public class MapActivity extends MainActivity implements OnMapReadyCallback,
        GoogleMap.OnMapLoadedCallback {
    public static final String RESULTS_RECORD_ID = "RESULTS_RECORD_ID";
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        initDrawer();

        this.measurementManager = new MeasurementManager(getApplicationContext());
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(RESULTS_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(RESULTS_RECORD_ID, -1), false);
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords(false);
            if(!recordList.isEmpty()) {
                record = recordList.get(recordList.size() - 1);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(), getString(R.string.no_results),
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Fill the spinner_map
        Spinner spinner = (Spinner) findViewById(R.id.spinner_map);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.choice_user_map, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new MapDropDownChooseListener(this));

        // Display the map
        setUpMapIfNeeded();

    }

    @Override
    public void onMapReady(GoogleMap mMap) {
        // Initialize map options. For example:
        mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        mMap.setOnMapLoadedCallback(this);
        this.mMap = mMap;
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    public void loadWebView() {
        WebView leaflet = (WebView) findViewById(R.id.webmapview);
        WebSettings webSettings = leaflet.getSettings();
        webSettings.setJavaScriptEnabled(true);
        leaflet.clearCache(true);
        leaflet.setInitialScale(200);
        leaflet.loadUrl("http://webcarto.orbisgis.org/noisemap.html");

    }

    @Override
    public void onMapLoaded() {
        Resources res = getResources();
        mMap.clear();
        Spinner spinner = (Spinner) findViewById(R.id.spinner_map);
        boolean onlySelected = spinner.getSelectedItemPosition() == 0;
        // Add markers and move the camera.
        List<MeasurementManager.LeqBatch> measurements = new ArrayList<MeasurementManager.LeqBatch>();
        measurements = measurementManager.getRecordLocations(onlySelected ? record.getId() : -1, true);
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for(int idMarker = 0; idMarker < measurements.size(); idMarker++) {
            MeasurementManager.LeqBatch leq = measurements.get(idMarker);
            LatLng position = new LatLng(leq.getLeq().getLatitude(), leq.getLeq().getLongitude());
            MarkerOptions marker = new MarkerOptions();
            marker.position(position);
            double leqValue = leq.computeGlobalLeq();
            marker.title(res.getString(R.string.map_marker_label, leqValue,
                    leq.getLeq().getAccuracy()));
            int nc=getNEcatColors(leqValue);    // Choose the color category in function of the sound level
            float[] hsv = new float[3];
            Color.colorToHSV(NE_COLORS[nc], hsv);  // Apply color category for the corresponding sound level
            marker.icon(BitmapDescriptorFactory.defaultMarker(hsv[0]));
            mMap.addMarker(marker);
            builder.include(position);
        }
        if(!measurements.isEmpty()) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0));
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.no_gps_results),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setUpMapIfNeeded() {
        SupportMapFragment mapFragment = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map));
        mapFragment.getMapAsync(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    private static class MapDropDownChooseListener implements AdapterView.OnItemSelectedListener {
        private MapActivity mapActivity;

        public MapDropDownChooseListener(MapActivity mapActivity) {
            this.mapActivity = mapActivity;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            FragmentManager fragmentManager = mapActivity
                    .getSupportFragmentManager();
            SupportMapFragment mapFragment = ((SupportMapFragment)fragmentManager.findFragmentById(R.id.map));
            WebView webView = (WebView) mapActivity.findViewById(R.id.webmapview);
            if(position <= 1) {
                if(mapFragment != null && mapFragment.isHidden()) {
                    fragmentManager.beginTransaction()
                                   .show(mapFragment).commit();
                    webView.setVisibility(View.GONE);
                }
                mapActivity.onMapLoaded();
            } else {
                // TODO server side map
                if(mapFragment != null) {
                    fragmentManager.beginTransaction()
                                   .hide(mapFragment).commit();
                    webView.setVisibility(View.VISIBLE);
                    if(webView != null) {
                        mapActivity.loadWebView();
                    }
                }
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    }
}
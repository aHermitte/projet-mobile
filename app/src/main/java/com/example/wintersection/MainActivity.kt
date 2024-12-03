package com.example.wintersection

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.LocationServices
import org.osmdroid.views.MapView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.json.JSONObject

class MainActivity : AppCompatActivity(){
    private lateinit var map: MapView
    private var results = JSONObject()

    private val dataReadyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val json = intent?.getStringExtra("results")
            if (json != null) {
                println("Main activity received data: $json")
                results = JSONObject(json)
                displayEvents()
            } else {
                println("Main activity received broadcast without data : $json")
            }

        }
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action == "com.example.wintersection.LOCATION_UPDATE") {
                val latitude = intent.getDoubleExtra("latitude", 0.0)
                val longitude = intent.getDoubleExtra("longitude", 0.0)

                // Handle the received location data
                println("Received location update: Latitude=$latitude, Longitude=$longitude")

                // You can update the UI or perform other actions with the location
                // Example: Display the location on a map
                updateMapLocation(latitude, longitude)
            }
        }
    }

    private fun updateMapLocation(latitude: Double, longitude: Double) {
        // Assuming you have a map object (e.g., OpenStreetMap or Google Maps)
        val newLocation = GeoPoint(latitude, longitude)
        map.controller.setCenter(newLocation)
        map.controller.setZoom(15.0)

        // Optionally, add a marker
        val marker = Marker(map)
        marker.position = newLocation
        marker.title = "Current Location"
        map.overlays.clear()
        map.overlays.add(marker)
        displayEvents()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        // Initialize the map
        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)


        getRoadEvents()
        listActivityListener()
        if (checkLocationPermission()) {
            getCurrentLocation()
            startPositionListener()
        } else {
            requestLocationPermission()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun addMarker(latitude: Double, longitude: Double, title: String, isUserLocation: Boolean = false) {
        val marker = Marker(map)
        marker.position = GeoPoint(latitude, longitude)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title

        //TODO: Make different icons for user location and road events
        val drawable = if (isUserLocation) {
            resources.getDrawable(R.drawable.pin)
        } else {
            resources.getDrawable(R.drawable.pin)
        }

        val bitmap = (drawable as BitmapDrawable).bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 30, 30, false)

        val scaledDrawable = BitmapDrawable(resources, scaledBitmap)
        marker.icon = scaledDrawable

        map.overlays.add(marker)
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                map.controller.setZoom(15.0)
                map.controller.setCenter(geoPoint)

                // Add a marker at the current location
                val marker = Marker(map)
                marker.position = geoPoint
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "You are here"
                map.overlays.add(marker)
            }
        }.addOnFailureListener {
            // Handle failure
            it.printStackTrace()
        }
    }

    private fun getRoadEvents() {
        //Register receiver
        val intentFilter = IntentFilter("com.example.wintersection.DATA_READY")
        LocalBroadcastManager.getInstance(this).registerReceiver(dataReadyReceiver, intentFilter)

        val serviceIntent = Intent(this, RoadService::class.java)
        startService(serviceIntent)
    }

    private fun startPositionListener() {
        val intertFilter = IntentFilter("com.example.wintersection.LOCATION_UPDATE")
        val serviceIntent = Intent(this, LocationService::class.java)
        startService(serviceIntent)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter("com.example.wintersection.LOCATION_UPDATE")
        )
    }

    private fun displayEvents() {
        val results = results.getJSONArray("results")
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val latitude = result.getDouble("latitude")
            val longitude = result.getDouble("longitude")
            val libelle = result.getString("libelle")
            println("Latitude: $latitude, Longitude: $longitude, Libelle: $libelle")
            addMarker(latitude, longitude, libelle)
        }
    }

    //Listener for button click to next activity
    private fun listActivityListener(){
        val button = findViewById<Button>(R.id.buttonnext)
        button.setOnClickListener {
            startListActivity()
        }
    }

    private fun startListActivity() {
        val intent = Intent(this, ListActivity::class.java)
        intent.putExtra("results", results.toString())
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                getCurrentLocation()
            } else {
                // Permission denied, handle appropriately
            }
        }
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dataReadyReceiver)
    }
}

package com.example.wintersection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.tileprovider.tilesource.ThunderforestTileSource
import org.osmdroid.tileprovider.tilesource.ThunderforestTileSource.TRANSPORT
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var userPos: Marker
    private var userLat: Double = 0.0
    private var userLong: Double = 0.0

    private var results = JSONObject()
    private val circleRadiusInMeters = 500.0
    private val eventAlertTimestamps = mutableMapOf<String, Long>()
    private val alertCooldownMillis = TimeUnit.MINUTES.toMillis(5)

    private val dataReadyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val json = intent?.getStringExtra("results")
            if (json != null) {
                println("Main activity received data: $json")
                results = JSONObject(json)

                val dbManager = DatabaseManager(this@MainActivity)
                saveResultsToDatabase(dbManager)
                displayEvents()
            } else {
                println("Main activity received broadcast without data : $json")
            }
        }
    }

    private fun saveResultsToDatabase(dbManager: DatabaseManager) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                200
            )
        }
        val resultsArray = results.getJSONArray("results")
        for (i in 0 until resultsArray.length()) {
            val result = resultsArray.getJSONObject(i)
            val latitude = result.getDouble("latitude")
            val longitude = result.getDouble("longitude")
            val libelle = result.getString("libelle")

            // Save each intersection to the database
            dbManager.insertIntersection(latitude, longitude, libelle)
        }
        println("All fetched intersections have been stored in the database.")
    }

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action == "com.example.wintersection.LOCATION_UPDATE") {
                val latitude = intent.getDoubleExtra("latitude", 0.0)
                val longitude = intent.getDoubleExtra("longitude", 0.0)
                userLat = latitude
                userLong = longitude

                println("Received location update: Latitude=$latitude, Longitude=$longitude")

                updateMapLocation(latitude, longitude)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        // Initialize the map
        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)

        val tileSource = ThunderforestTileSource(this, TRANSPORT)
        map.setTileSource(tileSource)

        getRoadEvents()
        listActivityListener()
        if (checkLocationPermission()) {
            startPositionListener()
        } else {
            requestLocationPermission()
        }
    }

    private fun updateMapLocation(latitude: Double, longitude: Double) {
        val newLocation = GeoPoint(latitude, longitude)
        map.controller.setCenter(newLocation)

        if (this::userPos.isInitialized) {
            map.overlays.remove(userPos)
        }

        addCircleAroundUser(newLocation)
        userPos = addMarker(latitude, longitude, "Current Location", true)
        recheckEventProximity()
    }

    private fun displayEvents() {
        val results = results.getJSONArray("results")
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val latitude = result.getDouble("latitude")
            val longitude = result.getDouble("longitude")
            val libelle = result.getString("libelle")
            //println("Latitude: $latitude, Longitude: $longitude, Libelle: $libelle")
            addMarker(latitude, longitude, libelle)
            checkEventProximity(GeoPoint(latitude, longitude), libelle)
        }
    }


    private fun checkEventProximity(eventLocation: GeoPoint, eventTitle: String) {
        if (this::userPos.isInitialized) {
            val userLocation = userPos.position
            val distance = userLocation.distanceToAsDouble(eventLocation) // Distance in meters

            if (distance <= circleRadiusInMeters) {
                val currentTime = SystemClock.elapsedRealtime()
                val lastAlertTime = eventAlertTimestamps[eventTitle]
                if (lastAlertTime == null || currentTime - lastAlertTime >= alertCooldownMillis) {
                    showEventAlert(eventTitle, distance)
                    eventAlertTimestamps[eventTitle] = currentTime
                    println("Event '$eventTitle' is within $circleRadiusInMeters meters (${distance.toInt()} meters away).")
                }

            }
        }
    }
    private fun showEventAlert(eventTitle: String, distance: Double) {
        Toast.makeText(
            this,
            "Event '$eventTitle' is within $circleRadiusInMeters meters (${distance.toInt()} meters away).",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun addMarker(latitude: Double, longitude: Double, title: String, isUserLocation: Boolean = false): Marker {
        val marker = Marker(map)
        marker.position = GeoPoint(latitude, longitude)

        marker.title = title

        if (isUserLocation) {
            setMarkerIcon(marker, R.drawable.bluepin)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        } else {
            setMarkerIcon(marker, R.drawable.redpin)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }

        map.overlays.add(marker)
        return marker
    }

    private fun setMarkerIcon(marker: Marker, iconResId: Int) {
        val drawable = ContextCompat.getDrawable(this, iconResId)
        val bitmap = (drawable as BitmapDrawable).bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 30, 30, false)
        val scaledDrawable = BitmapDrawable(resources, scaledBitmap)
        marker.icon = scaledDrawable
    }

    private fun addCircleAroundUser(location: GeoPoint) {
        map.overlays.removeIf { it is Polygon && it.title == "User Location Circle" }

        // Create a new circle
        val circle =object: Polygon() {
            override fun onSingleTapConfirmed(pEvent: MotionEvent?, pMapView: MapView?): Boolean {
                return false
            }
            override fun onLongPress(pEvent: MotionEvent?, pMapView: MapView?): Boolean {
                return false
            }

        }
        circle.points = Polygon.pointsAsCircle(location, circleRadiusInMeters)
        circle.fillColor = 0x220000FF
        circle.strokeColor = 0xFF0000FF.toInt()
        circle.strokeWidth = 2.0f
        circle.title = "User Location Circle"

        map.overlays.add(circle)
    }

    private fun recheckEventProximity() {
        if(results.length() == 0) return
        val results = results.getJSONArray("results")
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val latitude = result.getDouble("latitude")
            val longitude = result.getDouble("longitude")
            val libelle = result.getString("libelle")
            checkEventProximity(GeoPoint(latitude, longitude), libelle)
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

    private fun getRoadEvents() {
        val dbManager = DatabaseManager(this)
        val lastFetchTimestamp = dbManager.getMetadata("last_fetch_time")

        val currentTimeMillis = System.currentTimeMillis()
        if (lastFetchTimestamp != null) {
            val lastFetchTime = lastFetchTimestamp.toLong()
            if (currentTimeMillis - lastFetchTime < TimeUnit.DAYS.toMillis(1)) {
                // Load data from the database
                loadIntersectionsFromDatabase(dbManager)
                return
            }
        }

        // Otherwise, fetch new data
        val intentFilter = IntentFilter("com.example.wintersection.DATA_READY")
        LocalBroadcastManager.getInstance(this).registerReceiver(dataReadyReceiver, intentFilter)

        val serviceIntent = Intent(this, RoadService::class.java)
        startService(serviceIntent)

        // Save the current fetch timestamp
        dbManager.saveMetadata("last_fetch_time", currentTimeMillis.toString())
    }

    private fun loadIntersectionsFromDatabase(dbManager: DatabaseManager) {
        println("Reading from Database")
        val intersections = dbManager.getAllIntersections()
        intersections.forEach {
            val latitude = it["latitude"] as Double
            val longitude = it["longitude"] as Double
            val libelle = it["libelle"] as String
            addMarker(latitude, longitude, libelle)
            checkEventProximity(GeoPoint(latitude, longitude), libelle)
        }
    }



    private fun startPositionListener() {
        val serviceIntent = Intent(this, LocationService::class.java)
        startService(serviceIntent)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            locationReceiver,
            IntentFilter("com.example.wintersection.LOCATION_UPDATE")
        )
    }

    private fun listActivityListener() {
        val button = findViewById<Button>(R.id.buttonnext)
        button.setOnClickListener {
            startListActivity()
        }
    }

    private fun startListActivity() {
        val intent = Intent(this, ListActivity::class.java)
        intent.putExtra("results", results.toString())
        intent.putExtra("userLat", userLat)
        intent.putExtra("userLong", userLong)
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
                startPositionListener()
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
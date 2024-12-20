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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONArray
import org.json.JSONException
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
    private val REQUEST_CODE_IMPORT_JSON = 1001

    private var results = JSONObject()
    private val circleRadiusInMeters = 500.0
    private val eventAlertTimestamps = mutableMapOf<String, Long>()
    private val alertCooldownMillis = TimeUnit.MINUTES.toMillis(5)

    private var isCameraUnlocked = false
    private var centerPt = GeoPoint(userLat, userLong)

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
        setupFloatingActionButton()
        centerCameraOnUserButton()

        if (checkLocationPermission()) {
            startPositionListener()
        } else {
            requestLocationPermission()
        }
    }

    override fun onNewIntent(intent: Intent?) {
       super.onNewIntent(intent)

       val lat = intent?.getDoubleExtra("selectedLatitude", 0.0)
        val lon = intent?.getDoubleExtra("selectedLongitude", 0.0)
        val lib = intent?.getStringExtra("selectedLibelle")

        println("Back to main activity, lat: $lat, lon: $lon, lib: $lib")

        // Check if it corresponds to an existing intersection warning

        if (lat != null && lon != null && lib != null) {
            focusOnIntersection(lat, lon, lib)
        }

    }

    private fun focusOnIntersection(lat: Double, lon: Double, lib: String) {
        val newLocation = GeoPoint(lat, lon)
        centerPt = newLocation
        isCameraUnlocked = true

        // Set the map center to the new location
        map.controller.setCenter(centerPt)

        // Find the marker closest to the center of the map and display
        val centerGeoPoint = map.boundingBox.center
        val closestMarker = findClosestMarker(centerGeoPoint)

        closestMarker?.showInfoWindow()
    }

    private fun findClosestMarker(center: GeoPoint): Marker? {
        var closestMarker: Marker? = null
        var minDistance = Double.MAX_VALUE

        // Loop through the map's overlays (which should contain the markers)
        for (overlay in map.overlays) {
            if (overlay is Marker) {
                val marker = overlay
                val distance = center.distanceToAsDouble(marker.position)

                if (distance < minDistance) {
                    minDistance = distance
                    closestMarker = marker
                }
            }
        }

        return closestMarker
    }


    private fun setupFloatingActionButton() {
        val importButton = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floatingActionButton2)
        importButton.setOnClickListener {
            // Open the file picker for JSON files
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            startActivityForResult(intent, REQUEST_CODE_IMPORT_JSON)
        }
    }

    private fun updateMapLocation(latitude: Double, longitude: Double) {
        val newLocation = GeoPoint(latitude, longitude)
        if (!isCameraUnlocked) {
            centerPt = newLocation
            map.controller.setCenter(centerPt)
        }

        if (this::userPos.isInitialized) {
            map.overlays.remove(userPos)
        }

        addCircleAroundUser(newLocation)
        userPos = addMarker(latitude, longitude, "Current Location", true)
        recheckEventProximity()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT_JSON && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    val jsonString = contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
                    if (jsonString != null) {
                        importJsonToDatabase(jsonString)
                    } else {
                        Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error reading JSON file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importJsonToDatabase(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("results")) {
                val resultsArray = jsonObject.getJSONArray("results")
                val dbManager = DatabaseManager(this)

                for (i in 0 until resultsArray.length()) {
                    val result = resultsArray.getJSONObject(i)
                    val latitude = result.getDouble("latitude")
                    val longitude = result.getDouble("longitude")
                    val libelle = result.getString("libelle")

                    // Insert into the database
                    dbManager.insertIntersection(latitude, longitude, libelle)
                }

                Toast.makeText(this, "JSON imported successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid JSON format: Missing 'results' key", Toast.LENGTH_SHORT).show()
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Toast.makeText(this, "Error parsing JSON: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                isCameraUnlocked = true
                centerPt = clickedMarker.position
                map.controller.setCenter(centerPt)
                clickedMarker.showInfoWindow()
                true  // Return true to indicate that the event is handled
            }
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
        val newResultsArray = JSONArray()
        for (intersection in intersections) {
            val resultEntry = JSONObject().apply {
                put("latitude", intersection["latitude"])
                put("longitude", intersection["longitude"])
                put("libelle", intersection["libelle"])
            }
            newResultsArray.put(resultEntry)
        }

        results = JSONObject().apply {
            put("results", newResultsArray)
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
        val button = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.buttonnext)
        button.setOnClickListener {
            startListActivity()
        }
    }

    private fun centerCameraOnUserButton() {
        val button = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.floatingActionButton1)
        button.setOnClickListener {
            isCameraUnlocked = false
            centerPt = userPos.position
            map.controller.setCenter(centerPt)
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
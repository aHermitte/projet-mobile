package com.example.wintersection

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.views.MapView
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity(){
    private lateinit var map: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("prefs", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        // Initialize the map
        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)

        // TODO: Replace default location with actual phone position
        val mapController = map.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(48.8566, 2.3522)
        mapController.setCenter(startPoint)

        // TODO: Replace with actual alerts on intersections
        val marker = Marker(map)
        marker.position = startPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = "Marker in Paris"
        map.overlays.add(marker)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
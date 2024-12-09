package com.example.wintersection

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ListActivity : AppCompatActivity() {
    private var incidents = JSONArray();
    private var userLat = 0.0;
    private var userLong = 0.0;
    private var incidentsList = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val obj = intent.getStringExtra("results")?.let { JSONObject(it) }
        if (obj != null) {
            incidents = obj.getJSONArray("results")
        }
        userLat = intent.getDoubleExtra("userLat", 0.0)
        userLong = intent.getDoubleExtra("userLong", 0.0)
        println("Received userLat: $userLat, userLong: $userLong")

        setContentView(R.layout.activity_list)
        createIncidentList()
    }

    private fun createIncidentList() {
        for (i in 0 until incidents.length()) {
            val incident = incidents.getJSONObject(i)
            val dist = getDistanceToIncident(incident)
            val desc = incident.getString("libelle")
            val obj = JSONObject()
            obj.put("distance", dist)
            obj.put("description", desc)
            incidentsList.add(obj)
        }

        displayIncidents()
    }

    private fun displayIncidents() {
        val listView = findViewById<ListView>(R.id.incidentListView)

        incidentsList.sortBy { it.getDouble("distance") }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1, // Predefined layout for list items
            incidentsList.map { " Incident à ${distToString(it.getDouble("distance"))} \n ${it.getString("description")}  " }
        )
        listView.adapter = adapter
    }

    private fun distToString(dist: Double): String {
        val approxDist = dist.toInt()
        if (approxDist < 1000) {
            return "$approxDist m"
        }
        return "${(dist / 1000).toInt()} km"
    }
    private fun getDistanceToIncident(incident: JSONObject) : Double {
        println("Incident: $incident")
        val iLat = incident.getDouble("latitude")
        val iLong = incident.getDouble("longitude")

        return distanceInMeters(userLat, userLong, iLat, iLong)
    }


    fun distanceInMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Radius of the Earth in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }
}
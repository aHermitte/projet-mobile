package com.example.wintersection

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import org.json.JSONArray
import org.json.JSONObject

class ListActivity : AppCompatActivity() {
    private var incidents = JSONArray();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val obj = intent.getStringExtra("results")?.let { JSONObject(it) }
        if (obj != null) {
            incidents = obj.getJSONArray("results")
        }

        setContentView(R.layout.activity_list)
        displayIncidents()
    }

    private fun displayIncidents() {
        val listView = findViewById<ListView>(R.id.incidentListView)

        val incidentIndices = mutableListOf<String>()
        for (i in 0 until incidents.length()) {
            incidentIndices.add("Incident #$i : ${incidents[i]}")
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1, // Predefined layout for list items
            incidentIndices
        )
        listView.adapter = adapter
    }
}
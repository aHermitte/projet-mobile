package com.example.wintersection

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class RoadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("Service ready, fetching data now")
        serviceScope.launch {
            try {
                val data = fetchData()
                withContext(Dispatchers.Main) {
                    println("Fetched data: $data")
                    broadcastData(processData(data))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    println("Failed to retrieve data")
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun fetchData() : String {
        //val api = "https://opendata.bordeaux-metropole.fr/api/explore/v2.1/catalog/datasets/ci_chantier/records?limit=20"
        val api = "https://opendata.bordeaux-metropole.fr/api/explore/v2.1/catalog/datasets/ci_chantier/records?select=*&where=localisation%20LIKE%20%22l%27intersection%22&limit=100"
        val req = Request.Builder().url(api).build()
        return try {
            client.newCall(req).execute().use {
                response -> response.body?.string() ?: "No data"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to retrieve data"
        }
    }


    private fun processData(jsonString: String) : JSONObject {
        val jsonObject = JSONObject(jsonString)
        val results = jsonObject.getJSONArray("results")
        val filteredResults = JSONArray()
        val resultsMaxCount = jsonObject.getInt("total_count")
        val resultsCount = results.length()

        println("There are $resultsCount results out of $resultsMaxCount")
        //TODO: Fetch again if resultsCount < resultsMaxCount

        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val geoPoint = result.getJSONObject("geo_point_2d")
            val libelle = result.optString("libelle", "No libelle")

            val latitude = geoPoint.optDouble("lat")
            val longitude = geoPoint.optDouble("lon")

            val res = JSONObject()
            res.put("latitude", latitude)
            res.put("longitude", longitude)
            res.put("libelle", libelle)

            filteredResults.put(res)

        }

        return JSONObject().put("results", filteredResults)
    }

    private fun broadcastData(data: JSONObject) {
        val intent = Intent("com.example.wintersection.DATA_READY")
        intent.putExtra("results", data.toString())
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        println("Broadcasted data : $data")
    }
}
package com.example.wintersection

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RoadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("Service ready, fetching data now")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = fetchData()
                withContext(Dispatchers.Main) {
                    println("Fetched data: $data")
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

    override fun onBind(intent: Intent?): IBinder? = null;

    private fun fetchData() : String {
        val api = "https://opendata.bordeaux-metropole.fr/api/explore/v2.1/catalog/datasets/ci_chantier/records"
        val req = Request.Builder().url(api).build();
        return try {
            client.newCall(req).execute().use {
                response -> response.body?.string() ?: "No data";
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to retrieve data"
        }
    }
}
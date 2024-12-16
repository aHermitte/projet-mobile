package com.example.wintersection

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class DatabaseManager(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    // Add an intersection
    fun insertIntersection(latitude: Double, longitude: Double, libelle: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_LATITUDE, latitude)
            put(DatabaseHelper.COLUMN_LONGITUDE, longitude)
            put(DatabaseHelper.COLUMN_LIBELLE, libelle)
        }
        db.insert(DatabaseHelper.TABLE_INTERSECTIONS, null, values)
        db.close()
    }

    // Get all intersections
    fun getAllIntersections(): List<Map<String, Any>> {
        val intersections = mutableListOf<Map<String, Any>>()
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(DatabaseHelper.TABLE_INTERSECTIONS, null, null, null, null, null, null)

        while (cursor.moveToNext()) {
            val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LATITUDE))
            val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LONGITUDE))
            val libelle = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LIBELLE))
            intersections.add(mapOf("latitude" to latitude, "longitude" to longitude, "libelle" to libelle))
        }
        cursor.close()
        db.close()
        return intersections
    }

    // Save metadata (key-value pair)
    fun saveMetadata(key: String, value: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COLUMN_KEY, key)
            put(DatabaseHelper.COLUMN_VALUE, value)
        }
        db.replace(DatabaseHelper.TABLE_METADATA, null, values)
        db.close()
    }

    // Get metadata value by key
    fun getMetadata(key: String): String? {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            DatabaseHelper.TABLE_METADATA,
            arrayOf(DatabaseHelper.COLUMN_VALUE),
            "${DatabaseHelper.COLUMN_KEY} = ?",
            arrayOf(key),
            null,
            null,
            null
        )

        var value: String? = null
        if (cursor.moveToFirst()) {
            value = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_VALUE))
        }
        cursor.close()
        db.close()
        return value
    }
}
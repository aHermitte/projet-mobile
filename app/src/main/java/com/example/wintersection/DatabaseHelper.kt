package com.example.wintersection

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // Create table for storing intersections
        db.execSQL(
            "CREATE TABLE $TABLE_INTERSECTIONS (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_LATITUDE REAL, " +
                    "$COLUMN_LONGITUDE REAL, " +
                    "$COLUMN_LIBELLE TEXT)"
        )

        // Create table for storing last fetch timestamp
        db.execSQL(
            "CREATE TABLE $TABLE_METADATA (" +
                    "$COLUMN_KEY TEXT PRIMARY KEY, " +
                    "$COLUMN_VALUE TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_INTERSECTIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_METADATA")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "wintersection.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_INTERSECTIONS = "intersections"
        const val COLUMN_ID = "id"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_LIBELLE = "libelle"

        const val TABLE_METADATA = "metadata"
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
    }
}
package com.example.happyplaces.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.example.happyplaces.model.HappyPlaceModel


class DatabaseHandler(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "HappyPlacesDatabase"
        private const val TABLE_HAPPY_PLACE = "HappyPlacesTable"

        private const val KEY_ID = "_id"
        private const val KEY_TITLE = "title"
        private const val KEY_IMAGE = "image"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_DATE = "date"
        private const val KEY_LOCATION = "location"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val CREATE_HAPPY_PLACE_TABLE = ("CREATE TABLE $TABLE_HAPPY_PLACE (" +
                "$KEY_ID INTEGER PRIMARY KEY," +
                "$KEY_TITLE TEXT," +
                "$KEY_IMAGE TEXT," +
                "$KEY_DESCRIPTION TEXT," +
                "$KEY_DATE TEXT," +
                "$KEY_LOCATION TEXT," +
                "$KEY_LATITUDE DOUBLE," +
                "$KEY_LONGITUDE DOUBLE)")

        db?.execSQL(CREATE_HAPPY_PLACE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db!!.execSQL("DROP TABLE IF EXISTS $TABLE_HAPPY_PLACE")
        onCreate(db)
    }

    fun addHappyPlace(hpm: HappyPlaceModel): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues()

        contentValues.put(KEY_TITLE, hpm.title)
        contentValues.put(KEY_IMAGE, hpm.image)
        contentValues.put(KEY_DESCRIPTION, hpm.description)
        contentValues.put(KEY_DATE, hpm.date)
        contentValues.put(KEY_LOCATION, hpm.location)
        contentValues.put(KEY_LATITUDE, hpm.latitude)
        contentValues.put(KEY_LONGITUDE, hpm.longitude)

        val result = db.insert(TABLE_HAPPY_PLACE, null, contentValues)
        db.close()

        return result
    }

    fun getHappyPlacesList(): ArrayList<HappyPlaceModel> {
        val happyPlacesList = ArrayList<HappyPlaceModel>()
        val selectQuery = "SELECT * FROM $TABLE_HAPPY_PLACE"
        val db = this.readableDatabase

        try {
            val cursor: Cursor = db.rawQuery(selectQuery, null)

            if (cursor.moveToFirst()) {
                do {
                    val hpm = HappyPlaceModel(
                        cursor.getInt(cursor.getColumnIndex(KEY_ID)),
                        cursor.getString(cursor.getColumnIndex(KEY_TITLE)),
                        cursor.getString(cursor.getColumnIndex(KEY_IMAGE)),
                        cursor.getString(cursor.getColumnIndex(KEY_DESCRIPTION)),
                        cursor.getString(cursor.getColumnIndex(KEY_DATE)),
                        cursor.getString(cursor.getColumnIndex(KEY_LOCATION)),
                        cursor.getDouble(cursor.getColumnIndex(KEY_LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndex(KEY_LONGITUDE)),
                    )

                    happyPlacesList.add(hpm)
                } while (cursor.moveToNext())
            }

            cursor.close()
        } catch (e: SQLiteException) {
            db.execSQL(selectQuery)
            return ArrayList()
        }

        return happyPlacesList
    }

    fun updateHappyPlace(hpm: HappyPlaceModel): Long {
        val db = this.writableDatabase
        val contentValues = ContentValues()
        val whereClause = "$KEY_ID = ${hpm.id}"

        contentValues.put(KEY_ID, hpm.id)
        contentValues.put(KEY_TITLE, hpm.title)
        contentValues.put(KEY_IMAGE, hpm.image)
        contentValues.put(KEY_DESCRIPTION, hpm.description)
        contentValues.put(KEY_DATE, hpm.date)
        contentValues.put(KEY_LOCATION, hpm.location)
        contentValues.put(KEY_LATITUDE, hpm.latitude)
        contentValues.put(KEY_LONGITUDE, hpm.longitude)

        val result = db.update(TABLE_HAPPY_PLACE, contentValues, whereClause, null).toLong()
        db.close()

        return result
    }

    fun removeHappyPlace(hpm: HappyPlaceModel): Int {
        val db = this.writableDatabase
        val whereClause = "$KEY_ID = ${hpm.id}"

        val result = db.delete(TABLE_HAPPY_PLACE, whereClause, null)
        db.close()

        return result
    }
}
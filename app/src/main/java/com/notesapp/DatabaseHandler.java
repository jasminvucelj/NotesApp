package com.notesapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DatabaseHandler extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "notes.db";
    private static final String TABLE_NAME = "note_table";
    private static final int VERSION = 1;

    private static final String COL_ID = "_id";
    private static final String COL_TEXT = "text";
    private static final String COL_LATITUDE = "latitude";
    private static final String COL_LONGITUDE = "longitude";

    DatabaseHandler(Context context) {
        super(context, TABLE_NAME, null, VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        String query =
                "CREATE TABLE " + TABLE_NAME + " (" +
                        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_TEXT + " TEXT, " +
                        COL_LATITUDE + " DOUBLE, " +
                        COL_LONGITUDE + " DOUBLE);";
        db.execSQL(query);
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP IF TABLE EXISTS " + TABLE_NAME);
        onCreate(db);
    }


    public void deleteAll() {
        SQLiteDatabase db = getWritableDatabase();
        String query =
                "DELETE FROM " + TABLE_NAME + " WHERE 1;";
        db.execSQL(query);
    }


    // add note to db
    boolean addNote(Note note) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COL_TEXT, note.getText());
        values.put(COL_LATITUDE, note.getLatitude());
        values.put(COL_LONGITUDE, note.getLongitude());

        long result = db.insert(TABLE_NAME, null, values);

        return result != -1;
    }


    // delete note from db
    void deleteNote(String text) {
        SQLiteDatabase db = getWritableDatabase();
        String query = "DELETE FROM " + TABLE_NAME + " WHERE " +
                COL_TEXT + "=\"" + text + "\";";
        db.execSQL(query);
    }


    String databaseToString() {
        StringBuilder sb = new StringBuilder();
        SQLiteDatabase db = getWritableDatabase();

        String query = "SELECT * FROM " + TABLE_NAME + " WHERE 1";

        Cursor c = db.rawQuery(query, null);
        c.moveToFirst();

        while (!c.isAfterLast()){
            sb.append(c.getString(c.getColumnIndex(COL_TEXT)));
            sb.append("\t\t\t");
            sb.append(c.getDouble(c.getColumnIndex(COL_LATITUDE)));
            sb.append("\t\t\t");
            sb.append(c.getDouble(c.getColumnIndex(COL_LONGITUDE)));
            sb.append(System.getProperty("line.separator"));

            c.moveToNext();
        }

        c.close();
        db.close();
        return sb.toString();
    }
}

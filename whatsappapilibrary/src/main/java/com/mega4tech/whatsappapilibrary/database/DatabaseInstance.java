package com.mega4tech.whatsappapilibrary.database;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.mega4tech.whatsappapilibrary.model.WPackageWhatsapp;

import java.io.File;

/**
 * Created by Marco on 03/08/2018.
 */


//https://stackoverflow.com/a/19996964/6163123
//Access to the database to be thread safe (use multiple threads)
    //DatabaseManager
public class DatabaseInstance {

    private int mOpenCounter;

    private static DatabaseInstance instance;
    private SQLiteDatabase mDatabase;
    private String pathDb = "";

    public DatabaseInstance(String pathDb){
        this.pathDb = pathDb;
    }

    /*
    public static synchronized void initializeInstance(String path) {
        if (instance == null) {
            instance = new DatabaseInstance();
            instance.path = path;
        }
    }

    public static synchronized DatabaseInstance getInstance() {
        if (instance == null) {
            throw new IllegalStateException(DatabaseInstance.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return instance;
    }
    */

    public synchronized SQLiteDatabase openDatabase() {
        mOpenCounter++;
        if(mOpenCounter == 1) {
            // Opening new database
            mDatabase = SQLiteDatabase.openOrCreateDatabase(new File(pathDb),null);
        }
        return mDatabase;
    }

    public synchronized void closeDatabase() {
        mOpenCounter--;
        if(mOpenCounter == 0) {
            // Closing database
            mDatabase.close();

        }
    }

}

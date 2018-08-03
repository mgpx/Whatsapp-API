package com.mega4tech.whatsappapilibrary.database;


import android.database.sqlite.SQLiteDatabase;
import com.mega4tech.whatsappapilibrary.model.WPackageWhatsapp;

import java.io.File;
import java.util.Dictionary;
import java.util.HashMap;

/**
 * Created by Marco on 03/08/2018.
 */

public class DatabaseManager {


    private static DatabaseManager instance = null;

    public static DatabaseInstance db_wa = null;
    public static DatabaseInstance db_msgstore = null;

    private static void inicialize(WPackageWhatsapp wPackageWhatsapp){
        db_wa = new DatabaseInstance(wPackageWhatsapp.getAppFolderPath()+"/databases/wa.db");
        db_msgstore = new DatabaseInstance(wPackageWhatsapp.getAppFolderPath()+"/databases/msgstore.db");
    }

    public static synchronized void initializeInstance(WPackageWhatsapp wPackageWhatsapp) {
        if (instance == null) {
            instance = new DatabaseManager();
            instance.inicialize(wPackageWhatsapp);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(DatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }
        return instance;
    }

}

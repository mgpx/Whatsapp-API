package com.mega4tech.whatsappapilibrary.model;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;

import java.io.File;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by Marco on 02/08/2018.
 */

public class WPackage {

    protected String namePackage;
    protected Context context;


    public WPackage(Context context, String namePackage){
        this.namePackage = namePackage;
        this.context = context;
    }

    public String getNamePackage() {
        return namePackage;
    }


    public Boolean isInstalled(){
        File file = new File("/data/data/"+namePackage+"/");
        return file.exists();
    }

    public String getAppFolderPath(){
        return getApplicationFolderPath(this.context, this.namePackage);
    }

    public String getPrefPath(){
        return getPrefPath(this.context, this.namePackage);
    }

    public void stopApplication(){
        Shell.SU.run("am force-stop " + this.namePackage);
    }

    public void startApplication(){
        PackageManager pm = context.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(this.namePackage);
        context.startActivity(intent);
    }

    public void restartApplication(){
        stopApplication();
        startApplication();
    }


    @SuppressLint("SdCardPath")
    private static String getApplicationFolderPath(Context context, String packageName) {
        return "/data/data/" + packageName;
        /*
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            return context.getApplicationInfo().dataDir.replaceAll(context.getPackageName(), packageName);
        } else {
            return "/data/data/" + packageName;
        }
        */
    }

    @SuppressLint("SdCardPath")
    private static String getPrefPath(Context context, String packageName) {
        if (new File("/data/data/" + packageName).exists()) {
            return "/data/data/" + packageName;
        }
        return context.getApplicationInfo().dataDir.replaceAll(context.getPackageName(), packageName);
    }





}


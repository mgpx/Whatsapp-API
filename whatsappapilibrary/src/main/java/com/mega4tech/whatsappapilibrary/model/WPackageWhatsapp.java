package com.mega4tech.whatsappapilibrary.model;

import android.content.Context;
import android.os.Environment;

/**
 * Created by Marco on 02/08/2018.
 */


public class WPackageWhatsapp extends  WPackage {

    private String imgFolder;
    private String vidFolder;
    private String audFolder;


    public WPackageWhatsapp(Context context, String namePackage, String imgFolder, String vidFolder, String audFolder) {
        super(context,namePackage);
        this.imgFolder = imgFolder;
        this.vidFolder = vidFolder;
        this.audFolder = audFolder;

    }

    public String getImgFolder() {
        return imgFolder;
    }

    public String getVidFolder() {
        return vidFolder;
    }

    public String getAudFolder() {
        return audFolder;
    }

    public String getNameWhatsappPrefs(){
        return  this.namePackage + "_preferences.xml";
    }

    public String getNameRegisterPhone(){
        return "registration.RegisterPhone.xml";
    }


    public static WPackageWhatsapp Default(Context context){
        return new WPackageWhatsapp(
                context,
                "com.whatsapp",
                Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/Media/WhatsApp Images/Sent",
                Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/Media/WhatsApp Video/Sent",
                Environment.getExternalStorageDirectory().getAbsolutePath() + "/WhatsApp/Media/WhatsApp Audio/Sent");
    }

}

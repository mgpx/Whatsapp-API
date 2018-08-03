package com.mega4tech.whatsappapilibrary;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.mega4tech.whatsappapilibrary.database.DatabaseManager;
import com.mega4tech.whatsappapilibrary.exception.WhatsappNotInstalledException;
import com.mega4tech.whatsappapilibrary.liseteners.*;
import com.mega4tech.whatsappapilibrary.model.*;
import com.whatsapp.MediaData;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.*;

import eu.chainfire.libsuperuser.Shell;

/**
 * Created by aboodba on 02/03/2017.
 */

public class WhatsappApi {

    public static class Builder{

        private WhatsappApi instance = new WhatsappApi();

        public WhatsappApi.Builder setContext(Context context){
            instance.context = context;
            return this;
        }
        public WhatsappApi.Builder isExternalSpace(Boolean isExternalSpace){
            instance.isExternalSpace = isExternalSpace;
            return this;
        }

        public WhatsappApi.Builder setPackageWhatsapp(WPackageWhatsapp packageWhatsapp){
            instance.wPackageWhatsapp = packageWhatsapp;
            return this;
        }

        public WhatsappApi Build(){
            instance.Inicialize(false);
            return instance;
        }



    }
    private boolean isRootAvailable;

    //private SQLiteDatabase dbMessage;

    private WPackage wPackageThis = null;
    private WPackageWhatsapp wPackageWhatsapp = null;

    private Context context;
    private boolean isExternalSpace = false;


    public WhatsappApi() {

    }

    public void Inicialize(Boolean loadDb){
        wPackageThis = new WPackage(context, context.getPackageName());
        //whatsappPath = getApplicationFolderPath(context, "com.whatsapp");
        //whatsappPrefPath = getPrefPath(context, "com.whatsapp");
        //appPath = getApplicationFolderPath(context, context.getPackageName());
        //isWhatsappInstalled = (new File(whatsappPath)).exists();
        isRootAvailable = Shell.SU.available();

        String whatsappPath = wPackageWhatsapp.getAppFolderPath();
        String appPath = wPackageThis.getAppFolderPath();
        if (isRootAvailable) {
//            Shell.SU.run("am force-stop com.whatsapp");
            Shell.SU.run("mount -o -R rw,remount " + whatsappPath);
            Shell.SU.run("mount -o rw,remount " + whatsappPath + "/databases");
            Shell.SU.run("chmod 777 " + whatsappPath + "/databases");
            Shell.SU.run("chmod 777 " + whatsappPath + "/files");
            Shell.SU.run("chmod 777 " + whatsappPath + "/shared_prefs");
            Shell.SU.run("chmod 777 " + appPath + "/databases");
            Shell.SU.run("chmod 777 " + appPath + "/files");
            Shell.SU.run("chmod 777 " + appPath + "/shared_prefs");
            Shell.SU.run("chmod 777 " + whatsappPath + "/databases/msgstore.db");
            Shell.SU.run("chmod 777 " + whatsappPath + "/databases/msgstore.db-wal");
            Shell.SU.run("chmod 777 " + whatsappPath + "/databases/msgstore.db-shm");
            Shell.SU.run("chmod 777 " + whatsappPath + "/databases/wa.db");
            Shell.SU.run("chmod 777 " + whatsappPath + "/databases/wa.db-wal");
            Shell.SU.run("chmod 777 " + whatsappPath + "/databases/wa.db-shm");
            Shell.SU.run("chmod 777 " + whatsappPath); //this line solves the problem in some cases where the sql library can not open the
            Shell.SU.run("ls -l " + whatsappPath + "/databases/msgstore.db-shm");

            DatabaseManager.initializeInstance(this.wPackageWhatsapp);

        }
        /*
        if (isRootAvailable && loadDb) {
            initiate();
        }
        */
    }



    public boolean isWhatsappInstalled() {
        return wPackageWhatsapp.isInstalled();
    }


    @SuppressLint("StaticFieldLeak")
    public synchronized void readMessages(final int limit, final long lastSyncId, final boolean readMedia, final GetMessageListener getMessageListener) {
        new AsyncTask<Void, Void, List<WStoredMessage>>() {

            Exception ex;

            @Override
            protected List<WStoredMessage> doInBackground(Void... params) {
                try {
                    return readMessages(limit, lastSyncId, readMedia);
                } catch (Exception e) {
                    e.printStackTrace();
                    ex = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<WStoredMessage> data) {
                super.onPostExecute(data);
                if (getMessageListener != null) {
                    if (data != null) getMessageListener.receiveWhatsappMessages(data);
                    if (ex != null) getMessageListener.onError(ex);
                }
            }

        }.execute();

    }

    /**
     * Read Messages limit no of message after particular id
     *
     * @param limit
     * @param lastSyncId
     * @param readMedia  - whether you want to read media also
     * @return
     * @throws IOException
     * @throws WhatsappNotInstalledException
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized List<WStoredMessage> readMessages(int limit, long lastSyncId, boolean readMedia) throws IOException, WhatsappNotInstalledException {

        initialCheck();

        SQLiteDatabase dbMessage = DatabaseManager.db_msgstore.openDatabase();

        List<WStoredMessage> wStoredMessages = new ArrayList<>();
        if (dbMessage != null) {
            List<WMediaFile> mediaFiles = new ArrayList<>();
            if (readMedia) {
                mediaFiles = readMediaFiles();
            }
            String selectQuery = "SELECT * FROM messages where _id > ? ORDER BY _id LIMIT " + String.valueOf(limit);
            Cursor cursor = dbMessage.rawQuery(selectQuery, new String[]{String.valueOf(lastSyncId)});
            if (cursor != null) {
                cursor.moveToFirst();
                do {
                    try {
                        WStoredMessage message;
                        if (readMedia) {
                            message = new WStoredMessage(cursor, mediaFiles);
                        } else {
                            message = new WStoredMessage(cursor);
                        }
                        wStoredMessages.add(message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } while (cursor.moveToNext());
                cursor.close();
            }
        }

        DatabaseManager.db_msgstore.closeDatabase();
        return wStoredMessages;
    }

    @SuppressLint("StaticFieldLeak")
    public synchronized void readTableRowCount(final String tableName, final GetTableCountListener tableCountListener) {
        new AsyncTask<Void, Void, Integer>() {

            Exception ex;

            @Override
            protected Integer doInBackground(Void... params) {
                try {
                    return readTableRowCount(tableName);
                } catch (Exception e) {
                    e.printStackTrace();
                    ex = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Integer data) {
                super.onPostExecute(data);
                if (tableCountListener != null) {
                    if (data != null) tableCountListener.onTableCount(data);
                    if (ex != null) tableCountListener.onError(ex);
                }
            }

        }.execute();
    }

    @SuppressLint("StaticFieldLeak")
    public synchronized int readTableRowCount(String tableName) throws IOException, WhatsappNotInstalledException {

        initialCheck();

        SQLiteDatabase dbMessage = DatabaseManager.db_msgstore.openDatabase();

        int messageCount = 0;
        if (dbMessage != null && dbMessage.isOpen()) {
            String selectQuery = "SELECT count(*) as count FROM " + tableName;
            Cursor cursor = dbMessage.rawQuery(selectQuery, null);
            if (cursor != null) {
                cursor.moveToFirst();
                messageCount = cursor.getInt(cursor.getColumnIndexOrThrow("count"));
                cursor.close();
            }
        }

        DatabaseManager.db_msgstore.closeDatabase();
        return messageCount;
    }

    public String listOfTables() {

        SQLiteDatabase dbMessage = DatabaseManager.db_msgstore.openDatabase();

        List<String> list = new ArrayList<>();
        if (dbMessage != null && dbMessage.isOpen()) {
            String selectQuery = "SELECT name FROM sqlite_master WHERE type='table'";
            Cursor cursor = dbMessage.rawQuery(selectQuery, null);
            if (cursor != null) {
                cursor.moveToFirst();
                do {
                    try {
                        list.add(cursor.getString(cursor.getColumnIndex("name")));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } while (cursor.moveToNext());
                cursor.close();
            }
        }

        DatabaseManager.db_msgstore.closeDatabase();

        return Utils.join(",", list);
    }

    /**
     * Read MessageCount after particular Id
     *
     * @param lastSyncId
     * @return
     * @throws IOException
     * @throws WhatsappNotInstalledException
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized void readSyncMessageCount(final long lastSyncId, final GetTableCountListener tableCountListener) {
        new AsyncTask<Void, Void, Integer>() {

            Exception ex;

            @Override
            protected Integer doInBackground(Void... params) {
                try {
                    return readSyncMessageCount(lastSyncId);
                } catch (Exception e) {
                    e.printStackTrace();
                    ex = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Integer data) {
                super.onPostExecute(data);
                if (tableCountListener != null) {
                    if (data != null) tableCountListener.onTableCount(data);
                    if (ex != null) tableCountListener.onError(ex);
                }
            }

        }.execute();
    }

    /**
     * Read MessageCount after particular Id
     *
     * @param lastSyncId
     * @return
     * @throws IOException
     * @throws WhatsappNotInstalledException
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized int readSyncMessageCount(long lastSyncId) throws IOException, WhatsappNotInstalledException {

        initialCheck();

        SQLiteDatabase dbMessage = DatabaseManager.db_msgstore.openDatabase();

        int messageCount = 0;
        if (dbMessage != null && dbMessage.isOpen()) {
            String selectQuery = "SELECT count(*) as count FROM messages where _id <= ?";
            Cursor cursor = dbMessage.rawQuery(selectQuery, new String[]{String.valueOf(lastSyncId)});
            if (cursor != null) {
                cursor.moveToFirst();
                messageCount = cursor.getInt(cursor.getColumnIndexOrThrow("count"));
                cursor.close();
            }
        }

        DatabaseManager.db_msgstore.closeDatabase();
        return messageCount;
    }

    /**
     * Get Chats Asynchronous
     *
     * @param chatListener
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized void readChats(final GetChatListener chatListener) {

        new AsyncTask<Void, Void, List<WChat>>() {

            Exception ex;

            @Override
            protected List<WChat> doInBackground(Void... params) {
                try {
                    return readChats();
                } catch (Exception e) {
                    e.printStackTrace();
                    ex = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<WChat> data) {
                super.onPostExecute(data);
                if (chatListener != null) {
                    if (data != null) chatListener.onChats(data);
                    if (ex != null) chatListener.onError(ex);
                }
            }

        }.execute();

    }


    /**
     * Read chat list
     *
     * @return
     * @throws IOException
     * @throws WhatsappNotInstalledException
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized List<WChat> readChats() throws IOException, WhatsappNotInstalledException {

        initialCheck();
        SQLiteDatabase dbMessage = DatabaseManager.db_msgstore.openDatabase();

        List<WChat> wStoredMessages = new ArrayList<>();
        if (dbMessage != null && dbMessage.isOpen()) {
            String selectQuery = "SELECT * FROM chat_list";
            Cursor cursor = dbMessage.rawQuery(selectQuery, null);
            if (cursor != null) {
                cursor.moveToFirst();
                do {
                    wStoredMessages.add(new WChat(cursor));
                } while (cursor.moveToNext());
                cursor.close();
            }
        }

        DatabaseManager.db_msgstore.closeDatabase();

        return wStoredMessages;

    }

    /**
     * Read group participants
     *
     * @return
     * @throws IOException
     * @throws WhatsappNotInstalledException
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized List<WGroupParticipant> readGroupParticipants() throws IOException, WhatsappNotInstalledException {

        initialCheck();

        SQLiteDatabase dbMessage = DatabaseManager.db_msgstore.openDatabase();

        List<WGroupParticipant> wStoredMessages = new ArrayList<>();
        if (dbMessage != null && dbMessage.isOpen()) {
            String selectQuery = "SELECT * FROM group_participants";
            Cursor cursor = dbMessage.rawQuery(selectQuery, null);
            if (cursor != null) {
                cursor.moveToFirst();
                do {
                    wStoredMessages.add(new WGroupParticipant(cursor));
                } while (cursor.moveToNext());
                cursor.close();
            }
        }

        DatabaseManager.db_msgstore.closeDatabase();

        return wStoredMessages;

    }

    /**
     * Read preferences Asynchronous
     * @return
     * @throws IOException
     * @throws WhatsappNotInstalledException
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized void readPreferences(final GetPreferenceListener preferenceListener)  {
        new AsyncTask<Void, Void, WPreference>() {

            Exception ex;

            @Override
            protected WPreference doInBackground(Void... params) {
                try {
                    return readPreferences();
                } catch (Exception e) {
                    e.printStackTrace();
                    ex = e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(WPreference data) {
                super.onPostExecute(data);
                if (preferenceListener != null) {
                    if (data != null) preferenceListener.onPreference(data);
                    if (ex != null) preferenceListener.onError(ex);
                }
            }

        }.execute();
    }

    /**
     * Read preferences to get account number etc details
     * @return WPreference
     * @throws IOException
     * @throws WhatsappNotInstalledException
     */
    @SuppressLint("StaticFieldLeak")
    public synchronized WPreference readPreferences() throws IOException, WhatsappNotInstalledException {

        initialCheck();

        String messageDBPath = makeSharedPreferencesAccessible();
        String message1DBPath = makeRegistrationPreferencesAccessible();
        File file = new File(messageDBPath);
        File file1 = new File(message1DBPath);
        WPreference wPreference = null;
        if (file.exists()) {
            try {
                wPreference = new WPreference(new PreferenceXmlParser(Utils.readFileContent(file)).parse());
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (file1.exists()) {
            try {
                HashMap<String, Object> d = new PreferenceXmlParser(Utils.readFileContent(file1)).parse();
                if (wPreference == null) {
                    wPreference = new WPreference(d);
                } else {
                    wPreference.updateData(d);
                }
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return wPreference;
    }

    @SuppressLint("StaticFieldLeak")
    public synchronized void readPublicKey(final long keyId, final WhatsappDataListener listener) throws IOException, WhatsappNotInstalledException {

        initialCheck();

        new AsyncTask<Void, Void, WIdentity>() {
            @Override
            protected WIdentity doInBackground(Void... params) {
                WIdentity message = null;
                String messageDBPath = makeAxolotlAccessible();
                File file = new File(messageDBPath);
                if (file.exists()) {
                    SQLiteDatabase db = SQLiteDatabase.openDatabase(messageDBPath, null, 0);
                    String selectQuery = "SELECT * FROM identities where recipient_id = ? limit 1";
                    Cursor cursor = db.rawQuery(selectQuery, new String[]{String.valueOf(keyId)});
                    if (cursor.moveToFirst()) {
                        message = new WIdentity(cursor);
                    }
                    cursor.close();
                    db.close();
                }
                return message;
            }

            @Override
            protected void onPostExecute(WIdentity finish) {
                super.onPostExecute(finish);
                if (listener != null) {
                    listener.onIdentityData(finish);
                }
            }
        }.execute();


    }

    public String getMsgStorePath(boolean isPublic) {
        if (isWhatsappInstalled() && isRootAvailable) {
            return makeDbAccessible("databases", "msgstore.db", isPublic);
        }
        return null;
    }

    /**
     * Clean messages before last 7 days
     *
     * @throws Exception
     */
    public void cleanUpDb() throws Exception {
        long effTs = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
        this.cleanUpDb(effTs);
    }

    /**
     * Clean messages before effTs timestamp
     *
     * @throws Exception
     */
    public void cleanUpDb(long effTs) throws Exception {
       // initiate();

        SQLiteDatabase dbMessage = DatabaseManager.db_msgstore.openDatabase();

        if (dbMessage != null && dbMessage.isOpen()) {
            dbMessage.delete("messages", "timestamp < ? ", new String[]{String.valueOf(effTs)});
            dbMessage.delete("message_thumbnails", "timestamp < ? ", new String[]{String.valueOf(effTs)});
            //closeConnection();
            copyBackDbFile();
        }

        DatabaseManager.db_msgstore.closeDatabase();
    }

    private String makeDbAccessible(String basePath, String dbName, boolean isPublic) {
        String s1 = isPublic ? String.format("%1$s", context.getExternalFilesDir(context.getPackageName()).getAbsolutePath()) : wPackageThis.getAppFolderPath();
        String s2 = String.format("%1$s/%2$s", s1, "databases");

        if (!new File(s1).exists()) {
            Shell.SU.run("mkdir " + s1);
        }
        if (!new File(s2).exists()) {
            Shell.SU.run("mkdir " + s2);
        }
        String messageDBPath = String.format("%1$s/%2$s/%3$s", s1, basePath, dbName);
        String messageDB1Path = String.format(wPackageWhatsapp.getAppFolderPath() + "/%1$s/%2$s", basePath, dbName);
        Shell.SU.run("rm " + messageDBPath + "-wal");
        Shell.SU.run("rm " + messageDBPath + "-shm");
        Shell.SU.run("rm " + messageDBPath + "-journal");
        Shell.SU.run("cp " + messageDB1Path + " " + messageDBPath);
        Shell.SU.run("cp " + messageDB1Path + "-wal " + messageDBPath + "-wal");
        Shell.SU.run("cp " + messageDB1Path + "-shm " + messageDBPath + "-shm");
        Shell.SU.run("chmod 777 " + messageDBPath);
        Shell.SU.run("chmod 777 " + messageDBPath + "-wal");
        Shell.SU.run("chmod 777 " + messageDBPath + "-shm");
        return messageDBPath;
    }

    public static void makeFilePathPublic(String path) {
        Shell.SU.run("chmod 777 " + path);
    }

    public void makeFileAccessible(String path) {
        Shell.SU.run("chmod 777 " + path);
    }

    private void copyBackDbFile() {
        Shell.SU.run("am force-stop com.whatsapp");
        String messageDBPath = wPackageThis.getAppFolderPath() + "/databases/msgstore.db";
        Shell.SU.run("cp " + messageDBPath + " " + wPackageWhatsapp.getAppFolderPath() + "/databases/msgstore.db");
        Shell.SU.run("chmod 777 " + wPackageWhatsapp.getAppFolderPath() + "/databases/msgstore.db");
        Shell.SU.run("am start -n com.whatsapp/.Main");
    }

    private String makeSharedPreferencesAccessible() {
        return makeKeyFileAccessible(wPackageWhatsapp.getNameWhatsappPrefs(), "shared_prefs", wPackageWhatsapp.getNameWhatsappPrefs());
    }

    private String makeRegistrationPreferencesAccessible() {
        return makeKeyFileAccessible(wPackageWhatsapp.getNameWhatsappPrefs(), "shared_prefs", wPackageWhatsapp.getNameRegisterPhone());
    }

    private String makeAxolotlAccessible() {
        return makeKeyFileAccessible(wPackageWhatsapp.getAppFolderPath(), "databases", "axolotl.db");
    }

    private String makeKeyFileAccessible(String basePath, String path, String fileName) {
        String messageDBPath = String.format(wPackageThis.getAppFolderPath() + "/%1$s/%2$s", path, fileName);
        String message1DBPath = String.format(basePath + "/%1$s/%2$s", path, fileName);
        String DBPath = String.format(wPackageThis.getAppFolderPath() + "/%1$s", path);
        if (!new File(DBPath).exists()) {
            Shell.SU.run("mkdir " + DBPath);
        }
        Shell.SU.run("cp " + message1DBPath + " " + messageDBPath);
        Shell.SU.run("chmod 777 " + messageDBPath);
        return messageDBPath;
    }

    private void sendMessage(WContact contact, WMessage message) throws IOException {

        String name = null;
        Calendar c = null;
        String formattedDate = null;
        SimpleDateFormat df = null;
        File source = null;
        Random rand = null;
        File destination = null;

        switch (message.getType()) {
            case TEXT:
                break;
            case VIDEO:
                name = message.getFile().getPath();
                c = Calendar.getInstance();
                df = new SimpleDateFormat("yyyyMMMdd", Locale.getDefault());
                formattedDate = df.format(c.getTime());
                source = new File(name);
                rand = new Random();
                destination = new File(wPackageWhatsapp.getVidFolder(), "VID-" + formattedDate + "-WA" + (rand.nextInt(100) + rand.nextInt(75) + rand.nextInt(50)) + "." + FilenameUtils.getExtension(message.getFile().getName()));
                if (source.exists()) {
                    FileChannel src = new FileInputStream(source).getChannel();
                    FileChannel dst = new FileOutputStream(destination).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();
                }
                name = destination.getName();
                break;
            case IMAGE:
                name = message.getFile().getPath();
                c = Calendar.getInstance();
                df = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                formattedDate = df.format(c.getTime());
                source = new File(name);
                rand = new Random();
                destination = new File(wPackageWhatsapp.getImgFolder(), "IMG-" + formattedDate + "-WA" + (rand.nextInt(100) + rand.nextInt(75) + rand.nextInt(50)) + "." + FilenameUtils.getExtension(message.getFile().getName()));
                if (source.exists()) {
                    FileChannel src = new FileInputStream(source).getChannel();
                    FileChannel dst = new FileOutputStream(destination).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();
                }
                name = destination.getName();
                break;
            case AUDIO:
                name = message.getFile().getPath();
                c = Calendar.getInstance();
                df = new SimpleDateFormat("yyyyMMdd",Locale.getDefault());
                formattedDate = df.format(c.getTime());
                source = new File(name);
                rand = new Random();
                destination = new File(wPackageWhatsapp.getAudFolder(), "AUD-" + formattedDate + "-WA" + (rand.nextInt(100) + rand.nextInt(75) + rand.nextInt(50)) + "." + FilenameUtils.getExtension(message.getFile().getName()));
                if (source.exists()) {
                    FileChannel src = new FileInputStream(source).getChannel();
                    FileChannel dst = new FileOutputStream(destination).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();
                }
                name = destination.getName();
                break;
        }
        sendBigMessage(contact.getId(), message.getText(), name, message.getMime());
    }



    private void sendBigMessage(String jid, String msg, String file, String mimeType) {

        long l1;
        long l2;
        int k;
        String query2, query1;

        Random localRandom = new Random(20L);
        l1 = System.currentTimeMillis();
        l2 = l1 / 1000L;
        k = localRandom.nextInt();

        int mediaType = 0;

        if (mimeType == null || mimeType.length() < 2)
            mediaType = 0;
        else
            mediaType = (mimeType.contains("video")) ? 3
                    : (mimeType.contains("image")) ? 1
                    : (mimeType.contains("audio")) ? 2
                    : 0;

        ContentValues initialValues = new ContentValues();
        initialValues.put("key_remote_jid", jid);
        initialValues.put("key_from_me", 1);
        initialValues.put("key_id", l2 + "-" + k);
        initialValues.put("status", 1);
        initialValues.put("needs_push", 0);
        initialValues.put("timestamp", l1);
        initialValues.put("media_wa_type", mediaType);
        initialValues.put("media_name", file);
        initialValues.put("latitude", 0.0);
        initialValues.put("longitude", 0.0);
        initialValues.put("received_timestamp", l1);
        initialValues.put("send_timestamp", -1);
        initialValues.put("receipt_server_timestamp", -1);
        initialValues.put("receipt_device_timestamp", -1);
        initialValues.put("raw_data", -1);
        initialValues.put("recipient_count", 0);
        initialValues.put("media_duration", 0);

        if (!TextUtils.isEmpty(file) && !TextUtils.isEmpty(mimeType)) {
            //boolean isVideo = mimeType.contains("video");
            Bitmap bMap = null;
            File spec;
            if (mediaType == 3) {
                spec = new File(wPackageWhatsapp.getVidFolder(), file);
                bMap = ThumbnailUtils.createVideoThumbnail(spec.getAbsolutePath(), MediaStore.Video.Thumbnails.MICRO_KIND);
            } else if (mediaType == 2) {
                spec = new File(wPackageWhatsapp.getAudFolder(), file);
            } else {
                spec = new File(wPackageWhatsapp.getImgFolder(), file);
                bMap = BitmapFactory.decodeFile(spec.getAbsolutePath());
            }
            long mediaSize = (file.equals("")) ? 0 : spec.length();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (mediaType == 1 || mediaType == 3) {
                bMap = Bitmap.createScaledBitmap(bMap, 100, 59, false);
                bMap.compress(Bitmap.CompressFormat.JPEG, 60, bos);
            }
            byte[] bArray = bos.toByteArray();

            MediaData md = new MediaData();
            md.fileSize = mediaSize;
            md.file = spec;
            md.autodownloadRetryEnabled = true;
            byte[] arr = SerializationUtils.serialize(md);

            initialValues.put("thumb_image", arr);
            initialValues.put("quoted_row_id", 0);
            //initialValues.put("media_mime_type", mimeType);
            //initialValues.put("media_hash", "9vZ3oZyplgiZ40jJvo/sLNrk3c1fuLOA+hLEhEjL+rg=");
            initialValues.put("raw_data", bArray);
            initialValues.put("media_size", mediaSize);
            initialValues.put("origin", 0);
            initialValues.put("media_caption", msg);
        } else
            initialValues.put("data", msg);

        //long idm = dbMessage.insert("messages", null, initialValues);
        SQLiteDatabase db = DatabaseManager.db_msgstore.openDatabase();
        long idm = db.insert("messages", null, initialValues);

        query1 = " insert into chat_list (key_remote_jid) select '" + jid
                + "' where not exists (select 1 from chat_list where key_remote_jid='" + jid + "');";

        query2 = " update chat_list set message_table_id = (select max(messages._id) from messages) where chat_list.key_remote_jid='" + jid + "';";


        ContentValues values = new ContentValues();
        values.put("docid", idm);
        values.put("c0content", "null  ");
        //dbMessage.insert("messages_fts_content", null, values);
        db.insert("messages_fts_content", null, values);
        //dbMessage.execSQL(query1);
        //dbMessage.execSQL(query2);
        db.execSQL(query1);
        db.execSQL(query2);

        //dont use db.close();
        DatabaseManager.db_msgstore.closeDatabase();

        //TODO: need to copyback to whatsapp folder with copyBackupFolder method - not adding it without testing.
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public List<WMediaFile> readMediaFiles() {
        List<WMediaFile> wMediaFiles = new ArrayList<>();
        String[] folders = new String[]{"WhatsApp Images"};
        for (String folderName : folders) {
            String path = "/sdcard/WhatsApp/Media/" + folderName + "/";
            File folder = new File(path);
            if (folder.exists()) {
                File[] listOfFiles = folder.listFiles();
                if (listOfFiles != null) {
                    for (File file : listOfFiles) {
                        if (file.exists() && file.isFile()) {
                            wMediaFiles.add(new WMediaFile(file));
                        }
                    }
                }
            }
        }
        return wMediaFiles;
    }

    public boolean isRootAvailable() {
        return isRootAvailable;
    }


    @SuppressLint({"StaticFieldLeak", "SdCardPath"})
    public synchronized void getContacts(Context context, final GetContactsListener listener) throws WhatsappNotInstalledException {

        if (isWhatsappInstalled()) {

            new AsyncTask<Void, Void, List<WContact>>() {
                @Override
                protected List<WContact> doInBackground(Void... params) {
                    //Shell.SU.run("am force-stop " + wPackage.getName());
                    SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(new File(wPackageWhatsapp.getAppFolderPath()+"/databases/wa.db"), null);
                    List<WContact> contactList = new LinkedList<>();
                    String selectQuery = "SELECT  jid, display_name FROM wa_contacts where phone_type is not null and is_whatsapp_user = 1";
                    //and (display_name LIKE '%[400]%' or display_name LIKE '%[405]%' or display_name LIKE '%[406]%' or display_name LIKE '%[407]%' or display_name LIKE '%[408]%')
                    Cursor cursor = db.rawQuery(selectQuery, null);
                    if (cursor.moveToFirst()) {
                        do {
                            WContact contact = new WContact(cursor.getString(1), cursor.getString(0));
                            contactList.add(contact);
                        } while (cursor.moveToNext());
                    }
                    cursor.close();
                    db.close();
                    return contactList;
                }

                @Override
                protected void onPostExecute(List<WContact> contacts) {
                    super.onPostExecute(contacts);
                    if (listener != null) {
                        listener.receiveWhatsappContacts(contacts);
                    }
                }
            }.execute();

        } else
            throw new WhatsappNotInstalledException();


    }

    @SuppressLint("StaticFieldLeak")
    public synchronized void sendMessage(final List<WContact> contacts, final WMessage message, final Context context, final SendMessageListener listener) throws IOException, WhatsappNotInstalledException {


        if (isWhatsappInstalled()) {
            new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    //Shell.SU.run("am force-stop com.whatsapp");
                    SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(new File( wPackageWhatsapp.getAppFolderPath() + "/databases/msgstore.db"), null);
                    for (WContact contact : contacts) {
                        try {
                            sendMessage(contact, message);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    db.close();
                    //PackageManager pm = context.getPackageManager();
                    //Intent intent = pm.getLaunchIntentForPackage("com.whatsapp");
                    //context.startActivity(intent);
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean finish) {
                    super.onPostExecute(finish);
                    if (listener != null) {
                        listener.finishSendWMessage(contacts, message);
                    }
                }
            }.execute();
        } else
            throw new WhatsappNotInstalledException();

    }
/*
    private void initiate() {

        String messagePath = makeDbAccessible("databases", "msgstore.db", isExternalSpace);
        File file = new File(messagePath);
        try {
            if (file.exists() && dbMessage == null) {
                dbMessage = SQLiteDatabase.openDatabase(messagePath, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS | SQLiteDatabase.OPEN_READWRITE);
            }
        } catch (SQLiteException e) {
            dbMessage = null;
        }
    }

    private void closeConnection() {
        if (dbMessage != null) {
            if (dbMessage.isOpen()) {
                dbMessage.close();
            }
            dbMessage = null;
        }
    }
*/
    private void initialCheck() throws WhatsappNotInstalledException {
        if (!isWhatsappInstalled()) throw new WhatsappNotInstalledException();
    }

}

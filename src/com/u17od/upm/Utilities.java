/*
 * Universal Password Manager
 * Copyright (c) 2010-2011 Adrian Smith
 *
 * This file is part of Universal Password Manager.
 *   
 * Universal Password Manager is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Universal Password Manager is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Universal Password Manager; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package com.u17od.upm;

import java.io.File;
import java.util.Date;

import com.u17od.upm.database.PasswordDatabase;

import android.app.Activity;
import android.content.SharedPreferences;

public class Utilities {

    public static final String DEFAULT_DATABASE_FILE = "upm.db";
    public static final String PREFS_NAME = "UPMPrefs";
    public static final String PREFS_DB_FILE_NAME = "DB_FILE_NAME";

    public static File getDatabaseFile(Activity activity) {
        String dbFileName = getDatabaseFileName(activity);
        if (dbFileName == null || dbFileName.equals("")) {
            return new File(activity.getFilesDir(), DEFAULT_DATABASE_FILE);
        } else {
            return new File(activity.getFilesDir(), dbFileName);
        }
    }

    public static String getDatabaseFileName(Activity activity) {
        SharedPreferences settings = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        return settings.getString(PREFS_DB_FILE_NAME, DEFAULT_DATABASE_FILE);
    }

    public static void setDatabaseFileName(String dbFileName, Activity activity) {
        SharedPreferences settings = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREFS_DB_FILE_NAME, dbFileName);
        editor.commit();
    }

    public static boolean isSyncRequired(Activity activity) {
        UPMApplication app = (UPMApplication) activity.getApplication();
        PasswordDatabase db = app.getPasswordDatabase();
        Date timeOfLastSync = app.getTimeOfLastSync();

        boolean syncRequired = false;

        if (db.getDbOptions().getRemoteLocation() != null && !db.getDbOptions().getRemoteLocation().equals("")) {
            if (timeOfLastSync == null || System.currentTimeMillis() - timeOfLastSync.getTime() > (5 * 60 * 1000)) {
                syncRequired = true;
            }
        }

        return syncRequired;
    }

}

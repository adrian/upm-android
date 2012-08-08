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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Date;

import android.app.Activity;
import android.app.Application;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.u17od.upm.database.PasswordDatabase;

/**
 * This class replaces the regular Application class in the application and
 * allows us to store data at the application level.
 */
public class UPMApplication extends Application {

    private PasswordDatabase passwordDatabase;
    private Date timeOfLastSync;
    
    public Date getTimeOfLastSync() {
        return timeOfLastSync;
    }

    public void setTimeOfLastSync(Date timeOfLastSync) {
        this.timeOfLastSync = timeOfLastSync;
    }

    public void setPasswordDatabase(PasswordDatabase passwordDatabase) {
        this.passwordDatabase = passwordDatabase;
    }

    public PasswordDatabase getPasswordDatabase() {
        return passwordDatabase;
    }

    protected boolean copyFile(File source, File dest, Activity activity) {
        boolean successful = false;

        FileChannel sourceChannel = null;
        FileChannel destinationChannel = null;
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(source);
            sourceChannel = is.getChannel();

            File destFile = null;
            if (dest.isDirectory()) {
                destFile = new File(dest, source.getName());
            } else {
                destFile = dest;
            }

            os = new FileOutputStream(destFile);
            destinationChannel = os.getChannel();
            destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());

            successful=true;
        } catch (IOException e) {
            Log.e(activity.getClass().getName(), getString(R.string.file_problem), e);
            Toast.makeText(activity, R.string.file_problem, Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (sourceChannel != null) {
                    sourceChannel.close();
                }
                if (is != null) {
                    is.close();
                }
                if (destinationChannel != null) {
                    destinationChannel.close();
                }
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                Log.e(activity.getClass().getName(), getString(R.string.file_problem), e);
                Toast.makeText(activity, R.string.file_problem, Toast.LENGTH_LONG).show();
            }
        }

        return successful;
    }

    protected void restoreDatabase(Activity activity) {
        deleteDatabase(activity);
        File fileOnSDCard = new File(Environment.getExternalStorageDirectory(), Utilities.DEFAULT_DATABASE_FILE);
        File databaseFile = Utilities.getDatabaseFile(activity);
        ((UPMApplication) activity.getApplication()).copyFile(fileOnSDCard, databaseFile, activity);
    }

    protected void deleteDatabase(Activity activity) {
        Utilities.getDatabaseFile(activity).delete();
        Utilities.setDatabaseFileName(null, activity);
    }

}

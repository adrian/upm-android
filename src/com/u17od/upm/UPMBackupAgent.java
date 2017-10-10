package com.u17od.upm;

import java.io.IOException;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class UPMBackupAgent extends BackupAgentHelper {

    private static final String PREFS_BACKUP_KEY = "prefs";
    private static final String DBFILE_BACKUP_KEY = "dbFile";

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper prefBackupHelper =
                new SharedPreferencesBackupHelper(this, Prefs.PREFS_NAME);
        addHelper(PREFS_BACKUP_KEY, prefBackupHelper);

        String dbFileName = Utilities.getDatabaseFileName(this);
        Log.i(getClass().getName(),
                String.format("UPM database file to backup: %s", dbFileName));
        FileBackupHelper dbFileBackupHelper =
                new FileBackupHelper(this, dbFileName);
        addHelper(DBFILE_BACKUP_KEY, dbFileBackupHelper);

    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
             ParcelFileDescriptor newState) throws IOException {
        synchronized (UPMApplication.sDataLock) {
            super.onBackup(oldState, data, newState);
        }
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        synchronized (UPMApplication.sDataLock) {
            super.onRestore(data, appVersionCode, newState);
        }
    }

}

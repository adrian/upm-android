package com.u17od.upm;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;

import javax.crypto.SecretKey;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.u17od.upm.crypto.InvalidPasswordException;
import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.database.ProblemReadingDatabaseFile;


public abstract class SyncDatabaseActivity extends Activity {

    private static final int ENTER_PW_REQUEST_CODE = 222;
    public static final int SYNC_DB_REQUEST_CODE = 226;

    public static final int RESULT_REFRESH = 1;

    public static interface SyncResult {
        public static final int IN_SYNC = 0;
        public static final int UPLOAD_LOCAL = 1;
        public static final int KEEP_REMOTE = 2;
    }

    protected File downloadedDatabaseFile;

    protected abstract void uploadDatabase();
    protected abstract void downloadDatabase();

    protected void decryptDatabase() {
        SecretKey existingDBSecretKey = getPasswordDatabase().getEncryptionService().getSecretKey();
        try {
            PasswordDatabase passwordDatabase = null;
            if (downloadedDatabaseFile != null) {
                passwordDatabase = new PasswordDatabase(downloadedDatabaseFile, existingDBSecretKey);
            }
            syncDb(passwordDatabase);
        } catch (IOException e) {
            Log.e("SyncDatabaseActivity.onCreate()", "Problem reading database", e);
            UIUtilities.showToast(SyncDatabaseActivity.this, R.string.problem_reading_upm_db, true);
            finish();
        } catch (GeneralSecurityException e) {
            Log.e("SyncDatabaseActivity.onCreate()", "Problem decrypting database", e);
            UIUtilities.showToast(SyncDatabaseActivity.this, R.string.problem_decrypying_db, true);
            finish();
        } catch (ProblemReadingDatabaseFile e) {
            Log.e("SyncDatabaseActivity.onCreate()", "Not a password database", e);
            UIUtilities.showToast(SyncDatabaseActivity.this, R.string.not_password_database);
            finish();
        } catch (InvalidPasswordException e) {
            EnterMasterPassword.databaseFileToDecrypt = downloadedDatabaseFile;
            Intent i = new Intent(SyncDatabaseActivity.this, EnterMasterPassword.class);
            startActivityForResult(i, ENTER_PW_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch(requestCode) {
            case ENTER_PW_REQUEST_CODE:
                if (resultCode == Activity.RESULT_CANCELED) {
                    UIUtilities.showToast(this, R.string.enter_password_cancalled);
                    finish();
                } else {
                    syncDb(EnterMasterPassword.decryptedPasswordDatabase);
                }
                break;
        }
    }

    /**
     * Check if the downloaded DB is more recent than the current db.
     * If it is the replace the current DB with the downloaded one and reload
     * the accounts listview
     */
    protected int syncDb(PasswordDatabase dbDownloadedOnSync) {
        int syncResult = SyncResult.IN_SYNC;
        UPMApplication app = (UPMApplication) getApplication();
        if (dbDownloadedOnSync == null || dbDownloadedOnSync.getRevision() < app.getPasswordDatabase().getRevision()) {
            uploadDatabase();
            syncResult = SyncResult.UPLOAD_LOCAL;
        } else if (dbDownloadedOnSync.getRevision() > app.getPasswordDatabase().getRevision()) {
            app.copyFile(downloadedDatabaseFile, Utilities.getDatabaseFile(this), this);
            app.setPasswordDatabase(dbDownloadedOnSync);
            dbDownloadedOnSync.setDatabaseFile(Utilities.getDatabaseFile(this));
            setResult(RESULT_REFRESH);
            syncResult = SyncResult.KEEP_REMOTE;
            UIUtilities.showToast(this, R.string.new_db_downloaded);
            finish();
        } else if (dbDownloadedOnSync.getRevision() == app.getPasswordDatabase().getRevision()) {
            UIUtilities.showToast(this, R.string.db_uptodate);
            finish();
        }
        app.setTimeOfLastSync(new Date());
        if (downloadedDatabaseFile != null) {
            downloadedDatabaseFile.delete();
        }
        return syncResult;
    }

    protected PasswordDatabase getPasswordDatabase() {
        return ((UPMApplication) getApplication()).getPasswordDatabase();
    }

}

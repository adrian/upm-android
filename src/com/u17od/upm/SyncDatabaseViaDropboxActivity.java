package com.u17od.upm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;

public class SyncDatabaseViaDropboxActivity extends SyncDatabaseActivity {

    private static final String RETURNING_FROM_DB_AUTH = "retDBAuth";

    private DropboxAPI<AndroidAuthSession> mDBApi;

    private boolean returningFromDropboxAuthentication;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null){
            returningFromDropboxAuthentication =
                    savedInstanceState.getBoolean(
                            RETURNING_FROM_DB_AUTH, false);
        }
        prepareDropboxAPI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (returningFromDropboxAuthentication) {
            // We'll reach here if we're returning from the Dropbox
            // authentication activity. If auth was successful then download
            // the database file. Otherwise leave the activity and abort the
            // sync.
            if (mDBApi.getSession().authenticationSuccessful()) {
                // MANDATORY call to complete auth.
                // Sets the access token on the session
                mDBApi.getSession().finishAuthentication();

                // store the tokens so we don't need to authenticate again
                // during this session
                AccessTokenPair tokens = mDBApi.getSession().getAccessTokenPair();
                Utilities.setDropboxAccessTokenPair(this, tokens);

                downloadDatabase();
            } else {
                finish();
            }
        } else {
            // We'll reach here if we're entering the activity after selecting
            // the "Sync" menu option.
            downloadDatabase();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(RETURNING_FROM_DB_AUTH,
                returningFromDropboxAuthentication);
    }

    private void prepareDropboxAPI() {
        // Prepare Dropbox Session objects
        AppKeyPair appKeys = new AppKeyPair(DropboxConstants.APP_KEY, DropboxConstants.APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeys, DropboxConstants.ACCESS_TYPE);
        mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        AccessTokenPair accessTokenPair = Utilities.getDropboxAccessTokenPair(this);
        if (accessTokenPair != null) {
            mDBApi.getSession().setAccessTokenPair(accessTokenPair);
        }
    }

    @Override
    protected void uploadDatabase() {
        new UploadDatabaseTask().execute();
    }

    @Override
    protected void downloadDatabase() {
        new DownloadDatabaseTask().execute();
    }

    private class DownloadDatabaseTask extends AsyncTask<Void, Void, Integer> {

        private static final int ERROR_IO_ERROR = 1;
        private static final int ERROR_DROPBOX_UNLINKED = 2;
        private static final int ERROR_DROPBOX_ERROR = 3;

        private ProgressDialog progressDialog;

        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SyncDatabaseViaDropboxActivity.this,
                    "", getString(R.string.downloading_db));
            if (mDBApi == null) {
                prepareDropboxAPI();
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            FileOutputStream outputStream = null;
            try {
                // Download the file and save it to a temp file
                String remoteFileName = Utilities.getDatabaseFileName(SyncDatabaseViaDropboxActivity.this);
                downloadedDatabaseFile = new File(getCacheDir(), remoteFileName);
                outputStream = new FileOutputStream(downloadedDatabaseFile);
                DropboxAPI.DropboxFileInfo downloadedFileInfo =
                        mDBApi.getFile(remoteFileName, null, outputStream, null);
                // Store the db file rev for use in the UploadDatabaseTask
                // Prefs is used instead of the activity instance because the
                // activity could be recreate between now and then meaning the
                // instance variables are reset.
                Utilities.setConfig(SyncDatabaseViaDropboxActivity.this,
                        Utilities.DROPBOX_PREFS, Utilities.DROPBOX_DB_REV,
                        downloadedFileInfo.getMetadata().rev);
                return 0;
            } catch (IOException e) {
                Log.e("DownloadDropboxFileActivity.DownloadFileTask",
                        "IOException", e);
                return ERROR_IO_ERROR;
            } catch (DropboxUnlinkedException e) {
                Log.e("DownloadDropboxFileActivity.DownloadFileTask",
                        "Dropbox unlinked exception", e);
                return ERROR_DROPBOX_UNLINKED;
            } catch (DropboxServerException e) {
                // 404 is a valid response. Just means we haven't uploaded yet
                if (e.error != DropboxServerException._404_NOT_FOUND){
                    Log.e("DownloadDropboxFileActivity.DownloadFileTask",
                            "Dropbox server exception", e);
                    return ERROR_DROPBOX_ERROR;
                } else {
                    downloadedDatabaseFile = null;
                    return 0;
                }
            } catch (DropboxException e) {
                Log.e("DownloadDropboxFileActivity.DownloadFileTask",
                        "Dropbox exception", e);
                return ERROR_DROPBOX_ERROR;
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e("DownloadDropboxFileActivity.DownloadFileTask",
                                "IOException", e);
                        return ERROR_IO_ERROR;
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();

            switch (result) {
                case 0:
                    decryptDatabase();
                    break;
                case ERROR_IO_ERROR:
                    UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this,
                            R.string.problem_saving_db, true);
                    finish();
                    break;
                case ERROR_DROPBOX_UNLINKED:
                    returningFromDropboxAuthentication = true;
                    mDBApi.getSession().startAuthentication(SyncDatabaseViaDropboxActivity.this);
                    break;
                case ERROR_DROPBOX_ERROR:
                    UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this,
                            R.string.dropbox_problem, true);
                    finish();
                    break;
            }
        }
    }

    private class UploadDatabaseTask extends AsyncTask<Void, Void, Integer> {

        private static final int UPLOAD_OK = 0;
        private static final int ERROR_READING = 1;
        private static final int ERROR_DROPBOX = 2;

        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(
                    SyncDatabaseViaDropboxActivity.this, "",
                    getString(R.string.uploading_database));
            if (mDBApi == null) {
                prepareDropboxAPI();
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int result = UPLOAD_OK;

            FileInputStream inputStream = null;
            try {
                File databaseFile = getPasswordDatabase().getDatabaseFile();
                inputStream = new FileInputStream(databaseFile);

                String currentRev = Utilities.getConfig(
                        SyncDatabaseViaDropboxActivity.this,
                        Utilities.DROPBOX_PREFS, Utilities.DROPBOX_DB_REV);

                mDBApi.putFile(databaseFile.getName(), inputStream,
                        databaseFile.length(), currentRev, null);
            } catch (FileNotFoundException e) {
                Log.e("SyncDatabaseViaDropboxActivity",
                        "Problem reading database file", e);
                result = ERROR_READING;
            } catch (DropboxException e) {
                Log.e("SyncDatabaseViaDropboxActivity",
                        "Dropbox error", e);
                result = ERROR_DROPBOX;
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {}
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();
            switch (result) {
            case ERROR_READING:
                UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this, R.string.problem_reading_upm_db);
                break;
            case ERROR_DROPBOX:
                UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this, R.string.dropbox_problem);
                break;
            default:
                UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this, R.string.db_sync_complete);
                break;
            }
            finish();
        }

    }

}

package com.u17od.upm;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.SearchV2Result;
import com.dropbox.core.v2.files.WriteMode;
import com.u17od.upm.dropbox.DropboxClientFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.u17od.upm.DropboxConstants.DROPBOX_ACCESS_TOKEN;

public class SyncDatabaseViaDropboxActivity extends SyncDatabaseActivity {

    private static final String TAG = SyncDatabaseViaDropboxActivity.class.getName();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(Utilities.DROPBOX_PREFS, MODE_PRIVATE);
        String dropboxAccessToken = prefs.getString(DROPBOX_ACCESS_TOKEN, null);
        Log.i("onCreate", "dropboxAccessToken=" + dropboxAccessToken);

        if (dropboxAccessToken == null) {
            Auth.startOAuth2Authentication(SyncDatabaseViaDropboxActivity.this, DropboxConstants.APP_KEY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        String dropboxAccessToken = prefs.getString(DROPBOX_ACCESS_TOKEN, null);
        Log.i("onResume", "dropboxAccessToken=" + dropboxAccessToken);

        if (dropboxAccessToken == null) {
            dropboxAccessToken = Auth.getOAuth2Token();
            Log.i("onResume", "dropboxAccessToken after getOAuth2Token=" + dropboxAccessToken);
            if (dropboxAccessToken != null) {
                prefs.edit().putString(DROPBOX_ACCESS_TOKEN, dropboxAccessToken).commit();
                DropboxClientFactory.init(dropboxAccessToken);
                downloadDatabase();
            }
        } else {
            DropboxClientFactory.init(dropboxAccessToken);
            downloadDatabase();
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

        private static final String TAG = "DownloadDatabaseTask";
        private static final int ERROR_IO = 1;
        private static final int ERROR_DROPBOX = 2;
        private static final int ERROR_DROPBOX_INVALID_TOKEN = 3;
        private static final int REMOTE_FILE_DOESNT_EXIST = 4;

        private ProgressDialog progressDialog;

        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SyncDatabaseViaDropboxActivity.this,
                    "", getString(R.string.downloading_db));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            FileOutputStream outputStream = null;
            try {
                // Download the file and save it to a temp file
                String remoteFileName = Utilities.getDatabaseFileName(SyncDatabaseViaDropboxActivity.this);
                downloadedDatabaseFile = new File(getCacheDir(), remoteFileName);
                outputStream = new FileOutputStream(downloadedDatabaseFile);

                SearchV2Result searchResults = DropboxClientFactory.getClient()
                        .files().searchV2(remoteFileName);
                if (searchResults.getMatches().size() == 0) {
                    return REMOTE_FILE_DOESNT_EXIST;
                }

                FileMetadata metadata = DropboxClientFactory.getClient()
                        .files()
                        .download("/" + remoteFileName)
                        .download(outputStream);

                // Store the db file rev for use in the UploadDatabaseTask
                // Prefs is used instead of the activity instance because the
                // activity could be recreate between now and then meaning the
                // instance variables are reset.
                Utilities.setConfig(SyncDatabaseViaDropboxActivity.this,
                        Utilities.DROPBOX_PREFS, Utilities.DROPBOX_DB_REV,
                        metadata.getRev());

                return 0;
            } catch (IOException e) {
                Log.e(TAG, "IOException downloading database", e);
                return ERROR_IO;
            } catch (InvalidAccessTokenException e) {
                Log.e(TAG, "InvalidAccessTokenException downloading database", e);
                return ERROR_DROPBOX_INVALID_TOKEN;
            } catch (DbxException e) {
                Log.e(TAG, "DbxException downloading database", e);
                return ERROR_DROPBOX;
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "IOException closing database file stream", e);
                        return ERROR_IO;
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
                case ERROR_IO:
                    UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this,
                            R.string.problem_saving_db, true);
                    finish();
                    break;
                case ERROR_DROPBOX:
                    UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this,
                            R.string.dropbox_problem, true);
                    finish();
                    break;
                case ERROR_DROPBOX_INVALID_TOKEN:
                    prefs.edit().remove(DROPBOX_ACCESS_TOKEN).commit();
                    UIUtilities.showToast(SyncDatabaseViaDropboxActivity.this,
                            R.string.dropbox_token_problem, true);
                    finish();
                    break;
                case REMOTE_FILE_DOESNT_EXIST:
                    syncDb(null);
                    break;
            }
        }
    }

    private class UploadDatabaseTask extends AsyncTask<Void, Void, Integer> {

        private static final String TAG = "UploadDatabaseTask";
        private static final int UPLOAD_OK = 0;
        private static final int ERROR_IO = 1;
        private static final int ERROR_DROPBOX = 2;

        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(
                    SyncDatabaseViaDropboxActivity.this, "",
                    getString(R.string.uploading_database));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int result = UPLOAD_OK;

            FileInputStream inputStream = null;
            try {
                File databaseFile = getPasswordDatabase().getDatabaseFile();
                inputStream = new FileInputStream(databaseFile);
                DropboxClientFactory.getClient().files()
                        .uploadBuilder("/" + databaseFile.getName())
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream);
            } catch (IOException e) {
                Log.e(TAG, "IOException during database upload", e);
                result = ERROR_IO;
            } catch (DbxException e) {
                Log.e(TAG, "DbxException downloading database", e);
                return ERROR_DROPBOX;
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "IOException during database upload", e);
                        return ERROR_IO;
                    }
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();
            switch (result) {
            case ERROR_IO:
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

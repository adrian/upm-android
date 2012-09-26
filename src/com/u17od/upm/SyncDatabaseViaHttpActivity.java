package com.u17od.upm;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.u17od.upm.database.AccountInformation;
import com.u17od.upm.transport.HTTPTransport;
import com.u17od.upm.transport.TransportException;

/**
 * Activity used when syncing a database over HTTP. Syncing over HTTP doesn't
 * require any user interaction so this activity is transparent. The reason we
 * use it is so we can make use of the methods on it's parent class,
 * SyncDatabaseActivity.
 */
public class SyncDatabaseViaHttpActivity extends SyncDatabaseActivity {

    @Override
    protected void onResume() {
        super.onResume();
        downloadDatabase();
    }

    @Override
    protected void downloadDatabase() {
        new RetrieveRemoteDatabaseFromHTTP().execute();
    }

    @Override
    protected void uploadDatabase() {
        new UploadDatabase().execute();
    }

    private class RetrieveRemoteDatabaseFromHTTP extends AsyncTask<Void, Void, Integer> {

        private static final int PROBLEM_DOWNLOADING_DB = 1;
        private static final int NO_REMOTE_DB = 2;

        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SyncDatabaseViaHttpActivity.this, "", getString(R.string.syncing_database));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int errorCode = 0;

            String remoteURL = getPasswordDatabase().getDbOptions().getRemoteLocation();
            if (remoteURL.trim().equals("")) {
                errorCode = NO_REMOTE_DB;
            } else {
                String remoteFileName = Utilities.getDatabaseFileName(SyncDatabaseViaHttpActivity.this);
                String remoteURLUsername = null, remoteURLPassword = null;
                String accountWithAuthDetails = getPasswordDatabase().getDbOptions().getAuthDBEntry();
                if (accountWithAuthDetails != null && accountWithAuthDetails.length() > 0) {
                    AccountInformation account = getPasswordDatabase().getAccount(accountWithAuthDetails);
                    remoteURLUsername = new String(account.getUserId());
                    remoteURLPassword = new String(account.getPassword());
                }

                try {
                    SharedPreferences settings = getSharedPreferences(Prefs.PREFS_NAME, 0);
                    String trustedHostname = settings.getString(Prefs.PREF_TRUSTED_HOSTNAME, "");

                    HTTPTransport transport = new HTTPTransport(getFileStreamPath(
                            FullAccountList.CERT_FILE_NAME), trustedHostname,
                            getApplicationContext().getFilesDir());
                    downloadedDatabaseFile = transport.getRemoteFile(remoteURL, remoteFileName, remoteURLUsername, remoteURLPassword);
                } catch (TransportException e) {
                    Log.e("DownloadRemoteDatabase", "Problem downloading database", e);
                    errorCode = PROBLEM_DOWNLOADING_DB;
                }
            }

            return errorCode;
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();

            switch (result) {
                case 0:
                    decryptDatabase();
                    break;
                case PROBLEM_DOWNLOADING_DB:
                    UIUtilities.showToast(SyncDatabaseViaHttpActivity.this,
                            R.string.problem_downloading_db);
                    finish();
                    break;
                case NO_REMOTE_DB:
                    UIUtilities.showToast(SyncDatabaseViaHttpActivity.this,
                            R.string.no_remote_db);
                    finish();
                    break;
            }
        }

    }

    private class UploadDatabase extends AsyncTask<Void, Void, Integer> {

        private static final int UPLOAD_OK = 0;
        private static final int UPLOAD_ERROR = 1;

        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SyncDatabaseViaHttpActivity.this, "", getString(R.string.uploading_database));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int result = UPLOAD_OK;

            String remoteURL = getPasswordDatabase().getDbOptions().getRemoteLocation();
            String remoteURLUsername = null, remoteURLPassword = null;
            String accountWithAuthDetails = getPasswordDatabase().getDbOptions().getAuthDBEntry();
            if (accountWithAuthDetails != null && accountWithAuthDetails.length() > 0) {
                AccountInformation account = getPasswordDatabase().getAccount(accountWithAuthDetails);
                remoteURLUsername = new String(account.getUserId());
                remoteURLPassword = new String(account.getPassword());
            }

            SharedPreferences settings = getSharedPreferences(Prefs.PREFS_NAME, 0);
            String trustedHostname = settings.getString(Prefs.PREF_TRUSTED_HOSTNAME, "");

            HTTPTransport transport = new HTTPTransport(getFileStreamPath(
                    FullAccountList.CERT_FILE_NAME), trustedHostname,
                    getApplicationContext().getFilesDir());
            String fileName = getPasswordDatabase().getDatabaseFile().getName();
            try {
                transport.delete(remoteURL, fileName, remoteURLUsername, remoteURLPassword);
                transport.put(remoteURL, getPasswordDatabase().getDatabaseFile(), remoteURLUsername, remoteURLPassword);
            } catch (TransportException e) {
                Log.e("FullAccountList", e.getMessage(), e);
                result = UPLOAD_ERROR;
            }

            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();
            if (result == UPLOAD_OK) {
                UIUtilities.showToast(SyncDatabaseViaHttpActivity.this, R.string.db_sync_complete);
            } else {
                UIUtilities.showToast(SyncDatabaseViaHttpActivity.this, R.string.problem_uploading);
            }
            finish();
        }

    }

}

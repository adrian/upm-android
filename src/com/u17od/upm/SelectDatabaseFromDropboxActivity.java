package com.u17od.upm;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.core.DbxException;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.dropbox.DropboxClientFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.u17od.upm.DropboxConstants.DROPBOX_ACCESS_TOKEN;

public class SelectDatabaseFromDropboxActivity extends ListActivity {

    private static final String TAG = SelectDatabaseFromDropboxActivity.class.getName();
    private SharedPreferences prefs;

    private static final int ENTER_PW_REQUEST_CODE = 111;

    private DropboxAPI<AndroidAuthSession> mDBApi;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(Utilities.DROPBOX_PREFS, MODE_PRIVATE);
        String dropboxAccessToken = prefs.getString(DROPBOX_ACCESS_TOKEN, null);
        Log.i("onCreate", "dropboxAccessToken=" + dropboxAccessToken);

        if (dropboxAccessToken == null) {
            Auth.startOAuth2Authentication(SelectDatabaseFromDropboxActivity.this, DropboxConstants.APP_KEY);
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
                // Launch the async task where we'll download database filenames from
                // Dropbox and populate the ListView
                new DownloadListOfFilesTask().execute();
            }
        } else {
            DropboxClientFactory.init(dropboxAccessToken);
            // Launch the async task where we'll download database filenames from
            // Dropbox and populate the ListView
            new DownloadListOfFilesTask().execute();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * The only way this method can be called is if we're returning from
     * EnterMasterPassword after retrieving a database.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_CANCELED) {
            UIUtilities.showToast(this, R.string.enter_password_cancalled);
        } else {
            if (requestCode == ENTER_PW_REQUEST_CODE) {
                // Setting the DatabaseFileName preference effectively says
                // that this is the db to open when the app starts
                Utilities.setSyncMethod(Prefs.SyncMethod.DROPBOX, this);
                String selectedDropboxFilename =
                        Utilities.getConfig(this, Utilities.DROPBOX_PREFS,
                                Utilities.DROPBOX_SELECTED_FILENAME);
                Utilities.setDatabaseFileName(selectedDropboxFilename,
                        SelectDatabaseFromDropboxActivity.this);

                // Put a reference to the decrypted database on the Application object
                UPMApplication app = (UPMApplication) getApplication();
                app.setPasswordDatabase(EnterMasterPassword.decryptedPasswordDatabase);
                app.setTimeOfLastSync(new Date());

                setResult(RESULT_OK);
                finish();
            }
        }
    }

    /**
     * Called when an file from the listview is selected
     */
    @Override
    protected void onListItemClick(ListView lv, View v, int position, long id) {
        String selectedFileName = (String) lv.getItemAtPosition(position);
        Utilities.setConfig(this, Utilities.DROPBOX_PREFS,
                Utilities.DROPBOX_SELECTED_FILENAME, selectedFileName);
        new DownloadDatabaseTask().execute(selectedFileName);
    }

    private class DownloadListOfFilesTask extends AsyncTask<Void, Void, Integer> {

        private static final int ERROR_DROPBOX = 1;
        private static final int ERROR_DROPBOX_INVALID_TOKEN = 2;

        private List<Metadata> dropBoxEntries;

        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SelectDatabaseFromDropboxActivity.this,
                    "", getString(R.string.dropbox_get_file_list));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
                ListFolderResult filderContents =
                        DropboxClientFactory.getClient().files().listFolder("");
                dropBoxEntries = filderContents.getEntries();
                return 0;
            } catch (InvalidAccessTokenException e) {
                Log.e(TAG, "InvalidAccessTokenException downloading database", e);
                return ERROR_DROPBOX_INVALID_TOKEN;
            } catch (DbxException e) {
                Log.e(TAG, "DbxException downloading database", e);
                return ERROR_DROPBOX;
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();

            switch (result) {
                case 0:
                    setListAdapter(new ArrayAdapter<String>(
                            SelectDatabaseFromDropboxActivity.this,
                            android.R.layout.simple_list_item_1,
                            dropboxFiles(dropBoxEntries)));
                    break;
                case ERROR_DROPBOX_INVALID_TOKEN:
                    prefs.edit().remove(DROPBOX_ACCESS_TOKEN).commit();
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.dropbox_token_problem, true);
                    finish();
                    break;
                case ERROR_DROPBOX:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.dropbox_problem, true);
                    finish();
                    break;
            }
        }


        /*
         * Extract the filenames from the given list of Dropbox Entries and return
         * a simple String array.
         */
        private List<String> dropboxFiles(List<Metadata> dpEntries) {
            List<String> fileNames = new ArrayList<String>();
            for (Metadata entry : dpEntries) {
                fileNames.add(entry.getName());
            }
            return fileNames;
        }

    }


    private class DownloadDatabaseTask extends AsyncTask<String, Void, Integer> {

        private static final String TAG = "DownloadDatabaseTask";
        private static final int ERROR_IO = 1;
        private static final int ERROR_DROPBOX = 2;
        private static final int ERROR_DROPBOX_INVALID_TOKEN = 3;
        private static final int NOT_UPM_DB = 4;

        private ProgressDialog progressDialog;

        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SelectDatabaseFromDropboxActivity.this,
                    "", getString(R.string.downloading_db));
        }

        @Override
        protected Integer doInBackground(String... fileName) {
            FileOutputStream outputStream = null;
            try {
                // Download the file and save it to UPM's internal files area
                File file = new File(getFilesDir(), fileName[0]);
                outputStream = new FileOutputStream(file);
                DropboxClientFactory.getClient()
                        .files()
                        .download("/" + fileName[0])
                        .download(outputStream);

                // Check this is a UPM database file
                if (!PasswordDatabase.isPasswordDatabase(file)) {
                    return NOT_UPM_DB;
                }
                EnterMasterPassword.databaseFileToDecrypt = file;

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

            Intent i = null;
            switch (result) {
                case 0:
                    // Call up the EnterMasterPassword activity
                    // When it returns we'll pick up in the method onActivityResult
                    i = new Intent(SelectDatabaseFromDropboxActivity.this, EnterMasterPassword.class);
                    startActivityForResult(i, ENTER_PW_REQUEST_CODE);
                    break;
                case ERROR_IO:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.problem_saving_db, true);
                    finish();
                    break;
                case ERROR_DROPBOX:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.dropbox_problem, true);
                    finish();
                    break;
                case ERROR_DROPBOX_INVALID_TOKEN:
                    prefs.edit().remove(DROPBOX_ACCESS_TOKEN).commit();
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.dropbox_token_problem, true);
                    finish();
                    break;
                case NOT_UPM_DB:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.not_password_database, true);
                    finish();
                    break;
            }
        }
    }

}

package com.u17od.upm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.u17od.upm.database.PasswordDatabase;

public class SelectDatabaseFromDropboxActivity extends ListActivity {

    public static final String EXTRA_DB_FILENAMES = "EXTRA_DB_FILENAMES";

    private static final int ENTER_PW_REQUEST_CODE = 111;

    private DropboxAPI<AndroidAuthSession> mDBApi;
    private ProgressDialog progressDialog;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dropbox_file_list);

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
    protected void onResume() {
        super.onResume();

        // If we're returning from a successful Dropbox authentication then
        // update the session and store the access token pair
        if (mDBApi != null && mDBApi.getSession().authenticationSuccessful()) {
            // MANDATORY call to complete auth.
            // Sets the access token on the session
            mDBApi.getSession().finishAuthentication();

            // store the tokens so we don't need to authenticate again
            // during this session
            AccessTokenPair tokens = mDBApi.getSession().getAccessTokenPair();
            Utilities.setDropboxAccessTokenPair(this, tokens);
        }

        // Launch the async task where we'll download database filenames from
        // Dropbox and populate the ListView
        new DownloadListOfFilesTask().execute();
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
        new DownloadFileTask().execute(selectedFileName);
    }

    private class DownloadListOfFilesTask extends AsyncTask<Void, Void, Integer> {

        private static final int ERROR_CODE_DB_UNLINKED = 1;
        private static final int ERROR_CODE_DB_EXCEPTION = 2;

        private List<DropboxAPI.Entry> dropBoxEntries;

        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(SelectDatabaseFromDropboxActivity.this,
                    "", getString(R.string.dropbox_get_file_list));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
                DropboxAPI.Entry rootMetadata = mDBApi.metadata("/", 100, null, true, null);
                dropBoxEntries = rootMetadata.contents;
                return 0;
            } catch (DropboxUnlinkedException e) {
                Log.e("DropboxDownloadActivity", "Dropbox Unlinked Exception", e);
                Utilities.clearDropboxAccessTokenPair(SelectDatabaseFromDropboxActivity.this);
                return ERROR_CODE_DB_UNLINKED;
            } catch (DropboxException e) {
                Log.e("AppEntryActivity", "Problem communicating with Dropbox", e);
                return ERROR_CODE_DB_EXCEPTION;
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
                case ERROR_CODE_DB_UNLINKED:
                    mDBApi.getSession().startAuthentication(SelectDatabaseFromDropboxActivity.this);
                    break;
                case ERROR_CODE_DB_EXCEPTION:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.dropbox_problem, true);
                    break;
            }
        }


        /*
         * Extract the filenames from the given list of Dropbox Entries and return
         * a simple String array.
         */
        private List<String> dropboxFiles(List<DropboxAPI.Entry> dpEntries) {
            List<String> fileNames = new ArrayList<String>();
            for (DropboxAPI.Entry entry : dpEntries) {
                if (!entry.isDir) {
                    fileNames.add(entry.fileName());
                }
            }
            return fileNames;
        }

    }


    private class DownloadFileTask extends AsyncTask<String, Void, Integer> {

        private static final int ERROR_IO_ERROR = 1;
        private static final int ERROR_DROPBOX_ERROR = 2;
        private static final int NOT_UPM_DB = 3;

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
                mDBApi.getFile(fileName[0], null, outputStream, null);

                // Check this is a UPM database file
                if (!PasswordDatabase.isPasswordDatabase(file)) {
                    return NOT_UPM_DB;
                }
                EnterMasterPassword.databaseFileToDecrypt = file;

                return 0;
            } catch (DropboxException e) {
                return ERROR_DROPBOX_ERROR;
            } catch (IOException e) {
                return ERROR_IO_ERROR;
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        return ERROR_IO_ERROR;
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
                case ERROR_IO_ERROR:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.problem_saving_db, true);
                    break;
                case ERROR_DROPBOX_ERROR:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.dropbox_problem, true);
                    break;
                case NOT_UPM_DB:
                    UIUtilities.showToast(SelectDatabaseFromDropboxActivity.this,
                            R.string.not_password_database, true);
                    break;
            }
        }

    }

}

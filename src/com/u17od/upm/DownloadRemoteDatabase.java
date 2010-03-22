package com.u17od.upm;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.transport.HTTPTransport;
import com.u17od.upm.transport.TransportException;

public class DownloadRemoteDatabase extends Activity implements OnClickListener {

    private static final int ENTER_PW_REQUEST_CODE = 111;

    private EditText urlEditText;
    private EditText userid;
    private EditText password;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_remote_db);

        urlEditText = (EditText) findViewById(R.id.remote_db_url);
        userid = (EditText) findViewById(R.id.remote_url_userid);
        password = (EditText) findViewById(R.id.remote_url_password);

        Button downloadButton = (Button) findViewById(R.id.download_button);
        downloadButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        new DownloadDatabase().execute();
    }

    /**
     * The only way this method can be called is if we're returning from EnterMasterPassword
     * because we downloaded a remote db and the current master password didn't work.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_CANCELED) {
            UIUtilities.showToast(this, R.string.enter_password_cancalled);
        } else {
            if (requestCode == ENTER_PW_REQUEST_CODE) {
                // Setting the DatabaseFileName preference effectively says
                // that this is the db to open when the app starts
                Utilities.setDatabaseFileName(getDatabaseFileNameFromURL(), DownloadRemoteDatabase.this);
                
                // Put a reference to the decrypted database on the Application object
                UPMApplication app = (UPMApplication) getApplication();
                app.setPasswordDatabase(EnterMasterPassword.decryptedPasswordDatabase);

                setResult(RESULT_OK);
                finish();
            }
        }
    }

    private String getDatabaseFileNameFromURL() {
        // We need to store the database file name so that we know 
        // the name of the db to open when starting up
        String url = urlEditText.getText().toString();
        int slashIndex = url.lastIndexOf('/');
        String fileName = url.substring(slashIndex + 1);
        return fileName;
    }

    private class DownloadDatabase extends AsyncTask<Void, Void, Integer> {

        private static final int PROBLEM_DOWNLOADING_DB = 1;
        private static final int PROBLEM_CHECKING_DB = 2;
        private static final int NOT_UPM_DB = 3;

        private String errorMessage;
        private ProgressDialog progressDialog;

        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(DownloadRemoteDatabase.this, "", getString(R.string.downloading_db));
        }

        /**
         * Download the remote database file.
         * Check that it's a proper UPM database file.
         * Copy the downloaded file to the application database location.
         */
        @Override
        protected Integer doInBackground(Void... params) {
            int errorCode = 0;

            HTTPTransport transport = new HTTPTransport();
            File tempDB = null;
            try {
                tempDB = transport.getRemoteFile(urlEditText.getText().toString(),
                        userid.getText().toString(), password.getText().toString());

                if (PasswordDatabase.isPasswordDatabase(tempDB)) {
                    // Copy the downloaded file to the app's files dir.
                    // We retain the file name so that we know what file to delete
                    // on the server when doing a sync.
                    UPMApplication app = (UPMApplication) getApplication();
                    File destFile = new File(getFilesDir(), getDatabaseFileNameFromURL());
                    app.copyFile(tempDB, destFile, DownloadRemoteDatabase.this);
                    app.setPasswordDatabase(EnterMasterPassword.decryptedPasswordDatabase);

                    EnterMasterPassword.databaseFileToDecrypt = destFile;
                } else {
                    errorCode = NOT_UPM_DB;
                }
            } catch (TransportException e) {
                Log.e("DownloadRemoteDatabase", "Problem downloading database", e);
                errorMessage = e.getMessage();
                errorCode = PROBLEM_DOWNLOADING_DB;
            } catch (IOException e) {
                Log.e("DownloadRemoteDatabase", "IO problem", e);
                errorMessage = e.getMessage();
                errorCode = PROBLEM_CHECKING_DB;
            } finally {
                if (tempDB != null) {
                    tempDB.delete();
                }
            }

            return errorCode;
        }

        /*
         * If there was an error downloading the database then display it.
         * Otherwise launch the EnterMasterPassword activity. 
         */
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();

            Intent i = null;
            switch (result) {
                case 0:
                    // Call up the EnterMasterPassword activity
                    // When it returns we'll pick up in the method onActivityResult
                    i = new Intent(DownloadRemoteDatabase.this, EnterMasterPassword.class);
                    startActivityForResult(i, ENTER_PW_REQUEST_CODE);
                    break;
                case NOT_UPM_DB:
                    UIUtilities.showToast(DownloadRemoteDatabase.this, R.string.not_password_database, true);
                    break;
                case PROBLEM_DOWNLOADING_DB:
                    UIUtilities.showToast(DownloadRemoteDatabase.this, 
                            String.format(getString(R.string.problem_downloading_db), errorMessage),
                            true);
                    break;
                case PROBLEM_CHECKING_DB:
                    UIUtilities.showToast(DownloadRemoteDatabase.this,
                            String.format(getString(R.string.problem_checking_upm_db), errorMessage),
                            true);
                    break;
            }
        }

    }
    
}

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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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

        Button cancelButton = (Button) findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
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
                app.setTimeOfLastSync(new Date());

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
        private static final int PROBLEM_SAVING_DB = 2;
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

            SharedPreferences settings = getSharedPreferences(Prefs.PREFS_NAME, 0);
            String trustedHostname = settings.getString(Prefs.PREF_TRUSTED_HOSTNAME, "");

            HTTPTransport transport = new HTTPTransport(getFileStreamPath(
                    FullAccountList.CERT_FILE_NAME), trustedHostname, 
                    getApplicationContext().getFilesDir());
            try {
                byte[] passwordDBBytes = transport.get(
                        urlEditText.getText().toString(), 
                        userid.getText().toString(), 
                        password.getText().toString());

                if (PasswordDatabase.isPasswordDatabase(passwordDBBytes)) {
                    // Copy the downloaded bytes to the app's files dir.
                    // We retain the file name so that we know what file to delete
                    // on the server when doing a sync.
                    File destFile = new File(getFilesDir(), getDatabaseFileNameFromURL());

                    BufferedOutputStream buf = new BufferedOutputStream(
                            new FileOutputStream(destFile));
                    buf.write(passwordDBBytes);
                    buf.close();

                    EnterMasterPassword.databaseFileToDecrypt = destFile;
                } else {
                    errorCode = NOT_UPM_DB;
                }
            } catch (TransportException e) {
                Log.e("DownloadRemoteDatabase", "Problem downloading database", e);
                errorMessage = e.getMessage();
                errorCode = PROBLEM_DOWNLOADING_DB;
            } catch (IOException e) {
                Log.e("DownloadRemoteDatabase", "Problem writing to database file", e);
                errorMessage = e.getMessage();
                errorCode = PROBLEM_SAVING_DB;
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
                case PROBLEM_SAVING_DB:
                    UIUtilities.showToast(DownloadRemoteDatabase.this,
                            String.format(getString(R.string.problem_saving_db), errorMessage),
                            true);
                    break;
            }
        }

    }
    
}

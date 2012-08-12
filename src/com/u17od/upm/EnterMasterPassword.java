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
import java.io.IOException;
import java.security.GeneralSecurityException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.u17od.upm.crypto.InvalidPasswordException;
import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.database.ProblemReadingDatabaseFile;

/**
 * This Activity is responsible for prompting the user to enter their master
 * password and then decrypting the database. If the correct password is entered
 * then the AccountList Activity is loaded.
 */
public class EnterMasterPassword extends Activity implements OnClickListener {

    public static PasswordDatabase decryptedPasswordDatabase;
    public static File databaseFileToDecrypt;

    private EditText passwordField;
    private DecryptDatabase decryptDatabaseTask;
    private ProgressDialog progressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.enter_master_password);

        passwordField = (EditText) findViewById(R.id.password);
        passwordField.setText(null);

        // Make this class the listener for the click event on the OK button
        Button okButton = (Button) findViewById(R.id.master_password_open_button);
        okButton.setOnClickListener(this);

        passwordField.setOnKeyListener(new OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    openDatabase();
                    return true;
                }
                return false;
            }
        });

        decryptDatabaseTask = (DecryptDatabase) getLastNonConfigurationInstance();
        if (decryptDatabaseTask != null) {
            // Associate the async task with the new activity
            decryptDatabaseTask.setActivity(this);

            // If the decryptDatabaseTask is running display the progress
            // dialog. This can happen if the screen was rotated while the
            // background task is running.
            if (decryptDatabaseTask.getStatus() == AsyncTask.Status.RUNNING) {
                progressDialog = ProgressDialog.show(this, "",
                        this.getString(R.string.decrypting_db));
            }
        }
    }

    public ProgressDialog getProgressDialog() {
        return this.progressDialog;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.master_password_open_button:
                openDatabase();
                break;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // If the activity is being stopped while the progress dialog is
        // displayed (e.g. the screen is being rotated) dismiss it here.
        // We'll display it again in the new activity.
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance () {
        // Disassociate the background task from the activity. A new one will
        // be created imminently.
        if (decryptDatabaseTask != null) {
            decryptDatabaseTask.setActivity(null);
        }
        return decryptDatabaseTask;
    }

    private void openDatabase() {
        // Show the progress dialog
        progressDialog = ProgressDialog.show(
                this, "", this.getString(R.string.decrypting_db));

        // In certain situations (which I'm not clear on) databaseFileToDecrypt
        // can be null. Check here to ensure we don't end up crashing.
        if (EnterMasterPassword.databaseFileToDecrypt == null) {
            Log.w("EnterMasterPassword", "databaseFileToDecrypt was unexpectedly null");
            EnterMasterPassword.databaseFileToDecrypt = Utilities.getDatabaseFile(this);
        }

        // Create and execute the background task that will decrypt the db
        decryptDatabaseTask = new DecryptDatabase(this);
        decryptDatabaseTask.execute();
    }

    public EditText getPasswordField() {
        return passwordField;
    }

    // Show a progress dialog and then start the decrypting of the
    // db in a separate thread
    private static class DecryptDatabase extends AsyncTask<Void, Void, Integer> {

        private static final int ERROR_INVALID_PASSWORD = 1;
        private static final int ERROR_GENERIC_ERROR = 2;

        private EnterMasterPassword activity;
        private String errorMessage;
        private char[] password;

        public DecryptDatabase(EnterMasterPassword activity) {
            this.activity = activity;
        }

        @Override
        protected void onPreExecute() {
            password = activity.getPasswordField().getText().toString().toCharArray();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int errorCode = 0;
            try {
                // Attempt to decrypt the database
                decryptedPasswordDatabase = 
                        new PasswordDatabase(databaseFileToDecrypt, password);
            } catch (InvalidPasswordException e) {
                Log.e("EnterMasterPassword", e.getMessage(), e);
                errorMessage = e.getMessage();
                errorCode = ERROR_INVALID_PASSWORD;
            } catch (IOException e) {
                Log.e("EnterMasterPassword", e.getMessage(), e);
                errorMessage = e.getMessage();
                errorCode = ERROR_GENERIC_ERROR;
            } catch (GeneralSecurityException e) {
                Log.e("EnterMasterPassword", e.getMessage(), e);
                errorMessage = e.getMessage();
                errorCode = ERROR_GENERIC_ERROR;
            } catch (ProblemReadingDatabaseFile e) {
                Log.e("EnterMasterPassword", e.getMessage(), e);
                errorMessage = e.getMessage();
                errorCode = ERROR_GENERIC_ERROR;
            }
            
            return errorCode;
        }

        @Override
        protected void onPostExecute(Integer result) {
            activity.getProgressDialog().dismiss();

            switch (result) {
                case ERROR_INVALID_PASSWORD:
                    Toast toast = Toast.makeText(activity, R.string.invalid_password, Toast.LENGTH_SHORT);
                    toast.show();

                    // Set focus back to the password and select all characters
                    activity.getPasswordField().requestFocus();
                    activity.getPasswordField().selectAll();

                    break; 
                case ERROR_GENERIC_ERROR:
                    String message = String.format(activity.getText(R.string.generic_error_with_message).toString(), errorMessage);
                    UIUtilities.showToast(activity, message, true);
                    break;
                default :
                    activity.setResult(RESULT_OK);
                    activity.finish();
                    break;
            }
        }

        private void setActivity(EnterMasterPassword activity) {
            this.activity = activity;
        }

    }

}

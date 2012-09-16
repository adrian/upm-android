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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class AppEntryActivity extends Activity {

    private static final int NEW_DATABASE_DIALOG = 1;

    private static final int REQ_CODE_ENTER_PASSWORD = 0;
    private static final int REQ_CODE_CREATE_DB = 1;
    private static final int REQ_CODE_DOWNLOAD_DB = 2;
    private static final int REQ_CODE_OPEN_DB = 3;
    private static final int REQ_CODE_GET_DB_FILE = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (databaseFileExists()) {
            // If databaseFileToDecrypt is null then UPM is just starting so
            // show the EnterMasterPassword activity
            if (EnterMasterPassword.databaseFileToDecrypt == null) {
                EnterMasterPassword.databaseFileToDecrypt = Utilities.getDatabaseFile(this);
            }
            // savedInstanceState will be null if the app is just starting so
            // in this case we should display the EnterMasterPassword activity
            if (savedInstanceState == null) {
                Intent i = new Intent(AppEntryActivity.this, EnterMasterPassword.class);
                startActivityForResult(i, REQ_CODE_ENTER_PASSWORD);
            }
        } else {
            showDialog(NEW_DATABASE_DIALOG);
        }
    }


    /**
     * We can get here from either EnterMasterPassword, CreateNewDatabase or
     * DownloadRemoteDatabase. If all went well in one of those activities 
     * there should be a PasswordDatabase on the Application.
     * If there is proceed to FullAccountList.
     * If there isn't show the "New Database" dialog.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
        case REQ_CODE_ENTER_PASSWORD:
            if (resultCode == RESULT_OK) {
                ((UPMApplication) getApplication()).setPasswordDatabase(EnterMasterPassword.decryptedPasswordDatabase);
                Intent i = new Intent(AppEntryActivity.this, FullAccountList.class);
                startActivityForResult(i, REQ_CODE_OPEN_DB);
            } else {
                // User clicked Back from the EnterMasterPassword activity so quit
                finish();
            }
            break;
        case REQ_CODE_CREATE_DB:
            if (resultCode == RESULT_OK) {
                Intent i = new Intent(AppEntryActivity.this, FullAccountList.class);
                startActivityForResult(i, REQ_CODE_OPEN_DB);
            }
            break;
        case REQ_CODE_DOWNLOAD_DB:
        case REQ_CODE_GET_DB_FILE:
            if (resultCode == RESULT_OK) {
                Intent i = new Intent(AppEntryActivity.this, FullAccountList.class);
                startActivityForResult(i, REQ_CODE_OPEN_DB);
            }
            break;
        case REQ_CODE_OPEN_DB:
            if (resultCode == FullAccountList.RESULT_ENTER_PW) {
                EnterMasterPassword.databaseFileToDecrypt = Utilities.getDatabaseFile(this);
                Intent i = new Intent(AppEntryActivity.this, EnterMasterPassword.class);
                startActivityForResult(i, REQ_CODE_ENTER_PASSWORD);
            } else if (resultCode == FullAccountList.RESULT_EXIT) {
                // databaseFileToDecrypt is used in AppEntryActivity to indicate
                // weather EnterMasterPassword needs to be shown.
                EnterMasterPassword.databaseFileToDecrypt = null;
                finish();
            }
            break;
        }
    }

    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;

        switch(id) {
            case NEW_DATABASE_DIALOG:
                dialog = new Dialog(this);
                dialog.setContentView(R.layout.new_database_options);
                dialog.setTitle(R.string.new_database);

                Button newDatabase = (Button) dialog.findViewById(R.id.new_database);
                newDatabase.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(AppEntryActivity.this, CreateNewDatabase.class);
                        startActivityForResult(i, REQ_CODE_CREATE_DB);
                    }
                });

                Button restoreDatabase = (Button) dialog.findViewById(R.id.restore_database);
                restoreDatabase.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        File restoreFile = new File(Environment.getExternalStorageDirectory(), Utilities.DEFAULT_DATABASE_FILE);
                        if (restoreFile.exists()) {
                            ((UPMApplication) getApplication()).restoreDatabase(AppEntryActivity.this);
                            // Clear the activity stack and bring up AppEntryActivity
                            // This is effectively restarting the application
                            Intent i = new Intent(AppEntryActivity.this, AppEntryActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(i);
                            finish();
                        } else {
                            String messageRes = getString(R.string.restore_file_doesnt_exist);
                            String message = String.format(messageRes, restoreFile.getAbsolutePath());
                            Toast.makeText(AppEntryActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }
                });

                Button openRemoteDatabase = (Button) dialog.findViewById(R.id.open_remote_database);
                openRemoteDatabase.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(AppEntryActivity.this, DownloadRemoteDatabase.class);
                        startActivityForResult(i, REQ_CODE_DOWNLOAD_DB);
                    }
                });

                Button retrieveFromDropboxButton = (Button) dialog.findViewById(R.id.retrieve_from_dropbox);
                retrieveFromDropboxButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(AppEntryActivity.this, SelectDatabaseFromDropboxActivity.class);
                        startActivityForResult(i, REQ_CODE_GET_DB_FILE);
                    }
                });

                // Close this Activity if the dialog is cancelled 
                dialog.setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });
                break;
        }
        return dialog;
    }

    private boolean databaseFileExists() {
        return Utilities.getDatabaseFile(this).exists();
    }

}

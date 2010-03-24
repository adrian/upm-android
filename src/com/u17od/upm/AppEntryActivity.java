/*
 * $Id$
 * 
 * Universal Password Manager
 * Copyright (c) 2010 Adrian Smith
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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class AppEntryActivity extends Activity {

    private static final int NEW_DATABASE_DIALOG = 1;

    private static final int REQ_CODE_ENTER_PASSWORD = 0;
    private static final int REQ_CODE_CREATE_DB = 1;
    private static final int REQ_CODE_DOWNLOAD_DB = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (databaseFileExists()) {
            EnterMasterPassword.databaseFileToDecrypt = Utilities.getDatabaseFile(this);
            Intent i = new Intent(AppEntryActivity.this, EnterMasterPassword.class);
            startActivityForResult(i, REQ_CODE_ENTER_PASSWORD);
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
                startActivity(i);
            }
            finish();
            break;
        case REQ_CODE_CREATE_DB:
            if (resultCode == RESULT_OK) {
                Intent i = new Intent(AppEntryActivity.this, FullAccountList.class);
                startActivity(i);
                finish();
            }
        case REQ_CODE_DOWNLOAD_DB:
            if (resultCode == RESULT_OK) {
                Intent i = new Intent(AppEntryActivity.this, FullAccountList.class);
                startActivity(i);
                finish();
            }
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

                Button openRemoteDatabase = (Button) dialog.findViewById(R.id.open_remote_database);
                openRemoteDatabase.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(AppEntryActivity.this, DownloadRemoteDatabase.class);
                        startActivityForResult(i, REQ_CODE_DOWNLOAD_DB);
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

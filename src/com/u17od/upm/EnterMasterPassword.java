/*
 * $Id: EnterMasterPassword.java 37 2010-01-27 19:16:42Z Adrian $
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

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.u17od.upm.crypto.InvalidPasswordException;
import com.u17od.upm.database.PasswordDatabase;

/**
 * This Activity is responsible for prompting the user to enter their master
 * password and then decrypting the database. If the correct password is entered
 * then the AccountList Activity is loaded.
 */
public class EnterMasterPassword extends Activity implements OnClickListener, Runnable {

    public static final String DATABASE_FILE = "upm.db";  // the name of the db file in the filesystem

    private static final int GENERIC_ERROR_DIALOG = 1;     // id of the dialog used to display generic errors
    private static final int NEW_DATABASE_DIALOG = 2;     // dialog asking to download or create new db

    private static final int WHAT_INVALID_PASSWORD = 1;
    private static final int WHAT_GENERIC_ERROR = 2;
    
    private ProgressDialog progressDialog;
    private EditText passwordField;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        
        //Debug.startMethodTracing("upm");

        super.onCreate(savedInstanceState);

        if (databaseFileExists()) {
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
        } else {
        	showDialog(NEW_DATABASE_DIALOG);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.master_password_open_button:
                openDatabase();
                break;
        }
    }

    private void openDatabase() {
        // Show a progress dialog and then start the decrypting of the
        // db in a separate thread
        progressDialog = ProgressDialog.show(this, "", getString(R.string.decrypting_db));
        new Thread(this).start();
    }

    @Override
    public void run() {
        Message msg = Message.obtain();
        try {
            // Attempt to decrypt the database
            char[] password = passwordField.getText().toString().toCharArray();
            PasswordDatabase passwordDatabase = new PasswordDatabase(new File(getFilesDir(), DATABASE_FILE), password);

            // Make the PasswordDatabase available to the rest of the application
            // by storing it on the application
            ((UPMApplication) getApplication()).setPasswordDatabase(passwordDatabase);

            Intent i = new Intent(EnterMasterPassword.this, FullAccountList.class);
            startActivity(i);
        } catch (InvalidPasswordException e) {
            msg.what = WHAT_INVALID_PASSWORD;
        } catch (Exception e) {
            Log.e("EnterMasterPassword", "Problem decrypting database", e);
            msg.what = WHAT_GENERIC_ERROR;
        } finally {
            handler.sendMessage(msg);
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            progressDialog.dismiss();

            switch (msg.what) {
                case WHAT_INVALID_PASSWORD:
                    Toast toast = Toast.makeText(EnterMasterPassword.this, R.string.invalid_password, Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.TOP, 0, 0);
                    toast.show();
                    
                    // Set focus back to the password and select all characters
                    passwordField.requestFocus();
                    passwordField.selectAll();
                    
                    break; 
                case WHAT_GENERIC_ERROR:
                    showDialog(GENERIC_ERROR_DIALOG);
                    break; 
            }
        }
    };

    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
            case GENERIC_ERROR_DIALOG:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.generic_error)
                    .setNeutralButton(R.string.ok_label, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish(); // Close the application
                        }
                    });
                dialog = builder.create();
                break;
            case NEW_DATABASE_DIALOG:
                dialog = new Dialog(this);
                dialog.setCancelable(false);
                dialog.setContentView(R.layout.new_database_options);
                dialog.setTitle(R.string.new_database);
                
                Button newDatabase = (Button) dialog.findViewById(R.id.new_database);
                newDatabase.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Start the CreateNewDatabase activity and remove this one from the stack
                        Intent i = new Intent(EnterMasterPassword.this, CreateNewDatabase.class);
                        startActivity(i);
                        finish();
                    }
                });

                Button openRemoteDatabase = (Button) dialog.findViewById(R.id.open_remote_database);
                openRemoteDatabase.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Start the DownloadRemoteDatabase activity
                        Intent i = new Intent(EnterMasterPassword.this, DownloadRemoteDatabase.class);
                        startActivity(i);
                    }
                });

            	break;
            default:
                dialog = null;
        }
        return dialog;
    }

    private boolean databaseFileExists() {
        return new File(getFilesDir(), DATABASE_FILE).exists();
    }

}

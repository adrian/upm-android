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
import java.io.IOException;
import java.security.GeneralSecurityException;

import android.app.Activity;
import android.app.ProgressDialog;
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
import com.u17od.upm.database.ProblemReadingDatabaseFile;

/**
 * This Activity is responsible for prompting the user to enter their master
 * password and then decrypting the database. If the correct password is entered
 * then the AccountList Activity is loaded.
 */
public class EnterMasterPassword extends Activity implements OnClickListener, Runnable {

    private static final int GENERIC_ERROR_DIALOG = 1;     // id of the dialog used to display generic errors

    private static final int WHAT_INVALID_PASSWORD = 1;
    private static final int WHAT_GENERIC_ERROR = 2;

    private static final String BUNDLE_ERROR_MESSAGE = "BUNDLE_ERROR_MESSAGE";

    public static PasswordDatabase decryptedPasswordDatabase;
    public static File databaseFileToDecrypt;

    private ProgressDialog progressDialog;
    private EditText passwordField;

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
            decryptedPasswordDatabase = new PasswordDatabase(databaseFileToDecrypt, password);

            setResult(RESULT_OK);
            finish();
        } catch (InvalidPasswordException e) {
            msg.what = WHAT_INVALID_PASSWORD;
            msg.getData().putString(BUNDLE_ERROR_MESSAGE, e.getMessage());
        } catch (IOException e) {
            Log.e("EnterMasterPassword", e.getMessage(), e);
            msg.what = WHAT_GENERIC_ERROR;
            msg.getData().putString(BUNDLE_ERROR_MESSAGE, e.getMessage());
        } catch (GeneralSecurityException e) {
            Log.e("EnterMasterPassword", e.getMessage(), e);
            msg.what = WHAT_GENERIC_ERROR;
            msg.getData().putString(BUNDLE_ERROR_MESSAGE, e.getMessage());
        } catch (ProblemReadingDatabaseFile e) {
            Log.e("EnterMasterPassword", e.getMessage(), e);
            msg.what = WHAT_GENERIC_ERROR;
            msg.getData().putString(BUNDLE_ERROR_MESSAGE, e.getMessage());
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
                    String.format(getText(R.string.generic_error_with_message).toString(), msg.getData().getString(BUNDLE_ERROR_MESSAGE));
                    UIUtilities.showToast(EnterMasterPassword.this, R.string.generic_error_with_message, true);
                    showDialog(GENERIC_ERROR_DIALOG);
                    break; 
            }
        }
    };

}

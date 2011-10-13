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

import java.io.IOException;
import java.security.GeneralSecurityException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.u17od.upm.crypto.InvalidPasswordException;
import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.database.ProblemReadingDatabaseFile;

public class ChangeMasterPassword extends Activity implements OnClickListener {

    private EditText existingPassword;
    private EditText newPassword;
    private EditText newPasswordConfirmation;
    private ProgressDialog progressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_master_password);
    
        existingPassword = (EditText) findViewById(R.id.existing_master_password);
        newPassword = (EditText) findViewById(R.id.new_master_password);
        newPasswordConfirmation = (EditText) findViewById(R.id.new_master_password_confirm);
    
        // Make this class the listener for the click event on the OK button
        Button okButton = (Button) findViewById(R.id.change_master_password_ok_button);
        okButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.change_master_password_ok_button:
            // Check the two new password match
            if (existingPassword.getText().length() == 0) {
                Toast.makeText(this, R.string.request_master_password, Toast.LENGTH_SHORT).show();
            } else if (!newPassword.getText().toString().equals(newPasswordConfirmation.getText().toString())) {
                Toast.makeText(this, R.string.new_passwords_dont_match, Toast.LENGTH_SHORT).show();
            } else if (newPassword.getText().length() < CreateNewDatabase.MIN_PASSWORD_LENGTH) {
                String passwordTooShortResStr = getString(R.string.password_too_short);
                String resultsText = String.format(passwordTooShortResStr, CreateNewDatabase.MIN_PASSWORD_LENGTH);
                Toast.makeText(this, resultsText, Toast.LENGTH_SHORT).show();
            } else {
                new DecryptAndSaveDatabaseAsyncTask().execute();
            }
            break;
        }
    }

    private PasswordDatabase getPasswordDatabase() {
        return ((UPMApplication) getApplication()).getPasswordDatabase();
    }

    public class DecryptAndSaveDatabaseAsyncTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(ChangeMasterPassword.this, "", getString(R.string.saving_database));
        }
        
        @Override
        protected Integer doInBackground(Void... params) {
            Integer messageCode = null;
            try {
                // Attempt to decrypt the database so-as to test the password
                char[] password = existingPassword.getText().toString().toCharArray();
                new PasswordDatabase(Utilities.getDatabaseFile(ChangeMasterPassword.this), password);

                // Re-encrypt the database
                getPasswordDatabase().changePassword(newPassword.getText().toString().toCharArray());
                getPasswordDatabase().save();

                // We're finished with this activity so take it off the stack
                finish();
            } catch (InvalidPasswordException e) {
                Log.e("ChangeMasterPassword", e.getMessage(), e);
                messageCode = R.string.invalid_password;
            } catch (IOException e) {
                Log.e("ChangeMasterPassword", e.getMessage(), e);
                messageCode = R.string.generic_error;
            } catch (GeneralSecurityException e) {
                Log.e("ChangeMasterPassword", e.getMessage(), e);
                messageCode = R.string.generic_error;
            } catch (ProblemReadingDatabaseFile e) {
                Log.e("ChangeMasterPassword", e.getMessage(), e);
                messageCode = R.string.generic_error;
            }
            
            return messageCode;
        }
        
        protected void onPostExecute(Integer messageCode) {
            progressDialog.dismiss();

            if (messageCode != null) {
                Toast.makeText(ChangeMasterPassword.this, messageCode, Toast.LENGTH_SHORT).show();
                if (messageCode == R.string.invalid_password) {
                    // Set focus back to the password and select all characters
                    existingPassword.requestFocus();
                    existingPassword.selectAll();
                }
            }
            
        }

    }

}

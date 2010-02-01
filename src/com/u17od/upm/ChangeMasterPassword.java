/*
 * $Id: ChangeMasterPassword.java 37 2010-01-27 19:16:42Z Adrian $
 * 
 * Universal Password Manager
 * Copyright (C) 2005 Adrian Smith
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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.u17od.upm.crypto.InvalidPasswordException;
import com.u17od.upm.database.PasswordDatabase;

public class ChangeMasterPassword extends Activity implements OnClickListener, Runnable {

	private static final int GENERIC_ERROR_DIALOG = 1;     // id of the dialog used to display generic errors

	private static final int WHAT_INVALID_PASSWORD = 1;
	private static final int WHAT_GENERIC_ERROR = 2;

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
				// Show a dialog informing the user the db in being decrypted
				// Start a new thread to decrypt the database
				progressDialog = ProgressDialog.show(this, "", getString(R.string.saving_database));
				new Thread(this).start();
			}
			break;
		}
	}

	@Override
	public void run() {
		Message msg = Message.obtain();
		try {
			// Attempt to decrypt the database
			char[] password = existingPassword.getText().toString().toCharArray();
			new PasswordDatabase(new File(getFilesDir(), EnterMasterPassword.DATABASE_FILE), password);

			// Re-encrypt the database
			getPasswordDatabase().changePassword(newPassword.getText().toString().toCharArray());
			getPasswordDatabase().save();

			// We're finished with this activity so take it off the stack
			finish();
		} catch (InvalidPasswordException e) {
			msg.what = WHAT_INVALID_PASSWORD;
		} catch (Exception e) {
			Log.e("ChangeMasterPassword", "Problem decrypting/encrypting the database", e);
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
				Toast.makeText(ChangeMasterPassword.this, R.string.invalid_password, Toast.LENGTH_SHORT).show();
				
				// Set focus back to the password and select all characters
				existingPassword.requestFocus();
				existingPassword.selectAll();
				
				break; 
			case WHAT_GENERIC_ERROR:
				showDialog(GENERIC_ERROR_DIALOG);
				break; 
			}
		}
    };

    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
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
        }
        return dialog;
    }

	private PasswordDatabase getPasswordDatabase() {
		return ((UPMApplication) getApplication()).getPasswordDatabase();
	}

}

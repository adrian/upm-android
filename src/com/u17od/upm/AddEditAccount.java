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
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.u17od.upm.database.AccountInformation;
import com.u17od.upm.database.PasswordDatabase;

public class AddEditAccount extends Activity implements OnClickListener {

    private static final int GENERIC_ERROR_DIALOG = 1;

    public static final String MODE = "MODE";
    public static final int EDIT_MODE = 1;
    public static final int ADD_MODE = 2;

    public static AccountInformation accountToEdit;

    private int mode;

    private Button saveButton;
    private EditText accountName;
    private EditText userid;
    private EditText password;
    private EditText url;
    private EditText notes;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_edit_account_details);

        saveButton = (Button) findViewById(R.id.save_button);
        saveButton.setOnClickListener(this);

        ((Button) findViewById(R.id.cancel_button)).setOnClickListener(this);

        accountName = (EditText) findViewById(R.id.account_name);
        userid = (EditText) findViewById(R.id.account_userid);
        password = (EditText) findViewById(R.id.account_password);
        url = (EditText) findViewById(R.id.account_url);
        notes = (EditText) findViewById(R.id.account_notes);

        // Were we called to Add/Edit an Account
        Bundle extras = getIntent().getExtras();
        mode = extras.getInt(MODE);

        // Set the title based on weather we were called to Edit/Add
        if (mode == EDIT_MODE) {
            setTitle(getString(R.string.edit_account));

            // Populate the form with the account to edit
            accountName.setText(accountToEdit.getAccountName());
            userid.setText(new String(accountToEdit.getUserId()));
            password.setText(new String(accountToEdit.getPassword()));
            url.setText(new String(accountToEdit.getUrl()));
            notes.setText(new String(accountToEdit.getNotes()));
        } else { // must be add
            setTitle(getString(R.string.add_account));
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.save_button:
            String accountNameStr = accountName.getText().toString();

            // Ensure all the required data has been entered before saving the
            // account
            if (accountNameStr.trim().length() == 0) {
                Toast.makeText(this, R.string.account_name_required_error, Toast.LENGTH_SHORT).show();
            } else {
                
                // If editing this account then ensure another account doesn't exist with this same name
                if (mode == EDIT_MODE) {

                    AccountInformation secondAccount = getPasswordDatabase().getAccount(accountNameStr);
                    if (secondAccount != null && secondAccount != accountToEdit) {
                        Toast.makeText(this, getString(R.string.account_already_exists_error),  Toast.LENGTH_SHORT).show();
                    } else {
                        saveAccount(accountNameStr);
                    }

                } else { // must be adding account

                    // Check if an account with this name already exists
                    if (getPasswordDatabase().getAccount(accountNameStr) != null) {
                        Toast.makeText(this, getString(R.string.account_already_exists_error),  Toast.LENGTH_SHORT).show();
                    } else {
                        saveAccount(accountNameStr);
                    }
                }
            }

            break;
        case R.id.cancel_button:
            this.finish();
            break;
        }
    }

    private void saveAccount(String accountName) {
        byte[] useridBytes = userid.getText().toString().getBytes();
        byte[] passwordBytes = password.getText().toString().getBytes();
        byte[] urlBytes = url.getText().toString().getBytes();
        byte[] notesBytes = notes.getText().toString().getBytes();

        AccountInformation ai = new AccountInformation(
                accountName, useridBytes,
                passwordBytes, urlBytes, notesBytes);
        
        // If editing an account then delete the exiting one before adding it again
        if (mode == EDIT_MODE) {
            getPasswordDatabase().deleteAccount(accountToEdit.getAccountName());
            // Put the edited account back on the ViewAccountDetails
            // activity so that the view can be re-populated with the
            // edited details
            ViewAccountDetails.account = ai;
        }

        getPasswordDatabase().addAccount(ai);
        new SaveDatabaseAsyncTask(this, new Callback() {
            @Override
            public void execute() {
                AddEditAccount.this.finish();
            }
        }).execute(getPasswordDatabase());
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;

        switch(id) {
            case GENERIC_ERROR_DIALOG:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.generic_error)
                    .setNeutralButton(R.string.ok_label, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
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

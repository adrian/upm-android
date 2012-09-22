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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
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

    public static final String ACCOUNT_TO_EDIT = "ACCOUNT_TO_EDIT";

    public static final int EDIT_ACCOUNT_RESULT_CODE_TRUE = 25;
    public static final int EDIT_ACCOUNT_REQUEST_CODE = 223;
    public static final int OPEN_DATABASE_REQUEST_CODE = 225;

    private String accountToEdit;
    private int mode;

    private Button saveButton;
    private EditText accountName;
    private EditText userid;
    private EditText password;
    private EditText url;
    private EditText notes;
    private String originalAccountName;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Utilities.VERSION.SDK_INT >= Utilities.VERSION_CODES.HONEYCOMB) {
            getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);
        }
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
        accountToEdit = extras.getString(ACCOUNT_TO_EDIT);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getPasswordDatabase() == null) {
            // If we don't have a database (maybe UPM's process was terminated
            // since we were last here) we need to show the EnterMasterPassword
            // activity so the user can enter their master password and open
            // the password database.
            EnterMasterPassword.databaseFileToDecrypt = Utilities.getDatabaseFile(this);
            Intent enterMasterPasswordIntent = new Intent(this, EnterMasterPassword.class);
            startActivityForResult(enterMasterPasswordIntent, OPEN_DATABASE_REQUEST_CODE);
        } else {

            // Set the title based on weather we were called to Edit/Add
            if (mode == EDIT_MODE) {
                setTitle(getString(R.string.edit_account));

                AccountInformation accountToEdit =
                        getPasswordDatabase().getAccount(this.accountToEdit);

                // Populate the on-screen fields. If accountToEdit should happen
                // to be null (for some unknown reason) close the activity to
                // return to the FullAccountList.
                if (accountToEdit != null) {
                    originalAccountName = accountToEdit.getAccountName();

                    // Populate the form with the account to edit
                    accountName.setText(accountToEdit.getAccountName());
                    userid.setText(new String(accountToEdit.getUserId()));
                    password.setText(new String(accountToEdit.getPassword()));
                    url.setText(new String(accountToEdit.getUrl()));
                    notes.setText(new String(accountToEdit.getNotes()));
                } else {
                    Log.w("AddEditAccount", "accountToEdit was unexpectedly null");
                    this.finish();
                }
            } else { // must be add
                setTitle(getString(R.string.add_account));
            }

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        /*
         * If the database was successfully opened then make it available
         * on the Application
         */
        if (requestCode == OPEN_DATABASE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                ((UPMApplication) getApplication()).setPasswordDatabase(EnterMasterPassword.decryptedPasswordDatabase);
            }
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

                    AccountInformation accountToEdit =
                            getPasswordDatabase().getAccount(this.accountToEdit);

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

    private void saveAccount(final String accountName) {
        String useridBytes = userid.getText().toString();
        String passwordBytes = password.getText().toString();
        String urlBytes = url.getText().toString();
        String notesBytes = notes.getText().toString();

        AccountInformation ai = new AccountInformation(
                accountName, useridBytes,
                passwordBytes, urlBytes, notesBytes);
        
        // If editing an account then delete the exiting one before adding it again
        if (mode == EDIT_MODE) {
            getPasswordDatabase().deleteAccount(this.accountToEdit);
            // Put the edited account back on the ViewAccountDetails
            // activity so that the view can be re-populated with the
            // edited details
            ViewAccountDetails.account = ai;
        }

        getPasswordDatabase().addAccount(ai);
        new SaveDatabaseAsyncTask(this, new Callback() {
            @Override
            public void execute() {
                // If the account name has changed or we're added a new account
                // then pass back a value instructing the FullAccountList to
                // refresh the list of accounts
                if (!accountName.equals(originalAccountName) || mode == ADD_MODE) {
                    setResult(EDIT_ACCOUNT_RESULT_CODE_TRUE);
                }
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

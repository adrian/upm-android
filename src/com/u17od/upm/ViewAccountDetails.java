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
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.u17od.upm.database.AccountInformation;
import com.u17od.upm.database.PasswordDatabase;

public class ViewAccountDetails extends Activity {

    public static AccountInformation account;

    private static final int CONFIRM_DELETE_DIALOG = 0;
    public static final int VIEW_ACCOUNT_REQUEST_CODE = 224;

    private int editAccountResultCode = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Utilities.VERSION.SDK_INT >= Utilities.VERSION_CODES.HONEYCOMB) {
            getWindow().setFlags(LayoutParams.FLAG_SECURE, LayoutParams.FLAG_SECURE);
        }
        setContentView(R.layout.view_account_details);
    }

    /**
     * This method is called when returning from the edit activity. Since the
     * account details may have been changed we should repopulate the view 
     */
    @Override
    protected void onResume() {
        super.onResume();
        // If the account is null then finish (may be null because activity was
        // recreated since it was last visible
        if (account == null) {
            finish();
        } else {
            populateView();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.account, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean optionConsumed = false;

        switch (item.getItemId()) {
        case R.id.edit:
            if (Utilities.isSyncRequired(this)) {
                UIUtilities.showToast(this, R.string.sync_required);
            } else {
                Intent i = new Intent(ViewAccountDetails.this, AddEditAccount.class);
                i.putExtra(AddEditAccount.MODE, AddEditAccount.EDIT_MODE);
                i.putExtra(AddEditAccount.ACCOUNT_TO_EDIT, account.getAccountName());
                startActivityForResult(i, AddEditAccount.EDIT_ACCOUNT_REQUEST_CODE);
            }
            break;
        case R.id.delete:
            if (Utilities.isSyncRequired(this)) {
                UIUtilities.showToast(this, R.string.sync_required);
            } else {
                showDialog(CONFIRM_DELETE_DIALOG);
            }
            break;
        }

        return optionConsumed;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;

        switch(id) {
        case CONFIRM_DELETE_DIALOG:
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Are you sure?")
                .setTitle("Confirm Delete")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        getPasswordDatabase().deleteAccount(account.getAccountName());
                        final String accountName = account.getAccountName();

                        new SaveDatabaseAsyncTask(ViewAccountDetails.this, new Callback() {
                            @Override
                            public void execute() {
                                String message = String.format(getString(R.string.account_deleted), accountName);
                                Toast.makeText(ViewAccountDetails.this, message, Toast.LENGTH_SHORT).show();
                                // Set this flag so that when we're returned to the FullAccountList
                                // activity the list is refreshed
                                ViewAccountDetails.this.setResult(AddEditAccount.EDIT_ACCOUNT_RESULT_CODE_TRUE);
                                finish();
                            }
                        }).execute(getPasswordDatabase());
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
            dialog = builder.create();
        }

        return dialog;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch(requestCode) {
            case AddEditAccount.EDIT_ACCOUNT_REQUEST_CODE:
                editAccountResultCode = resultCode;
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // If the back button is pressed pass back the edit account flag
        // This is used to indicate if the list of account names on 
        // FullAccountList needs to be refreshed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            setResult(editAccountResultCode);
        }
        return super.onKeyDown(keyCode, event);
    } 

    private void populateView() {
        TextView accountNameTextView = (TextView) findViewById(R.id.account_name);
        accountNameTextView.setText(account.getAccountName());

        TextView accountUseridTextView = (TextView) findViewById(R.id.account_userid);
        accountUseridTextView.setText(new String(account.getUserId()));

        TextView accountPasswordTextView = (TextView) findViewById(R.id.account_password);
        accountPasswordTextView.setText(new String(account.getPassword()));

        TextView accountURLTextView = (TextView) findViewById(R.id.account_url);
        accountURLTextView.setText(new String(account.getUrl()));

        TextView accountNotesTextView = (TextView) findViewById(R.id.account_notes);
        accountNotesTextView.setText(new String(account.getNotes()));
    }

    private PasswordDatabase getPasswordDatabase() {
        return ((UPMApplication) getApplication()).getPasswordDatabase();
    }

}

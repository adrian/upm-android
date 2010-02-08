/*
 * $Id: FullAccountList.java 37 2010-01-27 19:16:42Z Adrian $
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class FullAccountList extends AccountsList {

    private static final int CONFIRM_RESTORE_DIALOG = 0;
    private static final int CONFIRM_OVERWRITE_BACKUP_FILE = 1;
    private static final int DIALOG_ABOUT = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    public void onResume() {
        super.onResume();
        setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, getPasswordDatabase().getAccountNames()));
        getListView().setOnItemLongClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean optionConsumed = false;

        switch (item.getItemId()) {
            case R.id.search:
                onSearchRequested();
                optionConsumed = true;
                break;
            case R.id.add:
                Intent i = new Intent(FullAccountList.this, AddEditAccount.class);
                i.putExtra(AddEditAccount.MODE, AddEditAccount.ADD_MODE);
                startActivity(i);
                break;
            case R.id.change_master_password:
                startActivity(new Intent(FullAccountList.this, ChangeMasterPassword.class));
                break;
            case R.id.restore:
                // Check to ensure there's a file to restore
                File restoreFile = new File(Environment.getExternalStorageDirectory(), EnterMasterPassword.DATABASE_FILE);
                if (restoreFile.exists()) {
                    showDialog(CONFIRM_RESTORE_DIALOG);
                } else {
                    String messageRes = getString(R.string.restore_file_doesnt_exist);
                    String message = String.format(messageRes, restoreFile.getAbsolutePath());
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.backup:
                // If there's already a backup file prompt the user if they want to overwrite
                File backupFile = new File(Environment.getExternalStorageDirectory(), EnterMasterPassword.DATABASE_FILE);
                if (backupFile.exists()) {
                    showDialog(CONFIRM_OVERWRITE_BACKUP_FILE);
                } else {
                    backupDatabase();
                }
                break;
            case R.id.about:
                showDialog(DIALOG_ABOUT);
                break;
        }

        return optionConsumed;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        switch(id) {
        case CONFIRM_RESTORE_DIALOG:
            dialogBuilder.setMessage(getString(R.string.confirm_restore_overwrite))
               .setCancelable(false)
               .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       restoreDatabase();
                       
                        // Send a broadcast event to start the app and then finish this
                        // activity. This needs to be done to reopen the newly
                        // restored db.
                        Intent i = new Intent(FullAccountList.this, RestartApp.class);
                        sendBroadcast(i);
                        finish();
                   }
               })
               .setNegativeButton("No", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                   }
               });
            break;
        case CONFIRM_OVERWRITE_BACKUP_FILE:
            File backupFile = new File(Environment.getExternalStorageDirectory(), EnterMasterPassword.DATABASE_FILE);
            String messageRes = getString(R.string.backup_file_exists);
            String message = String.format(messageRes, backupFile.getAbsolutePath());

            dialogBuilder.setMessage(message)
               .setCancelable(false)
               .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       backupDatabase();
                       Toast.makeText(FullAccountList.this, R.string.backup_successful, Toast.LENGTH_SHORT).show();
                   }
               })
               .setNegativeButton("No", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                   }
               });
            break;
        case DIALOG_ABOUT:
            View v = LayoutInflater.from(this).inflate(R.layout.dialog, null);
            TextView text = (TextView) v.findViewById(R.id.dialogText);
            text.setText(getString(R.string.aboutText));

            dialogBuilder
                .setTitle(R.string.about)
                .setIcon(android.R.drawable.ic_menu_info_details)
                .setPositiveButton(R.string.homepage, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Uri uri = Uri.parse(getString(R.string.homepageUrl));
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    }
                })
                .setNeutralButton(R.string.donate, new OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        Uri uri = Uri.parse(getString(R.string.donateURL));
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    }
                })
                .setNegativeButton(R.string.close, null)
                .setView(v);
            break;
        }

        dialog = dialogBuilder.create();
        return dialog;
    }

    private void restoreDatabase() {
        File fileOnSDCard = new File(Environment.getExternalStorageDirectory(), EnterMasterPassword.DATABASE_FILE);
        File databaseFile = new File(getFilesDir(), EnterMasterPassword.DATABASE_FILE);
        copyFile(fileOnSDCard, databaseFile);
    }

    private void backupDatabase() {
        File fileOnSDCard = new File(Environment.getExternalStorageDirectory(), EnterMasterPassword.DATABASE_FILE);
        File databaseFile = new File(getFilesDir(), EnterMasterPassword.DATABASE_FILE);
        copyFile(databaseFile, fileOnSDCard);
    }

    private void copyFile(File source, File dest) {
        FileChannel sourceChannel = null;
        FileChannel destinationChannel = null;
        try {
            sourceChannel = new FileInputStream(source).getChannel();
            destinationChannel = new FileOutputStream(dest).getChannel();
            destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        } catch (IOException e) {
            Log.e("AccountsList", getString(R.string.file_problem), e);
            Toast.makeText(this, R.string.file_problem, Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (sourceChannel != null) {
                    sourceChannel.close();
                }
                if (destinationChannel != null) {
                    destinationChannel.close();
                }
            } catch (IOException e) {
                Log.e("AccountsList", getString(R.string.file_problem), e);
                Toast.makeText(this, R.string.file_problem, Toast.LENGTH_LONG).show();
            }
        }
    }

}

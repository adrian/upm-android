/*
 * $Id: FullAccountList.java 37 2010-01-27 19:16:42Z Adrian $
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
import java.util.Date;

import javax.crypto.SecretKey;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.AsyncTask;
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

import com.u17od.upm.crypto.InvalidPasswordException;
import com.u17od.upm.database.AccountInformation;
import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.database.ProblemReadingDatabaseFile;
import com.u17od.upm.transport.HTTPTransport;
import com.u17od.upm.transport.TransportException;

public class FullAccountList extends AccountsList {

    private static final int CONFIRM_RESTORE_DIALOG = 0;
    private static final int CONFIRM_OVERWRITE_BACKUP_FILE = 1;
    private static final int DIALOG_ABOUT = 2;
    
    private static final int ENTER_PW_REQUEST_CODE = 222;

    private File downloadedDatabaseFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_CANCELED) {
            UIUtilities.showToast(this, R.string.enter_password_cancalled);
        } else {
            if (requestCode == ENTER_PW_REQUEST_CODE) {
                syncDb(EnterMasterPassword.decryptedPasswordDatabase);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        populateAccountList();
    }

    private void populateAccountList() {
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
                if (Utilities.isSyncRequired(this)) {
                    UIUtilities.showToast(this, R.string.sync_required);
                } else {
                    Intent i = new Intent(FullAccountList.this, AddEditAccount.class);
                    i.putExtra(AddEditAccount.MODE, AddEditAccount.ADD_MODE);
                    startActivity(i);
                }
                break;
            case R.id.change_master_password:
                if (Utilities.isSyncRequired(this)) {
                    UIUtilities.showToast(this, R.string.sync_required);
                } else {
                    startActivity(new Intent(FullAccountList.this, ChangeMasterPassword.class));
                }
                break;
            case R.id.restore:
                // Check to ensure there's a file to restore
                File restoreFile = new File(Environment.getExternalStorageDirectory(), Utilities.DEFAULT_DATABASE_FILE);
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
                File backupFile = new File(Environment.getExternalStorageDirectory(), Utilities.DEFAULT_DATABASE_FILE);
                if (backupFile.exists()) {
                    showDialog(CONFIRM_OVERWRITE_BACKUP_FILE);
                } else {
                    backupDatabase();
                }
                break;
            case R.id.about:
                showDialog(DIALOG_ABOUT);
                break;
            case R.id.sync:
                new RetrieveRemoteDatabase().execute();
                break;
            case R.id.preferences:
                startActivity(new Intent(this, Prefs.class));
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
            File backupFile = new File(Environment.getExternalStorageDirectory(), Utilities.DEFAULT_DATABASE_FILE);
            String messageRes = getString(R.string.backup_file_exists);
            String message = String.format(messageRes, backupFile.getAbsolutePath());

            dialogBuilder.setMessage(message)
               .setCancelable(false)
               .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       backupDatabase();
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
        File fileOnSDCard = new File(Environment.getExternalStorageDirectory(), Utilities.DEFAULT_DATABASE_FILE);
        File databaseFile = Utilities.getDatabaseFile(this);
        ((UPMApplication) getApplication()).copyFile(fileOnSDCard, databaseFile, this);
    }

    private void backupDatabase() {
        File fileOnSDCard = new File(Environment.getExternalStorageDirectory(), Utilities.DEFAULT_DATABASE_FILE);
        File databaseFile = Utilities.getDatabaseFile(this);
        ((UPMApplication) getApplication()).copyFile(databaseFile, fileOnSDCard, this);
        String message = String.format(getString(R.string.backup_complete), fileOnSDCard.getAbsolutePath());
        UIUtilities.showToast(this, message, false);
    }

    /**
     * Check if the downloaded DB is more recent than the current db.
     * If it is the replace the current DB with the downloaded one and reload
     * the accounts listview
     * @param dbDownloadedOnSync
     */
    private void syncDb(PasswordDatabase dbDownloadedOnSync) {
        UPMApplication app = (UPMApplication) getApplication();
        if (dbDownloadedOnSync == null || dbDownloadedOnSync.getRevision() < app.getPasswordDatabase().getRevision()) {
            new UploadDatabase().execute();
        } else if (dbDownloadedOnSync.getRevision() > app.getPasswordDatabase().getRevision()) {
            app.copyFile(downloadedDatabaseFile, Utilities.getDatabaseFile(this), this);
            app.setPasswordDatabase(dbDownloadedOnSync);
            dbDownloadedOnSync.setDatabaseFile(Utilities.getDatabaseFile(this));
            populateAccountList(); // so that the account list is refreshed
            UIUtilities.showToast(this, R.string.new_db_downloaded);
        } else if (dbDownloadedOnSync.getRevision() == app.getPasswordDatabase().getRevision()) {
            UIUtilities.showToast(this, R.string.db_uptodate);
        }
        app.setTimeOfLastSync(new Date());
    }

    private class UploadDatabase extends AsyncTask<Void, Void, Integer> {

        private static final int UPLOAD_OK = 0;
        private static final int UPLOAD_ERROR = 1;
        
        private ProgressDialog progressDialog;
        private String uploadError;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(FullAccountList.this, "", getString(R.string.uploading_database));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int result = UPLOAD_OK;

            String remoteURL = getPasswordDatabase().getDbOptions().getRemoteLocation();
            String remoteURLUsername = null, remoteURLPassword = null;
            String accountWithAuthDetails = getPasswordDatabase().getDbOptions().getAuthDBEntry();
            if (accountWithAuthDetails != null && accountWithAuthDetails.length() > 0) {
                AccountInformation account = getPasswordDatabase().getAccount(accountWithAuthDetails);
                remoteURLUsername = new String(account.getUserId());
                remoteURLPassword = new String(account.getPassword());
            }

            HTTPTransport transport = new HTTPTransport();
            String fileName = getPasswordDatabase().getDatabaseFile().getName();
            try {
                transport.delete(remoteURL, fileName, remoteURLUsername, remoteURLPassword);
                transport.put(remoteURL, getPasswordDatabase().getDatabaseFile(), remoteURLUsername, remoteURLPassword);
            } catch (TransportException e) {
                Log.e("FullAccountList", e.getMessage(), e);
                uploadError = e.getMessage();
                result = UPLOAD_ERROR;
            }

            return result;
        }
     
        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();
            if (result == UPLOAD_OK) {
                UIUtilities.showToast(FullAccountList.this, R.string.db_sync_complete);
            } else {
                String messageRes = getString(R.string.restore_file_doesnt_exist);
                String message = String.format(messageRes, uploadError);
                UIUtilities.showToast(FullAccountList.this, message, true);
            }
        }

    }

    private class RetrieveRemoteDatabase extends AsyncTask<Void, Void, Integer> {

        private static final int PROBLEM_DOWNLOADING_DB = 1;
        private static final int PROBLEM_READING_DB = 2;
        private static final int PROBLEM_DECRYPTING_DB = 3;
        private static final int NOT_UPM_DB = 4;
        private static final int INVALID_PASSWORD = 5;
        private static final int NO_REMOTE_DB = 6;

        private ProgressDialog progressDialog;
        private String errorMessage;
        private PasswordDatabase downloadedDatabase;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(FullAccountList.this, "", getString(R.string.syncing_database));
        }

        @Override
        protected Integer doInBackground(Void... params) {
            int errorCode = 0;

            String remoteURL = getPasswordDatabase().getDbOptions().getRemoteLocation();
            if (remoteURL.equals("")) {
                errorCode = NO_REMOTE_DB;
            } else {
                String remoteFileName = Utilities.getDatabaseFileName(FullAccountList.this);
                String remoteURLUsername = null, remoteURLPassword = null;
                String accountWithAuthDetails = getPasswordDatabase().getDbOptions().getAuthDBEntry();
                if (accountWithAuthDetails != null && accountWithAuthDetails.length() > 0) {
                    AccountInformation account = getPasswordDatabase().getAccount(accountWithAuthDetails);
                    remoteURLUsername = new String(account.getUserId());
                    remoteURLPassword = new String(account.getPassword());
                }
    
                try {
                    HTTPTransport httpTransport = new HTTPTransport();
                    downloadedDatabaseFile = httpTransport.getRemoteFile(remoteURL, remoteFileName, remoteURLUsername, remoteURLPassword);
                    if (downloadedDatabaseFile != null) {
                        SecretKey existingDBSecretKey = getPasswordDatabase().getEncryptionService().getSecretKey();
                        downloadedDatabase = new PasswordDatabase(downloadedDatabaseFile, existingDBSecretKey);
                    }
                } catch (TransportException e) {
                    Log.e("DownloadRemoteDatabase", "Problem downloading database", e);
                    errorMessage = e.getMessage();
                    errorCode = PROBLEM_DOWNLOADING_DB;
                } catch (IOException e) {
                    Log.e("DownloadRemoteDatabase", "Problem reading database", e);
                    errorMessage = e.getMessage();
                    errorCode = PROBLEM_READING_DB;
                } catch (GeneralSecurityException e) {
                    Log.e("DownloadRemoteDatabase", "Problem decrypting database", e);
                    errorMessage = e.getMessage();
                    errorCode = PROBLEM_DECRYPTING_DB;
                } catch (ProblemReadingDatabaseFile e) {
                    Log.e("DownloadRemoteDatabase", "Not a UPM database", e);
                    errorMessage = e.getMessage();
                    errorCode = NOT_UPM_DB;
                } catch (InvalidPasswordException e) {
                    errorCode = INVALID_PASSWORD;
                }
            }
            
            return errorCode;
        }

        @Override
        protected void onPostExecute(Integer result) {
            progressDialog.dismiss();

            switch (result) {
                case 0:
                    syncDb(downloadedDatabase);
                    break;
                case NOT_UPM_DB:
                    UIUtilities.showToast(FullAccountList.this, R.string.not_password_database, true);
                    break;
                case PROBLEM_DOWNLOADING_DB:
                    UIUtilities.showToast(FullAccountList.this, 
                            String.format(getString(R.string.problem_downloading_db), errorMessage),
                            true);
                    break;
                case PROBLEM_DECRYPTING_DB:
                    UIUtilities.showToast(FullAccountList.this,
                            String.format(getString(R.string.problem_decrypying_db), errorMessage),
                            true);
                    break;
                case PROBLEM_READING_DB:
                    UIUtilities.showToast(FullAccountList.this,
                            String.format(getString(R.string.problem_reading_upm_db), errorMessage),
                            true);
                    break;
                case INVALID_PASSWORD:
                    EnterMasterPassword.databaseFileToDecrypt = downloadedDatabaseFile;
                    Intent i = new Intent(FullAccountList.this, EnterMasterPassword.class);
                    startActivityForResult(i, ENTER_PW_REQUEST_CODE);
                    break;
                case NO_REMOTE_DB:
                    UIUtilities.showToast(FullAccountList.this, R.string.no_remote_db);
                    break;
            }
        }

    }

}

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

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import com.u17od.upm.database.PasswordDatabase;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;

public class SaveDatabaseAsyncTask extends AsyncTask<PasswordDatabase, Void, String> {

    private ProgressDialog progressDialog;
    private Activity activity;
    private Callback callback;

    public SaveDatabaseAsyncTask(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    @Override
    protected void onPreExecute() {
        progressDialog = ProgressDialog.show(activity, "", activity.getString(R.string.saving_database));
    }

    @Override
    protected String doInBackground(PasswordDatabase... params) {
        String message = null;

        try {
            params[0].save();
        } catch (IllegalBlockSizeException e) {
            Log.e("SaveDatabaseAsyncTask", e.getMessage(), e);
            message = String.format(activity.getString(R.string.problem_saving_db), e.getMessage());
        } catch (BadPaddingException e) {
            Log.e("SaveDatabaseAsyncTask", e.getMessage(), e);
            message = String.format(activity.getString(R.string.problem_saving_db), e.getMessage());
        } catch (IOException e) {
            Log.e("SaveDatabaseAsyncTask", e.getMessage(), e);
            message = String.format(activity.getString(R.string.problem_saving_db), e.getMessage());
        }

        return message;
    }

    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
            UIUtilities.showToast(activity, result, true);
        }

        progressDialog.dismiss();
        
        callback.execute();
    }

}

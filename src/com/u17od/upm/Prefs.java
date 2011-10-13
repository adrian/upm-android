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

import java.util.ArrayList;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.KeyEvent;

import com.u17od.upm.database.PasswordDatabase;

public class Prefs extends PreferenceActivity implements OnPreferenceChangeListener {

    public static final String PREFS_NAME = "UPMPrefs";
    
    public static final String PREF_TRUSTED_HOSTNAME = "trustedHostname";

    private ListPreference sharedURLAuthPref;
    private EditTextPreference sharedURLPref;
    private EditTextPreference trustedHostnamePref;
    private PasswordDatabase db;
    private boolean saveRequired;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        saveRequired = false;

        // Create the menu items
        addPreferencesFromResource(R.xml.settings);

        // Get a handle to the preference items
        sharedURLAuthPref = (ListPreference) findPreference("shared_url_auth");
        sharedURLPref = (EditTextPreference) findPreference("shared_url");
        trustedHostnamePref = (EditTextPreference) findPreference("trusted_hostname");

        sharedURLAuthPref.setOnPreferenceChangeListener(this);
        sharedURLPref.setOnPreferenceChangeListener(this);
        trustedHostnamePref.setOnPreferenceChangeListener(this);

        // Populate the preferences
        db = ((UPMApplication) getApplication()).getPasswordDatabase();
        sharedURLPref.setText(db.getDbOptions().getRemoteLocation());

        ArrayList<String> accountNamesAL = db.getAccountNames();
        String[] accountNames = new String[accountNamesAL.size() + 1];
        accountNames[0] = "";
        System.arraycopy(accountNamesAL.toArray(), 0, accountNames, 1, accountNamesAL.size());
        sharedURLAuthPref.setEntryValues(accountNames);
        sharedURLAuthPref.setEntries(accountNames);
        sharedURLAuthPref.setValue(db.getDbOptions().getAuthDBEntry());
        
        // Some preferences are stored using Android's SharedPreferences
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String trustedHostname = settings.getString(PREF_TRUSTED_HOSTNAME, "");
        trustedHostnamePref.setText(trustedHostname);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (saveRequired) {
                db.getDbOptions().setRemoteLocation(sharedURLPref.getText());
                db.getDbOptions().setAuthDBEntry(sharedURLAuthPref.getValue());
                new SaveDatabaseAsyncTask(this, new Callback() {
                    @Override
                    public void execute() {
                        Prefs.this.finish();
                    }
                }).execute(db);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    } 

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == sharedURLAuthPref) {
            if (!sharedURLAuthPref.getValue().equals(newValue)) {
                saveRequired = true;
            }
        } else if (preference == sharedURLPref) {
            if (!sharedURLPref.getText().equals(newValue)) {
                saveRequired = true;
            }
        }
        
        return true;
    }

    @Override
    protected void onStop(){
        super.onStop();

       // We need an Editor object to make preference changes.
       // All objects are from android.context.Context
       SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
       SharedPreferences.Editor editor = settings.edit();
       editor.putString(PREF_TRUSTED_HOSTNAME, trustedHostnamePref.getText());

       // Commit the edits!
       editor.commit();
     }

}

package com.u17od.upm;

import java.util.ArrayList;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.view.KeyEvent;

import com.u17od.upm.database.PasswordDatabase;

public class Prefs extends PreferenceActivity implements OnPreferenceChangeListener {

    private ListPreference sharedURLAuthPref;
    private EditTextPreference sharedURLPref;
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

        sharedURLAuthPref.setOnPreferenceChangeListener(this);
        sharedURLPref.setOnPreferenceChangeListener(this);

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
        
        return saveRequired;
    }

}

package com.u17od.upm;

import java.io.IOException;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;

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
        sharedURLAuthPref.setDefaultValue(db.getDbOptions().getAuthDBEntry());
    }

    @Override
    protected void onPause() {
        super.onStop();

        if (saveRequired) {
            db.getDbOptions().setRemoteLocation(sharedURLPref.getText());
            db.getDbOptions().setAuthDBEntry(sharedURLAuthPref.getValue());
            
            try {
                db.save();
            } catch (IllegalBlockSizeException e) {
                Log.e("Prefs", e.getMessage(), e);
                String message = String.format(getString(R.string.problem_saving_db), e.getMessage());
                UIUtilities.showToast(this, message, true);
            } catch (BadPaddingException e) {
                Log.e("Prefs", e.getMessage(), e);
                String message = String.format(getString(R.string.problem_saving_db), e.getMessage());
                UIUtilities.showToast(this, message, true);
            } catch (IOException e) {
                Log.e("Prefs", e.getMessage(), e);
                String message = String.format(getString(R.string.problem_saving_db), e.getMessage());
                UIUtilities.showToast(this, message, true);
            }
        }
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

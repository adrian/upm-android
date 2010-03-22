package com.u17od.upm;

import java.io.IOException;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.Log;

import com.u17od.upm.database.PasswordDatabase;

public class Prefs extends PreferenceActivity {

    private ListPreference sharedURLAuthPref;
    private EditTextPreference sharedURLPref;
    private PasswordDatabase db;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the menu items
        addPreferencesFromResource(R.xml.settings);

        // Get a handle to the preference items
        sharedURLAuthPref = (ListPreference) findPreference("shared_url_auth");
        sharedURLPref = (EditTextPreference) findPreference("shared_url");

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
    protected void onStop() {
        super.onStop();

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

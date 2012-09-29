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

import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;

public class SearchResults extends AccountsList {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_results);
        registerForContextMenu(getListView());
    }

    @Override
    public boolean onSearchRequested() {
        // Returning false here means that if the user can't initiate a search
        // while on the SearchResults page
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        // If the pw database is null then just close the activity.
        if (getPasswordDatabase() == null) {
            finish();
        } else {
            doSearch();
        }
    }

    private void doSearch() {
        final Intent queryIntent = getIntent();
        final String queryAction = queryIntent.getAction();
        if (Intent.ACTION_SEARCH.equals(queryAction)) {
            filterAccountsList(queryIntent.getStringExtra(SearchManager.QUERY));
        }
    }

    private void filterAccountsList(String textToFilterOn) {
        ArrayList<String> allAccountNames = getPasswordDatabase().getAccountNames(); 
        ArrayList<String> filteredAccountNames = new ArrayList<String>();
        String textToFilterOnLC = textToFilterOn.toLowerCase();
        
        // Loop through all the accounts and pick out those that match the search string
        for (String accountName : allAccountNames) {
            if (accountName.toLowerCase().indexOf(textToFilterOnLC) > -1) {
                filteredAccountNames.add(accountName);
            }
        }

        setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, filteredAccountNames));
    }

}

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

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.u17od.upm.database.AccountInformation;
import com.u17od.upm.database.PasswordDatabase;

public class AccountsList extends ListActivity implements OnItemClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.account_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
        case R.id.edit_account:
            editAccount(getAccount(info.targetView));
            return true;
        case R.id.copy_username:
            setClipboardText(getUsername(getAccount(info.targetView)));
            return true;
        case R.id.copy_password:
            setClipboardText(getPassword(getAccount(info.targetView)));
            return true;
        case R.id.launch_url:
            launchURL(getURL(getAccount(info.targetView)));
            return true;
        }
        return super.onContextItemSelected(item);
    }

    private void setClipboardText(String text) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setText(text);
    }

    private AccountInformation getAccount(View listviewItem) {
        return getPasswordDatabase().getAccount(((TextView) listviewItem).getText().toString());
    }

    private String getUsername(AccountInformation account) {
        return new String(account.getUserId());
    }

    private String getURL(AccountInformation account) {
        return new String(account.getUrl());
    }

    private String getPassword(AccountInformation account) {
        return new String(account.getPassword());
    }

    private void launchURL(String uriString) {
        if (uriString == null || uriString.equals("")) {
            UIUtilities.showToast(this, R.string.no_uri, true);
        } else {
            Uri uri = Uri.parse(uriString);
            if (uri.getScheme() == null) {
                uri = Uri.parse("http://" + uriString);
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri); 
            startActivity(intent); 
        }
    }

    private void viewAccount(AccountInformation ai) {
        // Pass the AccountInformation object o the AccountDetails Activity by
        // way of a static variable on that class. I really don't like this but
        // it seems like the best way of doing it
        // @see http://developer.android.com/guide/appendix/faq/framework.html#3
        ViewAccountDetails.account = ai;

        Intent i = new Intent(AccountsList.this, ViewAccountDetails.class);
        startActivityForResult(i, ViewAccountDetails.VIEW_ACCOUNT_REQUEST_CODE);
    }

    private void editAccount(AccountInformation ai) {
        if (Utilities.isSyncRequired(this)) {
            UIUtilities.showToast(this, R.string.sync_required);
        } else {
            if (ai != null) {
                Intent i = new Intent(AccountsList.this, AddEditAccount.class);
                i.putExtra(AddEditAccount.MODE, AddEditAccount.EDIT_MODE);
                i.putExtra(AddEditAccount.ACCOUNT_TO_EDIT, ai.getAccountName());
                startActivityForResult(i, AddEditAccount.EDIT_ACCOUNT_REQUEST_CODE);
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Get the name of the account the user selected
        TextView itemSelected = (TextView) v;
        viewAccount(getPasswordDatabase().getAccount(itemSelected.getText().toString()));
    }

    protected PasswordDatabase getPasswordDatabase() {
        return ((UPMApplication) getApplication()).getPasswordDatabase();
    }

	@Override
	public void onItemClick(AdapterView<?> arg0, View v, int position, long id) {
		onListItemClick(null, v, position, id);
	}

}

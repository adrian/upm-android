/*
 * $Id: AccountsList.java 37 2010-01-27 19:16:42Z Adrian $
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

import com.u17od.upm.database.AccountInformation;
import com.u17od.upm.database.PasswordDatabase;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemLongClickListener;

public class AccountsList extends ListActivity implements OnItemLongClickListener {

	@Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		// Get the name of the account the user selected
		TextView itemSelected = (TextView) view;
		AccountInformation ai = getPasswordDatabase().getAccount(itemSelected.getText().toString());
		AddEditAccount.accountToEdit = ai;

		Intent i = new Intent(AccountsList.this, AddEditAccount.class);
		i.putExtra(AddEditAccount.MODE, AddEditAccount.EDIT_MODE);
		startActivity(i);
		return true;
	}

	@Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
		// Get the name of the account the user selected
		TextView itemSelected = (TextView) v;
		AccountInformation ai = getPasswordDatabase().getAccount(itemSelected.getText().toString());

		// Pass the AccountInformation object o the AccountDetails Activity by
		// way of a static variable on that class. I really don't like this but
		// it seems like the best way of doing it
		// @see http://developer.android.com/guide/appendix/faq/framework.html#3
		ViewAccountDetails.account = ai;

		Intent i = new Intent(AccountsList.this, ViewAccountDetails.class);
		startActivity(i);
    }

	protected PasswordDatabase getPasswordDatabase() {
		return ((UPMApplication) getApplication()).getPasswordDatabase();
	}

}

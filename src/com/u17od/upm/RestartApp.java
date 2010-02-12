/*
 * $Id: RestartApp.java 37 2010-01-27 19:16:42Z Adrian $
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RestartApp extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, EnterMasterPassword.class);
        i.addFlags(Intent.FLAG_FROM_BACKGROUND);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); 
        context.startActivity(i);
    }

}

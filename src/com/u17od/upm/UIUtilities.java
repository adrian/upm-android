package com.u17od.upm;

import android.content.Context;
import android.widget.Toast;

public class UIUtilities {

    public static void showToast(Context context, int id) {
        showToast(context, id, false);
    }

    public static void showToast(Context context, int id, boolean longToast) {
        Toast.makeText(context, id, longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    public static void showToast(Context context, String message, boolean longToast) {
        Toast.makeText(context, message, longToast ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

}

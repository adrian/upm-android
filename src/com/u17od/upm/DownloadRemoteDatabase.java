package com.u17od.upm;

import java.io.File;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.u17od.upm.database.PasswordDatabase;
import com.u17od.upm.transport.HTTPTransport;

public class DownloadRemoteDatabase extends Activity implements OnClickListener, Runnable {

    private static final int WHAT_ERROR_DOWNLOADING = 1;
    private static final int WHAT_NOT_PW_DATABASE = 2;
    private static final String DATABASE_FILE = "upm.db";

    private EditText url;
    private EditText userid;
    private EditText password;
    private ProgressDialog progressDialog;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download_remote_db);

        url = (EditText) findViewById(R.id.remote_db_url);
        userid = (EditText) findViewById(R.id.remote_url_userid);
        password = (EditText) findViewById(R.id.remote_url_password);

        Button downloadButton = (Button) findViewById(R.id.download_button);
        downloadButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        progressDialog = ProgressDialog.show(this, "", getString(R.string.downloading_db));
        new Thread(this).start();
    }

    @Override
    public void run() {
        Message msg = Message.obtain();

        try {
            // Download the remote db file and save it
            HTTPTransport transport = new HTTPTransport();
            File tempDB = transport.getRemoteFile(url.getText().toString(),
                    userid.getText().toString(), password.getText().toString());
            
            // Check this is a password database before accepting it
            if (PasswordDatabase.isPasswordDatabase(tempDB)) {
                ((UPMApplication) getApplication()).copyFile(tempDB, new File(getFilesDir(), DATABASE_FILE), this);

                // Now that we have a database we need to prompt the user for the password
                Intent i = new Intent(DownloadRemoteDatabase.this, EnterMasterPassword.class);
                startActivity(i);
                finish();
            } else {
                msg.what = WHAT_NOT_PW_DATABASE;
            }

        } catch (Exception e) {
            Log.e("DownloadRemoteDatabase", "Problem downloading database", e);
            msg.what = WHAT_ERROR_DOWNLOADING;
            Bundle b = new Bundle();
            b.putString("error", e.getMessage()); 
            msg.setData(b);
        } finally {
            handler.sendMessage(msg);
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            progressDialog.dismiss();

            switch (msg.what) {
                case WHAT_ERROR_DOWNLOADING:
                    String errorMessage = (String) msg.getData().get("error");
                    showToast(getString(R.string.problem_downloading_db) + "\n\n" + errorMessage);
                    break; 
                case WHAT_NOT_PW_DATABASE:
                    showToast(getString(R.string.not_password_database));
                    break; 
            }
        }

        private void showToast(String message) {
            Toast toast = Toast.makeText(DownloadRemoteDatabase.this, message, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.TOP, 0, 0);
            toast.show();
        }
    };

}

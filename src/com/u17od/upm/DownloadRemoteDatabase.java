package com.u17od.upm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

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

import com.u17od.upm.transport.HTTPTransport;
import com.u17od.upm.transport.TransportException;

public class DownloadRemoteDatabase extends Activity implements OnClickListener, Runnable {

    private static final int WHAT_ERROR_DOWNLOADING = 1;
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
            copyFile(tempDB, new File(getFilesDir(), DATABASE_FILE));

            // Start up the 
            Intent i = new Intent(DownloadRemoteDatabase.this, EnterMasterPassword.class);
            startActivity(i);
            finish();
        } catch (TransportException e) {
            Log.e("DownloadRemoteDatabase", "Problem downloading database", e);
            msg.what = WHAT_ERROR_DOWNLOADING;
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
                    Toast toast = Toast.makeText(DownloadRemoteDatabase.this, R.string.problem_downloading_db, Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.TOP, 0, 0);
                    toast.show();
                    break; 
            }
        }
    };

    private void copyFile(File source, File dest) {
        FileChannel sourceChannel = null;
        FileChannel destinationChannel = null;
        try {
            sourceChannel = new FileInputStream(source).getChannel();
            destinationChannel = new FileOutputStream(dest).getChannel();
            destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        } catch (IOException e) {
            Log.e("AccountsList", getString(R.string.file_problem), e);
            Toast.makeText(this, R.string.file_problem, Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (sourceChannel != null) {
                    sourceChannel.close();
                }
                if (destinationChannel != null) {
                    destinationChannel.close();
                }
            } catch (IOException e) {
                Log.e("AccountsList", getString(R.string.file_problem), e);
                Toast.makeText(this, R.string.file_problem, Toast.LENGTH_LONG).show();
            }
        }
    }

}

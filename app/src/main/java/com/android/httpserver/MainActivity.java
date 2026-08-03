package com.android.httpserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.ViewModelProvider;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.httpserver.component.BottomSheet;
import com.android.httpserver.component.HistoryViewModel;
import com.android.httpserver.server.HttpServer;
import com.android.httpserver.server.ForegroundService;
import com.android.httpserver.util.NotificationHelper;
import com.android.httpserver.util.QRGen;

public class MainActivity extends AppCompatActivity {

    public static final int PORT = 8000;

    Button startServerBtn;
    TextView fileNameView, ipView;
    ImageView qrView;
    HttpServer httpServer;
    NotificationHelper notificationHelper;

    private static final int DIR_PICKER_REQUEST_CODE = 200;
    private static boolean SERVER_RUNNING = false;
    private HistoryViewModel historyViewModel;
    private final String noConnectionMessage = "Can't retrieve IP address. Check your network connection and try again";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startServerBtn = findViewById(R.id.server_btn);
        fileNameView = findViewById(R.id.file_name);
        ipView = findViewById(R.id.ip_view);
        qrView = findViewById(R.id.qr_view);
        historyViewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(HistoryViewModel.class);
        notificationHelper = new NotificationHelper(MainActivity.this);
        ipView.setText("");
        qrView.setImageDrawable(null);

        httpServer = HttpServer.getInstance(MainActivity.this, PORT, getContentResolver(), historyViewModel,
                notificationHelper);

        // Check if we already have directory permission
        if (!hasDirectoryPermission()) {
            requestDirectoryPermission();
        } else {
            fileNameView.setText("Shared Folder: " + getSavedDirName());
        }

        fileNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (SERVER_RUNNING) {
                    Toast.makeText(MainActivity.this, "Stop server first", Toast.LENGTH_SHORT).show();
                    return;
                }
                requestDirectoryPermission();
            }
        });

        startServerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (httpServer != null) {
                    startServer();
                }
            }
        });
    }

    private boolean hasDirectoryPermission() {
        SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
        String uriString = prefs.getString(Constants.KEY_EXT_PATH_URI, null);
        if (uriString == null)
            return false;
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(this, Uri.parse(uriString));
            return dir != null && dir.exists() && dir.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    private String getSavedDirName() {
        SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
        String uriString = prefs.getString(Constants.KEY_EXT_PATH_URI, null);
        if (uriString == null)
            return "";
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(this, Uri.parse(uriString));
            return dir != null ? dir.getName() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void requestDirectoryPermission() {
        showAlert(this, "Select Shared Folder",
                "Choose a folder to share over the network. All files and subfolders in the selected folder will be accessible.",
                this::openDirectoryPicker);
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, DIR_PICKER_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DIR_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri treeUri = data.getData();
                getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
                prefs.edit().putString(Constants.KEY_EXT_PATH_URI, treeUri.toString()).apply();

                DocumentFile dir = DocumentFile.fromTreeUri(this, treeUri);
                String dirName = dir != null ? dir.getName() : "Selected";
                fileNameView.setText("Shared Folder: " + dirName);
                Toast.makeText(this, "Folder selected: " + dirName, Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == DIR_PICKER_REQUEST_CODE && resultCode == RESULT_CANCELED) {
            if (!hasDirectoryPermission()) {
                showAlert(this, "Permission Required",
                        "A folder must be selected to use this app.",
                        () -> {
                        });
            }
        }
    }

    private void stopServer() {
        SERVER_RUNNING = false;
        Toast.makeText(MainActivity.this, "Stopping server", Toast.LENGTH_SHORT).show();
        httpServer.stop();
        stopService(new Intent(this, ForegroundService.class));
        qrView.setImageDrawable(null);
        ipView.setText("");
        startServerBtn.setText("Start Server");
        startServerBtn.setBackgroundResource(R.drawable.start_server_btn_bg);
        Toast.makeText(MainActivity.this, "Server stopped", Toast.LENGTH_SHORT).show();
    }

    private void startServer() {
        if (SERVER_RUNNING) {
            stopServer();
            return;
        }

        if (!hasDirectoryPermission()) {
            Toast.makeText(this, "Please select a folder first", Toast.LENGTH_SHORT).show();
            requestDirectoryPermission();
            return;
        }

        String ip = httpServer.getIPAddress();
        if (ip != null && ip.length() > 0) {
            String address = "http://" + ip + ":" + PORT;
            Bitmap qrBitmap = null;
            try {
                qrBitmap = QRGen.generateQR(address);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Can't generate QR", Toast.LENGTH_SHORT).show();
            }

            try {
                httpServer.start();
                SERVER_RUNNING = true;
                ipView.setText(address);
                if (qrBitmap != null)
                    qrView.setImageBitmap(qrBitmap);
                fileNameView.setText("Serving: " + getSavedDirName());
                startServerBtn.setText("Stop Server");
                startServerBtn.setBackgroundResource(R.drawable.stop_server_btn_bg);

                // Start foreground service to keep server alive
                Intent serviceIntent = new Intent(this, ForegroundService.class);
                serviceIntent.putExtra("address", address);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                Toast.makeText(MainActivity.this, "Server started on port: " + PORT, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                SERVER_RUNNING = false;
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                ipView.setText("Failed to start server");
                startServerBtn.setText("Start Server");
                startServerBtn.setBackgroundResource(R.drawable.start_server_btn_bg);
            }
        } else {
            ipView.setText(noConnectionMessage);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_history:
                historyViewModel.getAllHistory().observe(this, historyList -> {
                    if (getSupportFragmentManager().findFragmentByTag("BottomSheetTag") == null) {
                        BottomSheet bottomSheet = new BottomSheet(historyList, history -> {
                            historyViewModel.delete(history);
                            Toast.makeText(MainActivity.this, "History deleted", Toast.LENGTH_SHORT).show();
                        });
                        bottomSheet.show(getSupportFragmentManager(), "BottomSheetTag");
                    }
                });
                return true;

            case R.id.action_info:
                startActivity(new Intent(MainActivity.this, AppInfo.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showAlert(Context context, String title, String message, Runnable action) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    action.run();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        stopService(new Intent(this, ForegroundService.class));
    }
}

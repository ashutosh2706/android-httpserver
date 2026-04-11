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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.httpserver.component.BottomSheet;
import com.android.httpserver.component.HistoryViewModel;
import com.android.httpserver.model.FileInfo;
import com.android.httpserver.server.HttpServer;
import com.android.httpserver.util.NotificationHelper;
import com.android.httpserver.util.QRGen;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.time.LocalDateTime;

public class MainActivity extends AppCompatActivity {

    public static final int PORT = 8000;
    public static ConcurrentHashMap<String, FileInfo> fileMap;
    public static boolean RECEIVE_MODE = false;

    Button filePickerBtn, startServerBtn;
    TextView fileNameView, ipView;
    ImageView qrView;
    HttpServer httpServer;
    NotificationHelper notificationHelper;

    private static final int FILE_PICKER_REQUEST_CODE = 101;
    private static final int EXT_LOCATION_PICKER_REQUEST_CODE = 105;
    private static boolean SERVER_RUNNING = false;
    private HistoryViewModel historyViewModel;
    private final String noConnectionMessage = "Can't retrieve IP address. Check your network connection and try again";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        filePickerBtn = findViewById(R.id.picker_btn);
        startServerBtn = findViewById(R.id.server_btn);
        fileNameView = findViewById(R.id.file_name);
        ipView = findViewById(R.id.ip_view);
        qrView = findViewById(R.id.qr_view);
        historyViewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(HistoryViewModel.class);
        notificationHelper = new NotificationHelper(MainActivity.this);
        ipView.setText("");
        qrView.setImageDrawable(null);

        httpServer = HttpServer.getInstance(MainActivity.this, PORT, getContentResolver(), historyViewModel, notificationHelper);
        fileMap = new ConcurrentHashMap<>();

        fileNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(SERVER_RUNNING) {
                    Toast.makeText(MainActivity.this, "Server running", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setType("*/*");
                startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
            }
        });

        startServerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(httpServer != null) {
                    startServer();
                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if(data != null) {
                Uri fileUri = data.getData();
                /* don't require persistent read permission */
                // getContentResolver().takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String fileName = getFileNameFromUri(fileUri);
                String fileSize = getFileSizeReadable(fileUri);
                String uid = UUID.randomUUID().toString().substring(0, 5).trim();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fileMap.put(uid, new FileInfo(fileUri, uid, LocalDateTime.now(), fileName, fileSize));
                } else {
                    fileMap.put(uid, new FileInfo(fileUri, uid, null, fileName, fileSize));
                }
                fileNameView.setText("Sharing: " + fileName);
            }
        }

        if (requestCode == EXT_LOCATION_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri treeUri = data.getData();
                final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                SharedPreferences preferences = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(Constants.KEY_EXT_PATH_URI, treeUri.toString());
                editor.apply();
                handleFileReceive();
            }
        }

        if (requestCode == EXT_LOCATION_PICKER_REQUEST_CODE && resultCode == RESULT_CANCELED) {
            showAlert(
                    MainActivity.this,
                    "Permission Denied",
                    "Storage access is required for receiving files",
                    () -> {}
            );
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        // Fallback to last path segment if DISPLAY_NAME not available
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private String getFileSizeReadable(Uri fileUri) {
        Cursor cursor = getContentResolver().query(fileUri, null, null, null, null);
        String result = "";

        if (cursor != null && cursor.moveToFirst()) {
            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            if (!cursor.isNull(sizeIndex)) {
                long sizeInBytes = cursor.getLong(sizeIndex);
                result = formatFileSize(sizeInBytes);
            }
            cursor.close();
        }
        return result;
    }

    private String formatFileSize(long sizeInBytes) {
        double size = sizeInBytes;
        String unit;
        if (sizeInBytes >= 1024L * 1024L * 1024L) {
            size = size / (1024.0 * 1024.0 * 1024.0);
            unit = "GB";
        } else if (sizeInBytes >= 1024L * 1024L) {
            size = size / (1024.0 * 1024.0);
            unit = "MB";
        } else {
            size = size / 1024.0;
            unit = "KB";
        }
        return String.format(Locale.getDefault(), "%.2f %s", size, unit);
    }

    private void stopServer() {
        SERVER_RUNNING = false;
        RECEIVE_MODE = false;
        Toast.makeText(MainActivity.this, "Stopping server", Toast.LENGTH_SHORT).show();
        httpServer.stop();
        qrView.setImageDrawable(null);
        ipView.setText("");
        fileMap.clear();
        fileNameView.setText("");
        startServerBtn.setText("Start Server");
        startServerBtn.setBackgroundResource(R.drawable.start_server_btn_bg);
//            filePickerBtn.setBackgroundResource(R.drawable.active_file_picker_btn_bg);
        Toast.makeText(MainActivity.this, "Server stopped", Toast.LENGTH_SHORT).show();
    }

    private void startServer() {
        if(SERVER_RUNNING) {
            stopServer();
            return;
        }

        if(fileMap.size() == 0 && !RECEIVE_MODE) {
            Toast.makeText(MainActivity.this, "Please select a file", Toast.LENGTH_SHORT).show();
            return;
        }

        String ip = httpServer.getIPAddress();
        if(ip != null && ip.length() > 0) {
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
                if(qrBitmap != null)
                    qrView.setImageBitmap(qrBitmap);
                if (RECEIVE_MODE)
                    fileNameView.setText("Ready to receive file");
                startServerBtn.setText("Stop Server");
                startServerBtn.setBackgroundResource(R.drawable.stop_server_btn_bg);
//                filePickerBtn.setBackgroundResource(R.drawable.inactive_file_picker_btn_bg);
                Toast.makeText(MainActivity.this, "Server started successfully on port: " + PORT, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                SERVER_RUNNING = false;
                RECEIVE_MODE = false;
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                ipView.setText("Failed to start server");
                startServerBtn.setText("Start Server");
                startServerBtn.setBackgroundResource(R.drawable.start_server_btn_bg);
//                filePickerBtn.setBackgroundResource(R.drawable.active_file_picker_btn_bg);
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

            case R.id.action_file_receive:
                handleFileReceive();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void handleFileReceive() {
        SharedPreferences sharedPreferences = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
        String externalUri = sharedPreferences.getString(Constants.KEY_EXT_PATH_URI, null);
        try {
            if (externalUri == null) {
                showAlert(MainActivity.this, "Storage Permission", "App requires access to external storage for receiving files",
                        this::getPersistableWritePermission
                );
                return;
            }
            DocumentFile extDir = DocumentFile.fromTreeUri(MainActivity.this, Uri.parse(externalUri));
            if (extDir == null || !extDir.exists() || !extDir.canRead() || !extDir.canWrite()) {
                showAlert(MainActivity.this, "Storage Permission", "App requires access to external storage for receiving files",
                        this::getPersistableWritePermission
                );
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: "+e.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (SERVER_RUNNING) {
            Toast.makeText(MainActivity.this, "Server running", Toast.LENGTH_SHORT).show();
            return;
        }
        RECEIVE_MODE = true;
        startServer();
    }

    private void getPersistableWritePermission() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, EXT_LOCATION_PICKER_REQUEST_CODE);
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
        if(httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
    }
}
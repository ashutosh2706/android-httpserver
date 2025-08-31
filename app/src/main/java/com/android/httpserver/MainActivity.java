package com.android.httpserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
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

import com.android.httpserver.model.FileInfo;
import com.android.httpserver.model.History;
import com.android.httpserver.server.HttpServer;
import com.android.httpserver.util.QRGen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.time.LocalDateTime;

public class MainActivity extends AppCompatActivity {

    public static final int PORT = 8080;
    Button filePickerBtn, startServerBtn;
    TextView fileNameView, ipView;
    ImageView qrView;
    HttpServer httpServer;

    static final int FILE_PICKER_REQUEST_CODE = 101;
    public static ConcurrentHashMap<String, FileInfo> fileMap;
    private static boolean SERVER_RUNNING = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        filePickerBtn = findViewById(R.id.picker_btn);
        startServerBtn = findViewById(R.id.server_btn);
        fileNameView = findViewById(R.id.file_name);
        ipView = findViewById(R.id.ip_view);
        qrView = findViewById(R.id.qr_view);

        ipView.setText("");
        qrView.setImageDrawable(null);

        httpServer = new HttpServer(MainActivity.this, PORT, getContentResolver());

        fileMap = new ConcurrentHashMap<>();

        filePickerBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
                String uid = UUID.randomUUID().toString().substring(0, 5).trim();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    fileMap.put(uid, new FileInfo(fileUri, uid, LocalDateTime.now(), fileName));
                } else {
                    fileMap.put(uid, new FileInfo(fileUri, uid, null, fileName));
                }
                fileNameView.setText(fileName);
            }
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


    private void startServer() {

        if(SERVER_RUNNING) {
            Toast.makeText(MainActivity.this, "Stopping server", Toast.LENGTH_SHORT).show();
            // do the chores
            httpServer.stop();
            qrView.setImageDrawable(null);
            ipView.setText("");
            SERVER_RUNNING = false;
            fileMap.clear();
            fileNameView.setText("");
            startServerBtn.setText("Start Server");
            Toast.makeText(MainActivity.this, "Server stopped", Toast.LENGTH_SHORT).show();
            return;
        }

        if(fileMap.size() == 0) {
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
                ipView.setText(address);
                if(qrBitmap != null) {
                    qrView.setImageBitmap(qrBitmap);
                }
                SERVER_RUNNING = true;
                startServerBtn.setText("Stop Server");
                Toast.makeText(MainActivity.this, "Server started successfully on port: " + PORT, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                ipView.setText("Failed to start server");
            }

        } else {
            ipView.setText("Can't retrieve device IP.");
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
                //
                List<History> fileList = new ArrayList<>();
                fileList.add(new History("Document.pdf", "12 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Photo.jpg", "5.2 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Document.pdf", "12 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Photo.jpg", "5.2 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Document.pdf", "12 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Photo.jpg", "5.2 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Document.pdf", "12 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Photo.jpg", "5.2 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Document.pdf", "12 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Photo.jpg", "5.2 MB", "31-August-2025", R.drawable.ic_file_unknown));
                fileList.add(new History("Document.pdf", "12 MB", "31-August-2025", R.drawable.ic_file_unknown));


                HistoryBottomSheet bottomSheet = new HistoryBottomSheet(fileList);
                bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());

            default:
                return super.onOptionsItemSelected(item);
        }
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
package com.android.httpserver;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.httpserver.component.HistoryAdapter;
import com.android.httpserver.component.HistoryViewModel;
import com.android.httpserver.model.History;

public class HistoryActivity extends AppCompatActivity {

    private HistoryViewModel historyViewModel;
    private HistoryAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.history_recycler_view);
        emptyView = findViewById(R.id.empty_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        historyViewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))
                .get(HistoryViewModel.class);

        adapter = new HistoryAdapter(
                history -> {
                    historyViewModel.delete(history);
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                },
                history -> openFileWith(history.getFileName()));

        recyclerView.setAdapter(adapter);

        historyViewModel.getAllHistory().observe(this, historyList -> {
            adapter.setHistoryList(historyList);
            if (historyList == null || historyList.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        });
    }

    private void openFileWith(String fileName) {
        String mimeType = getMimeTypeFromFileName(fileName);
        if (mimeType == null) {
            mimeType = "*/*";
        }

        // Search for the file in the shared directory
        android.content.SharedPreferences prefs = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
        String uriString = prefs.getString(Constants.KEY_EXT_PATH_URI, null);
        if (uriString == null) {
            Toast.makeText(this, "Shared folder not set", Toast.LENGTH_SHORT).show();
            return;
        }

        androidx.documentfile.provider.DocumentFile rootDir = androidx.documentfile.provider.DocumentFile
                .fromTreeUri(this, Uri.parse(uriString));
        if (rootDir == null) {
            Toast.makeText(this, "Cannot access folder", Toast.LENGTH_SHORT).show();
            return;
        }

        androidx.documentfile.provider.DocumentFile file = findFileRecursive(rootDir, fileName);
        if (file == null) {
            Toast.makeText(this, "File not found in shared folder", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(file.getUri(), mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        Intent chooser = Intent.createChooser(intent, "Open with");
        if (chooser.resolveActivity(getPackageManager()) != null) {
            startActivity(chooser);
        } else {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private androidx.documentfile.provider.DocumentFile findFileRecursive(
            androidx.documentfile.provider.DocumentFile dir, String fileName) {
        for (androidx.documentfile.provider.DocumentFile child : dir.listFiles()) {
            if (child.isFile() && fileName.equals(child.getName())) {
                return child;
            }
            if (child.isDirectory()) {
                androidx.documentfile.provider.DocumentFile found = findFileRecursive(child, fileName);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private String getMimeTypeFromFileName(String fileName) {
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = fileName.substring(dotIndex + 1).toLowerCase();
        }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

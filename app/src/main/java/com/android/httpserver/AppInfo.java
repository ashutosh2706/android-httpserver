package com.android.httpserver;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class AppInfo extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_info);

        if(getSupportActionBar() != null) {
            getSupportActionBar().setTitle("App Info");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        Button bugReportBtn = findViewById(R.id.btnReportBug);
        bugReportBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String bugReportUrl = "https://github.com/ashutosh2706/android-httpserver/issues/new";
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(bugReportUrl));
                startActivity(browserIntent);
            }
        });

        TextView appVersionTView = findViewById(R.id.tvAppVersion);
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            appVersionTView.setText("Version "+versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(AppInfo.this, e.getMessage(), Toast.LENGTH_LONG).show();
            appVersionTView.setText("Version 1.0");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
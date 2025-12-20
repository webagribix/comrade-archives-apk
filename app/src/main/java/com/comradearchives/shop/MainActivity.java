package com.comradearchives.shop;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.content.FileProvider;
import java.io.File;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        // Link JavaScript "Android" object to this Java class
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                view.loadUrl("file:///android_asset/error.html");
            }
        });

        webView.loadUrl("https://comradearchives.hstn.me/comrade_shop/");
        setContentView(webView);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", (dialog, which) -> finish())
                .setNegativeButton("No", null)
                .show();
        }
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> webView.loadUrl("https://comradearchives.hstn.me/comrade_shop/"));
        }

        @JavascriptInterface
        public void downloadUpdate(String fileUrl) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
            // Hidden notification keeps the experience "In-App"
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            
            final String fileName = "comrade_update.apk";
            request.setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) return;
            
            final long downloadId = manager.enqueue(request);

            new Thread(() -> {
                boolean downloading = true;
                while (downloading) {
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(downloadId);
                    Cursor cursor = manager.query(q);
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        // Safety: Get column indices first to prevent crashes
                        int downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                        if (downloadedIdx != -1 && totalIdx != -1) {
                            int bytes_downloaded = cursor.getInt(downloadedIdx);
                            int bytes_total = cursor.getInt(totalIdx);

                            if (cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                                downloading = false;
                            }

                            if (bytes_total > 0) {
                                final int progress = (int) ((bytes_downloaded * 100L) / bytes_total);
                                // Push the percentage back to the web Progress Bar
                                runOnUiThread(() -> webView.loadUrl("javascript:updateDownloadProgress(" + progress + ")"));
                            }
                        }
                    }
                    if (cursor != null) cursor.close();
                    
                    try { Thread.sleep(500); } catch (Exception e) {} // Prevent CPU overload
                }
                installApk(fileName);
            }).start();
        }

        private void installApk(String fileName) {
            File file = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
            // This authority MUST match your AndroidManifest.xml provider
            Uri contentUri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".provider", file);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }
    }
}

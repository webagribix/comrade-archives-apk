package com.comradearchives.shop;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
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
        
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.endsWith("/comrade_shop/") || url.endsWith("index.php")) {
                    view.clearHistory();
                }
            }

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
            showBrandExitDialog();
        }
    }

    private void showBrandExitDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_exit, null);
        builder.setView(dialogView);

        final android.app.AlertDialog dialog = builder.create();
        
        // This ensures the custom layout handles the background
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Button btnExit = dialogView.findViewById(R.id.btn_exit_yes);
        Button btnStay = dialogView.findViewById(R.id.btn_exit_no);

        btnExit.setOnClickListener(v -> finish());
        btnStay.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public int getAppVersionCode() {
            // BuildConfig is automatically generated during build
            return com.comradearchives.shop.BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> webView.loadUrl("https://comradearchives.hstn.me/comrade_shop/"));
        }

        @JavascriptInterface
        public void downloadUpdate(String fileUrl) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            
            final String fileName = "comrade_update.apk";
            request.setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
            final long downloadId = manager.enqueue(request);

            new Thread(() -> {
                boolean downloading = true;
                while (downloading) {
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(downloadId);
                    Cursor cursor = manager.query(q);
                    if (cursor != null && cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                        if (statusIndex != -1 && cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false;
                        }

                        if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                            int bytes_downloaded = cursor.getInt(bytesDownloadedIndex);
                            int bytes_total = cursor.getInt(bytesTotalIndex);
                            if (bytes_total > 0) {
                                final int progress = (int) ((bytes_downloaded * 100L) / bytes_total);
                                runOnUiThread(() -> webView.loadUrl("javascript:updateDownloadProgress(" + progress + ")"));
                            }
                        }
                    }
                    if (cursor != null) cursor.close();
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                }
                installApk(fileName);
            }).start();
        }

        private void installApk(String fileName) {
            File file = new File(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
            Uri contentUri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".provider", file);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }
    }
}

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
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
        // Ensure the WebView can open new windows/popups if needed
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // FIX: The WebChromeClient allows the App to "hear" JavaScript alerts/confirms
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("System Message")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> result.confirm())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Confirmation")
                        .setMessage(message)
                        .setPositiveButton("Yes", (dialog, which) -> result.confirm())
                        .setNegativeButton("No", (dialog, which) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Clear history when hitting home to prevent "back-looping" into login/errors
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_exit, null);
        builder.setView(dialogView);

        final AlertDialog dialog = builder.create();
        
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
            // Returns the versionCode from your build.gradle
            return com.comradearchives.shop.BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> webView.loadUrl("https://comradearchives.hstn.me/comrade_shop/"));
        }

        @JavascriptInterface
        public void downloadUpdate(String fileUrl) {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileUrl));
            // Keep the download process invisible to standard notifications to stay "In-App"
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
                        int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                        if (statusIdx != -1 && cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false;
                        }

                        if (bytesDownloadedIdx != -1 && bytesTotalIdx != -1) {
                            int bytes_downloaded = cursor.getInt(bytesDownloadedIdx);
                            int bytes_total = cursor.getInt(bytesTotalIdx);
                            if (bytes_total > 0) {
                                final int progress = (int) ((bytes_downloaded * 100L) / bytes_total);
                                // Send progress back to the website UI
                                runOnUiThread(() -> webView.loadUrl("javascript:updateDownloadProgress(" + progress + ")"));
                            }
                        }
                    }
                    if (cursor != null) cursor.close();
                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
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

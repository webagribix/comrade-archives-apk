package com.comradearchives.shop;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // This removes the top title bar for a "plain" look
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        
        // This links the HTML "RETRY" button to the Android code
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void retry() {
                runOnUiThread(() -> webView.loadUrl("https://comradearchives.hstn.me/comrade_shop/"));
            }
        }, "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                view.loadUrl("file:///android_asset/error.html");
            }
        });

        webView.loadUrl("https://comradearchives.hstn.me/comrade_shop/");
        setContentView(webView);
    }
}

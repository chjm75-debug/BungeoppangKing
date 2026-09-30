package com.bungeoppang.king;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private FrameLayout root;
    private TextView status;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(42, 29, 23));

        status = new TextView(this);
        status.setText("붕어빵 장사왕\n게임 화면 준비 중…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(22f);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(Color.rgb(42, 29, 23));
        status.setPadding(32, 32, 32, 32);

        root.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);

        new Handler(Looper.getMainLooper()).postDelayed(this::loadGameSafely, 200);
    }

    private String readAsset(String fileName) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getAssets().open(fileName);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void loadGameSafely() {
        try {
            String html = readAsset("index.html");
            if (!html.contains("붕어빵 장사왕") || !html.contains("id=\"app\"")) {
                showError("게임 파일 검증 실패");
                return;
            }

            webView = new WebView(this);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setLoadWithOverviewMode(false);
            settings.setUseWideViewPort(true);
            settings.setMediaPlaybackRequiresUserGesture(false);

            webView.addJavascriptInterface(new ReadyBridge(), "AndroidBridge");
            webView.setWebChromeClient(new WebChromeClient());
            webView.setWebViewClient(new WebViewClient());

            root.addView(webView, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            webView.loadDataWithBaseURL(
                    "https://local.bungeoppang/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
            );

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (status != null && status.getVisibility() == View.VISIBLE) {
                    status.setText("게임 화면 렌더링 실패\n화면 캡처를 보내주세요.");
                }
            }, 6000);
        } catch (Throwable t) {
            showError("게임 시작 오류\n" + t.getClass().getSimpleName() + "\n" + String.valueOf(t.getMessage()));
        }
    }

    private class ReadyBridge {
        @JavascriptInterface
        public void ready() {
            runOnUiThread(() -> {
                if (status != null) status.setVisibility(View.GONE);
            });
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (status != null) {
                status.setVisibility(View.VISIBLE);
                status.setText(message);
                status.bringToFront();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.removeJavascriptInterface("AndroidBridge");
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}

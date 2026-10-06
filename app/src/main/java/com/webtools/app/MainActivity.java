package com.webtools.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private WebView web;
    private WebView popup;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private final String home = BuildConfig.APP_URL;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Firebase signInWithPopup için gerçek popup penceresi gerekir.
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        // Google, "; wv" içeren WebView kimliğini OAuth'ta reddeder (disallowed_useragent).
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        final String host = Uri.parse(home).getHost();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                // Aynı siteyse uygulama içinde aç, dış bağlantıları tarayıcıya ver.
                if (!r.isForMainFrame()) return false;
                Uri u = r.getUrl();
                String h = u.getHost();
                if (host != null && host.equals(h)) return false;
                if ("notdefteri-phi.vercel.app".equals(h)) return false;
                // Google/Firebase giriş sayfaları WebView içinde kalmalı; tarayıcıya gidersek
                // giriş dönüşünde "missing initial state" hatası oluşur.
                if (isAuthHost(h)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) {}
                return true;
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(null,
                        "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                        + "<body style='font-family:sans-serif;text-align:center;padding:30vh 24px 0;color:#0a1628'>"
                        + "<h3>Bağlantı kurulamadı</h3><p style='color:#5a6b82'>İnternet bağlantını kontrol et.</p>"
                        + "<p><a href='" + home + "' style='color:#4f46e5;font-weight:600'>Tekrar dene</a></p></body>",
                        "text/html", "utf-8", null);
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final WebView pop = new WebView(MainActivity.this);
                WebSettings ps = pop.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                ps.setUserAgentString(web.getSettings().getUserAgentString());
                CookieManager.getInstance().setAcceptThirdPartyCookies(pop, true);
                pop.setWebViewClient(new WebViewClient());
                pop.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView w) { closePopup(); }
                });
                root.addView(pop, new FrameLayout.LayoutParams(-1, -1));
                popup = pop;
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(pop);
                resultMsg.sendToTarget();
                return true;
            }
            @Override
            public void onCloseWindow(WebView w) { closePopup(); }
            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                customView = v; customCallback = cb;
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
                v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCallback != null) customCallback.onCustomViewHidden();
            }
        });

        if (b != null) web.restoreState(b); else web.loadUrl(home);
    }

    private void closePopup() {
        if (popup == null) return;
        root.removeView(popup);
        popup.destroy();
        popup = null;
    }

    private static boolean isAuthHost(String h) {
        if (h == null) return false;
        return h.equals("accounts.google.com") || h.endsWith(".firebaseapp.com")
            || h.endsWith(".web.app") || h.equals("apis.google.com")
            || h.endsWith(".googleapis.com") || h.endsWith(".gstatic.com");
    }

    @Override
    protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    @Override
    public void onBackPressed() {
        if (popup != null) { closePopup(); return; }
        if (customView != null) { web.getWebChromeClient().onHideCustomView(); return; }
        // Modal açıksa kapat; değilse geçmişte geri git (ör. Not Defteri'nden araçlara), yoksa çık.
        web.evaluateJavascript(
            "(function(){var m=document.getElementById('modal');"
            + "if(m&&m.classList.contains('show')){document.getElementById('closeBtn').click();return 1;}return 0;})()",
            v -> { if (!"1".equals(v)) { if (web.canGoBack()) web.goBack(); else finish(); } });
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
}

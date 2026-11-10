package com.sara.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest r) {
                r.grant(r.getResources());
            }
        });
        w.loadUrl("file:///android_asset/index.html");
        setContentView(w);
    }
}

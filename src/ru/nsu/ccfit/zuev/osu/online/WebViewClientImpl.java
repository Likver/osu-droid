package ru.nsu.ccfit.zuev.osu.online;

import android.webkit.WebView;
import android.webkit.WebViewClient;

public class WebViewClientImpl extends WebViewClient {

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if(url.startsWith(OnlineManager.host)) return false;
        return true;
    }

}
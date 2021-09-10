package ru.nsu.ccfit.zuev.osu.online;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import ru.nsu.ccfit.zuev.osu.GlobalManager;
import ru.nsu.ccfit.zuev.osu.MainActivity;
import ru.nsu.ccfit.zuev.osuplus.R;

public class WebViewActivity extends AppCompatActivity {

    public static final String EXTRA_INFO = "ru.nsu.ccfit.zuev.osuplus.WebViewActivityExtra";
    public static final String EXTRA_URL = "ru.nsu.ccfit.zuev.osuplus.WebViewActivityURL";
    public static final String JAVASCRIPT_INTERFACE_NAME = "Android";

    public static final String ENDPOINT_URL = OnlineManager.host.replace("api/", "");
    public static final String LOGIN_URL = ENDPOINT_URL + "user/?action=login";
    public static final String REGISTER_URL = ENDPOINT_URL + "user/?action=register";
    public static final String PROFILE_URL = ENDPOINT_URL + "profile.php?uid=%d";

    private WebView webview;
    private Activity mActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.webview_activity);

        mActivity = GlobalManager.getInstance().getMainActivity();

        webview = (WebView) findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClientImpl());

        String url = getIntent().getStringExtra(EXTRA_URL);
        if(url == LOGIN_URL) {
            // webview.addJavascriptInterface(new LoginTypeInterface(),
            // JAVASCRIPT_INTERFACE_NAME);
            webview.loadUrl(LOGIN_URL);
        }else if(url == REGISTER_URL) {
            webview.addJavascriptInterface(new RegisterTypeInterface(),
                JAVASCRIPT_INTERFACE_NAME);
            webview.loadUrl(REGISTER_URL);
        }else if(url == PROFILE_URL) {
            String extraInfo = getIntent().getStringExtra(EXTRA_INFO);
            if(extraInfo == null) {
                closeActivity();
            }
            webview.loadUrl(String.format(PROFILE_URL, EXTRA_INFO));
        }else {
            closeActivity();
        }
    }

    private void closeActivity() {
        mActivity.startActivity(new Intent(mActivity, MainActivity.class));
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(webview.canGoBack()) {
                webview.goBack();
            }else {
                closeActivity();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private class RegisterTypeInterface {
        @JavascriptInterface
        public void showSnackbar(String message) {
            Snackbar.make(findViewById(android.R.id.content), message, 1500).show();
        }
    }

    private class LoginTypeInterface {
    }

}
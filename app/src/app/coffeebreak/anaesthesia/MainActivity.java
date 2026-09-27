package app.coffeebreak.anaesthesia;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private static volatile boolean foreground = false;
    private WebView web;
    private String pendingIssueId;
    private boolean pageReady = false;

    static boolean isInForeground() {
        return foreground;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBars();

        web = new WebView(this);
        web.setBackgroundColor(isDark() ? 0xFF1C1512 : 0xFFFBF6EF);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Native");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                openExternal(request.getUrl().toString());
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingIssueId != null) {
                    openIssueInPage(pendingIssueId);
                    pendingIssueId = null;
                }
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        Notifier.ensureChannels(this);
        CheckJobService.ensureScheduled(this);
        handleIntent(getIntent());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra("issue_id");
        if (id == null) return;
        if (pageReady) openIssueInPage(id);
        else pendingIssueId = id;
    }

    private void openIssueInPage(String id) {
        web.evaluateJavascript("window.openIssue && window.openIssue(" + JSONObject.quote(id) + ")", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1001);
        if (pageReady) web.evaluateJavascript("window.onResumeApp && window.onResumeApp()", null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        foreground = false;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.handleBack ? window.handleBack() : false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"true".equals(value)) MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemBars();
        web.setBackgroundColor(isDark() ? 0xFF1C1512 : 0xFFFBF6EF);
        web.evaluateJavascript("window.setDark && window.setDark(" + isDark() + ")", null);
    }

    private boolean isDark() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBars() {
        Window w = getWindow();
        boolean dark = isDark();
        int bg = dark ? 0xFF1C1512 : 0xFFFBF6EF;
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(dark ? 0xFF241B17 : Color.WHITE);
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private void openExternal(String url) {
        if (url == null) return;
        String lower = url.toLowerCase();
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }

    /** Methods callable from the page's JavaScript as window.Native.* */
    final class Bridge {

        @JavascriptInterface
        public String getArchive() {
            return FeedStore.readArchive(MainActivity.this);
        }

        @JavascriptInterface
        public void refresh() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final JSONObject r = CheckJobService.runCheck(getApplicationContext());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript("window.onSynced && window.onSynced(" + r.toString() + ")", null);
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public String getState() {
            return FeedStore.prefs(MainActivity.this).getString("ui_state", "{}");
        }

        @JavascriptInterface
        public void setState(String json) {
            FeedStore.prefs(MainActivity.this).edit().putString("ui_state", json).apply();
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    openExternal(url);
                }
            });
        }

        @JavascriptInterface
        public void share(final String subject, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_SUBJECT, subject);
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    try {
                        startActivity(Intent.createChooser(i, "Share"));
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean isDark() {
            return MainActivity.this.isDark();
        }

        @JavascriptInterface
        public String info() {
            JSONObject o = new JSONObject();
            try {
                SharedPreferences p = FeedStore.prefs(MainActivity.this);
                PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
                o.put("versionName", pi.versionName);
                o.put("versionCode", pi.versionCode);
                o.put("lastCheck", p.getLong("last_check", 0));
                o.put("lastSuccess", p.getLong("last_success", 0));
                o.put("lastError", p.getString("last_error", ""));
                o.put("feedUrl", FeedStore.FEED_URL);
                o.put("repoUrl", FeedStore.REPO_URL);
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                o.put("notificationsOn", nm != null && nm.areNotificationsEnabled());
            } catch (Exception ignored) {
            }
            return o.toString();
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                        startActivity(i);
                    } catch (Exception e) {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }
                }
            });
        }
    }
}

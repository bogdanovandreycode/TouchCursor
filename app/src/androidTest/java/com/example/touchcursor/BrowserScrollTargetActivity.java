package com.example.touchcursor;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Offline HTML in the actual Chromium WebView accessibility implementation. */
public class BrowserScrollTargetActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView offset = new TextView(this);
        offset.setContentDescription("fixture-browser-offset");
        offset.setText("0");
        layout.addView(offset, new LinearLayout.LayoutParams(-1, (int) (40 * getResources().getDisplayMetrics().density)));
        TextView ready = new TextView(this);
        ready.setContentDescription("fixture-browser-ready");
        ready.setText("loading");
        layout.addView(ready);
        LinearLayout area = new LinearLayout(this);
        area.setContentDescription("fixture-browser-area");
        WebView browser = new WebView(this);
        browser.getSettings().setBlockNetworkLoads(true);
        browser.setOnScrollChangeListener((view, x, y, oldX, oldY) -> offset.setText(Integer.toString(y)));
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) { ready.setText("ready"); }
        });
        area.addView(browser, new LinearLayout.LayoutParams(-1, -1));
        layout.addView(area, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        StringBuilder html = new StringBuilder("<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body><h1>Offline scroll fixture</h1>");
        for (int i = 0; i < 200; i++) html.append("<p style='font-size:24px;padding:16px'>Browser paragraph ").append(i).append("</p>");
        html.append("</body></html>");
        browser.loadDataWithBaseURL(null, html.toString(), "text/html", "UTF-8", null);
    }
}

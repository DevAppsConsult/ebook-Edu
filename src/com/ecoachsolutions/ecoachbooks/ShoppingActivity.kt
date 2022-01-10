package com.ecoachsolutions.ecoachbooks

import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import com.ecoachsolutions.ecoachbooks.Helpers.Constants
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksApi
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksPreferences
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUrls


class ShoppingActivity : AppCompatActivity() {

    private val _webView: WebView by lazy { findViewById(R.id.webView) as WebView }
    private val _progressBar: ProgressBar by lazy { findViewById(R.id.pageLoadingProgressBar) as ProgressBar }
    private val _toolBar: Toolbar by lazy { findViewById(R.id.toolBar) as Toolbar }
    private val _ecoachBooksApi: EcoachBooksApi by lazy { EcoachBooksApi.getInstance(this) }
    private val _preferences by lazy { EcoachBooksPreferences(this) };

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shopping)
        // mProgressBar.getProgressDrawable().setColorFilter(getResources().getColor(R.color.ecoach_orange), PorterDuff.Mode.SRC_IN);
        //load the browser item
        setTitle(R.string.action_shop)
        setSupportActionBar(_toolBar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)


        val cookieManager = android.webkit.CookieManager.getInstance()
        var siteUrl = EcoachBooksUrls.SITE_URL;
        if (_preferences.shouldUseHttp()){
            siteUrl = siteUrl.replace("https://", "http://");
        }
        cookieManager.setCookie(siteUrl, "%s=%s".format(Constants.EC_BOOKS_COOKIE_AUTH_NAME, _ecoachBooksApi.getAuthToken()))
        cookieManager.setAcceptCookie(true)

        val webSettings = _webView.settings
        webSettings.javaScriptEnabled = true

        _webView.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return false
            }
        })
        _webView.setWebChromeClient(object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) {
                if (progress < 100 && _progressBar.visibility == View.GONE) {
                    //the item is loading and we need to show the loading bar
                    _progressBar.visibility = View.VISIBLE
                }
                _progressBar.progress = progress
                if (progress == 100) {
                    // done loading, hide the bar
                    _progressBar.visibility = View.GONE
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 21) {
            // AppRTC requires third party cookies to work
            android.webkit.CookieManager.getInstance().flush()
            cookieManager.setAcceptThirdPartyCookies(_webView, true)
        }

        _webView.loadUrl(EcoachBooksUrls.SITE_URL)


    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && _webView.canGoBack()) {
            _webView.goBack()
            return true
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event)
    }

    /* @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_shopping, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }*/
}

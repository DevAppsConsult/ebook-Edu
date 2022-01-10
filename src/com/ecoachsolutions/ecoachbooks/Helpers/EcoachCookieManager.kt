package com.ecoachsolutions.ecoachbooks.Helpers

import android.content.Context
import java.io.IOException
import java.net.CookieManager
import java.net.URI

/**
 * Created by Daniel on 2/21/2015.
 */
class EcoachCookieManager(val _context: Context) : CookieManager() {
    internal var webkitCookieManager: android.webkit.CookieManager = android.webkit.CookieManager.getInstance()
    private val _preferences by lazy { EcoachBooksPreferences(_context); }


    @Throws(IOException::class)
    override fun put(uri: URI, stringListMap: Map<String, List<String>>?) {
        super.put(uri, stringListMap)
        //String authCookie = Utils.GetAuthAuthTokenFromRetrofitResponse()
        if (stringListMap != null && stringListMap["Set-Cookie"] != null) {
            for (cookie in stringListMap["Set-Cookie"].orEmpty()) {
                val splitCookie = cookie.split("=".toRegex(), 2).toTypedArray()
                if (splitCookie[0] == Constants.EC_BOOKS_COOKIE_AUTH_NAME) {
                    _preferences.putString(Constants.EC_BOOKS_AUTH_TOKEN_TYPE, splitCookie[1]);
                    webkitCookieManager.setCookie(uri.toString(), splitCookie[1])
                }
            }
        }
    }
}

package com.ecoachsolutions.ecoachbooks.Helpers

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

//This isn't my original code. Taken from SO answer
class ObservableWebView : WebView {

    var onScrollChangedCallback: OnScrollChangedCallback? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    }

    private constructor(context: Context) : super(context) {
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (onScrollChangedCallback != null) {
            onScrollChangedCallback!!.onScroll(l, t)
        }
    }

    /**
     * Implement in the activity/fragment/view that you want to listen to the webview
     */
    interface OnScrollChangedCallback {
        fun onScroll(l: Int, t: Int)
    }
}

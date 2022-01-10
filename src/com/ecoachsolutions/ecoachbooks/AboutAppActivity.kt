package com.ecoachsolutions.ecoachbooks

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.Html
import android.text.method.LinkMovementMethod
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import kotlinx.android.synthetic.main.activity_about_app.*


class AboutAppActivity : AppCompatActivity() {

    var _appVersion = "0.8.0"
    var _logger = LogHelper(this);

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_app)
        //  mMitLicenseTextViewLink = (TextView) findViewById(R.id.mitLicenseLink);
        //this might be problematic as I can't use obfuscation on the apk until the lgpl dependency
        //is gone
        //   mIcons8TextViewLink = findViewById(R.id.icons8link) as TextView
        // mCreativeCommonsAttributionTextViewLink = findViewById(R.id.creativeCommonsLink) as TextView

        setTitle(R.string.action_about)

        //toolbar
        setSupportActionBar(toolBarInclude as Toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        //get app version, wrap in try-catch so you don't die
        try {
            _appVersion = packageManager.getPackageInfo(packageName, 0).versionName
        } catch (ex: Exception) {
            ex.printStackTrace()
            _logger.debug("Could not get version code. Defaulting to 0");
        }

        appVersionText.text = _appVersion

        apacheLicenseLink20.text = Html.fromHtml("<a href=\"http://www.apache.org/licenses/LICENSE-2.0.txt\">Apache License 2.0<a/>")
        apacheLicenseLink20.movementMethod = LinkMovementMethod.getInstance()

        //    mMitLicenseTextViewLink.setText(Html.fromHtml("<a href=\"http://opensource.org/licenses/MIT\">MIT License<a/>"));
        //    mMitLicenseTextViewLink.setMovementMethod(LinkMovementMethod.getInstance());

        /*  mIcons8TextViewLink.text = Html.fromHtml("<a href=\"http://icons8.com/\">- icons8<a/>")
          mIcons8TextViewLink.movementMethod = LinkMovementMethod.getInstance()

          mCreativeCommonsAttributionTextViewLink.text = Html.fromHtml("<a href=\"https://creativecommons.org/licenses/by-nd/3.0/\">- Creative Commons Attribution<a/>")
          mCreativeCommonsAttributionTextViewLink.movementMethod = LinkMovementMethod.getInstance()*/

    }
}

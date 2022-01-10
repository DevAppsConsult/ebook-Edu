package com.ecoachsolutions.ecoachbooks

import android.accounts.AccountManager
import android.app.Application
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.Helpers.getEcoachBooksAccount
import com.localytics.android.Localytics
import com.localytics.android.LocalyticsActivityLifecycleCallbacks
import io.fabric.sdk.android.Fabric

class EcoachBooksApplication : Application() {
    var _logger = LogHelper(this);
    override fun onCreate() {
        super.onCreate();
        _logger.debug("Setting up Fabric");
        var crashlytics = Crashlytics.Builder().core(CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build()).build();
        Fabric.with(this, crashlytics);
        var accountManager = AccountManager.get(this);
        var account = accountManager.getEcoachBooksAccount();

        _logger.debug("Initializing database...");
        EcoachBooksDatabase.initializeDb(applicationContext);

        _logger.debug("Setting up localytics");
        // Register LocalyticsActivityLifecycleCallbacks
        if (!BuildConfig.DEBUG) {
            registerActivityLifecycleCallbacks(LocalyticsActivityLifecycleCallbacks(this));
        }

        if (account != null) {
            var userName = accountManager.getUserData(account, "username");
            //log user info
            _logger.debug("Setting username to $userName");
            Crashlytics.setUserEmail(userName);

            if (!BuildConfig.DEBUG) {
                Localytics.setCustomerEmail(userName);
            }
        }



    }
}
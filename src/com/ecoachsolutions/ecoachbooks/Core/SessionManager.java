package com.ecoachsolutions.ecoachbooks.Core;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;

import com.ecoachsolutions.ecoachbooks.Helpers.Constants;

/**
 * Created by Daniel on 5/4/2014.
 */
public class SessionManager {
    private static SessionManager ourInstance = new SessionManager();
    private static SharedPreferences _prefs;
    private static AccountManager mAccountManager;
    public static SessionManager getInstance() {
        return ourInstance;
    }

    private SessionManager() {
        //Singleton constructor
    }

    public static boolean IsUserLoggedIn(Context context) {
        //if (Constants.DEV_MODE) { Constants.DEV_MODE = false; return false; }
        _prefs = context.getSharedPreferences(Constants.SHARED_PREFERENCES_FILE_NAME, 0);
        if (_prefs.contains(Constants.IS_USER_LOGGED_IN)) {
            //if it is present, check the set value
            return _prefs.getBoolean(Constants.IS_USER_LOGGED_IN, false);
        }
        return false;
    }

    public static boolean logUserIn(Context context) {
        _prefs = context.getSharedPreferences(Constants.SHARED_PREFERENCES_FILE_NAME, 0);
        SharedPreferences.Editor _editor = _prefs.edit();
        _editor.putBoolean(Constants.IS_USER_LOGGED_IN, true);
       return _editor.commit();
    }

    public static void logUserOut(Context context) {
        _prefs = context.getSharedPreferences(Constants.SHARED_PREFERENCES_FILE_NAME, 0);
        SharedPreferences.Editor _editor = _prefs.edit();
        if (_prefs.contains(Constants.IS_USER_LOGGED_IN)) {
            _editor.putBoolean(Constants.IS_USER_LOGGED_IN, false);
        }
        _editor.apply();
        mAccountManager = AccountManager.get(context);
        //Invalidate token here
        mAccountManager.invalidateAuthToken(Constants.EC_BOOKS_ACCNT_TYPE, null);
    }

    public static void UpdateUserLoginInfo() {
        //Updates the shared preferences with the user's logged in information.
        //In the future, this should use an SQLLite databse
    }
}

package com.ecoachsolutions.ecoachbooks.Helpers

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.ecoachsolutions.ecoachbooks.BuildConfig
import com.localytics.android.Localytics
import org.joda.time.DateTime
import org.joda.time.Hours
import org.joda.time.Minutes


class EcoachBooksPreferences(val context: Context) {
    val _sharedPrefs = context.getSharedPreferences(Constants.SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
    val _logger = LogHelper(this);

    fun clear() {
        _logger.debug("Clearing shared preferences!")
        _sharedPrefs.edit().clear().commit();
    }

    fun shouldSync() : Boolean {
        var lastSyncString = getString(PreferenceKeys.LAST_SYNC_ATTEMPT);
        if (TextUtils.isEmpty(lastSyncString)){
            _logger.debug("No value stored for last sync. Allowing sync!")
            return true
        }
        _logger.debug("Last sync was $lastSyncString")

        var timeSinceLastSync =  Minutes.minutesBetween(DateTime.parse(lastSyncString), DateTime.now()).minutes

        _logger.debug("It has been $timeSinceLastSync minutes since the last sync")
        return Math.abs(timeSinceLastSync) > 1
    }

    @Synchronized fun setLastSyncAttempt(){
        putString(PreferenceKeys.LAST_SYNC_ATTEMPT, DateTime.now().toString())
    }

    fun shouldUseHttp(): Boolean {
        return getBoolean(Constants.SHOULD_USE_HTTP, false);
    }

    fun disableUseHttp() {
        putBoolean(Constants.SHOULD_USE_HTTP, false);
        if (!BuildConfig.DEBUG) {
            Localytics.tagEvent(LocalyticsTags.DISABLED_HTTPS);
        }
    }

    fun enableUseHttp() {
        putBoolean(Constants.SHOULD_USE_HTTP, true);
    }


    //exposed methods (specific)
    fun saveTextZoom(newZoom: String) {
        putString(Constants.TEXT_SIZE, newZoom);
    }

    fun getTextZoom(): String {
        var zoom = getString(Constants.TEXT_SIZE);
        if (TextUtils.isEmpty(zoom)) {
            _logger.debug("No default zoom was set. Using default of ${Constants.DEFAULT_TEXT_ZOOM}");

            saveTextZoom(Constants.DEFAULT_TEXT_ZOOM.toString());
            zoom = Constants.DEFAULT_TEXT_ZOOM.toString();
        }

        return zoom;
    }

    //download version info
    fun setLastAppDownloadVersion(newVersion: String) {
        //this is stored without the periods.
        if (!TextUtils.isEmpty(newVersion)) {
            putString(Constants.LAST_SUCCESSFUL_APP_DOWNLOAD_VERSION_KEY, newVersion);
        }
    }

    fun getLastAppDownloadVersion(): String {
        return getString(Constants.LAST_SUCCESSFUL_APP_DOWNLOAD_VERSION_KEY);
    }

    //sync behavior
    fun setPeriodicSyncEnabled() {
        putBoolean(Constants.IS_PERIODIC_SYNC_SET, true);
    }

    fun isPeriodicSyncEnabled(): Boolean {
        return getBoolean(Constants.IS_PERIODIC_SYNC_SET, false);
    }

    fun getString(keyName: String): String {
        return _sharedPrefs.getString(keyName, "");
    }

    fun getBoolean(keyName: String, default: Boolean): Boolean {
        return _sharedPrefs.getBoolean(keyName, default);
    }

    fun getInt(keyName: String, default: Int): Int {
        return _sharedPrefs.getInt(keyName, default);
    }

    fun addToStringSet(keyName: String, value: String): Boolean {
        return withEditor(keyName, value, {
            editor ->
            var hashSet = _sharedPrefs.getStringSet(keyName, hashSetOf());
            hashSet.add(value);
            editor.putStringSet(keyName, hashSet);
        });
    }

    fun removeFromStringSet(keyName: String, value: String): Boolean {
        return withEditor(keyName, value, {
            editor ->
            var hashSet = _sharedPrefs.getStringSet(keyName, hashSetOf());
            hashSet.remove(value);
            editor.putStringSet(keyName, hashSet);
        });
    }

    fun putString(keyName: String, value: String): Boolean {
        return withEditor(keyName, value, {
            editor ->
            editor.putString(keyName, value);
        });
    }

    fun putInt(keyName: String, value: Int): Boolean {
        return withEditor(keyName, value, {
            editor ->
            editor.putInt(keyName, value);
        });
    }

    fun putBoolean(keyName: String, value: Boolean): Boolean {
        return withEditor(keyName, value, {
            editor ->
            editor.putBoolean(keyName, value);
        });
    }

    fun withEditor(keyName: String, value: Any, action: (editor: SharedPreferences.Editor) -> Unit): Boolean {
        _logger.debug("Writing $value to $keyName");
        var editor = _sharedPrefs.edit();
        action(editor);
        return editor.commit();
    }

}
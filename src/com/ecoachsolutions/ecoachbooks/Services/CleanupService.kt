package com.ecoachsolutions.ecoachbooks.Services

import android.app.IntentService
import android.content.Intent
import android.util.Log

import com.ecoachsolutions.ecoachbooks.Helpers.Constants
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils

import java.io.File

/**
 * Created by Daniel on 10/12/2014.
 */
class CleanupService : IntentService("CleanupService") {

    internal val tag = this.javaClass.name

    override fun onHandleIntent(intent: Intent) {
        /*Intend MUST contain:
        1. 'exclude' parameter specifying the Id of the book not to be wiped. If this is missing, the service
            will clean the entire cache folder which will cause any open book to fail.
     */
        var excludeId: String? = intent.getStringExtra(Constants.CLEANUP_SERVICE_EXCLUDE)
        excludeId = if (excludeId == null) "" else excludeId
        try {
            Log.d(tag, "Cleaning up cache and ignoring '%s'".format(excludeId))
            val cacheFolder = EcoachBooksUtils.getCacheBasePath(this)
            val files = File(cacheFolder).listFiles()

            for (f in files) {
                //If we have a file to exclude, ignore the cache folder.
                if (excludeId != null && !excludeId.isEmpty() && (f.name == excludeId + "_dec_tmp" || f.name == excludeId + "_dec")) {
                    continue
                }
                //else, delete the file/folder
                EcoachBooksUtils.deleteRecursive(f)
            }
        } catch (ex: Exception) {
            Log.e(tag, "Something went wrong. Check track trace below for " + ex.message)
            ex.printStackTrace()
        }

    }
}

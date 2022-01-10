package com.ecoachsolutions.ecoachbooks.Helpers

import android.R
import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import com.ecoachsolutions.ecoachbooks.Helpers.Constants
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUrls
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.Helpers.MasterSettings
import com.ecoachsolutions.ecoachbooks.Models.PingResponseEnum
import org.joda.time.DateTime
import retrofit.client.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/*Utility public functions for ecoach books 
 * 
 * Daniel Kotin
 */

object EcoachBooksUtils {

    /*
         * Meant to be used in a static way. Came up because stupid java has no
         * clean way for escaping strings
         */
    private val _logger = LogHelper(this);

    fun escapeString(s: String): String {
        return s.replace("\\", "\\\\")
    }

    /**
     * Calculates the appropriate inSampleSize of the images to be
     * loaded This is necessary to avoid out of memmory errors
     * @param options
     * *
     * @param targetHeight
     * *
     * @param targetWidth
     */
    fun calculateInSampleSize(options: BitmapFactory.Options,
                              targetWidth: Int, targetHeight: Int): Int {
        // targetWidth and targetHieght will be used to determine the approriate
        // sample size
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if ((height > targetHeight) || (width > targetWidth)) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            // loop until sample size is large enough to downsample properly
            while ((halfHeight / inSampleSize) > targetHeight && (halfWidth / inSampleSize) > targetWidth) {
                inSampleSize *= 2 // cut size down in half (preserving aspect
                // ratio);
            }
        }
        _logger.debug("Down sampling by: " + inSampleSize)
        return inSampleSize
    }


    fun decodeSampledBitmapFromFile(filePath: String,
                                    targetWidth: Int, targetHeight: Int,
                                    downsample: Boolean): Bitmap {
        val options = BitmapFactory.Options()
        if (downsample) {
            options.inJustDecodeBounds = true // tells it to only return the
            // boundaries of bitmap
            BitmapFactory.decodeFile(filePath, options)

            // calculate correct sample size
            options.inSampleSize = calculateInSampleSize(options, targetWidth,
                    targetHeight)

            // actually decode the bitmap sampled down to correct size
            options.inJustDecodeBounds = false // disable so it actually decodes
        }
        // the full bitmap
        return BitmapFactory.decodeFile(filePath, options)

    }

    fun makeEcoachBooksPath(path: String): File? {
        val ecoachbooksdir = File(Environment.getExternalStorageDirectory().absolutePath + File.separator + path)

        if (ecoachbooksdir.mkdirs() || ecoachbooksdir.isDirectory) {
            return ecoachbooksdir
        } else {
            _logger.error("Error in creating directory!")
            return null
        }
    }

    fun makeYesNoAlert(context: Context, messageResourceId: Int, positiveClickListsner: (dialog: DialogInterface, id: Int) -> Unit): AlertDialog {
        var builder = AlertDialog.Builder(context);
        builder.setPositiveButton(R.string.yes, positiveClickListsner);

        builder.setNegativeButton(R.string.no, fun(dialog, id) {
            dialog.cancel()
        })

        builder.setMessage(messageResourceId);

        return builder.create();
    }

    fun makeOkAlert(context: Context, titleResourceId: Int, message: String, okClickListener: (dialog: DialogInterface, id: Int) -> Unit = { dialog, which -> }): AlertDialog {
        var builder = AlertDialog.Builder(context);
        builder.setNeutralButton(R.string.ok, okClickListener);
        builder.setTitle(titleResourceId)
        builder.setMessage(message);
        builder.setCancelable(false);

        return builder.create();
    }


    fun getPreviews(path: String): Array<String> {
        val folder = File(path)
        folder.mkdirs()
        val files = folder.listFiles()
        val filepaths = ArrayList<String>()

        for (i in files.indices) {
            filepaths.add(files[i].absolutePath)
        }
        val results = arrayOfNulls<String>(filepaths.size)
        return filepaths.toArray<String>(results)
    }

    fun getFiles(path: String): Array<File> {
        val folder = File(path)
        val files = folder.listFiles()

        return files
    }


    fun showInfoDialog(message: String, context: Context) {
        val builder = AlertDialog.Builder(context)

        builder.setMessage(message).setCancelable(false).setPositiveButton("OK", { dialog, which -> /* Do nothing */ })
        builder.show()
    }

    fun writeStreamToToDisk(inStream: InputStream?, absFileName: String) {
        if (inStream == null) {
            _logger.debug("Cover stream is null. doing nothing");
            return;
        }
        var outStream: FileOutputStream? = null;
        try {
            outStream = FileOutputStream(absFileName);
            outStream.write(inStream.readBytes());
            _logger.debug("Wrote to $absFileName");
        } catch (ex: Exception) {
            _logger.error("Could not write file $absFileName", ex);
        } finally {
            inStream.close();
            outStream?.close();
        }
    }

    fun deleteEncryptedBookFromDisk(context: Context, id: String) {
        val outPath = getEncryptedFilePath(context, id)
        deleteRecursive(File(outPath))
    }

    fun addOrUpdateStringSetting(prefs: SharedPreferences, key: String, data: String) {
        val editor = prefs.edit()
        editor.putString(key, data)
        editor.apply()
    }

    fun addOrUpdateIntSetting(prefs: SharedPreferences, key: String, data: Int) {
        val editor = prefs.edit()
        editor.putInt(key, data)
        editor.apply()
    }

    fun encrypt(encKey: ByteArray, input: ByteArray): ByteArray {
        //key = password + bookId. This ensures that the encryption key is unique for each book

        val keyspec = SecretKeySpec(encKey, "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, keyspec)
        val encryptedData = cipher.doFinal(input)

        return encryptedData
    }

    fun decrypt(encKey: ByteArray, input: ByteArray): ByteArray {
        val keyspec = SecretKeySpec(encKey, "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, keyspec)
        return cipher.doFinal(input)
    }

    //NOTE: This method must generate the SAME byte array for the same key as it is used for input and output
    fun makeKey(key: String): ByteArray {
        var keybytes = key.toByteArray(charset("UTF-8"))
        val sha1 = MessageDigest.getInstance("SHA-1")

        keybytes = sha1.digest(keybytes)
        return Arrays.copyOf(keybytes, 16) //returns a 128-bit key
    }

    fun isBookDownloaded(bookId: String, prefs: SharedPreferences): Boolean {
        val downloadedBks = prefs.getStringSet(Constants.BOOKS_DOWNLOADED, HashSet<String>())
        return downloadedBks.contains(bookId)
    }

    fun isBookDownloaded(bookId: String, ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(ctx.getString(com.ecoachsolutions.ecoachbooks.R.string.book_preferences), Context.MODE_PRIVATE)
        return isBookDownloaded(bookId, prefs)
    }

    fun markBookAsDownloaded(bookId: String, prefs: SharedPreferences): Boolean {

        val downloadedBks = prefs.getStringSet(Constants.BOOKS_DOWNLOADED, HashSet<String>())
        val editor = prefs.edit()
        downloadedBks.add(bookId)
        editor.putStringSet(Constants.BOOKS_DOWNLOADED, downloadedBks)

        return editor.commit()
    }

    fun unMarkBookAsDownload(bookId: String, prefs: SharedPreferences): Boolean {
        val downloadedBks = prefs.getStringSet(Constants.BOOKS_DOWNLOADED, HashSet<String>())
        val editor = prefs.edit()
        downloadedBks.remove(bookId)

        editor.putStringSet(Constants.BOOKS_DOWNLOADED, null) //this null hack is needed because the changes don't always save. no idea why
        editor.commit()

        editor.putStringSet(Constants.BOOKS_DOWNLOADED, downloadedBks)
        return editor.commit()
    }

    fun getEncryptedFilePath(ctx: Context, bookId: String): String {
        return ctx.filesDir.absolutePath + File.separator + ctx.getString(com.ecoachsolutions.ecoachbooks.R.string.ecoachbooks_path) + File.separator + bookId
    }

    fun deleteStoredAppData(ctx: Context) {
        deleteRecursive(File(ctx.filesDir.absolutePath + File.separator + "ecoachbooks"))
    }

    fun getDecryptedFilePath(ctx: Context, bookId: String): String {
        return ctx.filesDir.absolutePath + File.separator + ctx.getString(com.ecoachsolutions.ecoachbooks.R.string.ecoachbookscache_path) + File.separator + bookId
    }

    fun getCacheBasePath(ctx: Context): String {
        return ctx.filesDir.absolutePath + File.separator + ctx.getString(com.ecoachsolutions.ecoachbooks.R.string.ecoachbookscache_path)
    }

    fun getExtractedFolderPath(ctx: Context, bookId: String): String {
        return getDecryptedFilePath(ctx, bookId) + "_tmp"
    }

    fun getDeviceId(ctx: Context): String {
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
    }

    //Static method to help with the generation of requests depending on whether we're hitting dev, staging or prod.
    //Use secure connections for ALL non dev machines.
    fun makeSecureConnection(urlString: String, method: String, authToken: String?, additionalHeaders: HashMap<String, String>?): HttpsURLConnection? {
        try {
            val urlObj = URL(urlString)
            val connection = urlObj.openConnection() as HttpsURLConnection

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, null, SecureRandom())
            connection.sslSocketFactory = sslContext.socketFactory

            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-type", "application/json")
            connection.setRequestProperty(Constants.EC_HEADER_APP_TYPE, Constants.EC_MOBILE_APP_TYPE) //we need this to handle special cases for mobile app
            connection.setRequestProperty(Constants.EC_HEADER_API_KEY, Constants.EC_API_KEY) //we need this to handle special cases for mobile app
            if (authToken != null && !authToken.isEmpty()) {
                connection.setRequestProperty("Cookie", "${Constants.EC_BOOKS_COOKIE_AUTH_NAME}=$authToken")
            }
            connection.doInput = true
            if (method.toLowerCase() == "post" || method.toLowerCase() == "put") {
                connection.requestMethod = "POST"
                connection.doOutput = true
            }

            if (additionalHeaders != null) {
                for (key in additionalHeaders.keys) {
                    connection.setRequestProperty(key, additionalHeaders[key])
                }
            }

            return connection
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

    }

    //Use insecure connections for dev machines.
    fun makeUnsecureConnection(urlString: String, method: String, authToken: String?, additionalHeaders: HashMap<String, String>?): HttpURLConnection? {
        try {
            val urlObj = URL(urlString)
            val connection = urlObj.openConnection() as HttpURLConnection

            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-type", "application/json")
            connection.setRequestProperty(Constants.EC_HEADER_APP_TYPE, Constants.EC_MOBILE_APP_TYPE) //we need this to handle special cases for mobile app
            connection.setRequestProperty(Constants.EC_HEADER_API_KEY, Constants.EC_API_KEY) //we need this to handle special cases for mobile app
            connection.setRequestProperty("Cookie", "${Constants.EC_BOOKS_COOKIE_AUTH_NAME}=$authToken")
            connection.doInput = true

            if (method.toLowerCase() == "post" || method.toLowerCase() == "put") {
                connection.requestMethod = "POST"
                connection.doOutput = true
            }

            if (additionalHeaders != null) {
                for (key in additionalHeaders.keys) {
                    connection.setRequestProperty(key, additionalHeaders[key])
                }
            }

            return connection
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

    }


    fun saveAccountBalance(ctx: Context, balance: String) {
        val prefs = ctx.getSharedPreferences(ctx.getString(com.ecoachsolutions.ecoachbooks.R.string.book_preferences), Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(Constants.ACCOUNT_BALANCE_KEY, balance)
        editor.commit()
    }

    fun getSavedAccountBalance(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(ctx.getString(com.ecoachsolutions.ecoachbooks.R.string.book_preferences), Context.MODE_PRIVATE)
        return prefs.getString(Constants.ACCOUNT_BALANCE_KEY, "0.00")
    }

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = cm.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
    }

    fun isWifiConnected(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        return wifiInfo.isConnected
    }

    fun canDownload(ctx: Context): Boolean {
        //0 - wifi, 1 - wifi and data, -1 = [deprecated] off
        val prefs = ctx.getSharedPreferences(ctx.getString(com.ecoachsolutions.ecoachbooks.R.string.book_preferences), Context.MODE_PRIVATE)
        val downloadMode = prefs.getInt(Constants.AUTOMATIC_UPDATE_MODE, 0)
        when (downloadMode) {
            0,-1 -> return isWifiConnected(ctx)
            1 -> return isOnline(ctx)
        }
        return false
    }

    fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            for (f in fileOrDirectory.listFiles()) {
                deleteRecursive(f)
            }
        }
        //We're on a file now, safe to just call .delete
        fileOrDirectory.delete()
    }

    fun getAuthTokenFromRetrofitResponse(response: Response): String? {

        for (header in response.headers) {
            if (header.name != null && header.name == "Set-Cookie") {
                val splitCookie = header.value.split(Regex.fromLiteral("="), 2)
                if (splitCookie[0] == Constants.EC_BOOKS_COOKIE_AUTH_NAME) {
                    return splitCookie[1]
                }
            }
        }
        return null
    }
/*

    fun getCondensedTimeBetweenDates(pastDate: DateTime, newDate: DateTime) : String {


    }
*/

}

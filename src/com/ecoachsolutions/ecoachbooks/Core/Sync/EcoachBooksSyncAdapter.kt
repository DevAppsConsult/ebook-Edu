package com.ecoachsolutions.ecoachbooks.Core.Sync

import android.accounts.Account
import android.accounts.AccountManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.util.Base64
import android.widget.Toast
import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.*
import com.ecoachsolutions.ecoachbooks.HomeActivity
import com.ecoachsolutions.ecoachbooks.Models.BookDto
import com.ecoachsolutions.ecoachbooks.Models.BookMetadata
import com.ecoachsolutions.ecoachbooks.Models.PurchasedBook
import com.ecoachsolutions.ecoachbooks.R
import com.ecoachsolutions.ecoachbooks.Services.CommentUploadService
import java.io.*
import java.util.*

/**
 * Sync adapter responsible for:
 * 1. Uploading content (Comments, etc.)
 * 2. Checking for new books
 * 3. Checking for updates to books
 */

class EcoachBooksSyncAdapter : AbstractThreadedSyncAdapter {

    constructor(context: Context, autoInitialize: Boolean, allowParallelSyncs: Boolean) : super(context, autoInitialize, allowParallelSyncs) {
    }

    constructor(context: Context, autoInitialize: Boolean) : super(context, autoInitialize) {
    }

    private val _logger = LogHelper(this);
    private val _ecoachBooksApi: EcoachBooksApi by lazy { EcoachBooksApi.getInstance(context); }
    private val _booksManager: BooksManager by lazy { BooksManager(context); }
    private val _accountManager: AccountManager by lazy { AccountManager.get(context) }
    private val _preferences: EcoachBooksPreferences by lazy { EcoachBooksPreferences(context); }
    private val lock = Any()

    override fun onPerformSync(account: Account?, extras: Bundle?, authority: String?, provider: ContentProviderClient?, syncResult: SyncResult?) {
        var isManualSync = extras?.getBoolean(Constants.IS_MANUAL_SYNC, false)?: false
        _logger.debug("Performing sync for account ${account.toString()}. Is manual sync: $isManualSync")
        //todo: fix sync adapter synchronization. Need to ensure we set LastSyncAttempt or IsSyncing property to prevent multiple calls
        synchronized(lock,  {
            if (!isManualSync){
                _logger.debug("Not manual sync. Checking whether or not to perform sync to prevent calls being made too close to each other")
                if (!_preferences.shouldSync()){
                    _logger.debug("Should sync was false. Aborting")
                    return;
                } else{
                    _logger.debug("Should sync was true! Performing sync")
                    _preferences.setLastSyncAttempt()
                }
            }
        })



        //0. Only work if connection is valid.
        _ecoachBooksApi.assertValidConnection(_accountManager, { error ->
            _logger.debug("Account cookie doesn't seem to be valid. Aborting sync.")
            //    LocalBroadcastManager.getInstance(this).sendBroadcast(Intent("do-login"));
        }, {
            _logger.debug("Account cookie is valid. Initializing sync actions.")
            //1. Fetch new books (so store last sync date in shared prefs or something)
            try {
                checkForNewBooks();
                uploadPendingComments();
                //checkForAppUpdates();
            } catch (ex: Exception) {
                _logger.error("Sync failed fatally!", ex);
            }
        });


    }

    private fun uploadPendingComments() {
        _logger.debug("Uploading pending comments...");
        var pendingComments = EcoachBooksDatabase.getInstance().getUploadableComments();
        _logger.debug("Found ${pendingComments.size} comments that need to be uploaded. Sending message to begin work")
        pendingComments.forEach { comment ->
            var commentUploadIntent = Intent(context, CommentUploadService::class.java)
            commentUploadIntent.putExtra(Constants.COMMENT_ID, comment);
            context.startService(commentUploadIntent);
        }
    }

    private fun checkForNewBooks() {
        _logger.debug("Checking for new books.... Current book ids from db: ${EcoachBooksDatabase.getInstance().currentBookIds}");
        _ecoachBooksApi.getBooksDistinctObservable(EcoachBooksDatabase.getInstance().currentBookIds)
                .retry(2)
                .subscribe({ getBooksResponse ->
                    processNewBooksList(getBooksResponse);
                }, { error ->
                    _logger.error("SyncAdapter failed to get new books :(", error);
                });

    }

    private fun checkForAppUpdates() {
        var currentAppVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName;
        var newVersion = Integer.parseInt(currentAppVersion!!.replace(".", ""));

        _logger.debug("Looking for updated versions of the app. Current version is $currentAppVersion");
        _ecoachBooksApi.getLatestAppVersion()
                .flatMap({ newAppVersion ->
                    newVersion = Integer.parseInt(newAppVersion.replace(".", ""))
                    val oldVersion = Integer.parseInt(currentAppVersion.replace(".", ""))

                    if (newVersion < oldVersion) {
                        _logger.debug("Server version of the app is $newAppVersion but existing version is $currentAppVersion. Will not update.");
                        Toast.makeText(context, context.getString(R.string.msg_has_latest_version), Toast.LENGTH_SHORT).show()
                        null
                    } else {
                        _logger.debug("Server version of the app is $newAppVersion but existing version is $currentAppVersion. Attempting to download update.");
                        _ecoachBooksApi.downloadApk()
                    }
                }).subscribe({ response ->
            if (response != null) {
                try {
                    _logger.debug("Processing response from DownloadApk call. Download size: " + response.body.length())
                    saveAndInstallApk(response.body.`in`(), newVersion.toString());
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        })

    }

    private fun writeBookCoversToDisk(mybooks: List<PurchasedBook>?): List<BookDto>? {
        if (mybooks == null || mybooks.isEmpty()) {
            //Raise something to show the "you have no books" message.
            return null
        }
        //track book Ids that have been successfully written
        val savedBooks = Collections.synchronizedList(ArrayList<BookDto>())

        Parallel.parallelFor(mybooks, object : Parallel.Operation<PurchasedBook> {
            override fun perform(pParameter: PurchasedBook) {

                try {
                    val f = context.getString(R.string.ecoachbookcovers_path)
                    val cacheDir = File("${context.filesDir}${File.separator}$f")
                    cacheDir.mkdirs()

                    val outFile = cacheDir.absolutePath + File.separator + pParameter.Id
                    _logger.debug("Writing book ${pParameter.Id} to $outFile")
                    val fos = BufferedOutputStream(FileOutputStream(outFile))
                    fos.write(Base64.decode(pParameter.CoverImage, Base64.DEFAULT))
                    fos.flush() // probably not necessary
                    fos.close()
                    //update the concurrent list on success

                    val dto = BookDto()
                    dto.Id = pParameter.Id
                    dto.AuthorName = pParameter.AuthorName
                    dto.Title = pParameter.Title

                    savedBooks.add(dto)

                } catch (ex: Exception) {
                    _logger.error("Failed to create cover path for book :( : " + pParameter.Id + ". Message: " + ex.message, ex)
                }

            }
        })

        _logger.debug("Successfully saved the covers for ${savedBooks.size} books out of ${mybooks.size}");
        return savedBooks;
    }

    private fun processNewBooksList(resp: BooksManager.GetMyBooksResponse?) {
        _logger.debug("getMybooks response: ${resp.toString()}");

        if (resp == null) {
            return
        }

        if ((resp.success!!.not())) {
            _logger.debug("Could not retrieve books: ${resp.message}");
            return
        }

        val unDownloadedLibraryBookIds = _booksManager.idsOfUnDownloadedBooks

        if (unDownloadedLibraryBookIds.size > 0) {
            //todo: there are undownloaded books, try to download them
            _logger.debug("Found some un downloaded books! $unDownloadedLibraryBookIds")
        }

        val bookDtos = writeBookCoversToDisk(resp.books)
        //When covers are on disk, tell the wrapper to update library view with the new images
        if ((bookDtos == null || bookDtos.isEmpty()) && resp.books.isNotEmpty() ) {
            return;
        }
        //Update database with new books
        _logger.debug("Attempting to add ${resp.books.size} new books to the database.");
        //  _booksManager.updateLibraryWithNewBooks(bookDtos)

        bookDtos?.forEach {
            bk ->
            EcoachBooksDatabase.getInstance().addOrUpdateBook(BookMetadata("", bk.AuthorName, bk.Title, bk.Id))
        }

        _logger.debug("Done saving books to database. Sending broadcast to add to UI");

        context.sendBroadcast(Intent(BroadcastMessages.BROADCAST_ADD_NEW_BOOK_TO_UI).putStringArrayListExtra(Constants.NEW_BOOK_IDS, ArrayList(if (bookDtos == null) ArrayList<String>() else bookDtos.map { dto -> dto.Id })))

    }

    //todo: raise notification so user can install the newest version
    private fun saveAndInstallApk(inStream: InputStream, newAppVersion: String) {
        //intentionally using the same name so it overwrites.
        val outFile = File(context.filesDir, "ecoachbooks_release.apk");
        val os = FileOutputStream(outFile)
        try {
            _logger.debug("Writing new app version to disk");
            inStream.copyTo(os);
            //success? set success
            _preferences.setLastAppDownloadVersion(newAppVersion);
            showNotificationForAppUpdate();
        } catch (ex: Exception) {
            _logger.error("Could not save apk to disk :(. " + ex);
        } finally {
            os.close();
            inStream.close();
        }

    }

    /*TODO:
        1. Fix notification icon
        2. Make update check happen once every 24 hours?
        3. Figure out why writing the new file to disk fails. ;_;
        4. Fix logic before prod release.
     */
    private fun showNotificationForAppUpdate() {
        val notificationBuilder = NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(context.getString(R.string.title_update_found))
                .setContentText(context.getString(R.string.msg_new_app_update));

        val intent = Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(File(context.getExternalFilesDir(null), "ecoachbooks_release.apk")), "application/vnd.android.package-archive")
        //context.applicationContext.startActivity(intent);

        var stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addParentStack(HomeActivity::class.java);
        stackBuilder.addNextIntent(intent);

        var pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        _logger.debug("Showing notification for app update!");

        notificationBuilder.setContentIntent(pendingIntent);

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(Notification_Keys.INSTALL_APP_UPDATE_NOTIFICATION, notificationBuilder.build());


    }


}
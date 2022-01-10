package com.ecoachsolutions.ecoachbooks.Services

import android.app.IntentService
import android.content.Intent
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.ecoachsolutions.ecoachbooks.Core.Database.DatabaseUtilities
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils
import com.ecoachsolutions.ecoachbooks.Helpers.Constants
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksApi
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.R
import rx.schedulers.Schedulers

/**
 * Upload comments made by the user
 */

class CommentUploadService : IntentService(CommentUploadService::class.java.name) {

    private val _logger = LogHelper(this);
    private val _ecoachApi by lazy { EcoachBooksApi.getInstance(applicationContext); }

    override fun onHandleIntent(intent: Intent?) {
        _logger.debug("Handling intent [$intent]");
        if (!EcoachBooksUtils.isOnline(applicationContext)){
            _logger.warn("Device is currently not online. Comment will be uploaded during the next sync cycle.");
        }
        var commentId = intent?.extras?.getLong(Constants.COMMENT_ID, -1L);
        if (commentId == -1L || commentId == null) {
            _logger.error("Comment Id was -1. Maybe something went wrong when trying to save the comment to the database :(");
            return;
        }

        var dbComment = EcoachBooksDatabase.getInstance().getCommentById(commentId);
        if (dbComment.status == DatabaseUtilities.UploadableItemStatus.Uploaded || dbComment.status == DatabaseUtilities.UploadableItemStatus.UploadFailed3){
            _logger.error("Excepted comment id $commentId to not be uploaded or failed for the 3rd time but status was ${dbComment.status}");
            return;
        }

        _logger.debug("About to upload comment id: $commentId, comment: $dbComment");


        _ecoachApi.addComment(dbComment)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({
                    _logger.debug("Successfully saved comment to site. Updating and deleting from database...");
                    try {
                        EcoachBooksDatabase.getInstance().updateCommentStatus(commentId, DatabaseUtilities.UploadableItemStatus.Uploaded);
                        EcoachBooksDatabase.getInstance().deleteComment(commentId);
                    } catch (ex: Exception) {
                        Crashlytics.logException(ex);
                        _logger.error("Failed to delete comment from database after successful upload! ")
                    }
                }, { error ->
                    _logger.error("Failed to upload comment $dbComment, id: $commentId", error);
                    Crashlytics.logException(error);
                    Toast.makeText(this, R.string.error_failed_to_post_comment, Toast.LENGTH_SHORT).show();
                    when(dbComment.status){
                      DatabaseUtilities.UploadableItemStatus.Pending -> EcoachBooksDatabase.getInstance().updateCommentStatus(commentId, DatabaseUtilities.UploadableItemStatus.UploadFailed1)
                      DatabaseUtilities.UploadableItemStatus.UploadFailed1 -> EcoachBooksDatabase.getInstance().updateCommentStatus(commentId, DatabaseUtilities.UploadableItemStatus.UploadFailed2)
                      DatabaseUtilities.UploadableItemStatus.UploadFailed2 -> EcoachBooksDatabase.getInstance().updateCommentStatus(commentId, DatabaseUtilities.UploadableItemStatus.UploadFailed3)
                        else -> {
                            _logger.error("Comment was not in expected state, forcing failure.")
                            EcoachBooksDatabase.getInstance().updateCommentStatus(commentId, DatabaseUtilities.UploadableItemStatus.UploadFailed3);
                        }
                    }
                })

    }

}

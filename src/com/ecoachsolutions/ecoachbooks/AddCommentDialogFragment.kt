package com.ecoachsolutions.ecoachbooks

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatDialogFragment
import android.text.TextUtils
import android.widget.EditText
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.ecoachsolutions.ecoachbooks.Core.Database.Comment
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.Constants
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.Services.CommentUploadService
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.concurrent.thread

class AddCommentDialogFragment : AppCompatDialogFragment() {
    private val _logger = LogHelper(this);
    var BookId = "";

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        var builder = AlertDialog.Builder(activity);
        var customView = activity.layoutInflater.inflate(R.layout.add_comment_dialog, null);

        builder.setView(customView);
        // builder.setMessage("weeee");
        builder.setTitle(R.string.prompt_comment);
        builder.setPositiveButton(R.string.prompt_submit, { dialog, which ->
            var editText = customView.findViewById(R.id.newCommentEditText) as EditText;
            var newComment = editText.text;
            _logger.debug("Positive click! With input: $newComment. Activity: $activity, Context: $context");
            Snackbar.make(activity.findViewById(R.id.readerAddCommentButton),R.string.msg_saving_comment, Snackbar.LENGTH_SHORT).show();
            var activityStore = activity;
            thread {
                try {
                    if (!TextUtils.isEmpty(newComment)) {
                        var comment = Comment();
                        comment.BookId = BookId;
                        comment.Comment = newComment.toString();
                        comment.DateCreated = DateTime.now(DateTimeZone.UTC).toString();
                        var commentId = EcoachBooksDatabase.getInstance().addComment(comment);
                        if (!EcoachBooksUtils.isOnline(activityStore)){
                            activityStore.runOnUiThread { Toast.makeText(activityStore,R.string.msg_comment_will_be_uploaded_when_online,Toast.LENGTH_LONG).show() }
                        } else{
                            try {
                                _logger.debug("Sending message to start comment uploader service");
                                //user is online, send message to kick off intent
                                var commentUploadIntent = Intent(activityStore, CommentUploadService::class.java)
                                commentUploadIntent.putExtra(Constants.COMMENT_ID, commentId);
                                activityStore.startService(commentUploadIntent);
                            } catch (ex: Exception) {
                                _logger.error("Failed to trigger intent service for upload! Will upload file as part of sync cycle", ex);
                                Crashlytics.logException(ex);
                            }
                        }
                    }
                } catch (ex: Exception) {
                    _logger.error("Failed to save comment!", ex)
                }
            };

            dialog.dismiss();
        })

        builder.setNegativeButton(android.R.string.cancel, { dialog, which ->
            _logger.debug("Negative button  click. byee!");
            dialog.dismiss();
        })

        return builder.create();
    }

}
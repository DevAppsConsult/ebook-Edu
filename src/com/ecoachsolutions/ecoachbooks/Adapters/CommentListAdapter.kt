package com.ecoachsolutions.ecoachbooks.Adapters

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.Models.BookCommentDisplayDto
import com.ecoachsolutions.ecoachbooks.R
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.util.*


class CommentListAdapter(var comments : ArrayList<BookCommentDisplayDto>, val context: Context) : RecyclerView.Adapter<CommentViewHolder>(){
    private val _logger = LogHelper(this)

    init {
        _logger.debug("Initialized comment list with ${comments.size} comments")
        if (comments.isEmpty()){
            _logger.debug("No comments for book. Adding empty state comment")
            comments.add(BookCommentDisplayDto(context.getString(R.string.msg_no_comments),
                                                context.getString(R.string.title_no_comments), DateTime.now()))
        }
    }

    override fun getItemCount(): Int {
        return comments.count()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder? {
        var viewToInflate = LayoutInflater.from(parent.context).inflate(R.layout.comment, parent, false)
        return CommentViewHolder(viewToInflate)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        var comment = comments[position]
        holder.commentContentsTextView.text = comment.comment
        var localDateTime = comment.commentDate.toLocalDateTime()
        holder.commenterAuthorInfoTextView.text = "${comment.authorName} - ${localDateTime.toString(DateTimeFormat.shortDateTime())}"
    }

}


class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val commenterAuthorInfoTextView: TextView
    val commentContentsTextView: TextView
    init {
        commenterAuthorInfoTextView = itemView.findViewById(R.id.authorInfo) as TextView
        commentContentsTextView = itemView.findViewById(R.id.commentContent) as TextView
        commentContentsTextView.movementMethod = android.text.method.ScrollingMovementMethod()
    }
}
package com.ecoachsolutions.ecoachbooks.Core.Database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper

object CommentsTable {
    val _logger = LogHelper(this);
    val TableName = "Comments";
    val Column_BookId = "BookId";
    val Column_DateCreated = "DateCreated";
    val Column_Comment = "Comment";
    val Column_Status = "Status";


    fun onCreate(db: SQLiteDatabase) {
        _logger.debug("Creating $TableName");

        var createScript = """Create Table if not exists $TableName (
                                _id integer primary key autoincrement,
                                $Column_BookId text,
                                $Column_DateCreated text,
                                $Column_Comment text,
                                $Column_Status integer default 0
                            )""";

        db.execSQL(createScript);

    }

    fun onUpgrade(db: SQLiteDatabase) {
    }

}

class Comment : BaseColumns {
    var BookId = "";
    var DateCreated = "";
    var Comment = "";
    var Status = DatabaseUtilities.UploadableItemStatus.Pending;

    companion object {
        fun fromCursor(cursor: Cursor) : Comment {
            var comment = Comment();
            comment.BookId = cursor.getString(0);
            comment.DateCreated = cursor.getString(1);
            comment.Comment = cursor.getString(2);
            comment.Status = DatabaseUtilities.UploadableItemStatus.values()[cursor.getInt(3)];

            return comment;
        }
    }
}

fun Comment.asContentValues() : ContentValues {
    var result = ContentValues();
    result.put(CommentsTable.Column_BookId, BookId);
    result.put(CommentsTable.Column_DateCreated, DateCreated);
    result.put(CommentsTable.Column_Comment, Comment);
    result.put(CommentsTable.Column_Status, Status.ordinal.toString());

    return result;
}


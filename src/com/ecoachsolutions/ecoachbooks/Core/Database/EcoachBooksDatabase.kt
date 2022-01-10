package com.ecoachsolutions.ecoachbooks.Core.Database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.Models.BookMetadata
import com.ecoachsolutions.ecoachbooks.Models.CommentDto
import com.ecoachsolutions.ecoachbooks.Models.asContentValues
import java.util.*


class EcoachBooksDatabase private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, EcoachBooksDatabase.DatabaseName, null, EcoachBooksDatabase.DatabaseVersion) {
    private val _logger = LogHelper(this);

    val currentBookIds: List<String>
        get() {
            val query = "SELECT $keyRemoteId FROM $booksTable"

            val db = readableDatabase
            val cursor = db.rawQuery(query, null)
            val results = ArrayList<String>()

            if (cursor.moveToFirst()) {
                do {
                    results.add(cursor.getString(0))
                } while (cursor.moveToNext())
            }
            cursor.close()
            return results
        }

    val allBooks: List<BookMetadata>
        get() {
            val bookMetadatas = LinkedList<BookMetadata>();
            val query = "SELECT * FROM " + booksTable;

            val db = this.writableDatabase;
            val cursor = db.rawQuery(query, null);

            var bk: BookMetadata
            if (cursor.moveToFirst()) {
                do {
                    bk = getBookFromCursor(cursor)
                    bookMetadatas.add(bk)
                } while (cursor.moveToNext())
            }
            cursor.close()
            return bookMetadatas
        }


    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        //Raw sql to create table
        _logger.debug("Creating database...");

        val createTable = "Create Table if not exists books (" + "id INTEGER PRIMARY KEY AUTOINCREMENT," + "path TEXT," + "author TEXT," + "title TEXT," + "remoteId TEXT," + "lastChapter TEXT," + "hash TEXT," + "scrollPosition TEXT)"
        CommentsTable.onCreate(sqLiteDatabase);
        //create table
        sqLiteDatabase.execSQL(createTable)
        _logger.debug("Done creating database.")
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, i: Int, i2: Int) {
        //Drop existing ones
        //   sqLiteDatabase.execSQL("DROP TABLE IF EXISTS books")

        //create a fresh one
        this.onCreate(sqLiteDatabase);

        CommentsTable.onUpgrade(sqLiteDatabase);
    }

    fun closeDatabase() {
        close();
        _logger.debug("Successfully closed database");
    }

    //Books
    fun getBookById(id: Int): BookMetadata {
        val db = readableDatabase

        val cursor = db.query(booksTable, // table
                columns, //column names
                " id = ?", //selections
                arrayOf(id.toString()), //selection arguments
                null, //group by
                null, //having
                null, //order by
                null) //limit

        cursor?.moveToFirst()

        val result = getBookFromCursor(cursor)
        cursor?.close()
        return result
    }

    fun addOrUpdateBook(bk: BookMetadata) {

        if (getBookByRemoteId(bk.remoteId) == null) {
            addBook(bk)
        } else {
            updateBook(bk)
        }

    }

    fun getBookByRemoteId(id: String): BookMetadata? {
        val db = readableDatabase
        val query = "SELECT * from $booksTable WHERE remoteId = '$id'"


        /*   Cursor cursor = db.query(booksTable, // table
                columns,       //column names
                " remoteId = ?",      //selections
                new String[]{id}, //selection arguments
                null,   //group by
                null,   //having
                null,  //order by
                null); //limit*/

        val cursor = db.rawQuery(query, null)

        if (cursor != null && cursor.moveToFirst()) {
            cursor.moveToFirst()
        } else {
            return null// book not found
        }

        val result = getBookFromCursor(cursor)
        cursor.close()
        // db.close();
        return result
    }

    fun getFilteredBooks(content: String): List<BookMetadata> {
        val bookMetadatas = LinkedList<BookMetadata>()
        val cursor = writableDatabase.query(booksTable, null, "$keyTitle like ? OR $keyAuthor like ?",
                arrayOf("%$content%", "%$content%"), null, null, null);

        var bk: BookMetadata
        if (cursor.moveToFirst()) {
            do {
                bk = getBookFromCursor(cursor)
                bookMetadatas.add(bk)
            } while (cursor.moveToNext())
        }
        cursor.close()
        // db.close();
        return bookMetadatas
    }

    fun addBook(bookMetadata: BookMetadata) {
        val db = this.writableDatabase;
        _logger.debug("Attempting to add book ${bookMetadata.id} to database");
        //Do not try to add a book if it already exists (by remote id). Log.
        val existing = getBookByRemoteId(bookMetadata.remoteId);

        if (existing != null) {
            _logger.debug("Book already exists and will not be re-added: " + bookMetadata.remoteId)
            return
        }

        val vals = bookMetadata.asContentValues();

        db.insert(booksTable, null, vals);

        // db.close();
    }

    fun updateBook(bookMetadata: BookMetadata): Int {
        val db = this.writableDatabase;
        _logger.debug("Attempting to update book ${bookMetadata.id} in database");
        val vals = bookMetadata.asContentValues();
        //update row
        val i = db.update(booksTable,
                vals,
                keyId + " = ?",
                arrayOf(bookMetadata.id.toString()));
        return i;
    }

    fun deleteBook(bookId: String) {
        writableDatabase.delete(booksTable, "$keyRemoteId = ?", arrayOf(bookId));
    }

    fun deleteBook(bookMetadata: BookMetadata) {
        val db = this.writableDatabase

        db.delete(booksTable,
                keyId + " = ?",
                arrayOf(bookMetadata.id.toString()))

        Log.d("deleteBook", "Deleted " + bookMetadata.toString())
    }

    //Comments
    fun getCommentsByStatus(status: DatabaseUtilities.UploadableItemStatus): ArrayList<Comment> {
        var commments = ArrayList<Comment>();
        var cursor: Cursor? = null;

        try {
            cursor = readableDatabase.query(CommentsTable.TableName,
                    arrayOf(CommentsTable.Column_BookId,
                            CommentsTable.Column_DateCreated,
                            CommentsTable.Column_Comment,
                            CommentsTable.Column_Status),
                    "${CommentsTable.Column_Status} = ? ",
                    arrayOf(status.ordinal.toString()),
                    null, null, null);

            while (cursor.moveToNext()) {
                _logger.debug("Adding comment with status $status");
                commments.add(Comment.fromCursor(cursor));
            }

        } catch(ex: Exception) {
            _logger.error("Could not fetch comments of status $status", ex);
        } finally {
            cursor?.close();
            return commments;
        }
    }

    fun getUploadableComments(): ArrayList<Long> {
        var comments = ArrayList<Long>();
        var cursor: Cursor? = null;

        try {
            cursor = readableDatabase.query(CommentsTable.TableName,
                    arrayOf(BaseColumns._ID),
                    "${CommentsTable.Column_Status} <> ? and ${CommentsTable.Column_Status} <> ? ",
                    arrayOf(DatabaseUtilities.UploadableItemStatus.Uploaded.ordinal.toString(),
                            DatabaseUtilities.UploadableItemStatus.UploadFailed3.ordinal.toString()),
                    null, null, null);

            while (cursor.moveToNext()) {
                comments.add(cursor.getLong(0));
            }

        } catch(ex: Exception) {
            _logger.error("Could not fetch uploadable comments :(", ex);
        } finally {
            cursor?.close();
            _logger.debug("Found ${comments.size} uploadable comments! $comments}")
            return comments;
        }
    }


    fun getCommentById(commentId: Long): CommentDto {
        var cursor: Cursor? = null;
        var comment = CommentDto();
        try {
            cursor = writableDatabase.query(CommentsTable.TableName,
                    arrayOf(CommentsTable.Column_Comment, CommentsTable.Column_BookId, CommentsTable.Column_Status),
                    "${BaseColumns._ID} = ?",
                    arrayOf(commentId.toString()), null, null, null);
            while (cursor.moveToNext()) {

                comment.comment = cursor.getString(0);
                comment.bookId = cursor.getString(1);
                comment.status = DatabaseUtilities.UploadableItemStatus.values()[cursor.getInt(2)]
                _logger.debug("Comment found: $comment");
            }

        } catch (ex: Exception) {
            Crashlytics.logException(ex);
            _logger.error("Failed to fetch comment by column $commentId", ex);
        }

        return comment;
    }

    fun updateCommentStatus(commentId: Long, newStatus: DatabaseUtilities.UploadableItemStatus) {
        _logger.debug("Updating comment $commentId status to $newStatus");

        var contentVals = ContentValues();
        contentVals.put(CommentsTable.Column_Status, newStatus.ordinal.toString());

        writableDatabase.update(CommentsTable.TableName, contentVals, "_id = ?", arrayOf(commentId.toString()));
    }

    fun addComment(newComment: Comment): Long {
        _logger.debug("Saving comment '${newComment.Comment}' for book ${newComment.BookId}to the database. Will upload when triggered")
        return writableDatabase.insert(CommentsTable.TableName, null, newComment.asContentValues());
    }

    fun deleteUploadedComments() {
        _logger.debug("Deleting Uploaded comments");
        writableDatabase.delete(CommentsTable.TableName, "${CommentsTable.Column_Status} = ?", arrayOf(DatabaseUtilities.UploadableItemStatus.Uploaded.ordinal.toString()))
    }

    fun deleteComment(localId: Long) {
        _logger.debug("Deleting comment with local id $localId");
        writableDatabase.delete(CommentsTable.TableName, "${BaseColumns._ID} = ?", arrayOf(localId.toString()));
    }


    private fun getBookFromCursor(cursor: Cursor): BookMetadata {
        val bk = BookMetadata(cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getString(4))

        bk.id = Integer.parseInt(cursor.getString(0))
        bk.hash = cursor.getString(6)
        bk.lastChapter = cursor.getString(5)
        bk.scrollPosition = cursor.getShort(7).toInt()
        return bk
    }

    companion object {
        private val _logger = LogHelper(this);
        //going with singleton pattern
        private var _inst: EcoachBooksDatabase? = null
        //static constant refs
        val booksTable = "books"
        val keyRemoteId = "remoteId"
        val keyPath = "path"
        val keyAuthor = "author"
        val keyTitle = "title"
        val keyLastChapter = "lastChapter"
        val keyhash = "hash"
        val keyId = "id"
        val keyScrollPosition = "scrollPosition"

        private val columns = arrayOf(keyId, keyPath, keyAuthor, keyTitle, keyLastChapter, keyRemoteId, keyhash, keyScrollPosition)

        //DB version
        private val DatabaseVersion = 3
        //DB Name
        val DatabaseName = "BooksDb"


        @Synchronized fun initializeDb(context: Context){
            if (_inst == null) {
                _logger.debug("Creating new DB class");
                _inst = EcoachBooksDatabase(context)
            }else{
                _logger.debug("DB class exists. Returning existing instance!");
            }
        }

        @Synchronized fun getInstance(): EcoachBooksDatabase {
            return _inst!!;
        }

    }
}

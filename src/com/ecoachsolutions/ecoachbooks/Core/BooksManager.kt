package com.ecoachsolutions.ecoachbooks.Core

import android.content.Context
import android.text.TextUtils
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.BookUtilities
import com.ecoachsolutions.ecoachbooks.Helpers.Constants
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksPreferences
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.Models.BookDto
import com.ecoachsolutions.ecoachbooks.Models.BookMetadata
import com.ecoachsolutions.ecoachbooks.Models.PurchasedBook
import com.ecoachsolutions.ecoachbooks.Models.asBookDto
import com.ecoachsolutions.ecoachbooks.R
import org.apache.commons.collections4.CollectionUtils
import java.io.File
import java.util.*

/**
 * Responsible for getting book information from the server for current user
 * Created by Daniel on 9/15/2014.
 */
class BooksManager(val context: Context) {
    private var _libraryBooks = ArrayList<BookDto>()
    private var _shelfBooks = ArrayList<BookDto>()
    private val _preferences = EcoachBooksPreferences(context);

    private var _logger = LogHelper(this)


    fun clearShelf() {
        _shelfBooks.clear()
        updateBooksInShelf()
    }

    //filtered library books
    val allBooks: ArrayList<BookDto>
        get() {
            val allbooks = ArrayList<BookDto>()

            val dbBooks = EcoachBooksDatabase.getInstance().allBooks

            _logger.debug("Total number of books is: ${dbBooks.size}");

            val basepath = context.filesDir.absolutePath + File.separator + context.getString(R.string.ecoachbookcovers_path)

            for (bkMetadata in dbBooks) {
                val dto = BookDto();
                dto.Title = bkMetadata.title;
                dto.Id = bkMetadata.remoteId;
                dto.AuthorName = bkMetadata.author;
                dto.CoverPath = basepath + File.separator + dto.Id;

                if (!TextUtils.isEmpty(bkMetadata.path)) {
                    dto.IsLocal = true;
                }

                allbooks.add(dto);
            }
            return allbooks;
        }

    fun getFilteredBooks(filter: String): ArrayList<BookDto> {
        var filteredBooks = ArrayList<BookDto>();

        if (TextUtils.isEmpty(filter)) {
            return allBooks;
        }
        var dbBooks = EcoachBooksDatabase.getInstance().getFilteredBooks(filter);
        _logger.debug("Filtered down to ${dbBooks.size} books");

        val basepath = context.filesDir.absolutePath + File.separator + context.getString(R.string.ecoachbookcovers_path)

        for (bkMetadata in dbBooks) {
            val dto = bkMetadata.asBookDto();
            dto.CoverPath = basepath + File.separator + dto.Id
            filteredBooks.add(dto)
        }

        return filteredBooks;
    }

    fun updateLibraryWithNewBooks(newBooks: List<BookDto>) {
        _logger.debug("Attempting to add ${newBooks.size} books to the library");

        val basepath = context.filesDir.absolutePath + File.separator + context.getString(R.string.ecoachbookcovers_path);

        for (newBook in newBooks) {
            val path = basepath + File.separator + newBook.Id;
            _logger.debug("Adding book to library: " + path);
            newBook.CoverPath = path;
        }
    }

    //Add a book to the library shared pref. Excepts absolute path!
    fun addBookToLibrary(dto: BookDto) {
        if (BookUtilities.BookInList(_libraryBooks, dto.Id)) {
            _logger.debug("The book is already in the list and won't be re-added");
            return;
        }
        _libraryBooks.add(dto);
    }

    fun markBookAsDownloaded(bookId: String): Boolean? {
        _logger.debug("Marking $bookId as downloaded");
        return _preferences.addToStringSet(Constants.BOOKS_DOWNLOADED, bookId);
    }

    fun markBookAsNotDownloaded(bookId: String): Boolean? {
        _logger.debug("Attempting to mark book as undownloaded:" + bookId)
        val _prefs = context.getSharedPreferences(context.resources.getString(R.string.book_preferences), 0);
        val downloadedBks = _prefs.getStringSet(Constants.BOOKS_DOWNLOADED, HashSet<String>());
        val editor = _prefs.edit();
        downloadedBks.remove(bookId);
        editor.putStringSet(Constants.BOOKS_DOWNLOADED, downloadedBks);

        return editor.commit();
    }

    //Set<String> shelfBooks = _prefs.getStringSet(Constants.SHELFPATHS, new HashSet<String>());
    val idsOfUnDownloadedBooks: ArrayList<String>
        get() {
            val _prefs = context.getSharedPreferences(context.resources.getString(R.string.book_preferences), 0)
            val downloadedBks = _prefs.getStringSet(Constants.BOOKS_DOWNLOADED, HashSet<String>())
            val libraryBooks = HashSet(EcoachBooksDatabase.getInstance().currentBookIds)

            _logger.debug("Books in database: $libraryBooks, books downloaded: $downloadedBks");

            return ArrayList(CollectionUtils.disjunction(downloadedBks, libraryBooks))
        }

    fun updateBooksInShelf() {
        val tmpshelf = HashSet<String>()
        val _prefs = context.getSharedPreferences(context.resources.getString(R.string.book_preferences), 0)
        val editor = _prefs.edit()

        for (dto in _shelfBooks) {
            tmpshelf.add(dto.Id)
        }
        //We want to store the books in the shelf in shared prefs. Library books are loaded based on what's on disk
        editor.putStringSet(Constants.SHELFPATHS, tmpshelf)

        val commited = editor.commit()

        if (commited) {
            _logger.debug("Successfully updated shelf shared prefs")
        } else {
            _logger.error("Couldn't commit changes for updated shelf shared prefs")
        }

    }

    data class GetMyBooksResponse(
            var books: List<PurchasedBook>,
            var message: String,
            var success: Boolean? = null
    )

    fun getBookDtoFromMetaData(metadata: BookMetadata): BookDto {
        val dto = BookDto()
        dto.Title = metadata.title
        dto.AuthorName = metadata.author
        dto.Id = metadata.remoteId
        dto.CoverPath = makeCoverPathFromId(dto.Id)

        return dto
    }

    fun makeCoverPathFromId(id: String): String {
        val basepath = context.filesDir.absolutePath + File.separator + context.getString(R.string.ecoachbookcovers_path)
        return basepath + File.separator + id
    }

    companion object {
        val _logger = LogHelper(this);

        fun getCurrentBookIds(ctx: Context): List<String> {
            return EcoachBooksDatabase.getInstance().currentBookIds
        }


        fun isBookDownloaded(bookId: String, ctx: Context): Boolean {
            val prefs = ctx.getSharedPreferences(ctx.resources.getString(R.string.book_preferences), 0)
            val downloadedBks = prefs.getStringSet(Constants.BOOKS_DOWNLOADED, HashSet<String>())

            return downloadedBks.contains(bookId)
        }


    }


}

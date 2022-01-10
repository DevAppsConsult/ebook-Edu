package com.ecoachsolutions.ecoachbooks.Models

import android.content.ContentValues
import android.text.TextUtils
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import java.io.File


class BookMetadata {
    var id: Int = 0;
    var remoteId: String ="";
    var path: String = "";
    var author: String = "";
    var title: String = "";
    //getters and setters
    var lastChapter: String? = null;
    var hash: String? = null; //hash geenrated with user info. Used to prevent copying
    var scrollPosition: Int = 0; //vertical scroll position

    constructor() {
    }

    constructor(path: String, author: String, title: String, remoteId: String) : super() {
        this.title = title
        this.author = author
        this.path = path
        this.remoteId = remoteId
    }

    override fun toString(): String {
        return "Book [id=$id, UUID=$remoteId, author=$author, title=$title, path=$path]";
    }

}

fun BookMetadata.asContentValues() : ContentValues {
    val vals = ContentValues();
    vals.put(EcoachBooksDatabase.keyPath, path);
    vals.put(EcoachBooksDatabase.keyAuthor, author);
    vals.put(EcoachBooksDatabase.keyTitle, title);
    vals.put(EcoachBooksDatabase.keyLastChapter, lastChapter);
    vals.put(EcoachBooksDatabase.keyRemoteId, remoteId);
    vals.put(EcoachBooksDatabase.keyhash, hash);
    vals.put(EcoachBooksDatabase.keyScrollPosition, scrollPosition);
    return vals;
}

fun BookMetadata.asBookDto() : BookDto {
    val dto = BookDto();
    dto.Title = title;
    dto.Id = remoteId;
    dto.AuthorName = author;
    dto.IsLocal = if (TextUtils.isEmpty(path)) false else true;
    return dto;
}
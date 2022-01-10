package com.ecoachsolutions.ecoachbooks.Models

import com.ecoachsolutions.ecoachbooks.Core.Database.DatabaseUtilities
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

class CommentDto {
    var comment: String = "";
    var bookId: String = "";
    @Transient var status: DatabaseUtilities.UploadableItemStatus = DatabaseUtilities.UploadableItemStatus.Pending;
}

data class CommentRequest(val comment: CommentDto);

class BookCommentDisplayDto {
    constructor(){}
    constructor(c: String, a: String, cd: String){
        comment = c
        authorName = a
        commentDate = DateTime.parse(cd, DateTimeFormat.forPattern("MM/dd/YYYY HH:mm:ss").withZoneUTC())
    }
    constructor(c: String, a: String, cd: DateTime){
        comment = c
        authorName = a
        commentDate = cd
    }
    var comment = "";
    var authorName = "";
    var commentDate = DateTime.now(DateTimeZone.UTC);
}

class CommentResponse {
    var BookId = ""
    var Content = ""
    var CreatedBy = ""
    var CreatedOn = ""
    var Id = ""

}
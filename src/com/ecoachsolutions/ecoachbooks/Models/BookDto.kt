package com.ecoachsolutions.ecoachbooks.Models

/**
 * Created by Daniel on 9/18/2014.
 */
class BookDto {
    var Id: String = "";
    var Title: String = "";
    var CoverPath: String = "";
    var AuthorName: String = "";
    var CachePath: String = "";
    //only useful if something is observing the write action
    var SaveProgress: Int = 0;
    //Add later
    var ReadingProgress: Int = 0;
    var IsLocal = false;
}



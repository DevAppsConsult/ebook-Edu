package com.ecoachtools.lectio;

import java.io.InputStream;
import java.util.List;


public class BookInfo {
    /**
     * Not yet used. ecoachbooks uses the coverimage from the site directly
     */
    /*a book should have:
    1. table of contents
    2. cover image path
    3.
    */
    private String CoverImagePath;
    private String Title;
    private String Author;
    private InputStream CoverFile;
    private List<Chapter> TableOfContents;

    public BookInfo(String title, String author, InputStream cover, List<Chapter> tableOfContents) {
        Title = title;
        TableOfContents = tableOfContents;
        Author = author;
        CoverFile = cover;
    }

    public String getCoverImagePath() {
        return CoverImagePath;
    }

    public List<Chapter> getTableOfContents() {
        return TableOfContents;
    }

    public void setCoverImagePath(String coverImagePath) {
        CoverImagePath = coverImagePath;
    }

    public String getTitle() {
        return Title;
    }

    public String getAuthor(){
        return Author;
    }

    public InputStream getCoverInputStream(){
        return CoverFile;
    }
}

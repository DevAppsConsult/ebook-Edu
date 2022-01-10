package com.ecoachtools.lectio;

/**
 * Created by Daniel on 5/2/2015.
 */
public class Chapter {
    //A book chapter. Use a list of these to create a table of contents
    //The title is the text title of the section
    //The resource link is the actual path to the book
    private String Title;
    private String ResourceLink;

    public String getTitle() {
        return Title;
    }

    public void setTitle(String title) {
        Title = title;
    }

    public String getResourceLink() {
        return ResourceLink;
    }

    public void setResourceLink(String resourceLink) {
        ResourceLink = resourceLink;
    }
}

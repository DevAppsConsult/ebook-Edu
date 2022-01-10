package com.ecoachtools.lectio;

import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.sax.StartElementListener;
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Daniel on 5/4/2015.
 * Reference: http://www.codeproject.com/Articles/592909/EPUB-Viewer-for-Android-with-Text-to-Speech
 *
 */
public class ElementParsers {
    private static final String XML_NAMESPACE_CONTAINER = "urn:oasis:names:tc:opendocument:xmlns:container";
    private static final String XML_NAMESPACE_PACKAGE = "http://www.idpf.org/2007/opf";
    private static final String XML_NAMESPACE_TABLE_OF_CONTENTS = "http://www.daisy.org/z3986/2005/ncx/";
    private ArrayList<Chapter> mChapters = new ArrayList<>();
    /**
     * Example container.xml file:
     * <?xml version="1.0" encoding="UTF-8" ?>
     *<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
     *    <rootfiles>
     *       <rootfile full-path="OPS/fb.opf" media-type="application/oebps-package+xml"/>
     *    </rootfiles>
     * </container>
     */

    public String mOpfFileName;
    public String mTocName;
    public HashMap<String, String> mManifestIndex = new HashMap<>();
    public HashMap<String, String> mManifestMediaTypes = new HashMap<>();
    public ArrayList<String> mSpine = new ArrayList<>();
    public String mBookTitle;
    public String mAuthor;
    public String mCoverLocation;
    public String mCoverId;
    public int mCurrentNavDepth = 0;
    public int mMaxNavDepth = 1;
    public int mActualNavDepth = -1;

    public ContainerParserResult getContainerFileParser(){
        final ContainerParserResult pr = new ContainerParserResult();
        RootElement root = new RootElement(XML_NAMESPACE_CONTAINER, "container");
        Element rootFilesElement = root.getChild(XML_NAMESPACE_CONTAINER, "rootfiles");
        Element singleRootFileElement = rootFilesElement.getChild(XML_NAMESPACE_CONTAINER,"rootfile");

        singleRootFileElement.setStartElementListener(new StartElementListener() {

            @Override
            public void start(Attributes attributes) {
                String mediaType = attributes.getValue("media-type");
                if ((mediaType != null) && (mediaType.equals("application/oebps-package+xml"))) {
                    //this tells us where to find the opf file for processing
                    mOpfFileName = attributes.getValue("full-path");
                }
            }
        });

        pr.contentHandler = root.getContentHandler();
        return pr;
    }

    public OpfParserResult getOpfFileParser(final String opfFileName){
        final OpfParserResult opfParserResult = new OpfParserResult();


        RootElement rootElement = new RootElement(XML_NAMESPACE_PACKAGE,"package");
        Element metadataElement = rootElement.getChild(XML_NAMESPACE_PACKAGE,"metadata");
        Element metaElement = metadataElement.getChild(XML_NAMESPACE_PACKAGE,"meta");
        Element manifestElement = rootElement.getChild(XML_NAMESPACE_PACKAGE,"manifest");
        Element manifestItemElement = manifestElement.getChild(XML_NAMESPACE_PACKAGE,"item");
        Element spineElement = rootElement.getChild(XML_NAMESPACE_PACKAGE,"spine");
        Element itemrefElements = rootElement.getChild(XML_NAMESPACE_PACKAGE,"itemref");
        //yay we have our elements, now we need to actually construct the target files
        //these files are the href attribute of the manifest items

        manifestItemElement.setStartElementListener(new StartElementListener() {
            @Override
            public void start(Attributes attributes) {
                String href = attributes.getValue("href");
                href = FilenameUtils.concat(FilenameUtils.getPath(opfFileName), href);
                //to look up location of items
                /* eg:
                    <item id="main0" href="main0.xml" media-type="application/xhtml+xml"/>
                    storing the full path for the href here
                 */
                mManifestIndex.put(attributes.getValue("id"), href);
                mManifestMediaTypes.put(href, attributes.getValue("media-type"));
                if (attributes.getValue("id").equalsIgnoreCase(mCoverId)) {
                    mCoverLocation = href;
                    Log.d("Lectio", "Set cover path to: " + mCoverLocation);

                }
            }
        });

        //parse metadata for cover file location
        metaElement.setStartElementListener(new StartElementListener() {
            //<meta name="cover" content="cover"/>
            //<meta name="cover" content="book-cover"/>
            @Override
            public void start(Attributes attributes) {
                String name = attributes.getValue("name");
                if (name != null && name.equalsIgnoreCase("cover")) {
                    mCoverId = attributes.getValue("content");
                    Log.d("Lectio", "Set cover id to: " + mCoverId);
                }
            }
        });

        //parse table of contents flow using the spine
        spineElement.setStartElementListener(new StartElementListener() {
            @Override
            public void start(Attributes attributes) {
                //we expect ncx here
                String tableOfContents = attributes.getValue("toc");
                mTocName = mManifestIndex.get(tableOfContents);
            }
        });

        //next the actual files from the item refs. add to spine so we know what to lookup
        itemrefElements.setStartElementListener(new StartElementListener() {
            @Override
            public void start(Attributes attributes) {
                //<itemref idref="main0" linear="yes"/>
                mSpine.add(attributes.getValue("idref"));
            }
        });
        opfParserResult.contentHandler = rootElement.getContentHandler();
        return opfParserResult;
    }

    /*
        <navPoint class="chapter" id="chap7379" playOrder="8">
            <navLabel>
                <text>Chapter 5 - At Random!</text>
            </navLabel>
            <content src="main5.xml"/>
        </navPoint>
     */


    public OpfHandler getOpfHandler(String opfFileName){
        return new OpfHandler(opfFileName);
    }

    public  ChapterListParserResult getChapterListFromNavPoints(){
        ChapterListParserResult parserResult = new ChapterListParserResult();

        RootElement rootElement = new RootElement(XML_NAMESPACE_TABLE_OF_CONTENTS,"ncx");
        Element navMapElement = rootElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"navMap");
        Element navPoint = navMapElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"navPoint");
        Element docTitleElement = rootElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"docTitle");
        Element titleTextElement = docTitleElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"text");
        Element docAuthorElement = rootElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"docAuthor");
        Element authorTextElement = docAuthorElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"text");

        //Get depth info for proper parsing
        Element headElement = rootElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS, "head");
        Element metaElement = headElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS, "meta");
        metaElement.setStartElementListener(new StartElementListener() {
            @Override
            public void start(Attributes attributes) {
                String name = attributes.getValue("name");
                String content = attributes.getValue("content");
                if (name.equalsIgnoreCase("dtb:depth")) {
                    content = TextUtils.isEmpty(content) ? "0" : content;
                    mActualNavDepth = Integer.parseInt(content);
                }

            }
        });

        //get title
        titleTextElement.setEndTextElementListener(new EndTextElementListener() {
            @Override
            public void end(String body) {
                mBookTitle = body;
            }
        });

        //get author
        authorTextElement.setEndTextElementListener(new EndTextElementListener() {
            @Override
            public void end(String body) {
                mAuthor = body;
            }
        });


        addNavPointToChapterList(navPoint);



        parserResult.chapterList = mChapters;
        parserResult.contentHandler = rootElement.getContentHandler();
        return parserResult;
    }



    public class OpfHandler extends DefaultHandler {

        public String mCoverId;
        public String mTocName;
        public HashMap<String, String> mManifestIndex = new HashMap<>();
        public HashMap<String, String> mManifestMediaTypes = new HashMap<>();
        public ArrayList<String> mSpine = new ArrayList<>();
        public String opfFileName;

        public OpfHandler(String opfFname){
            opfFileName = opfFname;
        }

        @Override
        public void startElement(String uri, String name,
                                 String qName, Attributes attributes){

            if (name == null){
                return;
            }
            //meta element for cover information
            String nameAttribute = attributes.getValue("name");
            if (name.equalsIgnoreCase("meta") && nameAttribute != null &&  nameAttribute.equalsIgnoreCase("cover")){
                mCoverId = attributes.getValue("content");
            }

            //get toc name from spine
            if (name.equalsIgnoreCase("spine")){
                String tableOfContents = attributes.getValue("toc");
                mTocName = mManifestIndex.get(tableOfContents);
            }

            //item elements for spine info
            if (name.equalsIgnoreCase("item")){
                String href = attributes.getValue("href");
                href = FilenameUtils.concat(FilenameUtils.getPath(opfFileName), href);
                //to look up location of items
                /* eg:
                    <item id="main0" href="main0.xml" media-type="application/xhtml+xml"/>
                    storing the full path for the href here
                 */
                mManifestIndex.put(attributes.getValue("id"), href);
                mManifestMediaTypes.put(href, attributes.getValue("media-type"));
                if (attributes.getValue("id").equalsIgnoreCase(mCoverId)) {
                    mCoverLocation = href;
                    Log.d("Lectio", "Set cover path to: " + mCoverLocation);

                }
            }

            //next the actual files from the item refs. add to spine so we know what to lookup
            if (name.equalsIgnoreCase("itemref")){
                mSpine.add(attributes.getValue("idref"));
            }
        }

        @Override
        public void endElement(String uri, String name, String qName){

        }

    }

    private void addNavPointToChapterList(final Element navPoint){

        Element navLabelElement = navPoint.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"navLabel");
        final Element textElement = navLabelElement.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"text");
        Element contentElement = navPoint.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS,"content");

        //wooo let's get your insides!
        navPoint.setStartElementListener(new StartElementListener() {
            @Override
            public void start(Attributes attributes) {
                Chapter newChapter = new Chapter();
                //newChapter.setTitle(textElement.);
                mChapters.add(newChapter);
                ++mCurrentNavDepth;
                if (mCurrentNavDepth == mMaxNavDepth){
                    Element child = navPoint.getChild(XML_NAMESPACE_TABLE_OF_CONTENTS, "navPoint");
                    addNavPointToChapterList(child);
                    mMaxNavDepth++;
                }
            }
        });

        textElement.setEndTextElementListener(new EndTextElementListener() {
            @Override
            public void end(String body) {
                mChapters.get(mChapters.size() - 1).setTitle(body); // get the last one;
            }
        });
        contentElement.setStartElementListener(new StartElementListener() {
            @Override
            public void start(Attributes attributes) {
                String src = FilenameUtils.concat(FilenameUtils.getPath(mOpfFileName), attributes.getValue("src"));
                mChapters.get(mChapters.size() - 1).setResourceLink(src);
            }
        });

        navPoint.setEndElementListener(new EndElementListener() {
            @Override
            public void end() {
                --mCurrentNavDepth;
            }
        });

    }

    public static class ContainerParserResult{
        public ContentHandler contentHandler;
        public String opfFileName;
    }

    public static class OpfParserResult{
        public ContentHandler contentHandler;
        public HashMap<String, String> manifestIndex;
        public HashMap<String, String> manifestMediaTypes;
        public ArrayList<String> spine;
        public String tocName;
    }

    public static class ChapterListParserResult{
        public ContentHandler contentHandler;
        public ArrayList<Chapter> chapterList;
    }
}

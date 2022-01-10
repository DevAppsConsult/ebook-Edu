package com.ecoachtools.lectio;

/*import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;*/

import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceResponse;

import com.ecoachtools.lectio.ElementParsers.ChapterListParserResult;
import com.ecoachtools.lectio.ElementParsers.ContainerParserResult;
import com.ecoachtools.lectio.ElementParsers.OpfParserResult;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

public class Lectio {
    /*
    1. main wrapper for creating and reading a book
    2. constructor will take path to epub file and a path in which to extract the book content
    3. very useful reference: http://www.codeproject.com/Articles/592909/EPUB-Viewer-for-Android-with-Text-to-Speech
     */

    private String mEpubPath;
    private BookInfo mBookInfo;
    private String mTitle;

    private ArrayList<Chapter> mChapterList;

    private XMLReader mXmlReader;

    //zip file contents
    private ZipFile mEpubZipFile;
    //filename constants
    private final String CONTAINER_FILE = "container";

    //parser
    private ElementParsers mElementParser;

    //private ZipFile
    public Lectio(String epubPath) throws IOException, ParserConfigurationException, SAXException {
        mEpubPath = epubPath;
        mEpubZipFile = new ZipFile(epubPath);
        mXmlReader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        mElementParser = new ElementParsers();
        parseEpub();
        mBookInfo = new BookInfo(mElementParser.mBookTitle, mElementParser.mAuthor, getCoverImage(), mChapterList);
    }

    public BookInfo getBook() {
        return mBookInfo;
    }

    private void parseEpub() {
        /*do the magic
        1. read container.xml file and determine location of opf file
        2. read the opf file to get the spine, the spine tells us the order of the files in the manifest
           and the table of contents file location
        3. the manifest tells us what files actually exist in the epub

         */

        ContainerParserResult containerParserResult = mElementParser.getContainerFileParser();
        parseXmlResource("META-INF/container.xml", containerParserResult.contentHandler);

        //get the content from opf file.
        OpfParserResult opfParserResult = mElementParser.getOpfFileParser(mElementParser.mOpfFileName);
        ElementParsers.OpfHandler opfHandler = mElementParser.getOpfHandler(mElementParser.mOpfFileName);
        parseXmlResource(mElementParser.mOpfFileName, opfParserResult.contentHandler);

        //next we need to generate the actual table of contents for the file as necessary
        ChapterListParserResult chapterListParserResult = mElementParser.getChapterListFromNavPoints();
        parseXmlResource(mElementParser.mTocName, chapterListParserResult.contentHandler);

        //expectation is that we have a fully populated chapter list beyond this point
        mChapterList = chapterListParserResult.chapterList;

    }

    private InputStream getCoverImage() {
        return getFileFromEpub(mElementParser.mCoverLocation);
    }

    public InputStream getFileFromEpub(String fileName) {
        ZipEntry zipEntry = mEpubZipFile.getEntry(fileName);
        if (zipEntry != null) {
            try {
                return mEpubZipFile.getInputStream(zipEntry);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //this is so bad... maybe I should return an empty stream..
        return null;
    }

    //use for handling webview request
    public WebResourceResponse interceptWebViewRequest(String url) {
        String resourceName = urlToResourceName(Uri.parse(url));
        return new WebResourceResponse(mElementParser.mManifestMediaTypes.get(resourceName),
                "UTF-8", getFileFromEpub(resourceName));
    }

    private static String urlToResourceName(Uri uri) {
        String resourceName = uri.getPath();
        Log.d("Lectio", "File path: " + resourceName);
        if (resourceName.startsWith("/")) {
            resourceName = resourceName.substring(1); //skip a leading '/' if need be
        }
        return resourceName;
    }

    private static Uri resourceNameToUri(String epubResourceName) {
        return new Uri.Builder()
                .scheme("file")
                .authority("")
                .appendEncodedPath(Uri.encode(epubResourceName, "/"))
                .build();
    }


    private void parseXmlResource(String fileName, ContentHandler handler) {
        InputStream inputStream = getFileFromEpub(fileName);
        if (inputStream != null) {
            try {
                mXmlReader.setContentHandler(handler);

                InputSource inputSource = new InputSource(inputStream);
                inputSource.setEncoding("UTF-8");
                mXmlReader.parse(inputSource);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //this is ... wtf.
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

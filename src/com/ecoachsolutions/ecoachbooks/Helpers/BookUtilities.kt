package com.ecoachsolutions.ecoachbooks.Helpers

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log

import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Models.BookDto
import com.ecoachsolutions.ecoachbooks.Models.BookMetadata
import com.ecoachsolutions.ecoachbooks.R
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils

import org.apache.commons.io.IOUtils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilenameFilter
import java.util.ArrayList
import java.util.Arrays
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.text.Regex

/**
 * BookCommon Class

 * @param
 */
class BookUtilities(context: Context) {

    enum class BookShelfSection {

        SHELF, LIBRARY, RECOMMENDED
    }

    private var _resources: Resources
    private val _logger = LogHelper(this);

    init {
         _resources = context.resources
        _resources.getString(R.string.ecoachbooks_path)
    }

    // finds book covers available and adds them to the list
    // Extract the covers into a folder
    // Create the folder if it doesn't already exist. Better method than
    // exception handling
    val data: ArrayList<String>?
        get() {

            val tag = "[Ecoach Books] - getData()"
            try {
                val bookspath = Environment.getExternalStorageDirectory().absolutePath + File.separator + _resources.getString(R.string.ecoachbooks_path)
                val path = EcoachBooksUtils.makeEcoachBooksPath(_resources.getString(R.string.ecoachbookcovers_path));
                val coverpath = path!!.absolutePath
                extractCoversFromBooks(bookspath, coverpath)

                val results = EcoachBooksUtils.getPreviews(coverpath)

                Log.i(tag, "Retrieved " + results.size + " files")
                return ArrayList(Arrays.asList(*results))

            } catch (e: Exception) {
                Log.e(tag, "An error occured: " + e.message)
                return null
            }

        }


    private fun extractCoversFromBooks(pathtofindbooks: String, outpath: String) {
        try {
            val pathToBookCoversFolder = File(outpath) // book covers
            // folder
            pathToBookCoversFolder.mkdirs() // inflate to dir if it doesn't alreadt exist
            val fnf = FilenameFilter { dir, filename ->
                val i = filename.lastIndexOf('.')
                if (i > 0) {
                    // only return epub files
                    val str = filename.substring(i)
                    if (str.contains(_resources.getString(R.string.ecoachbooks_extension_filter))) {
                        return@FilenameFilter true
                    }
                }
                false
            }

            val inpath = File(pathtofindbooks).listFiles(fnf)
            for (file in inpath) {
                extractCoverFromStream(file, File(outpath + File.separator + file.name + "_cover.jpeg"),
                        pathToBookCoversFolder)
            }

            // Cleanup orphaned covers
            cleanUpOrphanedCovers(inpath, pathToBookCoversFolder)
            // End cleanup covers
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    /*
     * Remove orphaned covers. This will account for situations where the user
     * manually deletes a book from the ebooks folder
     */
    private fun cleanUpOrphanedCovers(presentbooks: Array<File>,
                                      pathToBookCovers: File) {
        val covers = pathToBookCovers.list()
        val toDelete = ArrayList<String>()

        for (cover in covers) {
            // If the bookpath doesn't contain a source for this cover, delete
            // it

            val parentbookname = cover.replace("_cover.jp.*g".toRegex(), "")
            var hasParent: Boolean = false
            for (f in presentbooks) {
                if (f.name.contains(parentbookname)) {
                    hasParent = true
                    break
                }
            }
            if (!hasParent) {
                toDelete.add(cover)
            }
        }

        for (orphan in toDelete) {
            val die = File("$pathToBookCovers${File.separator}$orphan");
            die.delete()
        }
    }

    private fun extractCoverFromStream(f: File, outfile: File, outpath: File) {
        var zf: ZipFile?
        val tmpPath = "${outfile}_tmp"
        val regexpattern = ".*cover.jp.*g"
        // Check to see if the cover already exists. If it does, quit
        val covers = outpath.list()
        for (cover in covers) {
            if (cover.contains(f.name)) {
                return
            }
        }

        try {
            zf = ZipFile(f)
            var cover: ZipEntry?
            cover = zf.getEntry("cover.jpeg")
            if (cover == null) {
                // no cover.jpeg file exists.
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.toLowerCase().matches(Regex.fromLiteral(regexpattern))) {
                        cover = entry
                        break
                    }
                }
            }
            var ofs = FileOutputStream(tmpPath)

            // extract the file
            val bufferOut = BufferedOutputStream(ofs)
            val bufferIn = BufferedInputStream(
                    zf.getInputStream(cover))
            IOUtils.copy(bufferIn, bufferOut)

            // flush before close to no data is lost
            bufferOut.flush()
            bufferOut.close()
            bufferIn.close()
            //We need to scale down the file on extraction!!
            val out = EcoachBooksUtils.decodeSampledBitmapFromFile(tmpPath, _resources.getDimensionPixelSize(R.dimen.grid_column_width),
                    _resources.getDimensionPixelSize(R.dimen.grid_column_width), true)

            ofs = FileOutputStream(outfile)
            out.compress(Bitmap.CompressFormat.JPEG, 75, ofs)

        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    companion object {


        //Attempts to add books to the database. Does not throw an exception if the book already exists.
        fun addBooksToDatabase(context: Context, bks: List<BookDto>) {
            val db = EcoachBooksDatabase.getInstance()

            for (i in bks.indices) {
                val bk = bks.get(i)
                db.addOrUpdateBook(BookMetadata("", bk.AuthorName, bk.Title, bk.Id))
            }
        }

        fun addBooksToDatabase(context: Context, bk: BookMetadata) {
            val db = EcoachBooksDatabase.getInstance()
            db.addOrUpdateBook(bk)
        }

        fun BookInList(list: ArrayList<BookDto>, bookId: String): Boolean {
            for (bk in list) {
                if (bk.Id == bookId) {
                    return true
                }
            }
            return false
        }
    }

}

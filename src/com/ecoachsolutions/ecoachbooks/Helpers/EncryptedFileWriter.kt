package com.ecoachsolutions.ecoachbooks.Helpers

import android.content.Context
import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Core.DownloadBookData
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils
import com.ecoachsolutions.ecoachbooks.Models.BookDto
import com.ecoachsolutions.ecoachbooks.R
import rx.Observable
import rx.Subscriber
import rx.functions.Action0
import rx.functions.Func0
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.SecretKeySpec

class EncryptedFileWriter(internal val _context: Context) {
    private var mKeyBase = ""
    private var cancel = false
    private val _logger = LogHelper(this);

    fun setKeyBase(key: String) {
        mKeyBase = key
        _logger.debug("Key is set to :" + mKeyBase)
    }

    fun saveDownloadedBookToDisk(bookData: DownloadBookData): Observable<BookDto> {
        val _dto = bookData.dto

        return Observable.create(object : Observable.OnSubscribe<BookDto> {
            var prevProgress = 0;
            override fun call(subscriber: Subscriber<in BookDto>) {
                encryptAndWriteBookToDisk(bookData, object : IProgressUpdateListener {
                    override fun onProgressUpdate(progress: Int) {
                        _dto.SaveProgress = progress;
                        if (prevProgress == progress) { return; }
                        _logger.debug("sending update for $progress. Previous progress: $prevProgress");
                        prevProgress = progress;
                        subscriber.onNext(_dto);
                        if (progress == 100){
                            subscriber.onCompleted();
                        }
                    }
                })
            }
        }).doOnUnsubscribe {
            //we want to stop the download action
            _logger.debug("Unsubscribing");
            // cancel = true
        }.doOnSubscribe { cancel = false }
    }

    fun prepareBookForReadingObservable(dto: BookDto): Observable<BookDto> {
        return Observable.defer { Observable.just(prepareBookForReading(dto)) }
    }

    fun prepareBookForReading(dto: BookDto): BookDto {
        decryptBook(dto.Id)
        val starttime = System.nanoTime()

        val endtime = System.nanoTime()
        _logger.debug("decompression took: " + (endtime - starttime) / 1e6 + "ms")
        return dto
    }

    private fun encryptAndWriteBookToDisk(dto: DownloadBookData, pgl: IProgressUpdateListener): Boolean? {
        if (dto.inputStream == null) {
            return false
        }

        try {
            val encyKey = mKeyBase + dto.dto.Id

            val encCypher = Cipher.getInstance("AES")

            val keyspec = SecretKeySpec(EcoachBooksUtils.makeKey(encyKey), "AES")

            encCypher.init(Cipher.ENCRYPT_MODE, keyspec)
            val cis = CipherInputStream(dto.inputStream, encCypher)
            val startTime = System.nanoTime()
            //write the cipher input stream to disk.
            val result = writeEncryptedBookToDisk(dto.dto.Id, cis, dto.size, pgl)
            val encEndTime = System.nanoTime()
            //  Boolean result = WriteBookToDisk(context, id, new ByteArrayInputStream(encrypted));
            val writeEndTime = System.nanoTime()

            _logger.debug("write took: ${(writeEndTime - startTime) / 1e6}ms Book size(MB): " + dto.size / 1e6);
            return result

        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        }

    }

    //I do a lot of work. Do me off the main thread, bro.
    fun writeEncryptedBookToDisk(id: String, inputStream: CipherInputStream?, fileSize: Long, pgl: IProgressUpdateListener?): Boolean {
        val outDir = File("${_context.filesDir}${File.separator}${_context.getString(R.string.ecoachbooks_path)}");
        outDir.mkdirs() //create folder if it doesn't exist
        val outfile = outDir.absolutePath + File.separator + id
        var os: FileOutputStream? = null

        try {
            os = FileOutputStream(outfile)
            val buffer = ByteArray(8192)
            var size = inputStream!!.read(buffer)
            var total = 0
            var previousCompletedAmount = 0

            while (!cancel && size != -1) {
                os.write(buffer, 0, size)
                total += size
                if (fileSize > 0) {
                    var completedAmount = (total * 100 / fileSize).toInt();
                    if (completedAmount == previousCompletedAmount) {
                        _logger.debug("Completed amount matches previous amount. Ignoring.");
                    }else{
                        pgl?.onProgressUpdate(completedAmount);
                    }
                    previousCompletedAmount = completedAmount;
                }
                size = inputStream.read(buffer)
            }
            if (cancel) {
                _logger.debug("Book download cancelled. Deleting from disk...");
                deleteBookFromDisk(id)
            }
            //write file to disk in background task
        } catch (ex: Exception) {
            ex.printStackTrace()
            return false
        } finally {
            try {
                os?.flush()
                os?.close()

                inputStream?.close()
            } catch (ex: Exception) {
                ex.printStackTrace()
                return false
            }

        }
        return true
    }

    fun deleteBookFromDisk(id: String) {
        //this file exists in some state
        val outDir = File("${_context.filesDir}${File.separator}${_context.getString(R.string.ecoachbooks_path)}")
        val outfile = outDir.absolutePath + File.separator + id
        _logger.debug("Deleting file from disk: " + id);
        EcoachBooksUtils.deleteRecursive(File(outfile))
    }

    private fun decryptBook(bookId: String): String? {
        try {
            val decKey = mKeyBase + bookId
            _logger.debug("Decrypting $bookId with key $decKey")
            val decCipher = Cipher.getInstance("AES")
            val keySpec = SecretKeySpec(EcoachBooksUtils.makeKey(decKey), "AES")
            decCipher.init(Cipher.DECRYPT_MODE, keySpec)
            val outFile = EcoachBooksUtils.getDecryptedFilePath(_context, bookId)
            val encFile = _context.filesDir.absolutePath + File.separator + _context.getString(R.string.ecoachbooks_path) + File.separator + bookId
            if (!File(encFile).exists()) {
                return null;
            }
            //make cache dir
            val cachebasedir = _context.filesDir.absolutePath + File.separator + _context.getString(R.string.ecoachbookscache_path)
            File(cachebasedir).mkdirs()

            //delete any orphaned cached file
            EcoachBooksUtils.deleteRecursive(File(outFile))

            //should attempt to decrypt the file on '.read()' calls
            //            val cis = CipherInputStream(FileInputStream(encFile), decCipher)
            val cos = CipherOutputStream(FileOutputStream(outFile), decCipher)
            val encFs = FileInputStream(encFile)
            val starttime = System.nanoTime()
            // IOUtils.copy(cis, new FileOutputStream(outFile));

            val buffer = ByteArray(8192)
            var size = encFs.read(buffer)
            while (size != -1) {
                cos.write(buffer, 0, size)
                size = encFs.read(buffer);
            }
            cos.flush()
            cos.close()

            val endtime = System.nanoTime()
            _logger.debug("decryption and writing took:${(endtime - starttime) / 1e6}ms")

            return outFile
            //with the input stream set up, we need to perform extract the contents

        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }


    }

}

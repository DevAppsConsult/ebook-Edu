package com.ecoachsolutions.ecoachbooks.Adapters

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import com.crashlytics.android.Crashlytics
import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils
import com.ecoachsolutions.ecoachbooks.Helpers.*
import com.ecoachsolutions.ecoachbooks.Models.BookDto
import com.ecoachsolutions.ecoachbooks.R
import com.ecoachsolutions.ecoachbooks.ReaderActivity
import com.squareup.picasso.Picasso
import rx.Observable
import rx.Subscriber
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject
import java.io.File
import java.util.*

class BookListAdapter(var _books: ArrayList<BookDto>, //  private GenericImageLoader imgLoader;
                             private val callerContext: Context, var _bookDownloadedListener: IBookDownloadedListener?) : RecyclerView.Adapter<BookListAdapter.BookListViewHolder>() {
    private val _picasso: Picasso
    private var _ecoachBooksApi = EcoachBooksApi.getInstance(callerContext)
    private var _encryptedFileWriter: EncryptedFileWriter
    private var _listUpdatedListener: SerializedSubject<BookDto, BookDto>
    private val _booksManager = BooksManager(callerContext);
    private var _logger = LogHelper(this);

    init {
        _picasso = Picasso.with(callerContext.applicationContext)
        _encryptedFileWriter = EncryptedFileWriter(callerContext)
        val keyBase = EcoachBooksUtils.getDeviceId(callerContext)
        _encryptedFileWriter.setKeyBase(keyBase)
        _listUpdatedListener = SerializedSubject(PublishSubject.create<BookDto>())

        _logger.debug("Initialized with ${_books.size} books");
    }

    fun emptyItems() {
        val previousSize = _books.size
        _books.clear()
        notifyItemRangeRemoved(0, previousSize)
    }

    fun getListUpdateListenerObservable(): Observable<BookDto> {
        return _listUpdatedListener
    }

    fun removeBookFromList(bookDto: BookDto) {
        var dtoIndex = _books.indexOf(bookDto);
        _books.remove(bookDto);
        notifyItemRemoved(dtoIndex);
    }

    fun replaceExistingData(newList: ArrayList<BookDto>) {
        if (newList.isEmpty()) {
            return;
        }
        _books = newList
        _logger.debug("Adding books to library: " + _books.size + ". New list size: " + newList.size)
        notifyDataSetChanged();//0, mBooks.size())
    }

    fun addItemsToList(newBooks: List<BookDto>) {
        val newItemCount = newBooks.size

        if (newItemCount == 0){
            _logger.debug("No new books provided. ignoring request to add more. Books that should be visible: ${_books.map { x -> x.Id }}");
            return;
        }

        _books.addAll(0, newBooks)
        notifyItemRangeInserted(0, newItemCount)

        //the views are now properly updated in the layout manager.
        //we need to now get the first newItemCount views since we added those to the front of the list
        //and trigger their downloads
        //get preference
        _logger.debug("$newItemCount items added to list. Notifying listeners")
        Observable.from(newBooks).subscribe { bookDto ->
            _logger.debug("Receiving process book msg on '${Thread.currentThread().name}', '${Thread.currentThread().id}'")
            _listUpdatedListener.onNext(bookDto)
        }

    }

    fun addItemToFrontOfList(newBook: BookDto) {
        _books.add(0, newBook)
        notifyItemInserted(0)
        notifyItemRangeChanged(0, _books.size)
    }

    fun removeItemAtIndex(idx: Int) {
        _books.removeAt(idx)
        notifyItemRemoved(idx)
        notifyItemRangeChanged(0, _books.size)
    }

    fun triggerBookDownload(targetDlView: View, dto: BookDto) {
        //this will only be called with 0 or 1 but i'm catching the -1 case as a sanity check
        /* if (automaticDownloadPreference == -1) {
            _logger.debug( "Automatic download of books disabled. Will not download new books");
            return;
        }*/
        //check expected preference first and only download over the correct network
        if (EcoachBooksUtils.canDownload(callerContext) && !EcoachBooksUtils.isBookDownloaded(dto.Id, callerContext)) {
            //initiate download
            //this
            _logger.debug("Triggering book download: " + dto.Title)
            val listener = BookClickListener(targetDlView)
            listener.setTargetBook(dto)
            listener.startBookDownloader()
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): BookListViewHolder {
        var viewToInflate = R.layout.home_page_book_shelf
        val itemView = LayoutInflater.from(viewGroup.context).inflate(viewToInflate, viewGroup, false)
        return BookListViewHolder(itemView, BookClickListener(itemView))
    }

    override fun onBindViewHolder(bookListViewHolder: BookListViewHolder, position: Int) {
        //we have our viewholder now, need to populate it with content
        val currentBook = _books[position]
        // bookListViewHolder.bookTitle.setText(currentBook.Title);

        _picasso.load(File(currentBook.CoverPath)).placeholder(R.drawable.loading).error(R.drawable.loading_error).into(bookListViewHolder.bookCover)

        //set the item position
        bookListViewHolder.setItemPosition(position)
        bookListViewHolder.bookView.tag = currentBook.Id

        if (BooksManager.isBookDownloaded(currentBook.Id, callerContext) || currentBook.IsLocal) {
            bookListViewHolder.bookNotDownloadedIndicator.visibility = View.GONE;
            bookListViewHolder.bookCover.setColorFilter(Color.TRANSPARENT)
            bookListViewHolder.bookProgress.progress = 100
            bookListViewHolder.bookProgress.progressDrawable.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
            /*bookListViewHolder.bookView.setBackgroundColor(Color.WHITE);*/

        } else {
            bookListViewHolder.bookProgress.progress = 0
            bookListViewHolder.bookProgress.progressDrawable.setColorFilter(callerContext.resources.getColor(R.color.ecoach_orange), PorterDuff.Mode.SRC_IN)
            /*bookListViewHolder.bookCover.setColorFilter(0x24000000);
            bookListViewHolder.bookView.setBackgroundColor(0x24000000);*/
        }

        /*if (mSection == BookCommon.BookShelfSection.SHELF) {
            bookListViewHolder.bookCover.setColorFilter(Color.TRANSPARENT)
           // bookListViewHolder.readingProgress.visibility = View.GONE
            bookListViewHolder.bookProgress.progress = 100 //todo: set as actual reading progress for book
            bookListViewHolder.bookProgress.progressDrawable.setColorFilter(Color.BLUE, PorterDuff.Mode.SRC_IN)
        } else {
            //library
            if (BooksManager.isBookDownloaded(currentBook.Id, callerContext)) {
                bookListViewHolder.bookProgress.progress = 100
                bookListViewHolder.bookProgress.progressDrawable.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
            } else {
                bookListViewHolder.bookProgress.progress = 0
                bookListViewHolder.bookProgress.progressDrawable.setColorFilter(callerContext.resources.getColor(R.color.ecoach_orange), PorterDuff.Mode.SRC_IN)
            }
        }*/

    }

    override fun getItemCount(): Int {
        return _books.size
    }

    inner class BookListViewHolder(var bookView: View, //location of the item in the data source
                                          var _clickListener: BookClickListener) : RecyclerView.ViewHolder(bookView) {
        // val bookTitle: TextView by lazy { mParentView.findViewById(R.id.bookCover2) as ImageView }
        val bookCover: ImageView by lazy { bookView.findViewById(R.id.bookCover) as ImageView }
        val bookNotDownloadedIndicator: ImageView by lazy { bookView.findViewById(R.id.notDownloadedBookIndicator) as ImageView };
        val bookProgress: ProgressBar by lazy { bookView.findViewById(R.id.bookProgressBar) as ProgressBar };
        var _bookDto: BookDto = BookDto();
        fun setItemPosition(pos: Int) {
            _bookDto = _books.get(pos);
            _clickListener.setTargetBook(_bookDto);
        }

        init {
            bookView.setOnClickListener { view ->
                if (EcoachBooksUtils.isBookDownloaded(_bookDto.Id, callerContext)) {
                    _clickListener.startBookReader();
                } else {
                    /****** alert dialog  to confirm down load  */
                    //if the book is being downloaded, just call the downloader to handle the cancel action
                    if (!_clickListener.isBookDownloading() && !BooksManager.isBookDownloaded(_bookDto.Id, callerContext)) {
                        val dialogBuilder = AlertDialog.Builder(callerContext);
                        dialogBuilder.setMessage(callerContext.getString(R.string.prompt_start_downloading).replace("{{0}}", _bookDto.Title));
                        dialogBuilder.setPositiveButton(callerContext.getText(R.string.yes_text), object : DialogInterface.OnClickListener {

                            override fun onClick(dialog: DialogInterface, which: Int) {
                                _clickListener.startBookDownloader();
                                dialog.dismiss();
                            }
                        })

                        dialogBuilder.setNegativeButton(callerContext.getText(R.string.no_text), object : DialogInterface.OnClickListener {
                            override fun onClick(dialog: DialogInterface, which: Int) {
                                dialog.dismiss();
                            }
                        })
                        dialogBuilder.show();
                    } else if (_clickListener.isBookDownloading()) {
                        val dialogBuilder = AlertDialog.Builder(callerContext)
                        dialogBuilder.setMessage(callerContext.getString(R.string.prompt_stop_downloading).replace("{{0}}", _bookDto.Title))
                        dialogBuilder.setPositiveButton(callerContext.getText(R.string.yes_text), object : DialogInterface.OnClickListener {

                            override fun onClick(dialog: DialogInterface, which: Int) {
                                _clickListener.startBookDownloader()
                                dialog.dismiss()
                            }
                        })

                        dialogBuilder.setNegativeButton(callerContext.getText(R.string.no_text), object : DialogInterface.OnClickListener {
                            override fun onClick(dialog: DialogInterface, which: Int) {
                                dialog.dismiss()
                            }
                        })
                        dialogBuilder.show()
                    } else if (BooksManager.isBookDownloaded(_bookDto.Id, callerContext)) {
                        //Book has been downloaded, add to shelf
                        _bookDownloadedListener!!.downloadCompletedCallBack(_bookDto)
                    } else {
                        bookProgress.visibility = View.VISIBLE
                        _clickListener.startBookDownloader()
                    }
                }
            }


            bookView.setOnLongClickListener { view ->

                EcoachBooksUtils.makeYesNoAlert(callerContext, R.string.msg_remove_book_confirmation, { dialog, which ->
                    //only remove from library if not local book!!

                    if (_bookDto.IsLocal) {
                        _logger.debug("Removing local book: ${_bookDto.Id}");
                        removeBookFromList(_bookDto);
                        EcoachBooksDatabase.getInstance().deleteBook(_bookDto.Id);
                        _encryptedFileWriter.deleteBookFromDisk(bookView.tag as String);
                        EcoachBooksUtils.deleteRecursive(File(BooksManager(callerContext).makeCoverPathFromId(_bookDto.Id)));
                    } else {
                        _encryptedFileWriter.deleteBookFromDisk(bookView.tag as String);
                        _booksManager.markBookAsNotDownloaded(bookView.tag as String);
                        _logger.debug("Done deleting book");
                        //visually update the book
                        bookNotDownloadedIndicator.startAnimation(AnimationUtils.loadAnimation(callerContext, R.anim.fadein));
                        bookNotDownloadedIndicator.postOnAnimation { bookNotDownloadedIndicator.visibility = View.VISIBLE; }
                    }
                }).show();
                true
            }
        }//bookTitle = (TextView) itemView.findViewById(R.id.bookTitle2);
        // readingProgressPercent = (TextView) itemView.findViewById(R.id.readingProgressPercentage);

    }

    inner class BookClickListener(var targetView: View) //the full book view. Progress bar, et al.
    {
        var _bookDto: BookDto = BookDto();
        var _downloadSubscription: Subscription? = null
        var downloadTriggerSent = false

        fun setTargetBook(dto: BookDto) {
            _bookDto = dto;
        }

        fun isBookDownloading(): Boolean {
            val downloadProgress = targetView.findViewById(R.id.bookProgressBar) as ProgressBar
            val dlProgress = downloadProgress.progress
            return (downloadTriggerSent || (dlProgress > 0 && dlProgress < 100))
            //on cancel, set the downloadTriggerSent to false and reset download progress to 0
        }

        //start download
        fun startBookDownloader() {
            _logger.debug("Starting downloader for : " + _bookDto.Id)

            targetView.findViewById(R.id.bookProgressBar).visibility = View.VISIBLE
            //((TextView) mView.findViewById(R.id.readingProgressPercentage)).setText("0%");
            //don't download twice
            if (BooksManager.isBookDownloaded(_bookDto.Id, callerContext)) {
                _logger.debug("Book has already been downloaded!")
                Toast.makeText(callerContext, _bookDto.Title + " has already been downloaded", Toast.LENGTH_SHORT).show()
                return
            }

            //if user is offline, notify
            if (!EcoachBooksUtils.isOnline(callerContext)) {
                Toast.makeText(callerContext, "Could not download '${_bookDto.Title}'. Please make sure you're online and try again", Toast.LENGTH_SHORT).show()
            }

            if (downloadTriggerSent) {
                if (_downloadSubscription != null && !_downloadSubscription!!.isUnsubscribed) {
                    _downloadSubscription!!.unsubscribe()
                    Toast.makeText(callerContext, callerContext.getString(R.string.msg_download_book_cancelled).replace("{{0}}",_bookDto.Title), Toast.LENGTH_SHORT).show()
                    _logger.debug("Stopping download")
                }
                downloadTriggerSent = false
                val downloadProgress = targetView.findViewById(R.id.bookProgressBar) as ProgressBar
                downloadProgress.progress = 0
                return
            }

            downloadTriggerSent = true
            _downloadSubscription = _ecoachBooksApi.downloadBook(_bookDto.Id)
                    .observeOn(Schedulers.io())
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .retry(1)
                    .flatMap({ bookData ->
                        _logger.debug("Inside flatpmap")
                        bookData.dto = _bookDto
                        _encryptedFileWriter.saveDownloadedBookToDisk(bookData)
                    })
                    .subscribe(object : Subscriber<BookDto>() {
                        override fun onCompleted() {
                            _logger.debug("Download completed for :" + _bookDto.Id)
                           // Toast.makeText(callerContext, (callerContext as AppCompatActivity).getString(R.string.msg_finished_downloading_book).replace("{{0}}", _bookDto.Title), Toast.LENGTH_SHORT).show()
                        }

                        override fun onError(e: Throwable) {
                            downloadTriggerSent = false
                            e.printStackTrace()
                        }

                        override fun onNext(bookDto: BookDto) {
                            //update the target progressbar...
                            //  _logger.debug( "Download progress: " + bookDto.SaveProgress);
                            (callerContext as AppCompatActivity).runOnUiThread {

                                val _progressBar = targetView.findViewById(R.id.bookProgressBar) as ProgressBar;
                                _progressBar.progress = bookDto.SaveProgress

                                if (bookDto.SaveProgress == 100) {
                                    downloadTriggerSent = false
                                    _progressBar.progressDrawable.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
                                    _progressBar.startAnimation(AnimationUtils.loadAnimation(callerContext, R.anim.fadeout));
                                    _progressBar.postOnAnimation { _progressBar.visibility = View.GONE; }
                                    var _bookNotDownloadedIndicator = targetView.findViewById(R.id.notDownloadedBookIndicator) as ImageView;
                                    _bookNotDownloadedIndicator.startAnimation(AnimationUtils.loadAnimation(callerContext, R.anim.fadeout));
                                    _bookNotDownloadedIndicator.postOnAnimation { _bookNotDownloadedIndicator.visibility = View.GONE; }

                                    if (_bookDownloadedListener != null) {
                                        _bookDownloadedListener!!.downloadCompletedCallBack(bookDto)
                                    }
                                }
                            }
                        }
                    });
            /*.subscribe({ bookDto ->
                val _progressBar = targetView.findViewById(R.id.bookProgressBar) as ProgressBar;
                _progressBar.progress = bookDto.SaveProgress
                _logger.debug("Saving book: ${bookDto.SaveProgress}. Thread: ${Thread.currentThread().id}");
                if (bookDto.SaveProgress == 100) {
                    downloadTriggerSent = false
                    _progressBar.progressDrawable.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
                    _progressBar.startAnimation(AnimationUtils.loadAnimation(callerContext, R.anim.fadeout));
                    _progressBar.postOnAnimation { _progressBar.visibility = View.GONE; };

                    var _bookNotDownloadedIndicator = targetView.findViewById(R.id.notDownloadedBookIndicator) as ImageView;
                    _bookNotDownloadedIndicator.startAnimation(AnimationUtils.loadAnimation(callerContext, R.anim.fadeout));
                    _bookNotDownloadedIndicator.postOnAnimation { _bookNotDownloadedIndicator.visibility = View.GONE; };

                    if (_bookDownloadedListener != null) {
                        _logger.debug("Calling download completed callback");
                        _bookDownloadedListener!!.downloadCompletedCallBack(_bookDto);
                    }
                    _logger.debug("Download completed for :" + _bookDto.Id);
                    Toast.makeText(callerContext, "Finished downloading" + _bookDto.Title, Toast.LENGTH_SHORT).show();

                }

            }, { error ->
                downloadTriggerSent = false
                error.printStackTrace()
            }, { });*/
        }

        //start reader
        fun startBookReader() {
            //need to decrypt the book first
            //Show loading dialog?
            val _progressDialog = ProgressDialog(callerContext)
            _progressDialog.setMessage(callerContext.getString(R.string.title_loading_text));
            _progressDialog.isIndeterminate = true
            _progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
            _progressDialog.show();

            _encryptedFileWriter.prepareBookForReadingObservable(_bookDto)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe({ dto ->
                        _logger.debug("Starting reader for : " + dto.Id)
                        val intent = Intent(callerContext, ReaderActivity::class.java)

                        val extras = Bundle()
                        extras.putString(Constants.BOOK_TITLE, dto.Title)
                        extras.putString(Constants.BOOK_ID, dto.Id)
                        intent.putExtras(extras)
                        _progressDialog.dismiss()
                        callerContext.startActivity(intent)
                    }, {
                        error ->
                        _progressDialog.dismiss();
                        Crashlytics.logException(error);
                    });

        }
    }

}

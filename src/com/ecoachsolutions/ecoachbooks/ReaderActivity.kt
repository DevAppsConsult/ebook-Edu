package com.ecoachsolutions.ecoachbooks

import android.annotation.TargetApi
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.webkit.*
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.*
import com.ecoachsolutions.ecoachbooks.Helpers.ObservableWebView.OnScrollChangedCallback
import com.ecoachsolutions.ecoachbooks.Services.CleanupService
import com.ecoachtools.lectio.Chapter
import com.ecoachtools.lectio.Lectio
import com.squareup.picasso.Picasso
import org.apache.commons.lang3.StringUtils
import java.io.File


class ReaderActivity : AppCompatActivity() {
    // Responsible for showing the table of contents for the book on click

    //views and layout
    private var inflater: LayoutInflater? = null
    private val _drawerLayout: DrawerLayout by lazy { findViewById(R.id.bookReaderDrawerLayout) as DrawerLayout }
    private val _bookChapterListView: ListView by lazy { findViewById(R.id.chapterList) as ListView }
    private var _drawerToggle: ActionBarDrawerToggle? = null
    private val _bookChapterContentsWebView: ObservableWebView by lazy { findViewById(R.id.bookChapterContents) as ObservableWebView }
    private var _bookChapterListViewAdapter: BookChapterListViewAdapter? = null
    private val _buttonBar: LinearLayout by lazy { findViewById(R.id.contentReaderButtonBar) as LinearLayout }
    private val _readerViewCommentsButton: Button by lazy { findViewById(R.id.readerViewCommentsButton) as Button }
    private val _readerShareButton: Button by lazy { findViewById(R.id.readerShareButton) as Button }
    private val _nextChapterButton by lazy { findViewById(R.id.next_chapter_button) as Button }
    private val _prevChapterButton by lazy { findViewById(R.id.previous_chapter_button) as Button }


    //animations
    private val _animationFadeIn: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fadein) }
    private val animationFadeOut: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.fadeout) }
    private var barVisible: Boolean = false

    private val _preferences: EcoachBooksPreferences by lazy { EcoachBooksPreferences(applicationContext) };

    private var currentChapter = 0
    private var previousChapter = 0
    private var lastScrollPosition = 0
    private var scrollPosition = 0
    private var bookId = ""
    internal var title = ""

    private val _globalProgressDialog: ProgressDialog by lazy { ProgressDialog(this) }
    //new reader
    private var _lectioReader: Lectio? = null
    private val _booksManager: BooksManager by lazy { BooksManager(this) }
    private val _logger = LogHelper(this);

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reader_layout)

        val _toolbar = findViewById(R.id.toolBar) as Toolbar
        setSupportActionBar(_toolbar)

        initializeWebView();

        initializeChapterControlButtons();

        //update previous chapter
        previousChapter = if (currentChapter == 0) 0 else --currentChapter

        //Animation init
        _animationFadeIn.fillAfter = true
        animationFadeOut.fillAfter = true


        //get header image
        val chapterListHeader = findViewById(R.id.chapterListHeader) as ImageView

        //If the app is being called for the first time, there will be no previous state
        //data passed in so necessary book information exists in the intent. Else, extract this information
        // from the saved instance
        if (savedInstanceState == null) {
            bookId = intent.extras.getString(Constants.BOOK_ID)
            title = intent.extras.getString(Constants.BOOK_TITLE)
        } else {
            bookId = savedInstanceState.getString(Constants.BOOK_ID)
            title = savedInstanceState.getString(Constants.BOOK_TITLE)
        }
        //inflate from cover path
        Picasso.with(this).load(File(_booksManager.makeCoverPathFromId(bookId))).into(chapterListHeader)

        var _compressedBookPath = EcoachBooksUtils.getDecryptedFilePath(this, bookId);
        inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater;
        // Update book chapter
        var bookMetadata = EcoachBooksDatabase.getInstance().getBookByRemoteId(bookId);

        if (!TextUtils.isEmpty(bookMetadata?.path)) {
            _compressedBookPath = bookMetadata!!.path;
        }

        val lastChapter = bookMetadata?.lastChapter
        if (StringUtils.isNotEmpty(lastChapter)) {
            currentChapter = Integer.parseInt(lastChapter)
        }
        _drawerToggle = object : ActionBarDrawerToggle(this, _drawerLayout, _toolbar,
                R.string.drawer_open, R.string.drawer_close) {

            override fun onDrawerClosed(drawerView: View?) {
                super.onDrawerClosed(drawerView)
                invalidateOptionsMenu()
            }

            override fun onDrawerOpened(drawerView: View?) {
                super.onDrawerOpened(drawerView)
                invalidateOptionsMenu()
            }
        }

        // Set the new drawer toggle as the DrawerListener
        _drawerLayout.addDrawerListener(_drawerToggle!!)
        // These enable the icon to the top-right to control the drawer
        // open/close
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        // End init

        // Get book
        Thread {
            try {
                showProgressDialog(title)
                _lectioReader = Lectio(_compressedBookPath);

                _bookChapterListViewAdapter = BookChapterListViewAdapter(this, R.layout.chapterlist_layout,
                        R.id.chapter_name, _lectioReader!!.book.tableOfContents)

                //Scroll to last read page if exists
                val lastReadPage = EcoachBooksDatabase.getInstance().getBookByRemoteId(bookId)!!.scrollPosition
                _bookChapterContentsWebView.scrollBy(0, lastReadPage)
                // Set activity title to book title
                setTitle(title);

                //Set webviewclient to intercept url calls and fetch appropriate resource
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    _bookChapterContentsWebView.setWebViewClient(object : WebViewClient() {
                        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                        override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse {
                            return _lectioReader!!.interceptWebViewRequest(request.url.path)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            _globalProgressDialog.dismiss()
                            val jsPadding = "javascript:document.body.style['margin-bottom'] = '50px'"
                            view.evaluateJavascript(jsPadding, null)
                            _logger.debug("page finished!")
                            //    injectPaginationJs();
                        }
                    })
                } else {
                    _bookChapterContentsWebView.setWebViewClient(object : WebViewClient() {
                        override fun shouldInterceptRequest(webView: WebView, url: String): WebResourceResponse {
                            return _lectioReader!!.interceptWebViewRequest(url)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            _globalProgressDialog.dismiss()
                            val jsPadding = "javascript:(function() {document.body.style['margin-bottom'] = '50px'})()"
                            view.loadUrl(jsPadding);
                            _logger.debug("page finished!")
                            //      injectPaginationJs();
                        }
                    })
                }
                openLectioChapterInWebView(_lectioReader!!.book.tableOfContents[currentChapter].resourceLink)

                // Item click handler. Gets the chapter data and passes it to the
                // correct function for viewing
                _bookChapterListView.onItemClickListener = OnItemClickListener { parent, v, position, id ->
                    //don't do anything if the person just tapped on the current chapter
                    if (currentChapter == position) {
                        return@OnItemClickListener
                    }

                    previousChapter = currentChapter
                    currentChapter = position
                    loadChapterInfo(position)

                    // Close the drawers after loading the page
                    _drawerLayout.closeDrawers()
                }
                _bookChapterListView.adapter = _bookChapterListViewAdapter

            } catch (e: Exception) {
                runOnUiThread { _globalProgressDialog.dismiss() }
                _logger.error("Something went wrong when trying to open book :(", e);
                EcoachBooksUtils.makeOkAlert(this, R.string.title_something_went_wrong, getString(R.string.error_failed_to_load_content), { dialogInterface: DialogInterface, i: Int ->
                    this.finish();
                }).show();
            }
        }.run();
        //If initialization goes well, start cleanup service to remove older content
        startCleanup(bookId)

        // Localytics.tagEvent(LocalyticsTags.EVENT_BOOK_OPENED)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.contentviewmenu, menu)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(Constants.BOOK_TITLE, title)
        outState.putString(Constants.BOOK_TITLE, bookId)
        super.onSaveInstanceState(outState)
    }

    override fun onPostCreate(savedInstanceState1: Bundle?) {
        super.onPostCreate(savedInstanceState1)
        _drawerToggle!!.syncState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            //We do not want to delete the book on a config change because the book must still be
            //in the extracted state on disk when redrawing
            startCleanup("") //We passing no book will wipe everything from the cache folder
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        _drawerToggle!!.onConfigurationChanged(newConfig)

        //Force chapter retention
        loadChapterInfo(currentChapter)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        _logger.debug("options item selected! $item")
        if (_drawerToggle!!.onOptionsItemSelected(item)) {
            return true
        }
        // Handle your other action bar items...
        when (item.itemId) {
            R.id.action_settings -> {
                showSettingsPopup()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun initializeWebView() {
        //Webview init
        _bookChapterContentsWebView.setPadding(0, 0, 0, 100)
        var webviewSettins = _bookChapterContentsWebView.settings;

        webviewSettins.javaScriptEnabled = true
        // webviewSettinskgroundColor(Color.parseColor(getString(R.drawable.reader_text_backgroundcolor)));
        webviewSettins.cacheMode = WebSettings.LOAD_NO_CACHE
        webviewSettins.loadsImagesAutomatically = true
        webviewSettins.setGeolocationEnabled(false)
        webviewSettins.setNeedInitialFocus(false)
        webviewSettins.saveFormData = false
        webviewSettins.setAppCacheEnabled(true);
        //   webviewSettins.useWideViewPort = true;
        //  webviewSettins.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS;
        _bookChapterContentsWebView.isHorizontalScrollBarEnabled = false;

        /* Initialize touch listener for swipe gestures */
        _bookChapterContentsWebView.setOnTouchListener(object : OnSwipeTouchListener(applicationContext) {

            override fun onTouchHandler() {
                //Try to save to db
                if (lastScrollPosition != scrollPosition) {
                    val metadata = EcoachBooksDatabase.getInstance().getBookByRemoteId(bookId)
                    metadata?.scrollPosition = scrollPosition
                    EcoachBooksDatabase.getInstance().updateBook(metadata!!)
                }
                // _logger.debug("Saved vertical position to db: " + scrollPosition);
            }

            override fun onSwipeLeft() {
                gotoNextChapter()
            }

            override fun onSwipeRight() {
                gotoPreviousChapter()
            }

            override fun onScrollHandler() {
                //     _logger.debug("still scrolling");
            }

        })
        _bookChapterContentsWebView.onScrollChangedCallback = object : OnScrollChangedCallback {
            override fun onScroll(l: Int, t: Int) {
                //Do stuff
                lastScrollPosition = scrollPosition
                scrollPosition = t
                if (!_bookChapterContentsWebView.canScrollVertically(1)) {
                    //   _logger.debug( "bottom!");
                    _buttonBar.startAnimation(_animationFadeIn)
                    barVisible = true
                } else if (barVisible && _animationFadeIn.hasEnded()) {
                    _buttonBar.startAnimation(animationFadeOut)
                    barVisible = false
                }
            }
        }

        //Disable long click
        _bookChapterContentsWebView.setOnLongClickListener({ true })

        _bookChapterContentsWebView.isLongClickable = false;

    }

    private fun initializeChapterControlButtons() {
        _nextChapterButton.setOnClickListener({ gotoNextChapter() })
        _prevChapterButton.setOnClickListener({ gotoPreviousChapter() })
        _readerViewCommentsButton.setOnClickListener { showCommentsForBook() }
        _readerShareButton.setOnClickListener { showShareMenu() }
    }

    private fun updateChapterColor(view: View, color: Int) {
        view.setBackgroundColor(color)
    }

    private fun injectPaginationJs() {
        _logger.debug("Injecting pagination js...");
        //see: http://stackoverflow.com/questions/3636052/html-book-like-pagination
        var js = "javascript:function initialize() { " +
                "var d = document.getElementsByTagName('body')[0];" +
                "var ourH = window.innerHeight; " +
                "var ourW = window.innerWidth; " +
                "var fullH = d.offsetHeight; " +
                "var pageCount = Math.floor(fullH/ourH)+1;" +
                "var currentPage = 0; " +
                "var newW = pageCount*ourW; " +
                "d.style.height = ourH+'px';" +
                "d.style.width = newW+'px';" +
                "d.style.webkitColumnGap = '2px'; " +
                "d.style.margin = 0; " +
                "d.style.webkitColumnCount = pageCount;" +
                "}";

        _bookChapterContentsWebView.loadUrl(js);
        _bookChapterContentsWebView.loadUrl("javascript:initialize();");
    }

    private fun trackLastChapterForBook(chapter: String) {
        if (_lectioReader!!.book == null) {
            _logger.error("Error! Book not initialized!");
            return
        }

        val metadata = EcoachBooksDatabase.getInstance().getBookByRemoteId(bookId)
        metadata?.lastChapter = chapter
        EcoachBooksDatabase.getInstance().updateBook(metadata!!)
        _logger.debug("updated last read chapter to " + chapter)
    }

    //Chapter Navigation
    private fun gotoNextChapter() {
        previousChapter = currentChapter
        loadChapterInfo(++currentChapter)
    }

    private fun gotoPreviousChapter() {
        previousChapter = currentChapter
        loadChapterInfo(--currentChapter)
    }

    private fun showCommentsForBook() {
        _logger.debug("Showing comment.... for bookId = $bookId")

        if (!EcoachBooksUtils.isOnline(this)) {
            Snackbar.make(_bookChapterContentsWebView, R.string.msg_not_online, Snackbar.LENGTH_SHORT).show()
            return
        }

        var commentsViewIntent = Intent(this, CommentViewActivity::class.java)
        commentsViewIntent.putExtra(Constants.BOOK_ID, bookId)
        commentsViewIntent.putExtra(Constants.BOOK_TITLE, title)
        startActivity(commentsViewIntent)

    }

    private fun showShareMenu() {

    }

    private fun loadChapterInfo(position: Int) {
        if (position < 0 || position > _lectioReader!!.book.tableOfContents.size) {
            return
        }
        try {
            openLectioChapterInWebView(_lectioReader!!.book.tableOfContents[position].resourceLink)
            //update chapter highlighting
            val previousView = getViewByPosition(previousChapter, _bookChapterListView)
            val currentView = getViewByPosition(currentChapter, _bookChapterListView)

            updateChapterColor(currentView, ContextCompat.getColor(this, R.color.ecoach_orange))
            updateChapterColor(previousView, (ContextCompat.getDrawable(this, R.drawable.navdrawer_backgroundcolor) as ColorDrawable).color)

        } catch (ex: Exception) {
            Toast.makeText(this, "Could not open chapter :(. Please try again", Toast.LENGTH_SHORT).show()
            ex.printStackTrace()
        }

    }

    private fun openLectioChapterInWebView(url: String?) {
        _logger.error("Url provided was null for book. Will not be able to load book!");
        //hack to force the url overriding part of our pipeline to be called
        _bookChapterContentsWebView.loadUrl("file:///" + url)
        trackLastChapterForBook(currentChapter.toString())

        _bookChapterListView.post({ _bookChapterListView.smoothScrollToPosition(currentChapter) })
    }

    // helper method directly from http://stackoverflow.com/questions/24811536/android-listview-get-item-view-by-position
    //handles situations where the item is on screen and when it is not
    private fun getViewByPosition(pos: Int, listView: ListView): View {
        val firstListItemPosition = listView.firstVisiblePosition
        val lastListItemPosition = firstListItemPosition + listView.childCount - 1

        if (pos < firstListItemPosition || pos > lastListItemPosition) {
            return listView.adapter.getView(pos, null, listView)
        } else {
            val childIndex = pos - firstListItemPosition
            return listView.getChildAt(childIndex)
        }
    }

    private fun showProgressDialog(bookTitle: String) {
        runOnUiThread {
            _globalProgressDialog.setTitle(R.string.title_loading_text)
            _globalProgressDialog.setMessage(bookTitle)
            _globalProgressDialog.setCancelable(false)
            _globalProgressDialog.show()
            _logger.debug("Showing progress bar!")
        };
    }

    private fun showSettingsPopup() {
        val v = inflater!!.inflate(R.layout.reader_settings_dialog_layout, null)

        //Settings click handler initialize
        val fontSizeTextView = v.findViewById(R.id.textSizeDisplay) as TextView
        fontSizeTextView.text = _preferences.getTextZoom();

        val increaseFontSizeButton = v.findViewById(R.id.increaseFontSizeButton) as Button
        val decreaseFontSizeButton = v.findViewById(R.id.decreaseFontSizeButton) as Button

        increaseFontSizeButton.setOnClickListener({
            val txt = Integer.parseInt(fontSizeTextView.text.toString().replace("%", "")) + 5
            fontSizeTextView.text = "$txt%"
        })

        decreaseFontSizeButton.setOnClickListener({
            var txt = Integer.parseInt(fontSizeTextView.text.toString().replace("%", "")) - 5
            if (txt < 0) {
                txt = 0
            }
            fontSizeTextView.text = "$txt%"
        })


        val dialogClickListener = DialogInterface.OnClickListener { dialog, which ->
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> saveDisplaySettings(v)
                DialogInterface.BUTTON_NEGATIVE -> {
                }
            }// Do nothing
        }

        var textSettingsAlertDialog = AlertDialog.Builder(this)
        textSettingsAlertDialog.setTitle(R.string.action_settings)

        textSettingsAlertDialog.setView(v)
        textSettingsAlertDialog.setPositiveButton(android.R.string.ok, dialogClickListener)
        textSettingsAlertDialog.setNegativeButton(android.R.string.cancel, null)

        textSettingsAlertDialog.show()
    }

    /**
     * Save settings for the webview. Currently only handles size
     * @param v
     */
    private fun saveDisplaySettings(v: View) {

        val size = (v.findViewById(R.id.textSizeDisplay) as TextView).text.toString()
        _preferences.saveTextZoom(size.replace("%", ""));

        _bookChapterContentsWebView.settings.textZoom = Integer.parseInt(size.replace("%", ""))
        // mBookChapterContentsWebView.loadUrl("javascript:window.location.reload( true )");
    }

    private inner class BookChapterListViewAdapter(context: Context, resource: Int,
                                                   textViewResourceId: Int, objects: List<Chapter>) : ArrayAdapter<Chapter>(context, resource, textViewResourceId, objects) {

        private inner class ViewHolder(private val row: View) {
            private var titleHolder: TextView? = null

            val title: TextView
                get() {
                    if (null == titleHolder) {
                        titleHolder = row.findViewById(R.id.chapter_name) as TextView
                    }
                    return titleHolder!!
                }
        }

        override fun getView(position: Int, sourceConvertView: View?, parent: ViewGroup): View {
            var convertView = sourceConvertView
            var holder: ViewHolder?
            var title: TextView?
            val bookChapter = getItem(position)
            if (convertView == null) {
                convertView = inflater!!.inflate(R.layout.chapterlist_layout, null)
                holder = ViewHolder(convertView)
                convertView!!.tag = holder
            }
            holder = convertView.tag as ViewHolder
            title = holder.title
            title.text = bookChapter.title
            //NOTE: This might affect draw performance on older devices. Unsure.

            if (currentChapter != position) {
                convertView.setBackgroundColor(ContextCompat.getColor(context, R.color.navdrawer_backgroundcolor))
            } else {
                _logger.debug("Current chapter : " + currentChapter)
                convertView.setBackgroundColor(ContextCompat.getColor(context, R.color.ecoach_orange))
            }

            return convertView
        }

    }

    private fun startCleanup(bookId: String) {
        _logger.debug("Attempting to start cleanup service. Excluding book : " + bookId)
        val intent = Intent(this, CleanupService::class.java)
        intent.putExtra(Constants.CLEANUP_SERVICE_EXCLUDE, bookId)
        startService(intent)
    }

}

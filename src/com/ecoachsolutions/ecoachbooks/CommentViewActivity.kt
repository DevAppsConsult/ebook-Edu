package com.ecoachsolutions.ecoachbooks

import android.app.ProgressDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.MenuItem
import com.ecoachsolutions.ecoachbooks.Adapters.CommentListAdapter
import com.ecoachsolutions.ecoachbooks.Helpers.Constants
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksApi
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper
import com.ecoachsolutions.ecoachbooks.Models.BookCommentDisplayDto
import com.ecoachsolutions.ecoachbooks.Models.CommentResponse
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*

class CommentViewActivity : AppCompatActivity() {

    private val _logger = LogHelper(this)
    private val _ecoachBooksApi by lazy { EcoachBooksApi.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.comments_view)

        val _toolbar = findViewById(R.id.toolBar) as Toolbar
        setSupportActionBar(_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        var bookId = intent.getStringExtra(Constants.BOOK_ID)

        if(TextUtils.isEmpty(bookId)){
            _logger.debug("Book Id was not expected to be empty! Exiting activity since there will be no comments to display");
            finish();
            return;
        }

        initializeComments(bookId);

        //initialize view actions
        findViewById(R.id.readerAddCommentButton)?.setOnClickListener {
            var commentDialog = AddCommentDialogFragment();
            //set the book's Id for DB use
            commentDialog.BookId = bookId;
            commentDialog.show(supportFragmentManager, "AddCommentDialog");
        }

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        _logger.debug("Options item selected! $item")
        // Handle your other action bar items...
        when (item.itemId) {
            android.R.id.home -> {
                _logger.debug("Back button prezzed!");
                finish();
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun displayComments(bookId: String, comments : ArrayList<CommentResponse>){
        _logger.debug("Displaying comments for Book Id $bookId");

        var display = windowManager.defaultDisplay;
        var outMetrics = DisplayMetrics();
        display.getMetrics(outMetrics);
        var targetColumnCount = Math.round(outMetrics.widthPixels / resources.getDimension(R.dimen.comment_box_width));

        var gridLayoutManager = GridLayoutManager(this, targetColumnCount);
        gridLayoutManager.orientation = GridLayoutManager.VERTICAL;

        var recyclerView = findViewById(R.id.commentsRecyclerView) as RecyclerView;
        recyclerView.layoutManager = gridLayoutManager;

        var mappedComments = comments.map{ cr -> BookCommentDisplayDto(cr.Content, cr.CreatedBy, cr.CreatedOn) }

        recyclerView.adapter = CommentListAdapter(mappedComments as ArrayList<BookCommentDisplayDto>, this)

    }

    private fun initializeComments(bookId : String) {
        var pd = ProgressDialog(this)
        pd.setTitle(R.string.title_getting_comments)
        pd.show()

        _ecoachBooksApi.getComments(bookId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ cr ->
                            displayComments(bookId, cr)
                            pd.dismiss()
                        }, {
                            error ->
                            _logger.error("Something went wrong while fetching comments :(", error)
                            pd.dismiss()
                            EcoachBooksUtils.makeOkAlert(this, R.string.title_generic_error,
                                                        getString(R.string.error_failed_to_load_content),
                                                        { d, w ->
                                                            finish()
                                                            d.dismiss()
                                                        }).show()
                        })



    }

}

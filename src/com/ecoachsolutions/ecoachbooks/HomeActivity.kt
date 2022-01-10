package com.ecoachsolutions.ecoachbooks

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.app.SearchManager
import android.content.*
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.NavigationView
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.webkit.CookieSyncManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.ecoachsolutions.ecoachbooks.Adapters.BookListAdapter
import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Core.SessionManager
import com.ecoachsolutions.ecoachbooks.Helpers.*
import com.ecoachsolutions.ecoachbooks.Models.BookDto
import com.ecoachsolutions.ecoachbooks.Models.BookMetadata
import com.ecoachsolutions.ecoachbooks.Models.asBookDto
import com.ecoachtools.lectio.Lectio
import com.ipaulpro.afilechooser.utils.FileUtils
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*


class HomeActivity : AppCompatActivity(), IBookDownloadedListener {


    private var settingsPageDialog: AlertDialog.Builder? = null
    private var popupView: View? = null
    //navigation
    private var _drawerToggle: ActionBarDrawerToggle? = null
    private var _drawerLayout: DrawerLayout? = null
    private var _navDrawerView: NavigationView? = null
    private var _userNameTextView: TextView? = null
    private var _accountBalanceTextView: TextView? = null
    private var _importBooksButton: FloatingActionButton? = null;
    //end navigation
    private var _updateFrequencyOptions: Array<String>? = null
    private val _ecoachBooksApi: EcoachBooksApi by lazy { EcoachBooksApi.getInstance(this); }
    //Using this to track all the subscriptions here
    private var _compositeSubscription: CompositeSubscription? = null
    private var _shelfRecyclerView: RecyclerView? = null
    private val _booksManager: BooksManager by lazy { BooksManager(this); }
    private var _downloadNetworkRadioGroup: RadioGroup? = null
    private var _wiFiOnlyDownloadButton: RadioButton? = null
    private var _wifiAndDataDownloadButton: RadioButton? = null
    private val _preferences by lazy { EcoachBooksPreferences(this); }


    private var _appVersion: String? = null
    private var _toolBar: Toolbar? = null
    private var _shelfBooks = ArrayList<BookDto>()
    private var _shelfAdapter: BookListAdapter? = null;

    //layout managers
    var _shelfGridLayoutManager: GridLayoutManager? = null;

    //Loading screen fragment. Responsible
    var _loadingScreenFragmentContainer: View? = null

    //wtf
    private val _accountManager: AccountManager by lazy { AccountManager.get(this) }
    private var _account: Account? = null
    private val _globalProgressDialog: ProgressDialog by lazy { ProgressDialog(this); }
    private var _isBoundToUpdater = false
    private val FILE_SELECT_CODE = 0;


    var _logger = LogHelper(this);

    //broadcast receivers
    private val _loginMessageReceiver: BroadcastReceiver by lazy {
        var activity = this;
        var _ignoreNewMessage = false;
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (!_ignoreNewMessage && context != null) {
                    _ignoreNewMessage = true;
                    _logger.debug("Received message to start login activity");
                    try {

                        EcoachBooksUtils.makeYesNoAlert(activity, R.string.msg_revalidation_error, { dialog, which ->
                            startLoginActivity(activity);
                        }).show();
                    } catch (ex: Exception) {
                        _logger.error("Failed to handle ${BroadcastMessages.BROADCAST_DO_LOGIN} broadcast", ex);
                    }

                }
            }
        }

    }

    private val _newBookAddedMessageReceiver: BroadcastReceiver by lazy {

        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) {
                    return;
                }

                if (context != null) {
                    try {
                        var newBookIds = intent.getStringArrayListExtra(Constants.NEW_BOOK_IDS);

                        _logger.debug("Received message to add new books $newBookIds to UI! Undownloaded books: ${_booksManager.idsOfUnDownloadedBooks}, all books (manager) ${_booksManager.allBooks.size}, db: ${EcoachBooksDatabase.getInstance().currentBookIds}, books in shelf: ${_shelfAdapter?._books}");

                        val unDownloadedLibraryBookIds = _booksManager.idsOfUnDownloadedBooks

                        if (unDownloadedLibraryBookIds.size > 0) {
                            _logger.debug("Found some un downloaded books! $unDownloadedLibraryBookIds")
                        }

                        if (_shelfAdapter != null) {
                            var dbBooksNotInShelf = _booksManager.allBooks.filter { bk -> _shelfAdapter?._books?.map { sbk -> sbk.Id }?.contains(bk.Id) != true; }
                            _shelfAdapter?.addItemsToList(dbBooksNotInShelf);
                            removeLoadingScreen();
                        }
                    } catch (ex: Exception) {
                        _logger.error("Failed to handle ${BroadcastMessages.BROADCAST_ADD_NEW_BOOK_TO_UI} broadcast", ex);
                    }

                }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _logger.debug("Creating HomeActivity");
        setContentView(R.layout.home_page)
        _account = _accountManager.getEcoachBooksAccount();
        settingsPageDialog = AlertDialog.Builder(this)
        settingsPageDialog!!.setTitle(R.string.action_settings)
        _compositeSubscription = CompositeSubscription();

        /* we need this so retrofit doesn't crash since you can't sync webkit cookies without it */
        CookieSyncManager.createInstance(this)

        if (_account == null) {
            //There's no account on this device. Start authenticator
            _logger.debug("No account exists on this device. Starting login activity...");
            startLoginActivity(this);
            return
        } else {
            //There's an account but we need to be sure the account is actually valid
            _logger.debug("Checking validity of account...");
            _ecoachBooksApi.assertValidConnection(_accountManager, { error ->
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BroadcastMessages.BROADCAST_DO_LOGIN));
            });
            //Store cookie in shared pref
        }

        _updateFrequencyOptions = resources.getStringArray(R.array.auto_update_frequency_options);

        //Init views
        _importBooksButton = findViewById(R.id.importBooksButton) as FloatingActionButton;
        _importBooksButton!!.setOnClickListener { showImportBooksHandler(); }

        //get app version, wrap in try-catch so you don't die
        try {
            _appVersion = packageManager.getPackageInfo(packageName, 0).versionName;

        } catch (ex: Exception) {
            ex.printStackTrace();
            _logger.debug("Could not get version code. Defaulting to 0");
        }

        //Settings
        initializeShelf();

        initializeToolbarAndNavigationDrawer();

        handleIntent(intent);

        if (!intent.getBooleanExtra(Constants.IS_LOGIN_INTENT, false)) {
            requestManualSync();
        } else {
            //setup periodic sync every 5 minutes
            if (!_preferences.isPeriodicSyncEnabled()) {
                _logger.debug("Enabling periodic sync because it has not been enabled for this device.");
                ContentResolver.setSyncAutomatically(_account, getString(R.string.SyncAuthority), true);
                ContentResolver.addPeriodicSync(_account, getString(R.string.SyncAuthority), Bundle.EMPTY, 300);
                _preferences.setPeriodicSyncEnabled();
            }
        }
    }

    override fun onStart() {
        super.onStart()
        //bind to updater service
        //get account balance
        if (!EcoachBooksUtils.isOnline(this)) {
            return
        }
        _ecoachBooksApi.getAccountBalance()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ aDouble ->
                    val formattedBalance = "%.2f".format(aDouble)
                    //_logger.debug("GetAccountBalance returned: " + formattedBalance);
                    EcoachBooksUtils.saveAccountBalance(applicationContext, formattedBalance)
                    _accountBalanceTextView!!.text = formattedBalance
                }, { throwable ->
                    //On error
                    _logger.error("Could not get valid account balance", throwable)
                })

    }

    override fun onPause() {
        CookieSyncManager.getInstance().stopSync();
        super.onPause();
        unregisterReceiver(_loginMessageReceiver);
        unregisterReceiver(_newBookAddedMessageReceiver);
    }

    override fun onResume() {
        if (_isBoundToUpdater) {
            Log.d("Updater service", "Resuming work.")
        }
        super.onResume();

        //register local broadcast manager
        _logger.debug("Registering broadcast receivers");
        registerReceiver(_loginMessageReceiver, IntentFilter(BroadcastMessages.BROADCAST_DO_LOGIN));
        registerReceiver(_newBookAddedMessageReceiver, IntentFilter(BroadcastMessages.BROADCAST_ADD_NEW_BOOK_TO_UI));
    }

    override fun onStop() {
        CookieSyncManager.getInstance().startSync()
        if (_isBoundToUpdater) {
            _compositeSubscription!!.clear()
        }
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.mainactivitymenu, menu);

        var searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager;
        var searchView = menu.findItem(R.id.action_search).actionView as SearchView;
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName));
        searchView.setOnCloseListener {
            _shelfAdapter?.replaceExistingData(_booksManager.allBooks);
            false;
        }
        super.onCreateOptionsMenu(menu);
        return true;
    }

    override fun downloadCompletedCallBack(dto: BookDto) {

        _booksManager.markBookAsDownloaded(dto.Id);

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        _drawerToggle!!.onConfigurationChanged(newConfig);
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent);
        if (intent != null) {
            handleIntent(intent);
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (_drawerToggle!!.onOptionsItemSelected(item)) {
            return true
        }

        // handle menu item selections
        when (item.itemId) {
        /*
			 * case R.id.action_checkupdate: return true; case
			 * R.id.action_signin: return true; case R.id.action_settings:
			 * return true;
			 */
        /* R.id.action_clearshelf -> {
             clearShelf()
             return true
         }*/
            R.id.action_sync -> {
                requestManualSync()
                return true
            }
            R.id.action_signin -> {
                onClickSignOut()
                return true
            }
        /*case R.id.action_goto_site:
                onClickGoToSite();
                return true;*/
        /*case R.id.action_main_settings:
                showSettingsPopup();
                return true;*/
        /*case R.id.action_about:
                onClickAbout();
                return true;*/
        /* R.id.action_update -> {
             checkForUpdates()
             return true
         }*/
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                _logger.debug("Yay got file response: ${data?.data}");
                var uri = data?.data;

                // Get the File path from the Uri
                var path = FileUtils.getPath(this, uri);
                _logger.debug("File response path: $path");

                if (FileUtils.getExtension(path).toLowerCase() == ".epub") {
                    _logger.debug("Selected epub file");
                    addFileToLibrary(path);
                } else {
                    EcoachBooksUtils.makeOkAlert(this, R.string.choose_file, getString(R.string.msg_select_epub_book)).show()
                }
            } else {
                _logger.debug("Boo no result :(");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private fun initializeShelf() {
        var display = windowManager.defaultDisplay;
        var outMetrics = DisplayMetrics();
        display.getMetrics(outMetrics);
        var targetColumnCount = Math.round(outMetrics.widthPixels / resources.getDimension(R.dimen.grid_column_width_med));

        _shelfGridLayoutManager = GridLayoutManager(this, targetColumnCount);

        _shelfRecyclerView = findViewById(R.id.bookShelfView) as RecyclerView
        _shelfRecyclerView!!.layoutManager = _shelfGridLayoutManager

        //get book list for shelf and library
        //init them in the adapters
        _shelfBooks = _booksManager.allBooks;

        _shelfAdapter = BookListAdapter(_shelfBooks, this, this)

        _shelfRecyclerView!!.adapter = _shelfAdapter;

        _loadingScreenFragmentContainer = findViewById(R.id.fragmentContainer);
        if (_shelfBooks.isEmpty()) {
            /*
            If the user has no existing books in their library, seeing the empty app will be confusing
            Instead, show them a loading screen while we wait for the first pass of updater service
            to complete. The observer of the service is responsible for removing the loading screen
            */
            _loadingScreenFragmentContainer?.visibility = View.VISIBLE
            _logger.debug("Shelf is empty...")
        } else {
            _logger.debug("Shelf is not empty. Hiding loading screen")
            removeLoadingScreen();
        }

    }

    private fun initializeToolbarAndNavigationDrawer() {

        //toolbar
        //the first run of the updater service will remove the loading screen when done
        _toolBar = findViewById(R.id.toolBar) as Toolbar
        setSupportActionBar(_toolBar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        _drawerLayout = findViewById(R.id.homePageDrawerLayout) as DrawerLayout
        _drawerToggle = ActionBarDrawerToggle(this, _drawerLayout, _toolBar, R.string.action_settings, R.string.action_settings)

        _drawerLayout!!.setDrawerListener(_drawerToggle)

        (_drawerToggle as ActionBarDrawerToggle).syncState();

        //nav drawer
        _navDrawerView = findViewById(R.id.homeNavView) as NavigationView;
        (_navDrawerView as NavigationView).setNavigationItemSelectedListener { menuItem -> handleNavigationClick(menuItem) }

        var headerView = _navDrawerView!!.inflateHeaderView(R.layout.home_nav_drawer_header);
        //Nav drawer header text
        _userNameTextView = headerView.findViewById(R.id.userNameDisplay) as TextView;
        _accountBalanceTextView = headerView.findViewById(R.id.accountBalanceText) as TextView;
        _userNameTextView!!.text = _account!!.name;
        _accountBalanceTextView!!.text = EcoachBooksUtils.getSavedAccountBalance(this);
    }

    private fun addFileToLibrary(path: String) {
        val pd = ProgressDialog(this);
        pd.setMessage(getString(R.string.msg_select_adding_book_generic));
        pd.setTitle(R.string.title_please_wait);
        pd.isIndeterminate = true;
        pd.show();
        Runnable {
            val _lectioReader = Lectio(path);

            var bookMetaData = BookMetadata(path, _lectioReader.book.author, _lectioReader.book.title, UUID.randomUUID().toString());
            if (_lectioReader.book.coverInputStream == null) {
                _logger.debug("Cover input stream is null. bye bye");
            }

            EcoachBooksUtils.writeStreamToToDisk(_lectioReader.book.coverInputStream,
                    _booksManager.makeCoverPathFromId(bookMetaData.remoteId));
            runOnUiThread {
                pd.setMessage(getString(R.string.msg_select_adding_book).replace("{{0}}", _lectioReader.book.title));

                //add book to library
                BookUtilities.addBooksToDatabase(this, bookMetaData);
                var bookDto = bookMetaData.asBookDto();
                bookDto.CoverPath = _booksManager.makeCoverPathFromId(bookMetaData.remoteId);
                _shelfAdapter!!.addItemsToList(arrayListOf(bookDto));
                _booksManager.markBookAsDownloaded(bookMetaData.remoteId);
                pd.dismiss();
            }
        }.run();
    }

    private fun showImportBooksHandler() {

        // Create the ACTION_GET_CONTENT Intent
        var getContentIntent = FileUtils.createGetContentIntent();
        getContentIntent.type = "application/epub+zip";
        var intent = Intent.createChooser(getContentIntent, getString(R.string.msg_select_a_file));
        startActivityForResult(intent, FILE_SELECT_CODE);

    }

    //this is coming from the network
    private fun saveAndInstallApk(inStream: InputStream, fileSize: Long) {
        //progress dialog
        runOnUiThread({
            _globalProgressDialog.setMessage("Downloading update ")
            _globalProgressDialog.isIndeterminate = true
            _globalProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            _globalProgressDialog.max = 100
            _globalProgressDialog.show()
        })

        var success = false
        val outFile = File(getExternalFilesDir(null), "ecoachbooks_release.apk")
        try {
            _logger.error("Writing new app version to disk")
            val os = FileOutputStream(outFile)
            _globalProgressDialog.isIndeterminate = false
            _globalProgressDialog.progress = 0
            val buffer = ByteArray(8192)
            var size: Int = inStream.read(buffer)
            var total = 0
            while (size != -1) {
                os.write(buffer, 0, size)
                total += size
                if (fileSize > 0) {
                    val finalTotal = total
                    this.runOnUiThread({ _globalProgressDialog.progress = (finalTotal * 100 / fileSize).toInt() })

                }
            }
            success = true
        } catch (ex: Exception) {
            _logger.error("Could not save apk to disk :(. " + ex.message)
            ex.printStackTrace()
        }

        _globalProgressDialog.dismiss()
        //start installer?
        if (success) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(Uri.fromFile(outFile), "application/vnd.android.package-archive")
            startActivity(intent)
        }
    }

    private fun startLoginActivity(context: Context) {
        //Called to start the LoginActivity for a user that hasn't logged in yet
        //This activity MUST update the shared preference on a successful login
        val intent = Intent(context, LoginActivity::class.java)
        startActivity(intent)
        finish();
    }

    private fun checkForUpdates() {
        //do things...
        if (!EcoachBooksUtils.isOnline(this)) {
            Toast.makeText(this, R.string.msg_not_online, Toast.LENGTH_LONG).show()
            return
        }
        //check the last successful download version

        var lastDlVersionString = _preferences.getLastAppDownloadVersion();
        var currentAppVersion = Integer.parseInt(packageManager.getPackageInfo(packageName, 0).versionName.replace(".", ""));

        if (TextUtils.isEmpty(lastDlVersionString)) {
            _preferences.setLastAppDownloadVersion(currentAppVersion.toString());
            Snackbar.make(_shelfRecyclerView!!, R.string.msg_has_latest_version, Snackbar.LENGTH_SHORT).show();
            return;
        }

        //we have some version there.
        var lastSuccessfulDownloadVersion = Integer.parseInt(lastDlVersionString);

        if (lastSuccessfulDownloadVersion > currentAppVersion) {
            //we downloaded a version of the app, trigger the installer!
            _logger.debug("Current app version is outdated. New version is: $lastSuccessfulDownloadVersion. Triggering install.");
            val intent = Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(File(applicationContext.getExternalFilesDir(null), "ecoachbooks_release.apk")), "application/vnd.android.package-archive")
            startActivity(intent);
        } else {
            _logger.debug("No new version to install. current version: $currentAppVersion");
            Snackbar.make(_shelfRecyclerView!!, R.string.msg_has_latest_version, Snackbar.LENGTH_SHORT).show();
        }

    }

    private fun onClickSignOut() {
        //Do Signout
        _logger.debug("Attempting to sign out...")

        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage(R.string.msg_sign_out_confirm)
        dialogBuilder.setPositiveButton(getText(R.string.yes_text), { dialog, which ->
            _globalProgressDialog.setMessage(getString(R.string.signout_progress_text))
            _globalProgressDialog.isIndeterminate = true
            _globalProgressDialog.setCancelable(false)
            _globalProgressDialog.show()

            _ecoachBooksApi.logOff()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .subscribe({ cleanUpAccountInfo() }, { cleanUpAccountInfo() })
            dialog.dismiss()
        })

        dialogBuilder.setNegativeButton(getText(R.string.no_text), { dialog, which -> dialog.dismiss(); })
        dialogBuilder.show()

    }

    private fun requestManualSync() {
        //Check for new books
        //Forcing connection state checking here
        _logger.debug("Requesting sync because this was not started from login ... ${_account.toString()}");
        // LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BroadcastMessages.BROADCAST_ADD_NEW_BOOK_TO_UI))
        var settingsBundle = Bundle();
      //  settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
      //  settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        settingsBundle.putBoolean(Constants.IS_MANUAL_SYNC, true);

        Snackbar.make(_shelfRecyclerView!!, R.string.msg_checking_for_new_books, Snackbar.LENGTH_SHORT).show();

        ContentResolver.requestSync(_account, getString(R.string.SyncAuthority), settingsBundle);
    }

    private fun showSettingsPopup() {
        initializeSettingsPage()
        settingsPageDialog!!.show()
    }

    private fun saveSettings() {
        //-1 means no automatic updates and downloads
        var downloadMediumPreference = 0;
        if (_wiFiOnlyDownloadButton!!.isSelected) {
            downloadMediumPreference = 0
        } else {
            downloadMediumPreference = 1
        }

        _preferences.putInt(Constants.AUTOMATIC_UPDATE_MODE, downloadMediumPreference);
    }

    private fun initializeSettingsPage() {
        popupView = layoutInflater.inflate(R.layout.main_page_settings, null, false)
        //Initialize spinner contents

        _downloadNetworkRadioGroup = popupView!!.findViewById(R.id.updateMethodRadioGroup) as RadioGroup
        _wiFiOnlyDownloadButton = popupView!!.findViewById(R.id.downloadOverWifi) as RadioButton
        _wifiAndDataDownloadButton = popupView!!.findViewById(R.id.downloadOverWifiAndData) as RadioButton

        //disable
        settingsPageDialog!!.setPositiveButton(android.R.string.ok, { dialog, which ->
            saveSettings();
        })

        settingsPageDialog!!.setNegativeButton(android.R.string.cancel, null)
        //update toggle state

        settingsPageDialog!!.setView(popupView)
    }

    private fun handleNavigationClick(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.homeNavItem -> {
            }
            R.id.shopNavItem -> onClickShop()
            R.id.aboutNavItem -> onClickAbout();
            R.id.settingsNavItem -> onClickSettings();
        }

        _drawerLayout!!.closeDrawers();
        return true;
    }

    private fun removeLoadingScreen() {
        _logger.debug("Removing books loading screen");

        if (_loadingScreenFragmentContainer != null && _loadingScreenFragmentContainer?.visibility == View.VISIBLE) {
            _logger.debug("Done checking for new books, hiding fragment");
            _loadingScreenFragmentContainer?.visibility = View.GONE;
        }

        _logger.debug("Loading screen fragment visibility is: ${_loadingScreenFragmentContainer?.visibility}")

    }

    private fun onClickAbout() {
        val intent = Intent(this, AboutAppActivity::class.java)
        startActivity(intent)
    }

    private fun onClickSettings() {
        showSettingsPopup()
    }

    private fun onClickShop() {
        if (EcoachBooksUtils.isOnline(this)) {
            startActivity(Intent(this, ShoppingActivity::class.java))
        } else {
            Toast.makeText(this, R.string.please_connect_to_internet, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleIntent(intent: Intent) {

        if (Intent.ACTION_SEARCH.equals(intent.action)) {
            var query = intent.getStringExtra(SearchManager.QUERY);
            //use the query to search your data somehow
            _logger.debug("Search query! $query");

            var filteredBooks = _booksManager.getFilteredBooks(query);
            _shelfAdapter?.replaceExistingData(filteredBooks);

        }
    }


    private fun cleanUpAccountInfo() {
        val acmfuture = _accountManager.getAuthToken(_account, Constants.EC_BOOKS_AUTH_TOKEN_TYPE,
                null, null, null, null)
        val authTokenBundle = acmfuture.result
        val authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN)

        _accountManager.invalidateAuthToken(Constants.EC_BOOKS_ACCNT_TYPE, authToken)
        _accountManager.removeAccount(_account, null, null)
        SessionManager.logUserOut(applicationContext)

        _logger.debug("Closing database....")
        EcoachBooksDatabase.getInstance().closeDatabase()
        //Delete books from disk
        _logger.debug("Deleting downloaded app data....")
        applicationContext.deleteDatabase(EcoachBooksDatabase.DatabaseName)
        EcoachBooksUtils.deleteStoredAppData(applicationContext)
        EcoachBooksPreferences(applicationContext).clear();

      //  _logger.debug("Restarting application... bookData: ${EcoachBooksDatabase.getInstance().currentBookIds}");
        //Take the user to login screen
        runOnUiThread {
            _globalProgressDialog.dismiss();
            var i = baseContext.packageManager
                    .getLaunchIntentForPackage( baseContext.packageName);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            finish();
            startActivity(i);
        }
    }

    class loadingScreenFragment : Fragment() {

        override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val rootView = inflater!!.inflate(R.layout.loading_screen,
                    container, true)

            return rootView
        }

    }


}

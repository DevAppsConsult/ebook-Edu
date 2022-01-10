package com.ecoachsolutions.ecoachbooks.Services

import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Binder
import android.os.IBinder
import com.ecoachsolutions.ecoachbooks.BuildConfig
import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Helpers.*
import retrofit.RetrofitError
import rx.Observable
import rx.schedulers.Schedulers
import java.io.IOException
import java.net.HttpURLConnection
import java.util.*
import java.util.concurrent.TimeUnit


class BookUpdaterService() : Service() {

    private val _binder = BookUpdaterBinder()
    private var _timerPeriod = 120000 //2 minutes default
    private val _accountManager: AccountManager by lazy { AccountManager.get(applicationContext) }
    private val _ecoachBooksApi by lazy { EcoachBooksApi.getInstance(applicationContext) }
    private val _logger = LogHelper(this);
    private val _preferences: EcoachBooksPreferences by lazy { EcoachBooksPreferences(applicationContext) };


    override fun onBind(intent: Intent): IBinder? {
        return _binder
    }

    //cleanup
    override fun onDestroy() {
        _logger.debug("Service is being destroyed. Bye bye.")
        timer.cancel()
    }


    //Binder class to allow interfacing with the service
    inner class BookUpdaterBinder : Binder() {
        val service: BookUpdaterService
            get() = this@BookUpdaterService
    }


    fun watchAuthToken() {
        Observable.interval(if (BuildConfig.DEBUG) 1 else 5, TimeUnit.MINUTES)
                .flatMap { iteration ->
                    _logger.debug("Ping number $iteration ");
                    _ecoachBooksApi.pingObservable()
                }.retry(1)
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .subscribe({ pingResponse ->
                    _logger.debug("Ping response message: " + pingResponse.message);

                }, { throwable ->
                    //catch a 401 and force a re-auth with existing credentials
                    _logger.error("Ping failed in outer observer, this will stop the ping cycle.", throwable);
                    if (throwable is RetrofitError && throwable.response?.status == HttpURLConnection.HTTP_UNAUTHORIZED ) {
                        handlePingError();
                    }
                });
    }


    private fun handlePingError() {
        //catch a 401 and force a re-auth with existing credentials
        _logger.error("Got a 401 from Ping response. Attempting to re-authenticate with credentials")

        _logger.debug("User account is no longer valid. Invalidating...")
        _logger.debug("Old auth token from api: " + _ecoachBooksApi._authToken)
        var authToken = getAuthToken()
        _logger.debug("Old auth token from GetAuthToken call: " + authToken)

        _accountManager.invalidateAuthToken(Constants.EC_BOOKS_ACCNT_TYPE, authToken)
        authToken = getAuthToken()
        _logger.debug("New auth token from GetAuthToken call: " + _ecoachBooksApi._authToken)
        _preferences.putString(Constants.EC_BOOKS_AUTH_TOKEN_TYPE, authToken);
        //Calling getauthtoken after this will attempt to use the stored credentials to get a new token

    }

    fun checkForNewBooks(): Observable<BooksManager.GetMyBooksResponse> {
        return _ecoachBooksApi.getBooksDistinctObservable(BooksManager.getCurrentBookIds(applicationContext))
    }

    fun checkForNewBooks(period: Int): Observable<BooksManager.GetMyBooksResponse> {
        if (period < 0) {
            //negative period means no updating
            return Observable.empty<BooksManager.GetMyBooksResponse>()
        }
        _logger.debug("Scheduling updater with new api for $period minutes")

        return Observable.merge(_ecoachBooksApi.getBooksDistinctObservable(BooksManager.getCurrentBookIds(applicationContext)),
                Observable.interval(period.toLong(), TimeUnit.MINUTES).map({ _ecoachBooksApi.getBooksDistinct(BooksManager.getCurrentBookIds(applicationContext)) }))

    }

    fun updateFrequency(newFrequencyInMinutes: Int) {
        _logger.debug("Updating frequency to " + newFrequencyInMinutes)
        _timerPeriod = if (newFrequencyInMinutes == 0) _timerPeriod else (newFrequencyInMinutes * 60000)
        timer.cancel()

        if (newFrequencyInMinutes < 0) {
            //negative period means no updating
            return
        }

        timer = Timer()
        //  timer.schedule(new UpdateCheckerTimerTask(),0, timerPeriod);

    }

    private fun getAuthToken(): String {
        _logger.debug("User account is no longer valid. Invalidating...")
        try {
            val acmfuture = _accountManager.getAuthToken(_accountManager.getEcoachBooksAccount(), Constants.EC_BOOKS_AUTH_TOKEN_TYPE,
                    null, null, null, null)

            val authTokenBundle = acmfuture.result
            val authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN)

            //Calling getauthtoken after this will attempt to use the stored credentials to get a new token
            return authToken
        } catch (e: OperationCanceledException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: AuthenticatorException) {
            e.printStackTrace()
        }

        return ""
    }

    private val isConnected: Boolean
        get() {
            val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = cm.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting

        }

    companion object {
        private var timer = Timer()
    }

}

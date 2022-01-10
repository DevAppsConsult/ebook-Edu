package com.ecoachsolutions.ecoachbooks.Helpers

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Core.DownloadBookData
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils
import com.ecoachsolutions.ecoachbooks.Models.CommentDto
import com.ecoachsolutions.ecoachbooks.Models.CommentRequest
import com.ecoachsolutions.ecoachbooks.Models.CommentResponse
import com.ecoachsolutions.ecoachbooks.Models.GenericResponse
import com.ecoachsolutions.ecoachbooks.Services.IEcoachBooksService
import com.squareup.okhttp.OkHttpClient
import org.apache.commons.lang3.StringUtils
import retrofit.Callback
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.client.Request
import retrofit.client.Response
import rx.Observable
import rx.schedulers.Schedulers
import java.io.IOException
import java.net.CookieHandler
import java.net.HttpURLConnection
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/**
 * Created by Daniel on 2/21/2015.
 * Nasty singleton to handle api wrapper stuff
 */
class EcoachBooksApi(val _context: Context) {

    internal var restAdapter: RestAdapter
    internal var _ecoachBooksApi: IEcoachBooksService
    private val _logger = LogHelper(this);
    private val _preferences by lazy { EcoachBooksPreferences(_context); }

    init {

        var newOkClient = OkHttpClient();

        acceptExpiredCert(newOkClient);

        newOkClient.setReadTimeout(1, TimeUnit.MINUTES);
        newOkClient.setConnectTimeout(1, TimeUnit.MINUTES);

        var baseUrl = EcoachBooksUrls.BaseUrl;
        if (_preferences.shouldUseHttp()) {
            baseUrl = baseUrl.replace("https://", "http://");
            _logger.debug("Force http enabled. This is very unsecure and should only be used in extreme scenarios! Base Url is now: $baseUrl");
        }

        restAdapter = RestAdapter.Builder()
                .setEndpoint(baseUrl)
                .setRequestInterceptor(EcRequestInterceptor())
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setClient(InterceptingOkClient(_context, newOkClient))
                .build()
        _ecoachBooksApi = restAdapter.create(IEcoachBooksService::class.java)
        CookieHandler.setDefault(EcoachCookieManager(_context))
    }

    companion object {

        private var _instance: EcoachBooksApi? = null

        @Synchronized fun getInstance(context: Context): EcoachBooksApi {
            if (_instance == null) {
                _instance = EcoachBooksApi(context)
            }
            return _instance!!
        }
    }


    val _authToken: String get() {
        return _preferences.getString(Constants.EC_BOOKS_AUTH_TOKEN_TYPE);
    }

    fun assertValidConnection(accountManager: AccountManager, onErrorAction: (error: Throwable) -> Unit, onSuccessAction: () -> Unit = {}) {
        var account = accountManager.getEcoachBooksAccount();
        if (account == null) {
            _logger.error("Expected account to authenticate. Cannot asset credentials validity");
            return;
        }

        pingObservable().subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe({ pingResponse ->
                    _logger.debug("User's account is valid. Calling success handler.");
                    onSuccessAction();
                }, {
                    error ->
                    //if we have a 401, raise hell.
                    try {
                        onErrorAction(error);
                    } catch (ex: Exception) {
                        _logger.error("Failed to throw error for assert valid connection failure!!", ex);
                    }
                })
    }

    fun login(email: String, password: String): Observable<UserValidator.UserValidationResult> {
        var signinReq = SignInRequest(email, password);
        return _ecoachBooksApi.login(signinReq)
    }

    fun logOff() : Observable<GenericResponse>  {
        _logger.debug("Logging out");
        return _ecoachBooksApi.logOff();
    }

    fun loginSynchronous(email: String, password: String): UserValidator.UserValidationResult {
        return _ecoachBooksApi.loginSynchronous(email, password);
    }

    fun register(registrationModel: SignUpRequest): Observable<UserValidator.UserValidationResult> {
        return _ecoachBooksApi.register(registrationModel)
    }

    fun getBooksDistinctObservable(excludeList: List<String>): Observable<BooksManager.GetMyBooksResponse> {
        return _ecoachBooksApi.getBooksDistinctObservable(StringUtils.join(excludeList, ','))
    }

    fun getBooksDistinct(excludeList: List<String>): BooksManager.GetMyBooksResponse {
        return _ecoachBooksApi.getBooksDistinct(StringUtils.join(excludeList, ','))
    }

    fun downloadBook(bookId: String): Observable<DownloadBookData> {
        _logger.debug("Attempting to download id: $bookId");

        return _ecoachBooksApi.downloadBook(bookId).map({ response ->
            val bookData = DownloadBookData()
            try {
                bookData.inputStream = response.body.`in`()
                bookData.size = response.body.length()
            } catch (e: IOException) {
                _logger.error("Could not download the book :(", e);

            }

            bookData
        })

    }

    fun ping(callback: Callback<GenericResponse>) {
        _ecoachBooksApi.ping(callback)
    }

    fun pingObservable(): Observable<GenericResponse> {
        return _ecoachBooksApi.pingObservable()
    }

    fun ping(): GenericResponse {
        return _ecoachBooksApi.ping()
    }

    fun getLatestAppVersion(): Observable<String> {
        return _ecoachBooksApi.getLatestAppVersion()
    }

    fun getAccountBalance(): Observable<Double> {
        return _ecoachBooksApi.getAccountBalance()
    }

    fun downloadApk(): Observable<Response> {
        return _ecoachBooksApi.downloadApk()
    }

    fun getAuthToken(): String {
        return _authToken
    }

    @Synchronized fun addComment(commentDto: CommentDto) : Observable<GenericResponse>{
        return _ecoachBooksApi.addComment(CommentRequest(commentDto));
    }

    fun getComments(bookId: String): Observable<ArrayList<CommentResponse>>{
        return _ecoachBooksApi.getComments(bookId)
    }

    private fun acceptExpiredCert(client: OkHttpClient) {
        var sslContext = SSLContext.getInstance("TLS");


        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        // Initialise the TMF as you normally would, for example:
        tmf.init(null as KeyStore?)

        var originalTrustManager = tmf.trustManagers[0] as X509TrustManager;

        sslContext.init(null, arrayOf(object : javax.net.ssl.X509TrustManager {


            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                _logger.debug("Checking client trusted..");
                originalTrustManager.checkClientTrusted(chain, authType);
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                //we only want to catch the validation exception
                try {
                    _logger.debug("Checking server trusted..");
                    originalTrustManager.checkServerTrusted(chain, authType);
                } catch (ex: CertificateExpiredException) {
                    _logger.warn("Ignoring certificate expired exception!1")
                } catch (ex: SSLHandshakeException) {
                    _logger.warn("Ignoring certificate expired exception!2")
                } catch (ex: CertificateException) {
                    _logger.warn("Ignoring certificate expired exception!3")
                }
            }

            override fun getAcceptedIssuers(): Array<out X509Certificate>? {
                _logger.debug("getting accepted issuers list.");
                return originalTrustManager.acceptedIssuers;
            }

        }), null);

        client.sslSocketFactory = sslContext.socketFactory;
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory);
    }

    private inner class EcRequestInterceptor : RequestInterceptor {

        override fun intercept(request: RequestInterceptor.RequestFacade) {
            request.addHeader("Cookie", "%s=%s".format(Constants.EC_BOOKS_COOKIE_AUTH_NAME, getAuthToken()))
            //We need this header so unauthenticated requests ret
            request.addHeader(Constants.EC_HEADER_APP_TYPE, Constants.EC_MOBILE_APP_TYPE)
            request.addHeader(Constants.EC_HEADER_API_KEY, Constants.EC_API_KEY)
        }
    }

    //This class intercepts the response and automatically updates the stored auth token in the
    //shared preferences file
    private inner class InterceptingOkClient(val context: Context, okHttpClient: OkHttpClient) : OkClient(okHttpClient) {
        private val _logger = LogHelper(this);
        private val _preferences by lazy { EcoachBooksPreferences(context); }

        @Throws(IOException::class)
        override fun execute(request: Request): Response {
            val resp = super.execute(request);

            if (resp.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                _logger.error("Unauthorized");
                context.sendBroadcast(Intent(BroadcastMessages.BROADCAST_DO_LOGIN));
                return resp;
            }

            // Log.d("Interceptor!", "Response code: " + resp.getStatus());
            val authCookie = EcoachBooksUtils.getAuthTokenFromRetrofitResponse(resp)
            if (authCookie != null && EcoachBooksApi.getInstance(context)._authToken !== authCookie) {
                _logger.debug("Saving new auth cookie: $authCookie");
                _preferences.putString(Constants.EC_BOOKS_AUTH_TOKEN_TYPE, authCookie);

                //If the cookie is different from the one in the shared preference, update it and invalidate
                //the existing token in account manager?
            }
            return resp
        }
    }

}

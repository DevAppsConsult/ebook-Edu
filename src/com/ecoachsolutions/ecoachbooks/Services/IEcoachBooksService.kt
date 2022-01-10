package com.ecoachsolutions.ecoachbooks.Services

import com.ecoachsolutions.ecoachbooks.Core.BooksManager
import com.ecoachsolutions.ecoachbooks.Helpers.SignInRequest
import com.ecoachsolutions.ecoachbooks.Helpers.SignUpRequest
import com.ecoachsolutions.ecoachbooks.Models.GenericResponse

import retrofit.Callback
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.Headers
import retrofit.http.POST
import retrofit.http.Query
import retrofit.http.Streaming
import rx.Observable

import com.ecoachsolutions.ecoachbooks.Helpers.UserValidator.UserValidationResult
import com.ecoachsolutions.ecoachbooks.Models.CommentDto
import com.ecoachsolutions.ecoachbooks.Models.CommentRequest
import com.ecoachsolutions.ecoachbooks.Models.CommentResponse
import java.util.*

/**
 * Created by Daniel on 2/21/2015.
 * This service is a wrapper for the various ecoachbooks endpoints
 */
interface IEcoachBooksService {
    @Headers("Accept: application/json", "Content-type: application/json")
    @POST("/authenticate")
    fun loginSynchronous(@Query("email") email: String, @Query("password") password: String): UserValidationResult

    @POST("/authenticate")
    @Headers("Accept: application/json", "Content-type: application/json")
    fun login(@Body body: SignInRequest): Observable<UserValidationResult>

    @POST("/logoff")
    fun logOff(@Body body:String = ""): Observable<GenericResponse>

    @Headers("Accept: application/json", "Content-type: application/json")
    @POST("/register")
    fun register(@Body registrationModel: SignUpRequest): Observable<UserValidationResult>

    @Headers("Accept: application/json", "Content-type: application/json")
    @POST("/GetMyBooksDistinct")
    fun getBooksDistinctObservable(@Query("excludelist") excludeList: String, @Body body:String = ""): Observable<BooksManager.GetMyBooksResponse>

    @Headers("Accept: application/json", "Content-type: application/json")
    @POST("/GetMyBooksDistinct")
    fun getBooksDistinct(@Query("excludelist") excludeList: String, @Body body:String = ""): BooksManager.GetMyBooksResponse

    @POST("/ping")
    fun ping(callback: Callback<GenericResponse>)

    @POST("/ping")
    fun ping(@Body body:String = ""): GenericResponse

    @POST("/ping")
    fun pingObservable(@Body body:String = ""): Observable<GenericResponse>

    @GET("/DownloadBook")
    @Streaming
    @Headers("Accept: application/download", "Content-type: application/json")
    fun downloadBook(@Query("bookId") bookId: String): Observable<Response>

    @POST("/GetLatestAppVersion")
    @Headers("Accept: application/json", "Content-type: application/json")
    fun getLatestAppVersion(@Body body:String = ""): Observable<String>

    @POST("/GetAccountBalance")
    fun getAccountBalance(@Body body : String = ""): Observable<Double>


    @GET("/DownloadApk")
    @Streaming
    @Headers("Accept: application/download", "Content-type: application/json")
    fun downloadApk(): Observable<Response>

    @POST("/addcomment")
    fun addComment(@Body comment: CommentRequest) : Observable<GenericResponse>

    @GET("/GetCommentsForBook")
    fun getComments(@Query("bookId") bookId: String) : Observable<ArrayList<CommentResponse>>

}

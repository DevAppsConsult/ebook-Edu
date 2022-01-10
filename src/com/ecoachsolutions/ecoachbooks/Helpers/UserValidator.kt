package com.ecoachsolutions.ecoachbooks.Helpers

import android.content.Context
import com.google.gson.Gson
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import org.json.JSONObject
import java.net.HttpURLConnection

object UserValidator {
    // This class is responsible for validating a user using static methods

    private val _logger = LogHelper(this);


    fun authenticateUser(context: Context, email: String, pwd: String): UserValidationResult {
        val creds = JSONObject()
        creds.put("email", email)
        creds.put("password", pwd)
        //get auth content for the user
        var result = UserValidationResult()

        try {

            var okClient = OkHttpClient();
            okClient.cookieHandler = EcoachCookieManager(context);
            var requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), creds.toString());

            var request = Request.Builder()
                    .url(EcoachBooksUrls.AUTH_URL)
                    .post(requestBody)
                    .build();

            var response = okClient.newCall(request).execute();

            if (response.code() != HttpURLConnection.HTTP_OK) {
                _logger.debug("Failed to authenticate user with response code: ${response.code()}");
                return result;
            }

            var responseString = response.body().string();

            _logger.debug("Server response to auth was: '$responseString'");

            result = Gson().fromJson(responseString, UserValidationResult::class.java)

            var headers = response.headers("Set-Cookie").filter {
                it ->
                it.startsWith(Constants.EC_BOOKS_COOKIE_AUTH_NAME)
            };

            for (header in headers) {
                result.ecBooksAuth = header.removePrefix("${Constants.EC_BOOKS_COOKIE_AUTH_NAME}=");
            }

            _logger.debug("Authenticate result: $result");
            return result;

        } catch (e: Exception) {
            _logger.error("Failed to authenticate ", e);
            return result
        }
    }

    class UserValidationResult {
        var success: Boolean = false
        var message: String = ""
        var userId: String = ""
        var result: String = ""
        var ecBooksAuth: String = ""
    }
}

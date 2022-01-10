package com.ecoachsolutions.ecoachbooks.Helpers

import android.util.Log
import com.crashlytics.android.Crashlytics

class LogHelper<T : Any>(t : T){
    var loggingClassName : String = "";
    var logTag = "EcoachBooks"
    init {
        loggingClassName = t.javaClass.name;
    }

    fun warn(message: String){
        Log.w(loggingClassName, "$logTag: $message");
    }

    fun error(message: String, exception: Exception){
        Log.e(loggingClassName, "$logTag: $message",exception);
        Crashlytics.logException(exception);

    }

    fun error(message: String){
        Log.e(loggingClassName, "$logTag: $message")
    }

    fun error(message: String, exception: Throwable){
        Log.e(loggingClassName, "$logTag: $message",exception)
    }
    fun debug(message: String){
        Log.d(loggingClassName, "$logTag: $message")
    }


}
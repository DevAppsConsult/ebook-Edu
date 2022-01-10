package com.ecoachsolutions.ecoachbooks.Helpers

/**
 * Created by Daniel on 9/15/2014.
 */
object EcoachBooksUrls {
    //val DEV_URL = "http://192.168.0.107:4160/api"
    val DEV_URL = "http://10.0.2.2:4160/api"
    val STAGING_URL = "https://ecoachtools.net/api"
    val PROD_URL = "https://ecoachbooks.com/api"
    private val baseurl = BaseUrl
    val AUTH_URL = baseurl + "/authenticate"
    val BookDownloadUrl = baseurl + "/DownloadBook"
    val GetBooksUrl = baseurl + "/GetMyBooksDistinct"
    val PING_URL = baseurl + "/ping"
    val SITE_URL = "https://ecoachbooks.com/"
    //default to dev
    val BaseUrl: String
        get() {
            //return PROD_URL;
            if (MasterSettings.isProductionMode()) {
                return PROD_URL
            } else if (MasterSettings.isStagingMode()) {
                return STAGING_URL
            } else {
                return DEV_URL
            }
        }
}

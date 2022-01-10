package com.ecoachsolutions.ecoachbooks.Helpers

object Constants {
    const val BOOKSHELF_PATH = "bookshelf"
    const val LIBRARYHELF_PATH = "library"
    const val FIRST_USE = "FirstUse"
    const val BOOKS_DOWNLOADED = "downloadedBooks"
    const val SHELFPATHS = "ShelfPath"
    const val LIBRARYPATHS = "LibraryPath"
    const val IS_USER_LOGGED_IN = "IsLoggedIn"
    const val TEXT_SIZE = "TextSize"
    const val SHARED_PREFERENCES_FILE_NAME = "ecoachBooksPreferences"
    const val CREDENTIALS_VALID = 0
    const val CREDENTIALS_INVALID = -1
    const val EC_BOOKS_COOKIE_AUTH_NAME = "EcBooksAuth"
    const val EC_BOOKS_ACCNT_TYPE = "eCoachBooks"
    const val EC_BOOKS_AUTH_TOKEN_TYPE = "EcBooksAuth"
    const val EC_HEADER_APP_TYPE = "X-EcApp-Type"
    const val EC_HEADER_API_KEY = "X-Api-Key"
    const val EC_API_KEY = "UURG4YDWVN16185RIVWBYSYZ5"
    const val EC_MOBILE_APP_TYPE = "Ec-Books-Mobile-App"
    const val CLEANUP_SERVICE_EXCLUDE = "Exclude"
    const val UPDATE_FREQUENCY_KEY = "UpdateFrequency"
    const val AUTOMATIC_UPDATE_MODE = "AutomaticUpdateMode"
    const val ACCOUNT_BALANCE_KEY = "AccountBalance"
    const val LAST_SUCCESSFUL_APP_DOWNLOAD_VERSION_KEY = "LastSuccessfulAppDownloadVersion"
    const val IS_PERIODIC_SYNC_SET = "LastSuccessfulAppDownloadVersion"
    const val DEFAULT_TEXT_ZOOM = 120
    const val NEW_BOOK_IDS = "NewBookIds"

    const val SHOULD_USE_HTTP = "ShouldUseHttp"

    const val COMMENT_ID = "CommentId"
    const val BOOK_ID = "BookId"
    const val BOOK_TITLE = "Title"
    const val IS_LOGIN_INTENT = "IsLoginIntent"

    const val IS_MANUAL_SYNC = "IsManuelSync"

}

object PreferenceKeys{
    const val LAST_SYNC_ATTEMPT = "LastSyncAttempt"
}


object LocalyticsTags{
    const val DISABLED_HTTPS = "DisabledHttps"
    const val ENABLED_HTTPS = "EnabledHttps"
    const val EVENT_BOOK_OPENED = "Book Opened"
    const val ATTRIBUTE_TITLE = "Title"
    const val EVENT_BOOK_EXIT = "Book exited"
    const val EVENT_BOOK_DOWNLOADED = "Book downloaded"
    const val EVENT_BOOK_UNDOWNLOADED = "Book undownloaded"
}

object Notification_Keys{
    const val INSTALL_APP_UPDATE_NOTIFICATION = 0
}

object BroadcastMessages {
    const val BROADCAST_ADD_NEW_BOOK_TO_UI = "com.ecoach.ecoachbooks.new-book-downloaded"
    const val BROADCAST_DO_LOGIN = "com.ecoach.ecoachbooks.do-login"
}

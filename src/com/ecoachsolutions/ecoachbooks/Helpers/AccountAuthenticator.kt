package com.ecoachsolutions.ecoachbooks.Helpers

import android.accounts.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import com.ecoachsolutions.ecoachbooks.LoginActivity


class AccountAuthenticator : AbstractAccountAuthenticator {

    private var _context: Context? = null
    private val _accountManager: AccountManager by lazy { AccountManager.get(_context) }

    constructor(context: Context) : super(context) {
        _context = context
    }

    constructor() : super(null) {
        /**do nothing */
    }

    override fun editProperties(accountAuthenticatorResponse: AccountAuthenticatorResponse, s: String): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun addAccount(response: AccountAuthenticatorResponse, accountType: String, authTokenType: String, requiredFeatures: Array<String>, options: Bundle): Bundle {
        val intent = Intent(_context, LoginActivity::class.java)
        intent.putExtra(Constants.EC_BOOKS_ACCNT_TYPE, accountType)
        intent.putExtra(Constants.EC_BOOKS_AUTH_TOKEN_TYPE, authTokenType)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        val bundle = Bundle()
        bundle.putParcelable(AccountManager.KEY_INTENT, intent)
        return bundle
    }

    @Throws(NetworkErrorException::class)
    override fun confirmCredentials(accountAuthenticatorResponse: AccountAuthenticatorResponse, account: Account, bundle: Bundle): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun getAuthToken(accountAuthenticatorResponse: AccountAuthenticatorResponse, account: Account, authTokenType: String, options: Bundle): Bundle {
        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.


        var authToken = _accountManager.peekAuthToken(account, Constants.EC_BOOKS_AUTH_TOKEN_TYPE)

        // No token here. Ask the server to revalidate with the credentials we have stored
        if (TextUtils.isEmpty(authToken)) {
            val password = _accountManager.getPassword(account)
            if (password != null) {
                val vr = UserValidator.authenticateUser(_context!!, account.name, password)
                authToken = vr.ecBooksAuth //we will get an empty authToken if the provided credentials are invalid
            }
        }

        // If we get an authToken - we return it
        if (!TextUtils.isEmpty(authToken)) {
            val result = Bundle()
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name)
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type)
            result.putString(AccountManager.KEY_AUTHTOKEN, authToken)
            return result
        }

        //If token was empty, we need to fire our activity to get new credentials
        val intent = Intent(_context, LoginActivity::class.java)
        intent.putExtra(Constants.EC_BOOKS_ACCNT_TYPE, account.name)
        intent.putExtra(Constants.EC_BOOKS_AUTH_TOKEN_TYPE, account.type)
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, accountAuthenticatorResponse)
        var _options = Bundle()
        _options.putParcelable(AccountManager.KEY_INTENT, intent)
        return _options
    }

    override fun getAuthTokenLabel(s: String): String? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun updateCredentials(accountAuthenticatorResponse: AccountAuthenticatorResponse, account: Account, s: String, bundle: Bundle): Bundle? {
        return null
    }

    @Throws(NetworkErrorException::class)
    override fun hasFeatures(accountAuthenticatorResponse: AccountAuthenticatorResponse, account: Account, strings: Array<String>): Bundle? {
        return null
    }

    /*Hack because there's no member of the parent class which allows you to remove accounts..*/

}

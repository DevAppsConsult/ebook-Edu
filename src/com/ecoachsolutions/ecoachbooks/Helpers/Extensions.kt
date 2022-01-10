package com.ecoachsolutions.ecoachbooks.Helpers

import android.accounts.Account
import android.accounts.AccountManager

fun AccountManager.getEcoachBooksAccount() : Account? {
    return this.getAccountsByType(Constants.EC_BOOKS_ACCNT_TYPE).firstOrNull();
}
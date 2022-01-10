package com.ecoachsolutions.ecoachbooks.Core

interface OnFetchNewBooksCompleted {
    fun FetchNewBooksCallback(response: BooksManager.GetMyBooksResponse)
}

package com.ecoachsolutions.ecoachbooks.Helpers

import com.ecoachsolutions.ecoachbooks.Models.BookDto

/**
 * Created by Daniel on 2/28/2015.
 */
interface IBookDownloadedListener {
    fun downloadCompletedCallBack(dto: BookDto)
}

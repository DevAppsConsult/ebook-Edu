package com.ecoachsolutions.ecoachbooks.Core;

import android.view.View;

import com.ecoachsolutions.ecoachbooks.Models.BookDto;

/**
 * Created by Daniel on 9/23/2014.
 */
public interface OnBookDownloadCompleted {
    void DownloadCompletedCallBack(BookDto dto, View parentView);
}

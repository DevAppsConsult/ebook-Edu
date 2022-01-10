package com.ecoachsolutions.ecoachbooks.Core;

import android.view.View;

import com.ecoachsolutions.ecoachbooks.Models.BookDto;

/**
 * Created by Daniel on 9/24/2014.
 */
public interface OnDecryptDecompressBookCompleted {
    void BookReadyToReadCallback(BookDto path, View callerView);
}

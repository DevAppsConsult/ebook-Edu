package com.ecoachsolutions.ecoachbooks.Core.Sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Dummy content-provider
 */

class EcoachBooksContentProvider : ContentProvider() {
    override fun insert(p0: Uri?, p1: ContentValues?): Uri? {
        return null;
    }

    override fun query(p0: Uri?, p1: Array<out String>?, p2: String?, p3: Array<out String>?, p4: String?): Cursor? {
        return null;
    }

    override fun onCreate(): Boolean {
        return true;
    }

    override fun update(p0: Uri?, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        return 0;    }

    override fun delete(p0: Uri?, p1: String?, p2: Array<out String>?): Int {
        return 0;    }

    override fun getType(p0: Uri?): String? {
        return null;    }

}

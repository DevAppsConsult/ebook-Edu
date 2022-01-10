package com.ecoachsolutions.ecoachbooks.Core.Database

import android.database.sqlite.SQLiteDatabase
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper

object UpgradeModules {
    var _logger = LogHelper(this);

    fun upgradeBooksDatabase(db: SQLiteDatabase, version: Int){
        _logger.debug("Running upgrades for version $version");
        if (version == 3){
            //add a "comments" table.
        }

    }

}
package com.ecoachsolutions.ecoachbooks.Services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.ecoachsolutions.ecoachbooks.Core.Sync.EcoachBooksSyncAdapter
import com.ecoachsolutions.ecoachbooks.Helpers.LogHelper

/**
 * Responsible for syncing:
 * 1. New books
 * 2. Pending uploads
 * 3. Updates to books
 * 4. etc.
 */

class BookSyncService : Service() {

    private var _syncAdapter: EcoachBooksSyncAdapter? = null;
    private val _syncAdapterLock = Object();
    private val _logger = LogHelper(this);

    override fun onCreate() {
        super.onCreate();
        _logger.debug("Creating BookSyncService");

        synchronized(_syncAdapterLock, {
            if (_syncAdapter == null) {
                _syncAdapter = EcoachBooksSyncAdapter(applicationContext, true);
            }
        })

    }

    override fun onBind(intent: Intent?): IBinder? {
        _logger.debug("Bound to Sync Service from intent: $intent");

        return _syncAdapter?.syncAdapterBinder;
    }

}

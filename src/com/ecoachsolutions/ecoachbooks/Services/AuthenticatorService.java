package com.ecoachsolutions.ecoachbooks.Services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.ecoachsolutions.ecoachbooks.Helpers.AccountAuthenticator;

/**
 * Created by Daniel on 9/14/2014.
 */
public class AuthenticatorService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        AccountAuthenticator authenticator = new AccountAuthenticator(this);
        return authenticator.getIBinder();
    }
}

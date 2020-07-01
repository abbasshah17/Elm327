package com.obdelm327pro.service;

import android.os.Handler;

public class SafeHandler extends Handler {

    private Elm327ConnectionService mService;

    public SafeHandler(Elm327ConnectionService service) {
        mService = service;
    }
}

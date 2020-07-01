package com.obdelm327pro.service;

import android.support.annotation.NonNull;

public interface WifiODBCallback {

    void onStateChanged(@NonNull WifiState state);
}

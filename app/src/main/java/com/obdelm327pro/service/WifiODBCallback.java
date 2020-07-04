package com.obdelm327pro.service;

import androidx.annotation.NonNull;

public interface WifiODBCallback {

    void onStateChanged(@NonNull WifiState state);
}

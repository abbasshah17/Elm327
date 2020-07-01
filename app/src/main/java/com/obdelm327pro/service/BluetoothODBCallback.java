package com.obdelm327pro.service;

import android.support.annotation.NonNull;

public interface BluetoothODBCallback {
    void onStateChanged(@NonNull BluetoothState state);
}

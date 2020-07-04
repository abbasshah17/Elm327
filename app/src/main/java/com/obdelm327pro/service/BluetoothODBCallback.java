package com.obdelm327pro.service;

import androidx.annotation.NonNull;

public interface BluetoothODBCallback {
    void onStateChanged(@NonNull BluetoothState state);
}

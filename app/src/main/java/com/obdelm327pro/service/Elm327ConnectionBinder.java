package com.obdelm327pro.service;

import android.bluetooth.BluetoothDevice;
import android.os.Binder;

import androidx.annotation.NonNull;

public abstract class Elm327ConnectionBinder extends Binder {


    public abstract boolean isWifiConnected();

    public abstract void connectWifi();

    public abstract void registerWifiCallbacks(WifiODBCallback wifiCallbacks);

    public abstract void unregisterWifiCallbacks(WifiODBCallback wifiODBCallback);

    public abstract void disconnectWifi();

    public abstract boolean isBlueToothConnected();

    public abstract void listenForBluetooth();

    public abstract void prepareBluetooth();

    public abstract void connectBlueTooth(BluetoothDevice device);

    public abstract void registerBluetoothCallbacks(BluetoothODBCallback callback);

    public abstract void unregisterBluetoothCallbacks(BluetoothODBCallback callback);

    public abstract void disconnectBluetooth();

    public abstract void disconnectAllConnectionServices();

    public abstract void sendEcuMessage(String message) throws BluetoothConnectionNotEstablished;

    public abstract void registerElm327Updates(@NonNull Elm327Callback callback);

    public abstract void unregisterElm327Updates(@NonNull Elm327Callback callback);
}

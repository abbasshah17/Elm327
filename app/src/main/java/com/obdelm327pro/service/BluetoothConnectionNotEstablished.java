package com.obdelm327pro.service;

public class BluetoothConnectionNotEstablished extends ConnectionException {

    public BluetoothConnectionNotEstablished() {
        super("Bluetooth device not connected");
    }

    public BluetoothConnectionNotEstablished(String msg) {
        super(msg);
    }
}

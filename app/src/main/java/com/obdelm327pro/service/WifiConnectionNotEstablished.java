package com.obdelm327pro.service;

public class WifiConnectionNotEstablished extends ConnectionException {

    public WifiConnectionNotEstablished() {
        super("Bluetooth device not connected");
    }

    public WifiConnectionNotEstablished(String msg) {
        super(msg);
    }
}

package com.obdelm327pro.service;

import android.support.annotation.NonNull;

public class DeviceInfo {

    private String mDeviceName;
    private String mDeviceProtocol;

    DeviceInfo(@NonNull String deviceName, @NonNull String deviceProtocol) {
        mDeviceName = deviceName;
        mDeviceProtocol = deviceProtocol;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public String getDeviceProtocol() {
        return mDeviceProtocol;
    }
}

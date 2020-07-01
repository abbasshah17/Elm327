package com.obdelm327pro.service;

import android.support.annotation.NonNull;

public interface Elm327Callback {

    void onDeviceInfo(@NonNull String deviceInfo);

    void onInfo(@NonNull String info);

    void onNewConversation(@NonNull String conversation);

    void toastMessage(@NonNull String message);

    void onVoltageUpdate(@NonNull String voltage);

    void onEngineLoadUpdate(@NonNull String engineLoad);

    void onFuelConsumptionUpdate(@NonNull String fuelConsumption);

    void onCoolantTemperatureUpdate(@NonNull String coolantTemperature);

    void onRpmUpdate(int rpm);

    void onSpeedUpdate(int speed);

    void onIntakeTemperatureUpdate(@NonNull String intakeTemperature);

    void onMAF_AirFlowUpdate(@NonNull String mafAirflow);

    void onThrottlePositionUpdate(@NonNull String throttle);
}

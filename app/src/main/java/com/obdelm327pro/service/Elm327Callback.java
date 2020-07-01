package com.obdelm327pro.service;

import android.support.annotation.NonNull;

public interface Elm327Callback {

    void onDeviceInfo(@NonNull DeviceInfo deviceInfo);

    void onInfo(@NonNull String info);

    void onNewConversation(@NonNull String conversation);

    void toastMessage(@NonNull String message);

    void onVoltageUpdate(@NonNull Voltage voltage);

    void onEngineLoadUpdate(@NonNull EngineLoad engineLoad);

    void onFuelConsumptionUpdate(@NonNull FuelConsumption fuelConsumption);

    void onCoolantTemperatureUpdate(@NonNull Temperature coolantTemperature);

    void onRpmUpdate(@NonNull RPM rpm);

    void onSpeedUpdate(@NonNull Speed speed);

    void onIntakeTemperatureUpdate(@NonNull Temperature intakeTemperature);

    void onMAF_AirFlowUpdate(@NonNull AirflowRate maf);

    void onThrottlePositionUpdate(@NonNull ThrottlePosition throttle);
}

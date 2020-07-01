package com.obdelm327pro.service;

public class Voltage {
    private static final String VOLTAGE_UNIT = "V";
    private double voltage;


    public Voltage(double voltage) {
        this.voltage = voltage;
    }

    public double getVoltage() {
        return voltage;
    }

    public String getUnit() {
        return VOLTAGE_UNIT;
    }
}

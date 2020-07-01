package com.obdelm327pro.service;

public class FuelConsumption {

    private double mFuelConsumption;


    FuelConsumption(double fuelConsumption) {
        mFuelConsumption = fuelConsumption;
    }

    public double getFuelConsumption() {
        return mFuelConsumption;
    }

    public String getUnit() {
        return "l/h";
    }
}

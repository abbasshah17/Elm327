package com.obdelm327pro.service;

public class Pressure {

    private int pressure;


    Pressure(int pressure) {
        this.pressure = pressure;
    }

    public int getPressure() {
        return pressure;
    }

    public String getUnit() {
        return "kPa";
    }
}

package com.obdelm327pro.service;

public class AirflowRate {

    double airflowRate;


    AirflowRate(double airflowRate) {
        this.airflowRate = airflowRate;
    }

    public double getAirflowRate() {
        return airflowRate;
    }

    public String getUnit() {
        return "g/s";
    }
}

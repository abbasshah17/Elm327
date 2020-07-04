package com.obdelm327pro.service;

import androidx.annotation.NonNull;

public class Temperature {

    private int temperature;
    private Unit unit;


    Temperature(int temperature, @NonNull Unit unit) {
        this.temperature = temperature;
        this.unit = unit;
    }

    public int getTemperature() {
        return temperature;
    }

    public Unit getUnit() {
        return unit;
    }

    public enum Unit {
        CELSIUS {
            @Override
            public String value() {
                return "C°";
            }
        },
        FAHRENHEIT {
            @Override
            public String value() {
                return "F°";
            }
        };

        public abstract String value();
    }
}

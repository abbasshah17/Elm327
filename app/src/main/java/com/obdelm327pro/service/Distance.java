package com.obdelm327pro.service;

import android.support.annotation.NonNull;

public class Distance {

    private int distance;

    private Unit unit;


    Distance(int distance, @NonNull Unit unit) {
        this.distance = distance;
        this.unit = unit;
    }


    public int getDistance() {
        return distance;
    }

    public Unit getUnit() {
        return unit;
    }


    public enum Unit {
        KILO_METERS {
            @Override
            public String value() {
                return "km";
            }
        },
        MILES {
            @Override
            public String value() {
                return "M";
            }
        };

        public abstract String value();
    }
}

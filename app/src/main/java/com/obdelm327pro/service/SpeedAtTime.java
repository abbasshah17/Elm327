package com.obdelm327pro.service;

public class SpeedAtTime implements Comparable<SpeedAtTime>{

    private long timeStamp;

    private Speed speed;


    public SpeedAtTime(long timeStamp, Speed speed) {
        this.timeStamp = timeStamp;
        this.speed = speed;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public Speed getSpeed() {
        return speed;
    }

    @Override
    public int compareTo(SpeedAtTime specifiedSpeedAtTime) {

        if (timeStamp < specifiedSpeedAtTime.timeStamp) {
            return 1;
        }
        else if (timeStamp == specifiedSpeedAtTime.timeStamp) {
            return 0;
        }

        return -1;
    }
}

package com.gen.mavsdkandroidserialtest.models;

public class PositionRelative {

    private float distance;
    private float height;

    public PositionRelative(float distance, float height) {
        this.distance = distance;
        this.height = height;
    }

    public float getDistance() {
        return distance;
    }

    public float getHeight() {
        return height;
    }
}

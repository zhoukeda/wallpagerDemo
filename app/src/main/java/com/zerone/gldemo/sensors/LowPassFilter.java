package com.zerone.gldemo.sensors;

public class LowPassFilter {

    private double factor = 0.0;
    private final int width;
    private double[] last;

    public LowPassFilter(int width) {
        this.width = width;

        last = new double[width];
    }

    public void setFactor(double factor) {
        this.factor = factor;
    }

    public void setLast(double[] last) {
        this.last = last.clone();
    }

    public double[] filter(double[] input) {
        double[] output = new double[width];

        for (int i = 0; i < width-1; i++) {
            output[i] = factor * input[i] + (1 - factor) * last[i];
        }

        output[2] = input[2];

        last = output.clone();

        return output;
    }
}

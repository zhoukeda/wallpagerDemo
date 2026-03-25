package com.zerone.gldemo.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;import com.zerone.hidesktop.utils.sensors.RotationParser;

public class Parallax implements SensorEventListener {
    public final static double VERTICAL_FIX = 0.01;

    private final String TAG = getClass().getSimpleName();

    // Filters
    private final LowPassFilter sensitivityFilter = new LowPassFilter(3);
    private final LowPassFilter fallbackFilter = new LowPassFilter(3);
    private double[] resetDeg = new double[3];
    private boolean filtersInit;

    // Outputs
    private double degX;
    private double degY;
    private double degZ;

    private final SensorManager sensorManager;
    private final GenericParser parser;
    private final Context context;
    private int index = 0;
    private long lastDrawTime = 0L;
    private boolean isRegister = false;
    public Parallax(Context context) {

        this.context = context;

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        parser = getParser();

        if (parser == null) {
            Log.e(TAG, "No valid sensor available!");
        }

        filtersInit = false;
    }

    public  double getDegX() {
        return degX;
    }

    public double getDegY() {
        return degY;
    }

    public double getDegZ() {
        return degZ;
    }

    public  void setFallback(double fallback) {
        fallbackFilter.setFactor(fallback);
    }

    public void setSensitivity(double sensitivity) {
        sensitivityFilter.setFactor(sensitivity);
    }

    public  void start(int model) {

        if (parser != null) {
            for (Sensor sensor : parser.getSensors()) {
                sensorManager.registerListener(this, sensor, model);
            }
        }

        Log.d(TAG, "Sensor listener started!");
    }

    public  void stop() {
        sensorManager.unregisterListener(this);

        Log.d(TAG, "Sensor listener stopped!");
    }

    // SensorEventListenerMethods
    @Override
    public void onSensorChanged(SensorEvent event) {
//        index++;
//        if (System.currentTimeMillis() - lastDrawTime > 1000) {
//            LogUtils.i("EngineGravityImpl--->onSensorChanged--->一秒"+index+"次数");
//            if (index>3 && index<15) {
//                //自动休眠了，要重新注册
//                //防并发
//                if (!isRegister || lastDrawTime == 0L) {
//                    LogUtils.i("EngineGravityImpl--->onSensorChanged--->手动注销");
//                    return;
//                }
//                LogUtils.i("EngineGravityImpl--->onSensorChanged--->自动休眠，重新注册");
//                stop();
//                start();
//            }
//            lastDrawTime = System.currentTimeMillis();
//            index = 0;
//        }


        double[] newDeg = parser.parse(event);
//        LogUtils.d("俯仰角pitch: " + newDeg[0] + " roll: " + newDeg[1] + "Z轴线相关----》" + newDeg[2]);

        // Set the initial value of the filters to current val
        if (!filtersInit) {
            sensitivityFilter.setLast(newDeg);
            fallbackFilter.setLast(newDeg);
            filtersInit = true;
        }

        // Apply filter
        newDeg = sensitivityFilter.filter(newDeg);

        degY = newDeg[0] - resetDeg[0];
        degX = newDeg[1] - resetDeg[1];
        degZ = newDeg[2];

        resetDeg = fallbackFilter.filter(newDeg);

        if (degX > 180) {
            resetDeg[1] += degX - 180;
            degX = 180;
        }

        if (degX < -180) {
            resetDeg[1] += degX + 180;
            degX = -180;
        }


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    // Return the best sensor available
    private GenericParser getParser() {
        if (sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            Log.d(TAG, "Using gravity");
            return new GravityParser(context);
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            Log.d(TAG, "Using accelerometer");
            return new AccelerationParserNew(context);
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) {
            Log.d(TAG, "Using rotation vector");
            return new RotationParser(context);
        }

        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null) {
            Log.d(TAG, "Using accelerometer+magnetometer");
            return new AccelerationParser(context);
        }



        return null;
    }
}

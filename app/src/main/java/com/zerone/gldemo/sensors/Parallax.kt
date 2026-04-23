package com.zerone.gldemo.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class Parallax(private val context: Context) : SensorEventListener {
    private val TAG: String = javaClass.getSimpleName()

    // Filters
    private val sensitivityFilter = LowPassFilter(3)
    private val fallbackFilter = LowPassFilter(3)
    private var resetDeg = DoubleArray(3)
    private var filtersInit: Boolean

    // Outputs
    var degX: Double = 0.0
        private set
    var degY: Double = 0.0
        private set
    var degZ: Double = 0.0
        private set

    var baseDegX: Double = 0.0
        private set
    var baseDegY: Double = 0.0
        private set

    private var sensorManager: SensorManager? = null
    private val parser: GenericParser?
    init {

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        parser = getParser()

        if (parser == null) {
            Log.e(TAG, "No valid sensor available!")
        }

        filtersInit = false
    }

    fun setFallback(fallback: Double) {
        fallbackFilter.setFactor(fallback)
    }

    fun setSensitivity(sensitivity: Double) {
        sensitivityFilter.setFactor(sensitivity)
    }

    fun start(model: Int) {
        if (parser != null) {
            for (sensor in parser.getSensors()) {
                sensorManager?.registerListener(this, sensor, model)
            }
        }

        Log.d(TAG, "Sensor listener started!")
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "Sensor listener stopped!")
    }

    // SensorEventListenerMethods
    override fun onSensorChanged(event: SensorEvent?) {
        var newDeg = parser!!.parse(event)

        //        LogUtils.d("俯仰角pitch: " + newDeg[0] + " roll: " + newDeg[1] + "Z轴线相关----》" + newDeg[2]);

        // Set the initial value of the filters to current val
        if (!filtersInit) {
            sensitivityFilter.setLast(newDeg)
            fallbackFilter.setLast(newDeg)
            filtersInit = true
        }

        // Apply filter
        newDeg = sensitivityFilter.filter(newDeg)

        degY = newDeg[0] - resetDeg[0]
        degX = newDeg[1] - resetDeg[1]
        degZ = newDeg[2]

        resetDeg = fallbackFilter.filter(newDeg)

        if (degX > 180) {
            resetDeg[1] += degX - 180
            degX = 180.0
        }

        if (degX < -180) {
            resetDeg[1] += degX + 180
            degX = -180.0
        }

        if (baseDegX == 0.0){
            baseDegX = degX
        }
        if (baseDegY == 0.0){
            baseDegY = degY
        }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    // Return the best sensor available
    private fun getParser(): GenericParser? {
        if (sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null) {
            Log.d(TAG, "Using gravity")
            return GravityParser(context)
        }

        if (sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            Log.d(TAG, "Using accelerometer")
            return AccelerationParserNew(context)
        }

        if (sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) {
            Log.d(TAG, "Using rotation vector")
            return RotationParser(context)
        }

        if (sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null && sensorManager?.getDefaultSensor(
                Sensor.TYPE_MAGNETIC_FIELD
            ) != null
        ) {
            Log.d(TAG, "Using accelerometer+magnetometer")
            return AccelerationParser(context)
        }
        return null
    }

    fun reSetBaseXY(){
        baseDegX = 0.0
        baseDegY = 0.0
        degX = 0.0
        degY = 0.0
        degZ = 0.0
    }

    companion object {
        const val VERTICAL_FIX: Double = 0.01
    }
}

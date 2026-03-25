package com.zerone.gldemo.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 加速度传感器工具类 - 用于计算俯仰角和稳定的侧翻角
 * 使用低通滤波器消除抖动，提供平滑的角度数据
 */
class AccelerometerSensorHelper(context: Context) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // 滤波系数 (0.1-0.9)，值越大越平滑但响应越慢
    private var filterAlpha: Float = 0.8f

    // 滤波后的角度值
    private var filteredPitch: Float = 0f
    private var filteredTilt: Float = 0f  // 改为使用稳定的侧翻角度

    // 监听器
    private var orientationListener: OrientationListener? = null

    interface OrientationListener {
        fun onOrientationChanged(pitch: Float, tilt: Float)  // 参数名改为tilt
    }

    /**
     * 开始监听传感器数据
     */
    fun startListening(
        listener: OrientationListener? = null,
        samplingRate: Int = SensorManager.SENSOR_DELAY_NORMAL
    ) {
        orientationListener = listener
        // 优先使用旋转矢量传感器，因为它能提供更稳定的角度计算
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, samplingRate)
        } ?: run {
            // 如果没有旋转矢量传感器，回退到加速度计
            accelerometer?.let {
                sensorManager.registerListener(this, it, samplingRate)
            }
        }
    }

    /**
     * 停止监听传感器数据
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)
        orientationListener = null
    }

    /**
     * 设置滤波系数
     * @param alpha 0.1-0.9，默认0.8，值越大越平滑
     */
    fun setFilterAlpha(alpha: Float) {
        filterAlpha = alpha.coerceIn(0.1f, 0.9f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    processRotationVectorData(it.values)
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    // 回退到加速度计计算
                    val (rawPitch, rawRoll) = calculateRawOrientation(it.values)
                    filteredPitch = filterAlpha * filteredPitch + (1 - filterAlpha) * rawPitch

                    // 加速度计在垂直状态下效果更差，需要更强滤波
                    val verticalFilterAlpha = if (abs(filteredPitch) > 60f) 0.95f else filterAlpha
                    filteredTilt =
                        verticalFilterAlpha * filteredTilt + (1 - verticalFilterAlpha) * rawRoll

                    orientationListener?.onOrientationChanged(filteredPitch, filteredTilt)
                }

                else -> {

                }
            }
        }
    }


    /**
     * 处理旋转矢量传感器数据（更稳定）
     */
    private fun processRotationVectorData(values: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)

        // 原始俯仰角 (Pitch)，仍然用欧拉角
        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()


        val rawTilt = if (abs(rawPitch) < 80f) {
            Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        }else{
            filteredTilt
        }

        // 滤波
        filteredPitch = filterAlpha * filteredPitch + (1 - filterAlpha) * rawPitch
        filteredTilt = filterAlpha * filteredTilt + (1 - filterAlpha) * rawTilt

        orientationListener?.onOrientationChanged(filteredPitch, filteredTilt)
    }

    /**
     * 计算手机在任意 pitch 状态下的纯 Y 轴翻转角 tilt
     * rotationMatrix: 设备旋转矩阵，长度 9 (3x3)
     * 返回 tilt 范围 -90 ~ 90
     */
    fun calculateTilt(rotationMatrix: FloatArray): Float {
        // 设备前方向向量
        val fx = rotationMatrix[2] // -Z 方向 X 分量
        val fz = rotationMatrix[8] // -Z 方向 Z 分量

        // 绕 Y 轴旋转角
        val tilt = Math.toDegrees(atan2(fx.toDouble(), fz.toDouble())).toFloat()
        return tilt/2
    }
    /**
     * 计算原始俯仰角和翻滚角（未滤波）- 仅用于加速度计回退
     */
    private fun calculateRawOrientation(values: FloatArray): Pair<Float, Float> {
        val x = values[0] // 左右加速度
        val y = values[1] // 前后加速度
        val z = values[2] // 上下加速度

        // 计算俯仰角 (Pitch) - 上下倾斜（绕X轴）
        val pitch = Math.toDegrees(
            atan2(-y.toDouble(), sqrt((x * x + z * z).toDouble()))
        ).toFloat()

        // 计算翻滚角 (Roll) - 左右倾斜（绕Y轴）
        val roll = Math.toDegrees(
            atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))
        ).toFloat()

        return Pair(pitch, roll)
    }

    /**
     * 获取当前俯仰角（滤波后）
     */
    fun getCurrentPitch(): Float = filteredPitch

    /**
     * 获取当前侧翻角度（滤波后）
     * @return 侧翻角度，正值表示右侧翻，负值表示左侧翻
     */
    fun getCurrentTilt(): Float = filteredTilt


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 精度变化处理（通常不需要）
    }

}
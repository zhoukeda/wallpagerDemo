package com.zerone.gldemo

import android.content.Context
import android.content.UriRelativeFilter.FRAGMENT
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.ETC1Util.loadTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class AdvancedRenderer(
    private val context: Context,
    private val bg: Bitmap,
    private val fg: Bitmap
) : GLSurfaceView.Renderer {
//    onSensorChanged
    private var program = 0

    private var texBg = 0
    private var texFg = 0
//    private var texBlur = 0

    private var uDirection = 0
    private var uProgress = 0
    private var uTime = 0

    private var direction = floatArrayOf(0f, 1f)
    private var progress = 0f
    private var time = 0f

//    private lateinit var sensorManager: SensorManager

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = GLUtil.createProgram(VERTEX, FRAGMENT)

        texBg = GLUtil.loadTexture(bg)
        texFg = GLUtil.loadTexture(fg)
        GLES20.glUseProgram(program)
        uDirection = GLES20.glGetUniformLocation(program, "uDirection")
        uProgress = GLES20.glGetUniformLocation(program, "uProgress")
        uTime = GLES20.glGetUniformLocation(program, "uTime")

//        initSensor()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        time += 0.016f

        GLES20.glUseProgram(program)

        GLES20.glUniform2f(uDirection, direction[0], direction[1])
        GLES20.glUniform1f(uProgress, progress)
        GLES20.glUniform1f(uTime, time)

        // ✅ 开启混合（放这里）
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLUtil.bindTexture(0, texBg, program, "uBg")
        GLUtil.bindTexture(1, texFg, program, "uFg")

        GLUtil.drawFullScreenQuad(program)
    }

    fun setProgress(value: Int) {
        progress = (value / 100f).coerceIn(0f, 1f)
    }

    fun setDirection(x: Float, y: Float) {
        direction[0] = x
        direction[1] = y
    }

    // ---------- 重力控制 ----------
//    private fun initSensor() {
//        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
//        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
//    }

//    override fun onSensorChanged(event: SensorEvent) {
//        val x = event.values[0]
//        val y = event.values[1]
//
//        direction[0] = -x / 9.8f
//        direction[1] = y / 9.8f
//    }

//    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---------- 简单模糊 ----------
    private fun createBlurBitmap(src: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(src, src.width / 8, src.height / 8, true)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
    }

    companion object {
        const val VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val FRAGMENT = """
precision mediump float;

uniform sampler2D uBg;
uniform sampler2D uFg;
uniform vec2 uDirection;
uniform float uProgress;
uniform float uTime;

varying vec2 vTexCoord;

float wave(vec2 uv) {
    return sin(uv.y * 20.0 + uTime * 2.0) * 0.02;
}

void main() {
    vec2 dir = normalize(uDirection + 0.0001);

    float p0 = dot(vec2(0.0, 0.0), dir);
    float p1 = dot(vec2(1.0, 0.0), dir);
    float p2 = dot(vec2(0.0, 1.0), dir);
    float p3 = dot(vec2(1.0, 1.0), dir);

    float minProj = min(min(p0, p1), min(p2, p3));
    float maxProj = max(max(p0, p1), max(p2, p3));
    float range = maxProj - minProj;

    float proj = dot(vTexCoord, dir);
    proj = (proj - minProj) / range;

    float w = wave(vTexCoord);
    float waveVal = proj + w;
    
    // ✅ 修正：progress=0 时 mask=0，fg完全显示
    // progress=1 时 mask=1，fg完全不显示
    float edgeWidth = 0.05;
    float mask = smoothstep(uProgress - edgeWidth, uProgress, waveVal);
    
    
    vec4 fg = texture2D(uFg, vTexCoord);
    vec4 bg = texture2D(uBg, vTexCoord);
    
    // ✅ 分离控制（关键）
    float visibleAlpha =  uProgress;
    float fgAlpha = mix(visibleAlpha, 0.0, mask);

// ✅ 合成
    vec3 finalColor = mix(bg.rgb, fg.rgb, fgAlpha);

    gl_FragColor = vec4(finalColor, 1.0);
}
        """
    }

    //不带透明的
//    // ✅ 修正：fgAlpha 只与 mask 相关，progress 控制裁剪位置
//    float fgAlpha = 1.0 - clamp(mask, 0.0, 1.0);
//    vec3 finalColor = mix(bg.rgb, fg.rgb, fgAlpha);
//    gl_FragColor = vec4(finalColor, 1.0);



}
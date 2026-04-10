package com.zerone.gldemo.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.zerone.gldemo.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * @author dada
 * @date 2026/4/10
 * @desc
 */
class MeshRenderer(var content: Context) : GLSurfaceView.Renderer {

    private val vertexShaderCode = """
attribute vec2 aPos;
attribute vec2 aUV;
varying vec2 vUV;

void main() {
    vUV = aUV;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    private val fragmentShaderCode = """
precision mediump float;

uniform sampler2D uTex;
varying vec2 vUV;

void main() {
    gl_FragColor = texture2D(uTex, vUV);
}
"""
    private var program = 0

    private var bgTex = 0
    private var smallTex = 0
    // ✅ 添加：uniform 位置
    private var uTexLoc = 0

    private val N = 20

    private lateinit var base: FloatArray
    private lateinit var verts: FloatArray
    private lateinit var uv: FloatArray
    private lateinit var idx: ShortArray

    private var vBuffer: FloatBuffer? = null
    private var uvBuffer: FloatBuffer? = null
    private var iBuffer: ShortBuffer? = null

    private var touching = false
    private var tx = 0f
    private var ty = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {

        program = createProgram()
        uTexLoc = GLES20.glGetUniformLocation(program, "uTex")
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // 加载图片
        val bg = BitmapFactory.decodeResource(content.resources, R.drawable.bg)
        val small = BitmapFactory.decodeResource(content.resources, R.drawable.small_image)

        val matrix = android.graphics.Matrix()
        matrix.preScale(1f, -1f)
        val bgsmall = Bitmap.createBitmap(small, 0, 0, small.width, small.height, matrix, false)

        setBackground(bg)
        setSmallImage(bgsmall)

        initMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {

        // ✅ 添加日志检查纹理ID
        if (smallTex == 0) {
            android.util.Log.e("MeshRenderer", "smallTex is 0! Texture not loaded")
            // 绘制红色调试色
            GLES20.glClearColor(1f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            return
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        updateMesh()

        GLES20.glUseProgram(program)

        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val uvLoc = GLES20.glGetAttribLocation(program, "aUV")

        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(uvLoc)

        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vBuffer)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 0, uvBuffer)

        // ✅ 修改：正确绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, smallTex)
        GLES20.glUniform1i(uTexLoc, 0)  // 关键：设置 uniform

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            idx.size,
            GLES20.GL_UNSIGNED_SHORT,
            iBuffer
        )
    }

    private fun initMesh() {

        val step = 1f / N

        val v = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val id = mutableListOf<Short>()

        // ✅ 图片大小和位置
        val imageSize = 0.3f   // 占屏幕 30%
        val offset = (1f - imageSize) / 2f  // 居中：0.35

        for (y in 0..N) {
            for (x in 0..N) {

                v.add(x * step)
                v.add(y * step)

                uvList.add(x * step)
                uvList.add(y * step)
            }
        }

        for (y in 0 until N) {
            for (x in 0 until N) {

                val i = (y * (N + 1) + x).toShort()

                id.add(i)
                id.add((i + 1).toShort())
                id.add((i + N + 1).toShort())

                id.add((i + 1).toShort())
                id.add((i + N + 2).toShort())
                id.add((i + N + 1).toShort())
            }
        }

        base = v.toFloatArray()
        verts = base.copyOf()
        uv = uvList.toFloatArray()
        idx = id.toShortArray()

        vBuffer = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(verts)  // 添加这一行
                position(0)
            }

        uvBuffer = ByteBuffer.allocateDirect(uv.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(uv)
                position(0)
            }

        iBuffer = ByteBuffer.allocateDirect(idx.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(idx)
                position(0)
            }
    }

    private fun updateMesh() {

        if (!touching) {

            for (i in verts.indices) {
                verts[i] += (base[i] - verts[i]) * 0.1f
            }

        } else {

            val radius = 0.25f
            val strength = 0.4f

            for (i in base.indices step 2) {

                val x = base[i]
                val y = base[i + 1]

                val dx = x - tx
                val dy = y - ty

                val dist = sqrt(dx * dx + dy * dy)

                if (dist < radius) {

                    val f = (1f - dist / radius) * strength

                    verts[i] += dx * f
                    verts[i + 1] += dy * f
                }
            }
        }

        vBuffer?.clear()
        vBuffer?.put(verts)
        vBuffer?.position(0)
    }

    private fun createProgram(): Int {

        fun load(type: Int, code: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, code)
            GLES20.glCompileShader(s)
            return s
        }

        val vs = load(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fs = load(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
        }
    }
    fun hit(x: Float, y: Float): Boolean {
        return x in 0.35f..0.65f && y in 0.35f..0.65f
    }

    fun touch(x: Float, y: Float, down: Boolean) {
        tx = x
        ty = y
        touching = down
    }

    fun setBackground(bmp: Bitmap) {
        bgTex = loadTexture(bmp, bgTex)
    }

    fun setSmallImage(bmp: Bitmap) {
        android.util.Log.e("MeshRenderer", "setSmallImage called, bitmap = $bmp")
        smallTex = loadTexture(bmp, smallTex)
        android.util.Log.e("MeshRenderer", "smallTex after load = $smallTex")
    }

    private fun loadTexture(bitmap: Bitmap, texId: Int): Int {
        val textures = IntArray(1)
        if (texId == 0) {
            GLES20.glGenTextures(1, textures, 0)
        } else {
            textures[0] = texId
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return textures[0]
    }

}
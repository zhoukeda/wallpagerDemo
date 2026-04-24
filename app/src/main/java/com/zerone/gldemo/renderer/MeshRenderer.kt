package com.zerone.gldemo.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.VibrateUtils
import com.zerone.gldemo.GLUtil.createProgram
import com.zerone.gldemo.R
import com.zerone.gldemo.view.RequestRequesterListener
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
class MeshRenderer(var content: Context,val listener:RequestRequesterListener) : GLSurfaceView.Renderer {

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
    private var stretchTex = 0 // ✅ 新增：极限拉伸时的前景图片

    // ✅ 添加：uniform 位置
    private var uTexLoc = 0
    private var currentTex = 0 // ✅ 新增：当前正在使用的纹理

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

    private val radius = 0.4f       // 影响半径
    private val strength = 0.4f     // 拖拽力度 (0~1)

    // 记录上一帧的触摸位置 (OpenGL 坐标系)
    private var lastTx = 0f
    private var lastTy = 0f

    // 在类成员变量中添加背景专用的 Buffer，避免每帧创建
    private var bgVertexBuffer: FloatBuffer? = null
    private var bgUVBuffer: FloatBuffer? = null

    private var touchStartX = 0.35f
    private var touchEndX = 0.65f
    private var touchStartY = 0.35f
    private var touchEndY = 0.65f
    private val thresholdDistance = 0.05f

    private var startTx = 0f
    private var startTy = 0f

    private var width:Int = ScreenUtils.getScreenWidth()
    private var height:Int = ScreenUtils.getScreenHeight()

    // --- 物理回弹相关变量 ---
    private var isRebounding = false       // 是否正在回弹
    private var reboundStartTime = 0L      // 回弹开始的时间戳
    private val reboundDuration = 500L    // 回弹总时长 (1秒 = 1000毫秒)
    private var reboundVx = 0f             // X轴速度
    private var reboundVy = 0f             // Y轴速度
    private var speedFactor = 0.4f//距离系数（这里传进来的点位做了归一化处理，所以默认x 1000）




    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {

        program = createProgram()
        uTexLoc = GLES20.glGetUniformLocation(program, "uTex")
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // 加载图片
        val bg = BitmapFactory.decodeResource(content.resources, R.drawable.bg)
        val small = BitmapFactory.decodeResource(content.resources, R.drawable.small_image)
        // ✅ 新增：加载极限状态的图片
        val stretch =
            BitmapFactory.decodeResource(content.resources, R.drawable.small_image_stretch)

        val matrix = android.graphics.Matrix()
        matrix.preScale(1f, -1f)
        val fgsmall = Bitmap.createBitmap(small, 0, 0, small.width, small.height, matrix, false)
        val fgStretch =
            Bitmap.createBitmap(stretch, 0, 0, stretch.width, stretch.height, matrix, false) //


        GLES20.glUseProgram(program)

        //绘制背景
        initBackgroundBuffers()
        setBackground(bg)


        setSmallImage(fgsmall)
        setStretchImage(fgStretch) // ✅ 新增：设置极限图片
        initMesh()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        LogUtils.e("轨迹运动---->onDrawFrame")
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawBackground()
        updateMesh()
        drawMesh()
    }

    private fun drawMesh() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val uvLoc = GLES20.glGetAttribLocation(program, "aUV")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(uvLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, vBuffer)
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 0, uvBuffer)
        // ✅ 修改：正确绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, currentTex) // 这里改了！
        GLES20.glUniform1i(uTexLoc, 0)  // 关键：设置 uniform
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            idx.size,
            GLES20.GL_UNSIGNED_SHORT,
            iBuffer
        )
    }

    private fun drawBackground() {
        if (bgTex == 0) return
        // 使用同一个 Program 即可（因为着色器逻辑一样，都是贴图）
        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val uvLoc = GLES20.glGetAttribLocation(program, "aUV")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(uvLoc)
        // 绑定背景顶点
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, bgVertexBuffer)
        // 绑定背景 UV
        GLES20.glVertexAttribPointer(uvLoc, 2, GLES20.GL_FLOAT, false, 0, bgUVBuffer)
        // 绑定背景纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTex)
        GLES20.glUniform1i(uTexLoc, 0)
        // 绘制 4 个顶点组成的三角形带 (或者用 TRIANGLES 索引，这里简单起见直接用 TRIANGLE_STRIP 画 4 个点)
        // 注意：上面的顶点顺序是适配 TRIANGLE_STRIP 的：左下->右下->左上->右上
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun initMesh() {
        currentTex = smallTex
        val step = 1f / N
        val v = mutableListOf<Float>()
        val uvList = mutableListOf<Float>()
        val id = mutableListOf<Short>()
        // ✅ 图片大小和位置
        val scaleX = 0.48f
        val scaleY = 0.2216f
        val offsetX = (1f - scaleX) / 2f
        val offsetY = (1f - scaleY) / 2f
        for (y in 0..N) {
            for (x in 0..N) {
                val nx = x * step
                val ny = y * step
                // 👉 居中缩放关键
                val px = offsetX + nx * scaleX
                val py = offsetY + ny * scaleY
                // 转 NDC [-1, 1]
                v.add(px * 2f - 1f)
                v.add(py * 2f - 1f)
                uvList.add(nx)
                uvList.add(ny)
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
        val dx = tx - lastTx
        val dy = ty - lastTy
        // ✅ 1. 提前定义变量，用于记录最大拉伸量
        if (touching) {
            // ✅ 2. 核心优化：只遍历一次 (for 循环 A)
            for (i in verts.indices step 2) {
                val vx = verts[i]
                val vy = verts[i + 1]
                // --- A. 计算拉伸权重 (物理模拟) ---
                val distDx = vx - tx
                val distDy = vy - ty
                val dist = sqrt(distDx * distDx + distDy * distDy)
                if (dist < radius) {
                    val weight = (1f - dist / radius) * strength
                    verts[i] += dx * weight
                    verts[i + 1] += dy * weight
                }
            }
            // ✅ 3. 根据刚才遍历的结果，决定用哪张图
            // 注意：这里不要在这里赋值给 OpenGL，只记录状态
            lastTx = tx
            lastTy = ty

            vBuffer?.clear()
            vBuffer?.put(verts)
            vBuffer?.position(0)
        }

        if (isRebounding) {
            // 1. 计算当前进度 (0.0 到 1.0)
            val elapsed = System.currentTimeMillis() - reboundStartTime
            val progress = elapsed.toFloat() / reboundDuration
            if (progress >= 1.0f) {
                LogUtils.e("轨迹运动---->时间到，停止碰撞")
                // --- 时间到了：直接回到原位 ---
                VibrateUtils.vibrate(50)
                isRebounding = false
                resetMeshToBase() // 瞬间复位
                // 这里可以触发一个回调，告诉外面动画结束了
            } else {
                LogUtils.e("轨迹运动---->触发边界检测")
                for (i in verts.indices step 2) {
                    verts[i] += reboundVx
                    verts[i + 1] += reboundVy
                }
                // 3. 边界检测与反弹 (台球逻辑)
                // 假设屏幕/视图宽 width, 高 height
                checkBoundaryAndBounce()

                vBuffer?.clear()
                vBuffer?.put(verts)
                vBuffer?.position(0)
            }
            listener.updraw()
        }
    }

    // 检查边界
    // 检查边界
    private fun checkBoundaryAndBounce() {
        // 1. 获取包围盒
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (i in verts.indices step 2) {
            if (verts[i] < minX) minX = verts[i]
            if (verts[i] > maxX) maxX = verts[i]
            if (verts[i + 1] < minY) minY = verts[i + 1]
            if (verts[i + 1] > maxY) maxY = verts[i + 1]
        }
        // 2. 定义 NDC 边界
        val bound = 1.0f
        // --- X 轴反弹 ---
        // 碰到左墙 (NDC -1)
        if (minX <= -bound) {
            if (reboundVx < 0) {
                LogUtils.e("轨迹运动---->X轴反弹：左墙")
                reboundVx = -reboundVx // 速度反向
                // ✅ 修改点：计算需要移动的距离，把 minX 强行拉到 -1 里面一点点
                // 比如拉到 -0.99，确保彻底脱离碰撞区
                val correction = (-bound + 0.01f) - minX
                moveAllVertices(correction, 0f)
            }
        }
        // 碰到右墙 (NDC 1)
        else if (maxX >= bound) {
            if (reboundVx > 0) {
                LogUtils.e("轨迹运动---->X轴反弹：右墙")
                reboundVx = -reboundVx // 速度反向

                // ✅ 修改点：把 maxX 强行拉到 1 里面一点点
                // 比如拉到 0.99
                val correction = (bound - 0.01f) - maxX
                moveAllVertices(correction, 0f)
            }
        }

        // --- Y 轴反弹 ---
        // 碰到下墙 (NDC -1)
        if (minY <= -bound) {
            if (reboundVy < 0) {
                LogUtils.e("轨迹运动---->Y轴反弹：下墙")
                reboundVy = -reboundVy

                val correction = (-bound + 0.01f) - minY
                moveAllVertices(0f, correction)
            }
        }
        // 碰到上墙 (NDC 1)
        else if (maxY >= bound) {
            if (reboundVy > 0) {
                LogUtils.e("轨迹运动---->Y轴反弹：上墙")
                reboundVy = -reboundVy

                val correction = (bound - 0.01f) - maxY
                moveAllVertices(0f, correction)
            }
        }
    }

    // 辅助函数：整体移动图片
    private fun moveAllVertices(dx: Float, dy: Float) {
        VibrateUtils.vibrate(50)
        for (i in verts.indices step 2) {
            verts[i] += dx
            verts[i + 1] += dy
        }
    }

    // 开始运动
    private fun startRebound() {
        // 1. 计算位移向量 (按下 - 抬起) -> 弹弓效果
        val deltaX = startTx - tx
        val deltaY = startTy - ty
        // 2. 计算速度
        reboundVx = deltaX * speedFactor
        reboundVy = deltaY * speedFactor
        LogUtils.e("轨迹运动------>${reboundVx}---->${reboundVy}")
        val distance = sqrt(reboundVx * reboundVx + reboundVy * reboundVy)
        LogUtils.e("轨迹运动------>distance---->${distance}")
        // 阈值也要相应调整，因为现在的速度值很小了
        if (distance < 0.1f) {
            resetMeshToBase()
            return
        }
        isRebounding = true
        reboundStartTime = System.currentTimeMillis()
        // 强制刷新
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
        return x in touchStartX..touchEndX && y in touchStartY..touchEndY
    }


    fun touch(startX: Float, startY: Float, x: Float, y: Float, down: Boolean) {
        // 1. 转换坐标 (-1 ~ 1)
        val newTx = x * 2f - 1f
        val newTy = (1f - y) * 2f - 1f
        // ✅ 2. 转换起点坐标 (也要转成 OpenGL 坐标)
        val convertedStartTx = startX * 2f - 1f
        val convertedStartTy = (1f - startY) * 2f - 1f
        // 3. 判断图片切换逻辑 (保持你原来的逻辑)
        val dragDistance = sqrt((x - startX) * (x - startX) + (y - startY) * (y - startY))
        currentTex = if (dragDistance > thresholdDistance) stretchTex else smallTex
        if (down) {
            if (!touching) {
                // ✅ 4. 记录初始位置
                startTx = convertedStartTx
                startTy = convertedStartTy
                lastTx = newTx
                lastTy = newTy
            }
            tx = newTx
            ty = newTy
            touching = true
        } else {
            resetMeshToBase()
            touching = false
            startRebound()
        }
    }

    private fun resetMeshToBase() {
        // 简单的数组复制
        // base 是原始状态，verts 是当前渲染状态
        System.arraycopy(base, 0, verts, 0, base.size)

        // ✅ 关键：同步更新 Buffer，否则 OpenGL 还在画旧的数据
        vBuffer?.clear()
        vBuffer?.put(verts)
        vBuffer?.position(0)

        currentTex = smallTex

    }

    fun setBackground(bmp: Bitmap) {
        bgTex = loadTexture(bmp, bgTex)
    }

    fun setSmallImage(bmp: Bitmap) {
        smallTex = loadTexture(bmp, smallTex)
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
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return textures[0]
    }


    private fun initBackgroundBuffers() {
        // 定义全屏的 4 个顶点 (x, y) -> 覆盖整个屏幕
        val vertices = floatArrayOf(
            -1f, -1f, // 左下
            1f, -1f, // 右下
            -1f, 1f, // 左上
            1f, 1f  // 右上
        )

        // 定义对应的 UV 坐标 (0~1)
        // 注意：如果图片方向不对，可以调整这里，比如翻转 Y 轴
        val uvs = floatArrayOf(
            0f, 1f, // 左下
            1f, 1f, // 右下
            0f, 0f, // 左上
            1f, 0f  // 右上
        )

        bgVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(vertices); position(0) }

        bgUVBuffer = ByteBuffer.allocateDirect(uvs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(uvs); position(0) }
    }

    fun setStretchImage(bmp: Bitmap) {
        stretchTex = loadTexture(bmp, stretchTex)
    }
}
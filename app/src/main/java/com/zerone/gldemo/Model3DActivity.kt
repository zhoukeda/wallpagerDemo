package com.zerone.gldemo

import android.R.attr.bitmap
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.LogUtils
import com.google.android.filament.LightManager
import com.google.android.filament.Texture
import com.google.android.filament.Texture.PixelBufferDescriptor
import com.google.android.filament.TextureSampler
import com.zerone.gldemo.sensors.Parallax
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Transform
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * @author dada
 * @date 2026/4/10
 * @desc 网格扭曲
 */
class Model3DActivity : AppCompatActivity() {
    private lateinit var sceneView: SceneView
    private var autoRotate = true
    private var yaw = 0f
    private var pitch = 0f
    private var scale = 1f
    private val minScale = 0.2f
    private var maxScale = 1f

    //模型相关
    private var modelNode: ModelNode? = null
    private var texture: Texture? = null
    private var filamentInstance: ModelInstance? = null
    private var loader: ModelLoader? = null
    private var isDestroyed = false

    //触摸相关
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleDetector: ScaleGestureDetector
    private var isTouch = false//是否触摸

   //传感器相关
    private var parallax:Parallax? = null
    private var isParallxRuning = false
    private val sensitivity = 0.6f
    private var lastSensorPitch = 0f
    private var lastSensorYaw = 0f
    private var isStartParallx = true

    // 惯性相关的变量
    private var flingVelocityX = 0f
    private var flingVelocityY = 0f
    private val FLING_DAMPING = 0.95f // 惯性阻尼系数 (0.9~0.98 手感较好，越小停得越快)
    private val FLING_THRESHOLD = 10f // 停止惯性的速度阈值
    // 如果需要限制垂直角度，可以保留这个，如果不需要限制可删除
    private var pitchAccumulator = 0f
    private var flingVelocity = 0.003f//惯性速度系数

    //旋转矩阵
    private var currentRotation = Quaternion()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)

        parallax = Parallax(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                try {
                    LogUtils.e("Model3D", "进入销毁程序")
                    isDestroyed = true
                    LogUtils.e("Model3D", "停止渲染")
                    sceneView.onFrame = null
                    // 2️⃣ 移除节点
                    modelNode?.let {
                        LogUtils.e("Model3D", "移除节点")
                        sceneView.removeChildNode(it)
                        modelNode = null
                    }

                    // 2. 销毁模型
                    filamentInstance?.model?.let { model ->
                        LogUtils.e("Model3D", "销毁模型")
                        loader?.destroyModel(model)
                    }
                    // 3. 销毁纹理
                    texture?.let { tex ->
                        LogUtils.e("Model3D", "销毁纹理")
                        sceneView.engine.destroyTexture(tex)
                    }
                    // 4. 销毁场景视图
                    LogUtils.e("Model3D", "销毁场景视图")
                    sceneView.destroy()
                    // 5. 清理引用
                    filamentInstance = null
                    texture = null
                    loader = null
                    LogUtils.e("Model3D", "销毁完成")

                    finish()
                } catch (e: Exception) {
                    LogUtils.e("Model3D", "资源销毁失败", e)
                }
            }
        })


        initCLick()
        sceneView = SceneView(this, isOpaque = false)
        val contentlay = findViewById<FrameLayout>(R.id.lcontnet)
        contentlay.addView(sceneView)
        // 设置相机初始位置
        sceneView.cameraNode.position = Position(0f, 0f, 4f)
        sceneView.cameraNode.lookAt(Position(0f, 0f, 0f))
        // 创建初始 Manipulator
        sceneView.cameraManipulator = null

        // 2. 手势监听器 (GestureDetector)
        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                    if (scaleDetector.isInProgress) return true

                    autoRotate = false // 用户操作时暂停自动旋转
                    flingVelocityX = 0f // 用户手指拖动时，重置惯性速度
                    flingVelocityY = 0f

                    // --- 核心修改：计算增量并累积 ---
                    val deltaYaw = -dx * 0.5f // 灵敏度系数
                    val deltaPitch = -dy * 0.5f

                    // 可选：限制垂直角度，防止翻转过头 (如果需要无限垂直翻转，删掉这段限制)
                    pitchAccumulator += deltaPitch
                    if (pitchAccumulator > 90f) pitchAccumulator = 90f
                    if (pitchAccumulator < -90f) pitchAccumulator = -90f
                    // 注意：这里为了简单，我们直接用增量四元数，限制逻辑比较复杂，
                    // 如果不需要严格限制，直接用下面的增量即可。

                    // 创建增量四元数
                    val qYaw = Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), deltaYaw)
                    val qPitch = Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), deltaPitch)

                    // 累积：新动作 * 旧状态
                    currentRotation = qYaw * qPitch * currentRotation
                    currentRotation = normalize(currentRotation)

                    return true
                }

                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (scaleDetector.isInProgress) return true

                    autoRotate = false // 惯性滑动时也暂停自动旋转

                    // 记录速度 (除以 1000 是为了把像素/秒 转换成 合适的旋转力度)
                    flingVelocityX = velocityX * flingVelocity
                    flingVelocityY = velocityY * flingVelocity

                    LogUtils.e("Model3D", "onFling---->${flingVelocityX}---->${flingVelocityY}")
                    return true
                }
            }
        )


        scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor = detector.scaleFactor
                    scale *= factor
                    scale = scale.coerceIn(minScale, maxScale)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    super.onScaleEnd(detector)

                }
            }
        )


        sceneView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isTouch = true
                    isStartParallx = true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isTouch = false
                    startParallax()
                }
            }
            autoRotate = false
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }

        // 自动旋转
        sceneView.onFrame = onFrame@{ frameTimeNanos ->
            if (isDestroyed) return@onFrame
            if (autoRotate) {
                val qAuto = Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), 0.5f) // 0.5 是自动旋转速度
                currentRotation = qAuto * currentRotation
            }else{
                if (Math.abs(flingVelocityX) > FLING_THRESHOLD || Math.abs(flingVelocityY) > FLING_THRESHOLD) {
                    // 利用惯性速度生成微小的旋转
                    val qYaw = Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), flingVelocityX)
                    val qPitch = Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), flingVelocityY)
                    currentRotation = qYaw * qPitch * currentRotation
                    currentRotation = normalize(currentRotation)
                    // 速度衰减
                    flingVelocityX *= FLING_DAMPING
                    flingVelocityY *= FLING_DAMPING
                }

                if (!isTouch){
                    if (isStartParallx){
                        parallax?.reSetBaseXY()
                        lastSensorPitch = 0f
                        lastSensorYaw = 0f
                        isStartParallx = false
                    }else{
                        val currentSensorPitch = parallax?.degY?.toFloat().safe() - parallax?.baseDegY?.toFloat().safe()
                        val currentSensorYaw = parallax?.degX?.toFloat().safe() - parallax?.baseDegX?.toFloat().safe()
                        LogUtils.e("重力感应---->currentSensorPitch = ${currentSensorPitch}---->currentSensorYaw = ${currentSensorYaw}")
                        // 2. 计算增量 = 当前角度 - 上一帧角度
                        val deltaPitch = currentSensorPitch - lastSensorPitch
                        val deltaYaw = currentSensorYaw - lastSensorYaw
                        lastSensorPitch = currentSensorPitch
                        lastSensorYaw = currentSensorYaw
                        // 注意：这里的方向可能需要根据实际手机握持方向调整正负号
                        // 通常 X 轴倾斜控制 Pitch，Y 轴倾斜控制 Yaw
                        val qSensorPitch = Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), deltaPitch)
                        val qSensorYaw = Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), deltaYaw)
                        currentRotation = qSensorYaw * qSensorPitch * currentRotation
                        currentRotation = normalize(currentRotation)
                    }


                }
            }
            updateModelTransform()
        }

        // 加载模型
        lifecycleScope.launch {
            if (isDestroyed) return@launch
            val file = copyAssetToCache(this@Model3DActivity, "五角星安卓贴图材质.glb")
            loader = ModelLoader(sceneView.engine, this@Model3DActivity)
            filamentInstance = loader?.createModelInstance(file)
            modelNode = filamentInstance?.let { ModelNode(it) }.apply {
                this?.position = Position(0f, 0f, 0f)
                this?.scale = Scale(scale)
            }

            modelNode?.let {
                sceneView.addChildNode(it)
            }

            Log.d("Model3D", "模型加载成功")
        }
    }

    private fun initCLick() {
        findViewById<Button>(R.id.btn1).setOnClickListener {
            changeTexture("widget_dianzibaji_demo2.webp")

        }

        findViewById<Button>(R.id.btn2).setOnClickListener {
            changeTexture("widget_dianzibaji_demo3.webp")
        }
    }


    private fun changeTexture(fileName: String) {
        sceneView.post {
            if (isDestroyed) return@post
            texture?.let {
                sceneView.engine.destroyTexture(it)
                texture = null
            }
            val bitmap = loadBitmapFromAssets(
                sceneView.context,
                fileName
            )

            texture = Texture.Builder()
                .width(bitmap.width)
                .height(bitmap.height)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.SRGB8_A8)
                .build(sceneView.engine)


            val buffer = ByteBuffer.allocateDirect(bitmap.byteCount)
            buffer.order(ByteOrder.nativeOrder())
            bitmap.copyPixelsToBuffer(buffer)
            buffer.rewind()

            val pbd = PixelBufferDescriptor(
                buffer,
                Texture.Format.RGBA,
                Texture.Type.UBYTE,
                1,
                0,
                0,
                bitmap.width,
                null,
                null
            )
            texture?.setImage(sceneView.engine, 0, pbd)
            filamentInstance?.materialInstances?.forEach { material ->
                Log.d("Model3D", "material = ${material.name}")
                texture?.let {
                    material.setParameter(
                        "baseColorMap",
                        it,
                        TextureSampler()
                    )
                }
            }
        }
    }


    private fun updateModelTransform() {
        LogUtils.e("Model3D", "传感器数据---->${pitch}---->${yaw}")
        val node = modelNode ?: return
        node.transform = Transform(
            position = Position(0f, 0f, 0f),
            quaternion = currentRotation,
            scale = Scale(scale)
        )
    }

    /**
     * ✅ 将 Manipulator 同步到当前相机位置（通过重建）
     */
    fun copyAssetToCache(context: Context, name: String): File {
        val file = File(context.cacheDir, name)
        if (!file.exists()) {
            context.assets.open(name).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
    }

    fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap {
        context.assets.open(fileName).use { input ->
            return BitmapFactory.decodeStream(input)
                ?: throw IllegalArgumentException("Bitmap decode failed: $fileName")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!autoRotate){
            startParallax()
        }
        isDestroyed = false
    }

    override fun onStop() {
        super.onStop()
        stopParallax()
        LogUtils.e("Model3D", "页面暂停")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.e("Model3D", "页面销毁")
    }

    private fun startParallax(){
        if (!isParallxRuning){
            LogUtils.e("Model3D", "开启传感器")
            isParallxRuning = true
            parallax?.setSensitivity(sensitivity.toDouble())
            parallax?.start(SensorManager.SENSOR_DELAY_GAME)
        }

    }

    private fun stopParallax(){
        if (isParallxRuning){
            LogUtils.e("Model3D", "关闭传感器")
            parallax?.stop()
        }
    }
}
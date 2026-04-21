package com.zerone.gldemo

import android.R.attr.bitmap
import android.R.attr.y
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.Engine
import com.google.android.filament.LightManager
import com.google.android.filament.Texture
import com.google.android.filament.Texture.PixelBufferDescriptor
import com.google.android.filament.TextureSampler
import com.google.android.filament.utils.TextureType
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.io.File
import io.github.sceneview.math.Transform
import io.github.sceneview.math.Rotation // 注意导入这个
import io.github.sceneview.texture.ImageTexture
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

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
    private var scale = 0.3f
    private val minScale = 0.2f
    private var maxScale = 1f
    private var modelNode: ModelNode? = null
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleDetector: ScaleGestureDetector
    private var angle = 0f


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)

        sceneView = SceneView(this, isOpaque = false)
        val contentlay = findViewById<FrameLayout>(R.id.lcontnet)
        contentlay.addView(sceneView)
        // 设置相机初始位置
        sceneView.cameraNode.position = Position(0f, 0f, 3f)
        sceneView.cameraNode.lookAt(Position(0f, 0f, 0f))
        // 创建初始 Manipulator
        sceneView.cameraManipulator = null

        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    dx: Float,
                    dy: Float
                ): Boolean {
                    if (scaleDetector.isInProgress) return true
                    yaw -= dx * 0.5f
                    pitch -= dy * 0.5f
                    updateModelTransform()
                    return true
                }
            }
        )


        scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor = detector.scaleFactor
                    scale *= factor
                    scale = scale.coerceIn(minScale, maxScale)
                    updateModelTransform()
                    return true
                }
            }
        )


        sceneView.setOnTouchListener { _, event ->
            autoRotate = false
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }

        // 自动旋转
        sceneView.onFrame = onFrame@{ frameTimeNanos ->
            if (autoRotate) {
                yaw += 0.3f
                updateModelTransform()
            }
        }

        // 添加灯光
        val light = LightNode(
            engine = sceneView.engine,
            type = LightManager.Type.DIRECTIONAL
        ) {
            intensity(100_000f)
        }
        sceneView.addChildNode(light)
        // 加载模型
        lifecycleScope.launch {
            val file = copyAssetToCache(this@Model3DActivity, "DamagedHelmet.glb")
            val loader = ModelLoader(sceneView.engine, this@Model3DActivity)
            val filamentInstance = loader.createModelInstance(file)
            modelNode = ModelNode(filamentInstance).apply {
                position = Position(0f, 0f, 0f)
                scale = Scale(1f)
            }

            modelNode?.let {
                sceneView.addChildNode(it)
                sceneView.post {

                    val bitmap = loadBitmapFromAssets(
                        sceneView.context,
                        "widget_dianzibaji_demo.png"
                    )

                    val texture = Texture.Builder()
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

                    texture.setImage(sceneView.engine, 0, pbd)

                    filamentInstance.materialInstances.forEach { material ->
                        Log.d("Model3D", "material = ${material.name}")
                        material.setParameter(
                            "baseColorMap",
                            texture,
                            TextureSampler()
                        )
                    }
                }

            }

            Log.d("Model3D", "模型加载成功")
        }


    }



    private fun updateModelTransform() {
        val node = modelNode ?: return
        // 1. 创建 Rotation 对象 (欧拉角)
        val rotation = Rotation(pitch, yaw, 0f)
        // 2. 使用库提供的 Transform 构造函数
        // 源码分析：fun Transform(position, rotation, scale) 内部会自动调用 rotation.toQuaternion()
        node.transform = Transform(
            position = Position(0f, 0f, 0f),
            rotation = rotation,
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
}
package com.zerone.gldemo

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.filament.LightManager
import io.github.sceneview.SceneView
import io.github.sceneview.gesture.transform
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.io.File

/**
 * @author dada
 * @date 2026/4/10
 * @desc 网格扭曲
 */
class Model3DActivity : AppCompatActivity() {
    private lateinit var sceneView: SceneView
    private var autoRotate = true
    private var angle = 0f
    private var isManipulatorSynced = false  // ✅ 标记是否已同步过
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
        sceneView.cameraManipulator = createManipulator(0f, 0f, 3f)


        // ✅ 关键：触摸时停止自动旋转，并同步 Manipulator 到当前相机位置
        sceneView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 1. 停止自动旋转
                    autoRotate = false

                    // 2. 只在第一次触摸时同步（避免重复重建导致的手势中断）
                    if (!isManipulatorSynced) {
                        syncManipulatorToCurrentCamera()
                        isManipulatorSynced = true
                    }
                }
            }
            // 返回 false 让 CameraGestureDetector 继续处理手势
            false
        }

        // 自动旋转
        sceneView.onFrame = onFrame@{ frameTimeNanos ->
            if (autoRotate) {
                angle += 0.5f
                val radius = 3f
                val x = kotlin.math.sin(angle * 0.01f) * radius
                val z = kotlin.math.cos(angle * 0.01f) * radius
                sceneView.cameraNode.position = Position(x, 0f, z)
                sceneView.cameraNode.lookAt(Position(0f, 0f, 0f))
            }
        }

//        sceneView.setOnGestureListener(
//            onScale = { detector, e, node ->
//                val manipulator = sceneView.cameraManipulator ?: return
//                val current = manipulator.transform
//                val pos = current.translation
//                val dist = pos.length()
//                val next = dist * detector.scaleFactor
//                val clamped = next.coerceIn(minDistance, maxDistance)
//                val ratio = clamped / dist
//                val newPos = pos * ratio
//                manipulator.transform = manipulator.transform.copy(
//                    translation = newPos
//                )
//            }
//        )

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
            val file = copyAssetToCache(this@Model3DActivity, "安卓2.glb")
            val loader = ModelLoader(sceneView.engine, this@Model3DActivity)
            val modelInstance = loader.createModelInstance(file)
            val node = ModelNode(modelInstance).apply {
                position = Position(0f, 0f, 0f)
                scale = Scale(0.3f)
            }
            sceneView.addChildNode(node)

            Log.d("Model3D", "模型加载成功")
        }
    }

    private fun createManipulator(x: Float, y: Float, z: Float): com.google.android.filament.utils.Manipulator {
        return com.google.android.filament.utils.Manipulator.Builder()
            .orbitHomePosition(x, y, z)
            .targetPosition(0f, 0f, 0f)
            .orbitSpeed(0.005f, 0.005f)
            .zoomSpeed(0.05f)
            .viewport(sceneView.width, sceneView.height)
            .build(com.google.android.filament.utils.Manipulator.Mode.ORBIT)
    }

    /**
     * ✅ 将 Manipulator 同步到当前相机位置（通过重建）
     */
    private fun syncManipulatorToCurrentCamera() {
        val currentPos = sceneView.cameraNode.position

        // 重新创建 Manipulator，使用当前相机的实际位置
        val newManipulator = com.google.android.filament.utils.Manipulator.Builder()
            .orbitHomePosition(currentPos.x, currentPos.y, currentPos.z)
            .targetPosition(0f, 0f, 0f)
            .orbitSpeed(0.005f, 0.005f)
            .zoomSpeed(0.05f)
            .viewport(sceneView.width, sceneView.height)
            .build(com.google.android.filament.utils.Manipulator.Mode.ORBIT)

        sceneView.cameraManipulator = newManipulator

        Log.d("Model3D", "同步相机位置: (${currentPos.x}, ${currentPos.y}, ${currentPos.z})")
    }

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

    fun getDistance(): Float {
        val p = sceneView.cameraNode.position
        return kotlin.math.sqrt(p.x*p.x + p.y*p.y + p.z*p.z)
    }

}
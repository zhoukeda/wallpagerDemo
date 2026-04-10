package com.zerone.gldemo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.zerone.gldemo.renderer.AdvancedRenderer

/**
 * @author dada
 * @date 2026/4/10
 * @desc 光栅效果
 */
class AdvancedActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    private lateinit var renderer: AdvancedRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_advanced)

        val glView = findViewById<GLSurfaceView>(R.id.glView)

        glView.setEGLContextClientVersion(2)

        val bg = BitmapFactory.decodeResource(resources, R.drawable.bg2)
        val fg = BitmapFactory.decodeResource(resources, R.drawable.fg)

        val matrix = android.graphics.Matrix()
        matrix.preScale(1f, -1f)

        val bgNew = Bitmap.createBitmap(bg, 0, 0, bg.width, bg.height, matrix, false)
        val fgNew = Bitmap.createBitmap(fg, 0, 0, fg.width, fg.height, matrix, false)

        renderer = AdvancedRenderer(this, bgNew, fgNew)

        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // -------------------------
        // 按钮控制方向
        // -------------------------
        findViewById<Button>(R.id.btn_left).setOnClickListener {
            renderer.setDirection(-1f, 1f)
            startAnim()
        }

        findViewById<Button>(R.id.btn_top).setOnClickListener {
            renderer.setDirection(0f, 1f)
            startAnim()
        }

        findViewById<Button>(R.id.btn_right).setOnClickListener {
            renderer.setDirection(1f, 1f)
            startAnim()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            renderer.setProgress(100)
        }


        findViewById<Button>(R.id.btn20).setOnClickListener {
            renderer.setProgress(20)
        }

        findViewById<Button>(R.id.btn40).setOnClickListener {
            renderer.setProgress(40)
        }

        findViewById<Button>(R.id.btn60).setOnClickListener {
            renderer.setProgress(60)
        }

        findViewById<Button>(R.id.btn80).setOnClickListener {
            renderer.setProgress(80)
        }




    }

    private fun startAnim() {
        if (isAnimating) return   // 防止重复点击
        isAnimating = true
        handler.post(object : Runnable {
            var p = 100
            override fun run() {
                renderer.setProgress(p)
                p -= 2   // 控制速度（可调）
                if (p >= 0) {
                    handler.postDelayed(this, 16)
                } else {
                    isAnimating = false
                }
            }
        })
    }

}
package com.zerone.gldemo.view

import android.R.attr.height
import android.R.attr.startX
import android.R.attr.width
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY
import android.opengl.GLUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import com.zerone.gldemo.renderer.MeshRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

/**
 * @author dada
 * @date 2026/4/10
 * @desc
 */
class TouchMeshGLView(context: Context) : GLSurfaceView(context),RequestRequesterListener {

    private var renderer:MeshRenderer? = null
    private var startX = 0f
    private var startY = 0f
    private var isHit = false

    init {
        setEGLContextClientVersion(2)
        renderer = MeshRenderer(context,this)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }


    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = e.x / width
        val y = e.y / height

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                isHit = renderer?.hit(startX, startY) == true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isHit) {
                    renderer?.touch(startX, startY, x, y, true)
                    requestRender()
                }
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                renderer?.touch(0f, 0f, 0f, 0f, false)
                if (isHit) {
                    requestRender()
                }
            }
        }
        return true
    }

    override fun updraw() {
        requestRender()
    }

}

interface RequestRequesterListener {
    fun updraw()
}
package com.zerone.gldemo

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.zerone.gldemo.view.TouchMeshGLView

/**
 * @author dada
 * @date 2026/4/10
 * @desc 网格扭曲
 */
class MeshActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TouchMeshGLView(this)

        setContentView(view)

    }
}
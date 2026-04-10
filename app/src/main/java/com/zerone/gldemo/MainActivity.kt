package com.zerone.gldemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlin.jvm.java

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnWangge).setOnClickListener {
            startActivity(Intent(this, MeshActivity::class.java))
        }

        findViewById<Button>(R.id.btnGuangShan).setOnClickListener {
                startActivity(Intent(this, AdvancedActivity::class.java))
        }


    }




}
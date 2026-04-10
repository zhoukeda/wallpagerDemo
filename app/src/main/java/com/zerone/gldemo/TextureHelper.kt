package com.zerone.gldemo

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

/**
 * @author dada
 * @date 2026/4/10
 * @desc
 */
object TextureHelper {

    fun loadTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)

        GLES20.glGenTextures(1, textureIds, 0)

        if (textureIds[0] == 0) {
            throw RuntimeException("Failed to generate texture")
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        bitmap.recycle()

        return textureIds[0]
    }
}
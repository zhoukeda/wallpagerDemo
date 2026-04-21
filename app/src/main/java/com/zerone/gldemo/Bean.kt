package com.zerone.gldemo

import io.github.sceneview.math.Position

data class CameraState(
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var distance: Float = 3f,
    var target: Position = Position(0f, 0f, 0f),
)
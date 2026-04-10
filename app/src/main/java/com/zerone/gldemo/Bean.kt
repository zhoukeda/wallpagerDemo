package com.zerone.gldemo

data class RasterWallpaperBean(
    var wallpaper: String? = "",
    var top: String? = "",
    var bottom: String? = "",
    var left: String? = "",
    var right: String? = "",
)

data class Vertex(var x: Float, var y: Float)
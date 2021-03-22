package com.odogwudev.arcore.model

import android.graphics.Bitmap

data class ResultImage(
    val image: Bitmap,
    val faceInfo: FaceInfo
) {

    data class FaceInfo(
        val width: Float,
        val point: Point
    )

    data class Point(
        val x: Float,
        val y: Float
    )
}
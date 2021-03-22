package com.odogwudev.arcore.util


import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import androidx.core.graphics.ColorUtils
import com.odogwudev.arcore.model.ColorPoint
import timber.log.Timber

object ImageAntiAliasing {

    fun antiAliasing(masking: Bitmap) {
        // detect edges
        val top = ArrayList<ColorPoint>()
        val left = ArrayList<ColorPoint>()
        val right = ArrayList<ColorPoint>()
        val bottom = ArrayList<ColorPoint>()

        // top
        for (x in 0 until masking.width) {
            for (y in 0 until masking.height) {
                val pixel = masking.getPixel(x, y)
                if (isFilled(pixel)) {
                    top.add(ColorPoint(Point(x, y), pixel))
                    break
                }
            }
        }

        // left
        for (y in 0 until masking.height) {
            for (x in 0 until masking.width) {
                val pixel = masking.getPixel(x, y)
                if (isFilled(pixel)) {
                    left.add(ColorPoint(Point(x, y), pixel))
                    break
                }
            }
        }

        // right
        for (y in 0 until masking.height) {
            for (x in masking.width - 1 downTo 0) {
                val pixel = masking.getPixel(x, y)
                if (isFilled(pixel)) {
                    right.add(ColorPoint(Point(x, y), pixel))
                    break
                }
            }
        }

        // bottom
        for (x in 0 until masking.width) {
            for (y in masking.height - 1 downTo 0) {
                val pixel = masking.getPixel(x, y)
                if (isFilled(pixel)) {
                    bottom.add(ColorPoint(Point(x, y), pixel))
                    break
                }
            }
        }

        // set pixels
        setColorForAntialiasing(top, masking, Direction.FROM_TOP)
        setColorForAntialiasing(bottom, masking, Direction.FROM_BOTTOM)
        setColorForAntialiasing(left, masking, Direction.FROM_LEFT)
        setColorForAntialiasing(right, masking, Direction.FROM_RIGHT)
    }

    private fun setColorForAntialiasing(
        colorPoints: ArrayList<ColorPoint>,
        masking: Bitmap,
        direction: Direction
    ) {
        val stepMax = 9
        val stepAlpha = 42

        for (colorPoint in colorPoints) {
            var alpha = Color.alpha(colorPoint.color)//colorPoint.color shr 24 and 0xFF
//            Timber.i("$colorPoint / $alpha ")

            when (direction) {
                Direction.FROM_TOP -> {

                    for (y in colorPoint.point.y downTo 0) {
//                        Timber.i("${colorPoint.point.x} $y / step ${colorPoint.point.y  - y}")

                        ColorUtils.setAlphaComponent(colorPoint.color, alpha).let { color ->
//                            Timber.i("$color ")
                            masking.setPixel(colorPoint.point.x, y, color)
//                            Timber.i("${masking.getPixel(colorPoint.point.x, y)} ")
                        }
                        alpha -= stepAlpha
                        if (y - 1 < 0 || alpha < 0 || colorPoint.point.y - (y - 1) >= stepMax) break
                    }

                }
                Direction.FROM_LEFT -> {
                    for (x in colorPoint.point.x downTo 0) {
//                        Timber.i("$x ${colorPoint.point.y} / step ${colorPoint.point.x  - x}")

                        ColorUtils.setAlphaComponent(colorPoint.color, alpha).let { color ->
                            masking.setPixel(x, colorPoint.point.y, color)
                        }
                        alpha -= stepAlpha
                        if (x - 1 < 0 || alpha < 0 || colorPoint.point.x - (x - 1) >= stepMax) break
                    }
                }
                Direction.FROM_RIGHT -> {
                    for (x in colorPoint.point.x until masking.width) {
//                        Timber.i("$x ${colorPoint.point.y} / step ${colorPoint.point.x  - x}")

                        ColorUtils.setAlphaComponent(colorPoint.color, alpha).let { color ->
                            masking.setPixel(x, colorPoint.point.y, color)
                        }
                        alpha -= stepAlpha
                        if (x + 1 < 0 || alpha < 0 || colorPoint.point.x - (x + 1) >= stepMax) break
                    }
                }
                Direction.FROM_BOTTOM -> {
                    for (y in colorPoint.point.y until masking.height) {
//                        Timber.i("${colorPoint.point.x} $y / step ${colorPoint.point.y  - y}")

                        ColorUtils.setAlphaComponent(colorPoint.color, alpha).let { color ->
                            masking.setPixel(colorPoint.point.x, y, color)
                        }
                        alpha -= stepAlpha
                        if (y + 1 < masking.height || alpha < 0 || colorPoint.point.y - (y + 1) >= stepMax) break
                    }
                }
            }
        }

    }

    private fun isFilled(pixel: Int): Boolean {
        return pixel != Color.TRANSPARENT
    }

    enum class Direction {
        FROM_TOP,
        FROM_LEFT,
        FROM_RIGHT,
        FROM_BOTTOM
    }
}
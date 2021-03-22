package com.odogwudev.arcore.util


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Pair
import android.util.Size
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

object BitmapUtils {

    fun resizeImage(targetSize: Size, selectBitmap: Bitmap): Bitmap {
        Timber.i("resizeImage ${selectBitmap.width} / ${selectBitmap.height}  targetSize $targetSize")
        val targetedSize: Pair<Int, Int> = Pair(targetSize.width, targetSize.height)
        val targetWidth = targetedSize.first
        val maxHeight = targetedSize.second
        // Determine how much to scale down the image
        val scaleFactor = max(
            targetSize.width.toFloat() / targetWidth.toFloat(),
            targetSize.height.toFloat() / maxHeight.toFloat()
        )
        return Bitmap.createScaledBitmap(
            selectBitmap,
            (targetSize.width / scaleFactor).toInt(),
            (targetSize.height / scaleFactor).toInt(),
            true
        )
    }

    private fun scaleBitmapAndKeepRatio(
        targetBmp: Bitmap,
        reqWidthInPixels: Int,
        reqHeightInPixels: Int
    ): Bitmap {
        if (targetBmp.height == reqHeightInPixels && targetBmp.width == reqWidthInPixels) {
            return targetBmp
        }
        val matrix = Matrix()
        matrix.setRectToRect(
            RectF(
                0f, 0f,
                targetBmp.width.toFloat(),
                targetBmp.height.toFloat()
            ),
            RectF(
                0f, 0f,
                reqWidthInPixels.toFloat(),
                reqHeightInPixels.toFloat()
            ),
            Matrix.ScaleToFit.FILL
        )
        return Bitmap.createBitmap(
            targetBmp, 0, 0,
            targetBmp.width,
            targetBmp.height, matrix, true
        )
    }

    fun bitmapToByteBuffer(
        bitmapIn: Bitmap,
        width: Int,
        height: Int,
        mean: Float = 0.0f,
        std: Float = 255.0f
    ): ByteBuffer {
        val bitmap = scaleBitmapAndKeepRatio(bitmapIn, width, height)
        val inputImage = ByteBuffer.allocateDirect(1 * width * height * 3 * 4)  //*** buffer
        inputImage.order(ByteOrder.nativeOrder())
        inputImage.rewind()

        val intValues = IntArray(width * height)
        bitmap/*bitmap*/.getPixels(intValues, 0, width, 0, 0, width, height)
        var pixel = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = intValues[pixel++]

                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                inputImage.putFloat(((value shr 16 and 0xFF) - mean) / std)
                inputImage.putFloat(((value shr 8 and 0xFF) - mean) / std)
                inputImage.putFloat(((value and 0xFF) - mean) / std)
            }
        }

        inputImage.rewind()
        return inputImage
    }

    fun createEmptyBitmap(imageWidth: Int, imageHeight: Int, color: Int = 0): Bitmap {
        val ret = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.RGB_565)
        if (color != 0) {
            ret.eraseColor(color)
        }
        return ret
    }

    fun convertFileToBitmap(path: String): Bitmap? {
        return try {
            val file = File(path)
            return BitmapFactory.decodeFile(file.path)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
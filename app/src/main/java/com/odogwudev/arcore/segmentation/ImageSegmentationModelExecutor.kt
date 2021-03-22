package com.odogwudev.arcore.segmentation


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.core.graphics.ColorUtils
import android.util.Log
import android.util.Size
import com.odogwudev.arcore.util.BitmapUtils
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.collections.HashSet
import kotlin.random.Random
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import timber.log.Timber

class ImageSegmentationModelExecutor(
    context: Context
//  private var useGPU: Boolean = false
) {
    private var gpuDelegate: GpuDelegate? = null

    private val segmentationMasks: ByteBuffer
    private val interpreter: Interpreter

    private var fullTimeExecutionTime = 0L
    private var preprocessTime = 0L
    private var imageSegmentationTime = 0L
    private var maskFlatteningTime = 0L

    private var numberThreads = 4
    private var imageHeight = 257

    init {

        interpreter = getInterpreter(context, imageSegmentationModel, false)
        segmentationMasks = ByteBuffer.allocateDirect(1 * imageSize * imageSize * NUM_CLASSES * 4)
        segmentationMasks.order(ByteOrder.nativeOrder())
    }

    fun execute(data: Bitmap): Bitmap {
        try {
            fullTimeExecutionTime = SystemClock.uptimeMillis()

            preprocessTime = SystemClock.uptimeMillis()

            val scaledBitmap =
                BitmapUtils.resizeImage(
                    Size(imageSize, imageSize),
                    data
                )
            Timber.i("2) ${scaledBitmap.width} ${scaledBitmap.height}")

            val contentArray =
                BitmapUtils.bitmapToByteBuffer(
                    scaledBitmap,
                    imageSize,
                    imageSize,
                    IMAGE_MEAN,
                    IMAGE_STD
                )
            preprocessTime = SystemClock.uptimeMillis() - preprocessTime

            imageSegmentationTime = SystemClock.uptimeMillis()
            interpreter.run(contentArray, segmentationMasks)
            imageSegmentationTime = SystemClock.uptimeMillis() - imageSegmentationTime
            Timber.tag(TAG).d("Time to run the model $imageSegmentationTime")

            maskFlatteningTime = SystemClock.uptimeMillis()
            val (maskImageApplied, maskOnly, itensFound) =
                convertBytebufferMaskToBitmap(
                    segmentationMasks, imageSize, imageSize, scaledBitmap,
                    segmentColors
                )
            maskFlatteningTime = SystemClock.uptimeMillis() - maskFlatteningTime
            Timber.tag(TAG).d("Time to flatten the mask result $maskFlatteningTime")

            fullTimeExecutionTime = SystemClock.uptimeMillis() - fullTimeExecutionTime
            Timber.tag(TAG).d("Total time execution $fullTimeExecutionTime")

            return maskOnly/* ModelExecutionResult(
        maskImageApplied,
        scaledBitmap,
        maskOnly,
        formatExecutionLog(),
        itensFound
      )*/
        } catch (e: Exception) {
            val exceptionLog = "something went wrong: ${e.message}"
            Log.d(TAG, exceptionLog)

            val emptyBitmap =
                BitmapUtils.createEmptyBitmap(
                    imageSize,
                    imageSize
                )
            return emptyBitmap/*ModelExecutionResult(
        emptyBitmap,
        emptyBitmap,
        emptyBitmap,
        exceptionLog,
        HashSet(0)
      )*/
        }
    }

    // base: https://github.com/tensorflow/tensorflow/blob/master/tensorflow/lite/java/demo/app/src/main/java/com/example/android/tflitecamerademo/ImageClassifier.java
    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        fileDescriptor.close()
        return retFile
    }

    @Throws(IOException::class)
    private fun getInterpreter(
        context: Context,
        modelName: String,
        useGpu: Boolean = false
    ): Interpreter {
        val tfliteOptions = Interpreter.Options()
        tfliteOptions.setNumThreads(numberThreads)

        gpuDelegate = null
        if (useGpu) {
            gpuDelegate = GpuDelegate()
            tfliteOptions.addDelegate(gpuDelegate)
        }

        return Interpreter(loadModelFile(context, modelName), tfliteOptions)
    }

    fun close() {
        interpreter.close()
        if (gpuDelegate != null) {
            gpuDelegate!!.close()
        }
    }

    private fun convertBytebufferMaskToBitmap(
        inputBuffer: ByteBuffer,
        imageWidth: Int,
        imageHeight: Int,
        backgroundImage: Bitmap,
        colors: IntArray
    ): Triple<Bitmap, Bitmap, Set<Int>> {
        val conf = Bitmap.Config.ARGB_8888
        val maskBitmap = Bitmap.createBitmap(imageWidth, imageHeight, conf)
        val resultBitmap = Bitmap.createBitmap(imageWidth, imageHeight, conf)
        val scaledBackgroundImage = backgroundImage
//      BitmapUtils.scaleBitmapAndKeepRatio(
//        backgroundImage,
//        imageWidth,
//        imageHeight
//      )
        val mSegmentBits = Array(imageWidth) { IntArray(imageHeight) }
        val itemsFound = HashSet<Int>()
        inputBuffer.rewind()

        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                var maxVal = 0f
                mSegmentBits[x][y] = 0

                for (c in 0 until NUM_CLASSES) {
                    val value = inputBuffer
                        .getFloat((y * imageWidth * NUM_CLASSES + x * NUM_CLASSES + c) * 4)
                    if (c == 0 || value > maxVal) {
                        maxVal = value
                        mSegmentBits[x][y] = c
                    }
                }

                itemsFound.add(mSegmentBits[x][y])
                val newPixelColor = ColorUtils.compositeColors(
                    colors[mSegmentBits[x][y]],
                    scaledBackgroundImage.getPixel(x, y)
                )
                resultBitmap.setPixel(x, y, newPixelColor)
                maskBitmap.setPixel(x, y, colors[mSegmentBits[x][y]])
            }
        }

        return Triple(resultBitmap, maskBitmap, itemsFound)
    }

    companion object {

        private const val TAG = "ImageSegmentationMExec"
        private const val imageSegmentationModel = "deeplabv3_257_mv_gpu.tflite"
        const val imageSize = 257
        const val NUM_CLASSES = 21
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f

        val segmentColors = IntArray(NUM_CLASSES)

        init {

            val random = Random(System.currentTimeMillis())
            segmentColors[0] = Color.TRANSPARENT
            for (i in 1 until NUM_CLASSES) {
                segmentColors[i] = Color.argb(
                    (255),//(128),
                    getRandomRGBInt(
                        random
                    ),
                    getRandomRGBInt(
                        random
                    ),
                    getRandomRGBInt(
                        random
                    )
                )
            }
        }

        private fun getRandomRGBInt(random: Random) = (255 * random.nextFloat()).toInt()
    }
}
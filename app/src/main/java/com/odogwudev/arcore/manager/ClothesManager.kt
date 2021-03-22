package com.odogwudev.arcore.manager


import android.content.Context
import android.graphics.*
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.odogwudev.arcore.R
import com.odogwudev.arcore.model.ResultImage
import com.odogwudev.arcore.segmentation.ImageSegmentationModelExecutor
import com.odogwudev.arcore.util.BitmapUtils
import com.odogwudev.arcore.util.ImageAntiAliasing
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ClothesManager(context: Context) {

    private var imageSegmentationModel: ImageSegmentationModelExecutor =
        ImageSegmentationModelExecutor(context)

    lateinit var clothesBitmap: Bitmap
    private val options =
        FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
            .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .setMinFaceSize(0f)
            .build()

    private val detector =
        FirebaseVision.getInstance().getVisionFaceDetector(options)

    init {
        context.resources.getDrawable(R.drawable.origin_clothes).run {
            clothesBitmap = this.toBitmap() // width, height, config
            Timber.i("clothesBitmap size ${clothesBitmap.width} / ${clothesBitmap.height}")
        }
    }

    suspend fun processImage(): ResultImage? = coroutineScope {
        try {
            detectFace(clothesBitmap).let { faces ->
                // 성공 & 얼굴 정보 얻기
                Timber.i("detectFace result $faces")

                getFaceContourInfo(faces).let { faceInfo ->

                    val result = imageSegmentationModel.execute(clothesBitmap)

                    ImageAntiAliasing.antiAliasing(result)

                    val resizeMask = BitmapUtils.resizeImage(
                        Size(clothesBitmap.width, clothesBitmap.height),
                        result
                    )
                    val removedBg = splitBg(resizeMask, clothesBitmap)

                    val editedImage = editClothesImage(removedBg, faceInfo)

                    Timber.i("end")
                    return@coroutineScope ResultImage(
                        editedImage,
                        ResultImage.FaceInfo(
                            faceInfo.rectWidth,
                            ResultImage.Point(faceInfo.chinBottomPos.px, faceInfo.chinBottomPos.py)
                        )
                    )
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@coroutineScope null
        }
    }

    private fun splitBg(markedImage: Bitmap, originImage: Bitmap): Bitmap {

        val resultingImage = Bitmap.createBitmap(
            originImage.width,
            originImage.height, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(resultingImage)
        canvas.drawARGB(0, 0, 0, 0)

        val paint = Paint().apply {
            isAntiAlias = true // 경계선을 부드럽게 해주는 플래그
            isDither = true
        }

        val rect = Rect(0, 0, originImage.width, originImage.height)
        canvas.drawBitmap(markedImage, rect, rect, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(originImage, rect, rect, paint)

        return resultingImage
    }

    private fun editClothesImage(
        resizedBitmap: Bitmap,
        faceContourInfo: FaceDetectInfo
    ): Bitmap {

        val splitResult = splitImage(resizedBitmap, faceContourInfo)

        return splitResult
    }

    private fun splitImage(targetBitmap: Bitmap, faceContourInfo: FaceDetectInfo): Bitmap {

        return Bitmap.createBitmap(
            targetBitmap,
            0,
            faceContourInfo.chinBottomPos.py.toInt(),
            targetBitmap.width,
            targetBitmap.height - faceContourInfo.chinBottomPos.py.toInt()
        )
    }

    private suspend fun getFaceContourInfo(faces: List<FirebaseVisionFace>) =
        suspendCoroutine<FaceDetectInfo> { cont ->

            for (i in faces.indices) {
                faces[i].let { face ->
                    val x = face.boundingBox.centerX().toFloat()
                    val y = face.boundingBox.centerY().toFloat()

                    val xOffset = face.boundingBox.width() / 2.0f
                    val yOffset = face.boundingBox.height() / 2.0f
                    val left = x - xOffset
                    val top = y - yOffset
                    val right = x + xOffset
                    val bottom = y + yOffset

                    val landmarks = face.getContour(FirebaseVisionFaceContour.FACE)
                    val chinPoint = landmarks.points[18]
                    val chinBottomPos = FaceContourData(chinPoint.x, chinPoint.y)

//                var chinBottomPos = FaceContourData(x, bottom)

                    FaceDetectInfo(left, top, right - left, bottom - top, x, y, chinBottomPos).run {
                        Timber.i("$this")
                        cont.resume(this)
                    }
                }
            }
        }

    private suspend fun detectFace(resizedBitmap: Bitmap) =
        suspendCoroutine<List<FirebaseVisionFace>> { cont ->

            try {

                FirebaseVisionImage.fromBitmap(resizedBitmap).let { firebaseVisionImage ->

                    detector.detectInImage(firebaseVisionImage)
                        .addOnSuccessListener { faces ->

                            if (faces.isNotEmpty()) {
                                cont.resume(faces)
                            } else {
                                cont.resumeWithException(java.lang.Exception("face not found"))
                            }
                        }
                        .addOnFailureListener { e ->
                            // Task failed with an exception
                            e.printStackTrace()
                            cont.resumeWithException(e)
                        }

                }
            } catch (e: Exception) {
                e.printStackTrace()
                cont.resumeWithException(e)
            }
        }

    data class FaceContourData(
        val px: Float,
        val py: Float
    )

    data class FaceDetectInfo(
        val left: Float,
        val top: Float,
        val rectWidth: Float,
        val rectHeight: Float,
        val centerPx: Float,
        val centerPy: Float,
        val chinBottomPos: FaceContourData
    )
}
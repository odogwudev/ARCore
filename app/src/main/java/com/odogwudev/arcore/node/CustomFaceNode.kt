package com.odogwudev.arcore.node

import android.content.Context
import android.widget.ImageView
import com.google.ar.core.AugmentedFace
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.DpToMetersViewSizer
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.AugmentedFaceNode
import com.odogwudev.arcore.R
import com.odogwudev.arcore.model.ResultImage
import timber.log.Timber

class CustomFaceNode(
    augmentedFace: AugmentedFace?,
    val resultImage: ResultImage,
    val context: Context
) : AugmentedFaceNode(augmentedFace) {

    private var chinNode: Node? = null
    private var imageView: ImageView? = null


    companion object {

        const val PIXEL_PER_METER = 750

        enum class FaceRegion {
            CHIN,
            FACE_LEFT,
            FACE_RIGHT
        }
    }

    override fun onActivate() {
        super.onActivate()
        Timber.i("onActivate")
        chinNode = Node()
        chinNode?.setParent(this)

        ViewRenderable.builder()
            .setView(context, R.layout.clothes_view)
            .setVerticalAlignment(ViewRenderable.VerticalAlignment.TOP)
            .setSizer(DpToMetersViewSizer(PIXEL_PER_METER))
//            .setSizer(FixedWidthViewSizer(1f))
            .build()
            .thenAccept { uiRenderable: ViewRenderable ->
                uiRenderable.isShadowCaster = false
                uiRenderable.isShadowReceiver = false
                chinNode?.renderable = uiRenderable

                uiRenderable.view.findViewById<ImageView>(R.id.clothes_image).let { clothes ->
                    imageView = clothes
                    clothes.setOnClickListener {}
                    clothes.setImageBitmap(resultImage.image)
//                    clothes.setImageResource(R.drawable.clothes)
                }

            }
            .exceptionally { throwable: Throwable? ->
                throw AssertionError(
                    "Could not create ui element",
                    throwable
                )
            }


    }

    private fun getRegionPose(region: FaceRegion): Vector3? {
        val buffer = augmentedFace?.meshVertices
        if (buffer != null) {
            return when (region) {
                FaceRegion.CHIN ->
                    Vector3(
                        buffer.get(152 * 3),
                        buffer.get(152 * 3 + 1),
                        buffer.get(152 * 3 + 2)
                    )
                FaceRegion.FACE_LEFT ->
                    Vector3(
                        buffer.get(162 * 3),
                        buffer.get(162 * 3 + 1),
                        buffer.get(162 * 3 + 2)
                    )
                FaceRegion.FACE_RIGHT ->
                    Vector3(
                        buffer.get(389 * 3),
                        buffer.get(389 * 3 + 1),
                        buffer.get(389 * 3 + 2)
                    )
            }
        }
        return null
    }

    override fun onUpdate(frameTime: FrameTime?) {
        super.onUpdate(frameTime)

        augmentedFace?.let { face ->
            getRegionPose(FaceRegion.CHIN)?.let {
//                Log.i("onupdate", "size : $it")
                calculateFaceSize(face)
//                val width = imageView?.width?.toFloat()
//                val height = imageView?.height?.toFloat()
//                Log.i("onupdate", "size : $size")
//                val size = 0.2f
                chinNode?.localPosition = Vector3(it.x, it.y /*- 0.035f*/, it.z/* + 0.015f*/)
//                chinNode?.localScale = Vector3(size, size, size)
//                chinNode?.localRotation = Quaternion.axisAngle(Vector3(0.0f, 0.0f, 1.0f), -10f)
            }
        }
    }

    private fun calculateFaceSize(face: AugmentedFace): Float {

        val left = face.getRegionPose(AugmentedFace.RegionType.FOREHEAD_LEFT)
        val right = face.getRegionPose(AugmentedFace.RegionType.FOREHEAD_RIGHT)
//        Log.i("calculateFaceSize", "left/right $left/$right size ${right.tx() - left.tx()}")

        val realLeft = getRegionPose(FaceRegion.FACE_LEFT)
        val realRight = getRegionPose(FaceRegion.FACE_RIGHT)
        val realSize = (realRight?.x ?: 0f) - (realLeft?.x ?: 0f)
//        Log.i("calculateFaceSize", "realLeft/realRight ${realLeft}/$realRight size $realSize")

        return realSize
    }

}
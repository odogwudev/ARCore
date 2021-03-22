package com.odogwudev.arcore

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedFace
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.Renderable
import com.odogwudev.arcore.manager.ClothesManager
import com.odogwudev.arcore.node.CustomFaceNode
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    var faceNodeMap = HashMap<AugmentedFace, CustomFaceNode>()
    val viewModel = MainViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish()) {
            return
        }
        viewModel.setupClothesManager(ClothesManager(this))
        viewModel.setupClothes()

        setContentView(R.layout.activity_main)
        val arFragment = face_fragment as FaceFragment

//        val arFragment = SampleFragment.newInstance()
//        supportFragmentManager.beginTransaction()
//                .replace(R.id.container, arFragment)
//                .commit()

        val sceneView: ArSceneView = arFragment.arSceneView
        sceneView.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        val scene = sceneView.scene

        scene.addOnUpdateListener {
            sceneView.session
                ?.getAllTrackables(AugmentedFace::class.java)?.let {
                    for (face in it) {
                        if (!faceNodeMap.containsKey(face)) {
                            viewModel.processedImage.value?.let { resultImage ->
                                val faceNode =
                                    CustomFaceNode(face, resultImage, this)
                                faceNode.setParent(scene)
                                faceNodeMap[face] = faceNode
                            }
                        }
                    }
                    // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
                    val iter = faceNodeMap.entries.iterator()
                    while (iter.hasNext()) {
                        val entry = iter.next()
                        val face = entry.key
                        if (face.trackingState == TrackingState.STOPPED) {
                            val faceNode = entry.value
                            faceNode.setParent(null)
                            iter.remove()
                        }
                    }
                }
        }


    }

    private fun checkIsSupportedDeviceOrFinish(): Boolean {
        if (ArCoreApk.getInstance()
                .checkAvailability(this) == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE
        ) {
            Toast.makeText(this, "Augmented Faces requires ARCore", Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        val openGlVersionString = (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.deviceConfigurationInfo
            ?.glEsVersion

        openGlVersionString?.let { s ->
            if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPEN_GL_VERSION) {
                Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show()
                finish()
                return false
            }
        }
        return true
    }

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName

        private const val MIN_OPEN_GL_VERSION = 3.0

    }
}
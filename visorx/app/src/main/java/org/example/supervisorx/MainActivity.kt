package org.example.supervisorx

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceCountText: TextView

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required for VisorX",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val faceDetector by lazy {

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .setLandmarkMode(
                FaceDetectorOptions.LANDMARK_MODE_NONE
            )
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_NONE
            )
            .build()

        FaceDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()
        checkCameraPermission()
    }

    private fun setupUI() {

        previewView = PreviewView(this)

        faceCountText = TextView(this).apply {
            text = "Faces: 0"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            setPadding(30, 20, 30, 20)
        }

        val container = android.widget.FrameLayout(this)

        container.addView(
            previewView,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val textParams =
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )

        textParams.leftMargin = 30
        textParams.topMargin = 50

        container.addView(faceCountText, textParams)

        setContentView(container)
    }

    private fun checkCameraPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider =
                        previewView.surfaceProvider
                }

            val imageAnalyzer =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()
                    .also { analysis ->

                        analysis.setAnalyzer(
                            ContextCompat.getMainExecutor(this)
                        ) { imageProxy ->

                            analyzeImage(imageProxy)
                        }
                    }

            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

            } catch (exception: Exception) {

                Toast.makeText(
                    this,
                    "Camera failed: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {

        val mediaImage = imageProxy.image

        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        faceDetector.process(image)
            .addOnSuccessListener { faces ->

                if (faces.isEmpty()) {

                    runOnUiThread {
                        faceCountText.text = "Faces: 0"
                    }

                    return@addOnSuccessListener
                }

                // Find the largest detected face.
                val primaryFace = faces.maxByOrNull { face ->

                    val width = face.boundingBox.width()
                    val height = face.boundingBox.height()

                    width * height
                }

                val primaryIndex =
                    if (primaryFace != null) {
                        faces.indexOf(primaryFace) + 1
                    } else {
                        -1
                    }

                runOnUiThread {

                    faceCountText.text =
                        "Faces: ${faces.size}\n" +
                                "Primary: Face $primaryIndex"
                }
            }
            .addOnFailureListener {

                runOnUiThread {
                    faceCountText.text = "Detection error"
                }
            }
            .addOnCompleteListener {

                imageProxy.close()
            }
    }

    override fun onDestroy() {
        super.onDestroy()

        faceDetector.close()
    }
}
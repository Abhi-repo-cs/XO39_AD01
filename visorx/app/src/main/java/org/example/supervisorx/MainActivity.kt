package org.example.supervisorx

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
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
import com.google.mlkit.vision.face.Face

import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var faceOverlay: FaceOverlayView
    private lateinit var faceCountText: TextView
    private lateinit var poseText: TextView
    private lateinit var riskText: TextView
    private lateinit var privacyShield: PrivacyShieldView

    // Risk engine
    private val riskEngine = RiskEngine()

    // Tracking state
    private var previousPrimaryRect: Rect? = null
    private var primaryCandidateIndex = -1
    private var primaryCandidateFrames = 0

    // Camera permission
    private val requestPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            }
        }

    // ML Kit face detector
    private val detector by lazy {

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .setMinFaceSize(0.15f)
            .build()

        FaceDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createUI()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            requestPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    // ----------------------------------------------------
    // UI
    // ----------------------------------------------------

    private fun createUI() {

        previewView = PreviewView(this)

        faceOverlay = FaceOverlayView(this)
        privacyShield = PrivacyShieldView(this).apply {
            visibility = android.view.View.GONE
        }

        faceCountText = TextView(this).apply {

            text = "Faces: 0"
            textSize = 20f

            setPadding(
                30,
                30,
                30,
                30
            )
        }

        poseText = TextView(this).apply {

            text = "Pose: Waiting"
            textSize = 18f

            setPadding(
                30,
                30,
                30,
                30
            )
        }

        riskText = TextView(this).apply {

            text = "Risk: LOW"
            textSize = 20f

            setPadding(
                30,
                30,
                30,
                30
            )
        }

        val root = FrameLayout(this)

        // Camera preview
        val faceCountParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        faceCountParams.leftMargin = 30
        faceCountParams.topMargin = 30

        root.addView(
            faceCountText,
            faceCountParams
        )

        // Face boxes
        val poseParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        poseParams.leftMargin = 30
        poseParams.topMargin = 90

        root.addView(
            poseText,
            poseParams
        )

        // Face count
        val riskParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        riskParams.leftMargin = 30
        riskParams.topMargin = 180

        root.addView(
            riskText,
            riskParams
        )


        // Head pose
        root.addView(
            poseText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Risk
        root.addView(
            riskText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            privacyShield,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
    }

    // ----------------------------------------------------
    // CAMERA
    // ----------------------------------------------------

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            // Camera preview
            val preview =
                Preview.Builder()
                    .build()

            preview.setSurfaceProvider(
                previewView.surfaceProvider
            )

            // Image analysis
            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(this)
            ) { imageProxy ->

                analyzeImage(imageProxy)
            }

            // Front camera
            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    // ----------------------------------------------------
    // FACE ANALYSIS
    // ----------------------------------------------------

    private fun analyzeImage(
        imageProxy: ImageProxy
    ) {

        val mediaImage =
            imageProxy.image

        if (mediaImage == null) {

            imageProxy.close()
            return
        }

        val image =
            InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

        detector.process(image)

            .addOnSuccessListener { faces ->

                // Get face rectangles
                val rectangles =
                    faces.map { it.boundingBox }

                // Find primary person
                val primaryIndex =
                    findPrimaryFace(
                        rectangles,
                        mediaImage.width,
                        mediaImage.height
                    )

                // ----------------------------------------
                // PRIMARY HEAD POSE
                // ----------------------------------------

                var poseMessage =
                    "Pose: No primary face"

                if (
                    primaryIndex >= 0 &&
                    primaryIndex < faces.size
                ) {

                    val primaryFace =
                        faces[primaryIndex]

                    val yaw =
                        primaryFace.headEulerAngleY

                    val pitch =
                        primaryFace.headEulerAngleX

                    val facingScreen =
                        abs(yaw) <= 20f &&
                                abs(pitch) <= 25f

                    poseMessage =
                        if (facingScreen) {

                            "Pose: FACING SCREEN\n" +
                                    "Yaw: %.1f°  Pitch: %.1f°"
                                        .format(
                                            yaw,
                                            pitch
                                        )

                        } else {

                            "Pose: LOOKING AWAY\n" +
                                    "Yaw: %.1f°  Pitch: %.1f°"
                                        .format(
                                            yaw,
                                            pitch
                                        )
                        }
                }

                // ----------------------------------------
                // SECONDARY HEAD POSE
                // ----------------------------------------

                var secondaryFacingScreen =
                    false

                faces.forEachIndexed { index, face ->

                    // Ignore primary person
                    if (index != primaryIndex) {

                        val yaw =
                            face.headEulerAngleY

                        val pitch =
                            face.headEulerAngleX

                        val facing =
                            abs(yaw) <= 20f &&
                                    abs(pitch) <= 25f

                        if (facing) {
                            secondaryFacingScreen = true
                        }
                    }
                }

                // ----------------------------------------
                // RISK ENGINE
                // ----------------------------------------

                val riskResult =
                    riskEngine.calculateRisk(

                        faces = rectangles,

                        primaryIndex =
                            primaryIndex,

                        imageWidth =
                            mediaImage.width,

                        imageHeight =
                            mediaImage.height,

                        secondaryFacingScreen =
                            secondaryFacingScreen
                    )

                // ----------------------------------------
                // UPDATE UI
                // ----------------------------------------

                runOnUiThread {

                    faceCountText.text =
                        "Faces: ${faces.size}"

                    poseText.text =
                        poseMessage

                    riskText.text =
                        "Risk: ${riskResult.level} " +
                                "(${(riskResult.score * 100).toInt()}%)"

                    faceOverlay.updateFaces(
                        rectangles,
                        primaryIndex
                    )
                }
            }

            .addOnFailureListener {
                // Ignore individual frame failures.
            }

            .addOnCompleteListener {

                // VERY IMPORTANT:
                // Always close ImageProxy.
                imageProxy.close()
            }
    }

    // ----------------------------------------------------
    // PRIMARY FACE TRACKING
    // ----------------------------------------------------

    private fun findPrimaryFace(
        faces: List<Rect>,
        imageWidth: Int,
        imageHeight: Int
    ): Int {

        if (faces.isEmpty()) {

            primaryCandidateIndex = -1
            primaryCandidateFrames = 0
            previousPrimaryRect = null

            return -1
        }

        // No primary yet
        if (previousPrimaryRect == null) {

            val index =
                calculateBestPrimary(
                    faces,
                    imageWidth,
                    imageHeight
                )

            previousPrimaryRect =
                faces[index]

            return index
        }

        val previousRect =
            previousPrimaryRect!!

        var bestTrackingIndex = -1
        var bestIoU = 0f

        // Compare current faces
        // with previous primary
        faces.forEachIndexed { index, face ->

            val iou =
                calculateIoU(
                    previousRect,
                    face
                )

            if (iou > bestIoU) {

                bestIoU = iou
                bestTrackingIndex = index
            }
        }

        // Previous primary is still visible
        if (
            bestTrackingIndex >= 0 &&
            bestIoU > 0.25f
        ) {

            primaryCandidateIndex = -1
            primaryCandidateFrames = 0

            previousPrimaryRect =
                faces[bestTrackingIndex]

            return bestTrackingIndex
        }

        // Previous primary disappeared
        val newCandidate =
            calculateBestPrimary(
                faces,
                imageWidth,
                imageHeight
            )

        if (
            primaryCandidateIndex ==
            newCandidate
        ) {

            primaryCandidateFrames++

        } else {

            primaryCandidateIndex =
                newCandidate

            primaryCandidateFrames = 1
        }

        // Wait for 5 frames
        // before switching primary
        if (primaryCandidateFrames >= 5) {

            previousPrimaryRect =
                faces[newCandidate]

            primaryCandidateIndex = -1
            primaryCandidateFrames = 0

            return newCandidate
        }

        // Do not immediately switch
        return -1
    }

    // ----------------------------------------------------
    // PRIMARY FACE SCORING
    // ----------------------------------------------------

    private fun calculateBestPrimary(
        faces: List<Rect>,
        imageWidth: Int,
        imageHeight: Int
    ): Int {

        if (faces.size == 1) {
            return 0
        }

        val largestArea =
            faces.maxOf { face ->

                face.width().toFloat() *
                        face.height().toFloat()
            }

        val centerX =
            imageWidth / 2f

        val centerY =
            imageHeight / 2f

        val maxDistance =
            sqrt(
                centerX * centerX +
                        centerY * centerY
            )

        var bestIndex = 0
        var bestScore = -1f

        faces.forEachIndexed { index, face ->

            // ----------------------------------------
            // SIZE SCORE
            // ----------------------------------------

            val area =
                face.width().toFloat() *
                        face.height().toFloat()

            val sizeScore =
                (area / largestArea)
                    .coerceIn(0f, 1f)

            // ----------------------------------------
            // CENTER SCORE
            // ----------------------------------------

            val faceCenterX =
                face.centerX().toFloat()

            val faceCenterY =
                face.centerY().toFloat()

            val distance =
                sqrt(

                    (faceCenterX - centerX) *
                            (faceCenterX - centerX) +

                            (faceCenterY - centerY) *
                            (faceCenterY - centerY)
                )

            val centerScore =
                (1f - distance / maxDistance)
                    .coerceIn(0f, 1f)

            // ----------------------------------------
            // FINAL SCORE
            // ----------------------------------------

            val score =
                0.70f * sizeScore +
                        0.30f * centerScore

            if (score > bestScore) {

                bestScore = score
                bestIndex = index
            }
        }

        return bestIndex
    }

    // ----------------------------------------------------
    // IOU TRACKING
    // ----------------------------------------------------

    private fun calculateIoU(
        a: Rect,
        b: Rect
    ): Float {

        val left =
            maxOf(
                a.left,
                b.left
            )

        val top =
            maxOf(
                a.top,
                b.top
            )

        val right =
            minOf(
                a.right,
                b.right
            )

        val bottom =
            minOf(
                a.bottom,
                b.bottom
            )

        val intersectionWidth =
            maxOf(
                0,
                right - left
            )

        val intersectionHeight =
            maxOf(
                0,
                bottom - top
            )

        val intersectionArea =
            intersectionWidth *
                    intersectionHeight

        if (intersectionArea == 0) {
            return 0f
        }

        val areaA =
            a.width() *
                    a.height()

        val areaB =
            b.width() *
                    b.height()

        val unionArea =
            areaA +
                    areaB -
                    intersectionArea

        return intersectionArea.toFloat() /
                unionArea.toFloat()
    }

    // ----------------------------------------------------
    // CLEANUP
    // ----------------------------------------------------

    override fun onDestroy() {

        detector.close()

        super.onDestroy()
    }
}
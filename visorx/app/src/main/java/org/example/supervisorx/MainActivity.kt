package org.example.supervisorx

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    private lateinit var previewView: PreviewView
    private lateinit var faceOverlay: FaceOverlayView

    private lateinit var faceCountText: TextView
    private lateinit var poseText: TextView
    private lateinit var riskText: TextView

    private lateinit var riskIndicator: RiskIndicatorView
    private lateinit var riskDetails: RiskDetailsView
    private lateinit var privacyShield: PrivacyShieldView

    // ---------------------------------------------------------
    // RISK ENGINE
    // ---------------------------------------------------------

    private val riskEngine = RiskEngine()

    // ---------------------------------------------------------
    // PRIMARY FACE TRACKING
    // ---------------------------------------------------------

    private var previousPrimaryRect: Rect? = null

    private var primaryCandidateIndex = -1
    private var primaryCandidateFrames = 0

    // ---------------------------------------------------------
    // PRIVACY SHIELD STATE
    // ---------------------------------------------------------

    private var shieldActive = false

    private var highRiskStartTime = 0L
    private var lowRiskStartTime = 0L

    private val highRiskDelay = 500L
    private val recoveryDelay = 800L

    // ---------------------------------------------------------
    // CAMERA PERMISSION
    // ---------------------------------------------------------

    private val requestPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            }
        }

    // ---------------------------------------------------------
    // ML KIT FACE DETECTOR
    // ---------------------------------------------------------

    private val detector by lazy {

        val options =
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_FAST
                )
                .setMinFaceSize(0.15f)
                .build()

        FaceDetection.getClient(options)
    }

    // =========================================================
    // ACTIVITY
    // =========================================================

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
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

    // =========================================================
    // CREATE UI
    // =========================================================

    private fun createUI() {

        // -----------------------------------------------------
        // CAMERA PREVIEW
        // -----------------------------------------------------

        previewView =
            PreviewView(this).apply {

                scaleType =
                    PreviewView.ScaleType.FILL_CENTER
            }

        // -----------------------------------------------------
        // FACE OVERLAY
        // -----------------------------------------------------

        faceOverlay =
            FaceOverlayView(this)

        // -----------------------------------------------------
        // PRIVACY SHIELD
        // -----------------------------------------------------

        privacyShield =
            PrivacyShieldView(this).apply {

                visibility =
                    View.GONE
            }

        // -----------------------------------------------------
        // STATUS TEXT
        // -----------------------------------------------------

        faceCountText =
            TextView(this).apply {

                text = "Faces: 0"
                textSize = 18f

                setPadding(
                    20,
                    10,
                    20,
                    5
                )
            }

        poseText =
            TextView(this).apply {

                text = "Pose: Waiting"
                textSize = 16f

                setPadding(
                    20,
                    5,
                    20,
                    5
                )
            }

        riskText =
            TextView(this).apply {

                text = "Risk: LOW"
                textSize = 18f

                setPadding(
                    20,
                    5,
                    20,
                    10
                )
            }

        // -----------------------------------------------------
        // RISK INDICATOR
        // -----------------------------------------------------

        riskIndicator =
            RiskIndicatorView(this)

        // -----------------------------------------------------
        // IMPORTANT:
        // INITIALIZE riskDetails BEFORE USING IT
        // -----------------------------------------------------

        riskDetails =
            RiskDetailsView(this)

        // -----------------------------------------------------
        // STATUS PANEL
        // -----------------------------------------------------

        val statusPanel =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                addView(
                    faceCountText
                )

                addView(
                    poseText
                )

                addView(
                    riskText
                )
            }

        // -----------------------------------------------------
        // ROOT FRAME
        // -----------------------------------------------------

        val root =
            FrameLayout(this)

        // -----------------------------------------------------
        // CAMERA
        // -----------------------------------------------------

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // -----------------------------------------------------
        // FACE OVERLAY
        // -----------------------------------------------------

        root.addView(
            faceOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // -----------------------------------------------------
        // STATUS PANEL
        // -----------------------------------------------------

        val statusParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )

        statusParams.leftMargin = 15
        statusParams.topMargin = 15

        root.addView(
            statusPanel,
            statusParams
        )

        // -----------------------------------------------------
        // RISK INDICATOR
        // -----------------------------------------------------

        val riskIndicatorParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                120
            )

        riskIndicatorParams.leftMargin = 0
        riskIndicatorParams.rightMargin = 0
        riskIndicatorParams.topMargin = 270

        root.addView(
            riskIndicator,
            riskIndicatorParams
        )

        // -----------------------------------------------------
        // RISK DETAILS
        // -----------------------------------------------------

        val riskDetailsParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                280
            )

        riskDetailsParams.leftMargin = 0
        riskDetailsParams.rightMargin = 0
        riskDetailsParams.topMargin = 400

        root.addView(
            riskDetails,
            riskDetailsParams
        )

        // -----------------------------------------------------
        // PRIVACY SHIELD
        // -----------------------------------------------------

        root.addView(
            privacyShield,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // -----------------------------------------------------
        // SET SCREEN
        // -----------------------------------------------------

        setContentView(root)
    }

    // =========================================================
    // START CAMERA
    // =========================================================

    private fun startCamera() {

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val cameraProvider =
                cameraProviderFuture.get()

            // -------------------------------------------------
            // PREVIEW
            // -------------------------------------------------

            val preview =
                Preview.Builder()
                    .build()

            preview.setSurfaceProvider(
                previewView.surfaceProvider
            )

            // -------------------------------------------------
            // IMAGE ANALYSIS
            // -------------------------------------------------

            val imageAnalysis =
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(
                        ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                    )
                    .build()

            imageAnalysis.setAnalyzer(
                ContextCompat.getMainExecutor(this)
            ) { imageProxy ->

                analyzeImage(
                    imageProxy
                )
            }

            // -------------------------------------------------
            // FRONT CAMERA
            // -------------------------------------------------

            val cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            // -------------------------------------------------
            // BIND CAMERA
            // -------------------------------------------------

            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    // =========================================================
    // ANALYZE CAMERA IMAGE
    // =========================================================

    private fun analyzeImage(
        imageProxy: ImageProxy
    ) {

        val mediaImage =
            imageProxy.image

        if (mediaImage == null) {

            imageProxy.close()
            return
        }

        // -----------------------------------------------------
        // ROTATION
        // -----------------------------------------------------

        val rotation =
            imageProxy.imageInfo.rotationDegrees

        // -----------------------------------------------------
        // ML KIT INPUT
        // -----------------------------------------------------

        val image =
            InputImage.fromMediaImage(
                mediaImage,
                rotation
            )

        // -----------------------------------------------------
        // FACE DETECTION
        // -----------------------------------------------------

        detector.process(image)

            .addOnSuccessListener { faces ->

                // -------------------------------------------------
                // FACE RECTANGLES
                // -------------------------------------------------

                val rectangles =
                    faces.map {

                        it.boundingBox
                    }

                // -------------------------------------------------
                // ROTATED IMAGE DIMENSIONS
                // -------------------------------------------------

                val rotatedWidth: Int
                val rotatedHeight: Int

                if (
                    rotation == 90 ||
                    rotation == 270
                ) {

                    rotatedWidth =
                        mediaImage.height

                    rotatedHeight =
                        mediaImage.width

                } else {

                    rotatedWidth =
                        mediaImage.width

                    rotatedHeight =
                        mediaImage.height
                }

                // -------------------------------------------------
                // FIND PRIMARY FACE
                // -------------------------------------------------

                val primaryIndex =
                    findPrimaryFace(
                        rectangles,
                        rotatedWidth,
                        rotatedHeight
                    )

                // -------------------------------------------------
                // PRIMARY FACE POSE
                // -------------------------------------------------

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

                // -------------------------------------------------
                // SECONDARY FACE POSE
                // -------------------------------------------------

                var secondaryFacingScreen =
                    false

                faces.forEachIndexed { index, face ->

                    if (
                        index != primaryIndex
                    ) {

                        val yaw =
                            face.headEulerAngleY

                        val pitch =
                            face.headEulerAngleX

                        val facing =
                            abs(yaw) <= 20f &&
                                    abs(pitch) <= 25f

                        if (facing) {

                            secondaryFacingScreen =
                                true
                        }
                    }
                }

                // -------------------------------------------------
                // CALCULATE RISK
                // -------------------------------------------------

                val riskResult =
                    riskEngine.calculateRisk(

                        faces =
                            rectangles,

                        primaryIndex =
                            primaryIndex,

                        imageWidth =
                            rotatedWidth,

                        imageHeight =
                            rotatedHeight,

                        secondaryFacingScreen =
                            secondaryFacingScreen
                    )

                // -------------------------------------------------
                // UPDATE UI
                // -------------------------------------------------

                runOnUiThread {

                    // ---------------------------------------------
                    // FACE COUNT
                    // ---------------------------------------------

                    faceCountText.text =
                        "Faces: ${faces.size}"

                    // ---------------------------------------------
                    // POSE
                    // ---------------------------------------------

                    poseText.text =
                        poseMessage

                    // ---------------------------------------------
                    // RISK TEXT
                    // ---------------------------------------------

                    riskText.text =
                        "Risk: ${riskResult.level} " +
                                "(${(riskResult.score * 100).toInt()}%)"

                    // ---------------------------------------------
                    // RISK INDICATOR
                    // ---------------------------------------------

                    riskIndicator.updateRisk(
                        riskResult.score,
                        riskResult.level
                    )

                    // ---------------------------------------------
                    // RISK DETAILS
                    // ---------------------------------------------

                    riskDetails.updateSignals(

                        presenceScore =
                            riskResult.presenceScore,

                        proximityScore =
                            riskResult.proximityScore,

                        poseScore =
                            riskResult.poseScore,

                        persistenceScore =
                            riskResult.persistenceScore
                    )

                    // ---------------------------------------------
                    // PRIVACY SHIELD
                    // ---------------------------------------------

                    updatePrivacyShield(
                        riskResult.level
                    )

                    // ---------------------------------------------
                    // FACE OVERLAY
                    // ---------------------------------------------

                    faceOverlay.updateFaces(

                        newFaces =
                            rectangles,

                        newPrimaryIndex =
                            primaryIndex,

                        sourceWidth =
                            mediaImage.width,

                        sourceHeight =
                            mediaImage.height,

                        rotation =
                            rotation,

                        shouldMirror =
                            true
                    )
                }
            }

            // -----------------------------------------------------
            // FAILURE
            // -----------------------------------------------------

            .addOnFailureListener {
                // Ignore individual frame failures.
            }

            // -----------------------------------------------------
            // CLOSE IMAGE
            // -----------------------------------------------------

            .addOnCompleteListener {

                imageProxy.close()
            }
    }

    // =========================================================
    // PRIVACY SHIELD
    // =========================================================

    private fun updatePrivacyShield(
        riskLevel: String
    ) {

        val currentTime =
            System.currentTimeMillis()

        // -----------------------------------------------------
        // HIGH RISK
        // -----------------------------------------------------

        if (riskLevel == "HIGH") {

            lowRiskStartTime = 0L

            if (
                highRiskStartTime == 0L
            ) {

                highRiskStartTime =
                    currentTime
            }

            val highRiskDuration =
                currentTime -
                        highRiskStartTime

            // ---------------------------------------------
            // REQUIRE HIGH RISK FOR 500 ms
            // ---------------------------------------------

            if (
                highRiskDuration >=
                highRiskDelay
            ) {

                shieldActive =
                    true
            }

        } else {

            // -------------------------------------------------
            // NOT HIGH RISK
            // -------------------------------------------------

            highRiskStartTime =
                0L

            if (shieldActive) {

                if (
                    lowRiskStartTime == 0L
                ) {

                    lowRiskStartTime =
                        currentTime
                }

                val lowRiskDuration =
                    currentTime -
                            lowRiskStartTime

                // ---------------------------------------------
                // REQUIRE LOW/MEDIUM FOR 800 ms
                // BEFORE REMOVING SHIELD
                // ---------------------------------------------

                if (
                    lowRiskDuration >=
                    recoveryDelay
                ) {

                    shieldActive =
                        false

                    lowRiskStartTime =
                        0L
                }

            } else {

                lowRiskStartTime =
                    0L
            }
        }

        // -----------------------------------------------------
        // SHOW / HIDE SHIELD
        // -----------------------------------------------------

        privacyShield.visibility =

            if (shieldActive) {

                View.VISIBLE

            } else {

                View.GONE
            }
    }

    // =========================================================
    // PRIMARY FACE TRACKING
    // =========================================================

    private fun findPrimaryFace(
        faces: List<Rect>,
        imageWidth: Int,
        imageHeight: Int
    ): Int {

        // -----------------------------------------------------
        // NO FACES
        // -----------------------------------------------------

        if (faces.isEmpty()) {

            primaryCandidateIndex =
                -1

            primaryCandidateFrames =
                0

            previousPrimaryRect =
                null

            return -1
        }

        // -----------------------------------------------------
        // FIRST DETECTION
        // -----------------------------------------------------

        if (
            previousPrimaryRect == null
        ) {

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

        // -----------------------------------------------------
        // TRY TO TRACK PREVIOUS PRIMARY
        // -----------------------------------------------------

        val previousRect =
            previousPrimaryRect!!

        var bestTrackingIndex =
            -1

        var bestIoU =
            0f

        faces.forEachIndexed { index, face ->

            val iou =
                calculateIoU(
                    previousRect,
                    face
                )

            if (
                iou > bestIoU
            ) {

                bestIoU =
                    iou

                bestTrackingIndex =
                    index
            }
        }

        // -----------------------------------------------------
        // TRACK SUCCESS
        // -----------------------------------------------------

        if (
            bestTrackingIndex >= 0 &&
            bestIoU > 0.25f
        ) {

            primaryCandidateIndex =
                -1

            primaryCandidateFrames =
                0

            previousPrimaryRect =
                faces[bestTrackingIndex]

            return bestTrackingIndex
        }

        // -----------------------------------------------------
        // TRACKING LOST
        // -----------------------------------------------------

        val newCandidate =
            calculateBestPrimary(
                faces,
                imageWidth,
                imageHeight
            )

        // -----------------------------------------------------
        // CHECK CANDIDATE PERSISTENCE
        // -----------------------------------------------------

        if (
            primaryCandidateIndex ==
            newCandidate
        ) {

            primaryCandidateFrames++

        } else {

            primaryCandidateIndex =
                newCandidate

            primaryCandidateFrames =
                1
        }

        // -----------------------------------------------------
        // CONFIRM NEW PRIMARY AFTER 5 FRAMES
        // -----------------------------------------------------

        if (
            primaryCandidateFrames >= 5
        ) {

            previousPrimaryRect =
                faces[newCandidate]

            primaryCandidateIndex =
                -1

            primaryCandidateFrames =
                0

            return newCandidate
        }

        // -----------------------------------------------------
        // TEMPORARILY NO PRIMARY
        // -----------------------------------------------------

        return -1
    }

    // =========================================================
    // CALCULATE BEST PRIMARY FACE
    // =========================================================

    private fun calculateBestPrimary(
        faces: List<Rect>,
        imageWidth: Int,
        imageHeight: Int
    ): Int {

        // -----------------------------------------------------
        // ONLY ONE FACE
        // -----------------------------------------------------

        if (
            faces.size == 1
        ) {

            return 0
        }

        // -----------------------------------------------------
        // LARGEST FACE
        // -----------------------------------------------------

        val largestArea =
            faces.maxOf { face ->

                face.width().toFloat() *
                        face.height().toFloat()
            }

        // -----------------------------------------------------
        // SCREEN CENTER
        // -----------------------------------------------------

        val centerX =
            imageWidth / 2f

        val centerY =
            imageHeight / 2f

        val maxDistance =
            sqrt(

                centerX * centerX +
                        centerY * centerY
            )

        var bestIndex =
            0

        var bestScore =
            -1f

        // -----------------------------------------------------
        // SCORE EACH FACE
        // -----------------------------------------------------

        faces.forEachIndexed { index, face ->

            // ---------------------------------------------
            // SIZE SCORE
            // ---------------------------------------------

            val area =
                face.width().toFloat() *
                        face.height().toFloat()

            val sizeScore =
                (area / largestArea)
                    .coerceIn(
                        0f,
                        1f
                    )

            // ---------------------------------------------
            // CENTER SCORE
            // ---------------------------------------------

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
                (
                        1f -
                                distance /
                                maxDistance
                        )
                    .coerceIn(
                        0f,
                        1f
                    )

            // ---------------------------------------------
            // FINAL PRIMARY SCORE
            // ---------------------------------------------

            val score =
                0.70f * sizeScore +
                        0.30f * centerScore

            if (
                score > bestScore
            ) {

                bestScore =
                    score

                bestIndex =
                    index
            }
        }

        return bestIndex
    }

    // =========================================================
    // INTERSECTION OVER UNION
    // =========================================================

    private fun calculateIoU(
        a: Rect,
        b: Rect
    ): Float {

        // -----------------------------------------------------
        // INTERSECTION
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // NO INTERSECTION
        // -----------------------------------------------------

        if (
            intersectionArea == 0
        ) {

            return 0f
        }

        // -----------------------------------------------------
        // AREAS
        // -----------------------------------------------------

        val areaA =
            a.width() *
                    a.height()

        val areaB =
            b.width() *
                    b.height()

        // -----------------------------------------------------
        // UNION
        // -----------------------------------------------------

        val unionArea =
            areaA +
                    areaB -
                    intersectionArea

        // -----------------------------------------------------
        // IOU
        // -----------------------------------------------------

        return intersectionArea.toFloat() /
                unionArea.toFloat()
    }

    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        detector.close()

        riskEngine.reset()

        super.onDestroy()
    }
}
package org.example.supervisorx

import android.graphics.Rect
import kotlin.math.abs

data class RiskResult(
    val score: Float,
    val level: String,
    val secondaryDetected: Boolean,

    // Individual risk signals
    val presenceScore: Float,
    val proximityScore: Float,
    val poseScore: Float,
    val persistenceScore: Float
)

class RiskEngine {

    // Number of consecutive frames in which a secondary
    // person has been detected.
    private var secondaryFrames = 0

    // Number of frames without a secondary person.
    private var noSecondaryFrames = 0

    /**
     * Calculate the current privacy risk.
     *
     * Signals:
     * 1. Presence    - Is another person detected?
     * 2. Proximity   - How large is the secondary face compared
     *                  with the primary face?
     * 3. Pose        - Is the secondary person facing the screen?
     * 4. Persistence - Has the secondary person remained present?
     */
    fun calculateRisk(
        faces: List<Rect>,
        primaryIndex: Int,
        imageWidth: Int,
        imageHeight: Int,
        secondaryFacingScreen: Boolean
    ): RiskResult {

        // ---------------------------------------------------------
        // NO PRIMARY FACE / NO SECONDARY PERSON
        // ---------------------------------------------------------

        if (
            faces.size <= 1 ||
            primaryIndex < 0 ||
            primaryIndex >= faces.size
        ) {

            noSecondaryFrames++

            // Slowly forget previous detection.
            if (noSecondaryFrames >= 10) {
                secondaryFrames = 0
            }

            return RiskResult(
                score = 0f,
                level = "LOW",
                secondaryDetected = false,
                presenceScore = 0f,
                proximityScore = 0f,
                poseScore = 0f,
                persistenceScore =
                    (secondaryFrames / 30f)
                        .coerceIn(0f, 1f)
            )
        }

        // ---------------------------------------------------------
        // SECONDARY PERSON DETECTED
        // ---------------------------------------------------------

        secondaryFrames++
        noSecondaryFrames = 0

        val secondaryFaces =
            faces.filterIndexed { index, _ ->
                index != primaryIndex
            }

        if (secondaryFaces.isEmpty()) {

            return RiskResult(
                score = 0f,
                level = "LOW",
                secondaryDetected = false,
                presenceScore = 0f,
                proximityScore = 0f,
                poseScore = 0f,
                persistenceScore =
                    (secondaryFrames / 30f)
                        .coerceIn(0f, 1f)
            )
        }

        // ---------------------------------------------------------
        // SIGNAL 1: PRESENCE
        // ---------------------------------------------------------

        val presenceScore = 1f

        // ---------------------------------------------------------
        // SIGNAL 2: PROXIMITY
        // ---------------------------------------------------------

        val primaryFace = faces[primaryIndex]

        val primaryArea =
            primaryFace.width().toFloat() *
                    primaryFace.height().toFloat()

        if (primaryArea <= 0f) {

            return RiskResult(
                score = 0f,
                level = "LOW",
                secondaryDetected = true,
                presenceScore = presenceScore,
                proximityScore = 0f,
                poseScore = 0f,
                persistenceScore =
                    (secondaryFrames / 30f)
                        .coerceIn(0f, 1f)
            )
        }

        val largestSecondaryArea =
            secondaryFaces.maxOf { face ->

                face.width().toFloat() *
                        face.height().toFloat()
            }

        /*
         * A larger secondary face usually means the person
         * is closer to the phone.
         *
         * Example:
         *
         * Secondary face = 50% of primary face
         * proximityScore = 0.5
         *
         * Secondary face >= primary face
         * proximityScore = 1.0
         */
        val proximityScore =
            (
                    largestSecondaryArea /
                            primaryArea
                    )
                .coerceIn(0f, 1f)

        // ---------------------------------------------------------
        // SIGNAL 3: HEAD POSE
        // ---------------------------------------------------------

        /*
         * ML Kit tells us whether the secondary face is
         * approximately facing the screen.
         *
         * Unlike the old implementation, we DON'T count
         * head pose twice as both pose and gaze.
         */
        val poseScore =
            if (secondaryFacingScreen) {
                1f
            } else {
                0f
            }

        // ---------------------------------------------------------
        // SIGNAL 4: PERSISTENCE
        // ---------------------------------------------------------

        /*
         * Gradually increase the persistence contribution.
         *
         * 1 frame  -> ~0.03
         * 15 frames -> 0.50
         * 30+ frames -> 1.00
         */
        val persistenceScore =
            (secondaryFrames / 30f)
                .coerceIn(0f, 1f)

        // ---------------------------------------------------------
        // FINAL RISK SCORE
        // ---------------------------------------------------------

        /*
         * Weights:
         *
         * Presence    = 20%
         * Proximity   = 30%
         * Pose        = 35%
         * Persistence = 15%
         *
         * Pose and proximity are the strongest indicators.
         */
        val score =
            0.20f * presenceScore +
                    0.30f * proximityScore +
                    0.35f * poseScore +
                    0.15f * persistenceScore

        // ---------------------------------------------------------
        // RISK LEVEL
        // ---------------------------------------------------------

        val level =
            when {
                score >= 0.65f -> "HIGH"
                score >= 0.35f -> "MEDIUM"
                else -> "LOW"
            }

        return RiskResult(
            score = score,
            level = level,
            secondaryDetected = true,
            presenceScore = presenceScore,
            proximityScore = proximityScore,
            poseScore = poseScore,
            persistenceScore = persistenceScore
        )
    }

    /**
     * Reset the engine.
     *
     * Useful when the camera is stopped/restarted.
     */
    fun reset() {
        secondaryFrames = 0
        noSecondaryFrames = 0
    }
}
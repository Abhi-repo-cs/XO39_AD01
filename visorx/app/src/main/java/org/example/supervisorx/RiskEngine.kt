package org.example.supervisorx

import android.graphics.Rect
import kotlin.math.abs

data class RiskResult(
    val score: Float,
    val level: String,
    val secondaryDetected: Boolean
)

class RiskEngine {

    private var secondaryFrames = 0

    fun calculateRisk(
        faces: List<Rect>,
        primaryIndex: Int,
        imageWidth: Int,
        imageHeight: Int,
        secondaryFacingScreen: Boolean
    ): RiskResult {

        // No secondary person.
        if (faces.size <= 1 || primaryIndex < 0) {

            secondaryFrames = 0

            return RiskResult(
                score = 0f,
                level = "LOW",
                secondaryDetected = false
            )
        }

        // A secondary person exists.
        secondaryFrames++

        val secondaryFaces =
            faces.filterIndexed { index, _ ->
                index != primaryIndex
            }

        if (secondaryFaces.isEmpty()) {
            return RiskResult(
                score = 0f,
                level = "LOW",
                secondaryDetected = false
            )
        }

        /*
         * 1. PRESENCE
         *
         * Another person is visible.
         */
        val presenceScore = 1f

        /*
         * 2. PROXIMITY
         *
         * Larger secondary face =
         * person is probably closer.
         */
        val primaryFace = faces[primaryIndex]

        val primaryArea =
            primaryFace.width().toFloat() *
                    primaryFace.height().toFloat()

        val largestSecondaryArea =
            secondaryFaces.maxOf { face ->
                face.width().toFloat() *
                        face.height().toFloat()
            }

        val proximityScore =
            (largestSecondaryArea / primaryArea)
                .coerceIn(0f, 1f)

        /*
         * 3. HEAD POSE
         *
         * For the MVP, head orientation is used
         * as an approximation for attention toward
         * the screen.
         */
        val poseScore =
            if (secondaryFacingScreen) {
                1f
            } else {
                0f
            }

        /*
         * 4. GAZE PROXY
         *
         * We don't have eye-gaze detection yet.
         * Head orientation acts as the gaze proxy.
         */
        val gazeScore = poseScore

        /*
         * 5. PERSISTENCE
         *
         * Risk increases when the secondary
         * person remains visible.
         */
        val persistenceScore =
            (secondaryFrames / 30f)
                .coerceIn(0f, 1f)

        /*
         * Multi-signal risk score.
         */
        val score =
            0.15f * presenceScore +
                    0.25f * proximityScore +
                    0.30f * poseScore +
                    0.15f * gazeScore +
                    0.15f * persistenceScore

        val level =
            when {
                score >= 0.65f -> "HIGH"
                score >= 0.35f -> "MEDIUM"
                else -> "LOW"
            }

        return RiskResult(
            score = score,
            level = level,
            secondaryDetected = true
        )
    }
}
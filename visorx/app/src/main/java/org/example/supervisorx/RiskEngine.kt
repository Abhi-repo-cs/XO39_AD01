package org.example.supervisorx

import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class RiskEngine {

    companion object {
        private const val RISK_THRESHOLD = 0.65f
        private const val WATCH_THRESHOLD = 0.35f

        private const val PERSISTENCE_TARGET_MS = 1200L
        private const val MAX_TRACK_DISTANCE = 0.18f

        private const val CLOSE_FACE_AREA = 0.055f
        private const val MIN_FACE_AREA = 0.008f

        private const val PRESENCE_WEIGHT = 0.10f
        private const val PROXIMITY_WEIGHT = 0.30f
        private const val POSE_WEIGHT = 0.30f
        private const val PERSISTENCE_WEIGHT = 0.20f
        private const val STABILITY_WEIGHT = 0.10f

        private const val RISE_SMOOTHING = 0.35f
        private const val FALL_SMOOTHING = 0.20f
    }

    /*
     * Keep this as an enum internally, but expose the
     * result level as a String so the existing MainActivity
     * does not need to be rewritten.
     */
    enum class RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    data class RiskResult(
        val score: Float,
        val level: String,
        val presenceScore: Float,
        val proximityScore: Float,
        val poseScore: Float,
        val persistenceScore: Float,
        val stabilityScore: Float
    )

    private var observerStartTime = 0L
    private var previousObserverRect: RectF? = null
    private var smoothedScore = 0f

    /**
     * Calculates the current shoulder-surfing threat.
     *
     * MainActivity supplies Android Rect objects.
     * They are converted internally to RectF.
     */
    fun calculateRisk(
        faces: List<Rect>,
        primaryIndex: Int,
        imageWidth: Int,
        imageHeight: Int,
        secondaryFacingScreen: Boolean
    ): RiskResult {

        if (
            faces.size < 2 ||
            primaryIndex !in faces.indices ||
            imageWidth <= 0 ||
            imageHeight <= 0
        ) {
            resetObserver()

            val score = smoothScore(0f)

            return createResult(
                score = score,
                presenceScore = 0f,
                proximityScore = 0f,
                poseScore = 0f,
                persistenceScore = 0f,
                stabilityScore = 0f
            )
        }

        val primary = normalizeRect(
            RectF(faces[primaryIndex]),
            imageWidth,
            imageHeight
        )

        val observer = findBestObserver(
            faces = faces,
            primaryIndex = primaryIndex,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )

        if (observer == null) {
            resetObserver()

            val score = smoothScore(0f)

            return createResult(
                score = score,
                presenceScore = 0f,
                proximityScore = 0f,
                poseScore = 0f,
                persistenceScore = 0f,
                stabilityScore = 0f
            )
        }

        val now = System.currentTimeMillis()

        /*
         * IMPORTANT:
         * Calculate stability BEFORE updating the previous
         * observer rectangle.
         */
        val stabilityScore = calculateStability(observer)

        /*
         * Persistence is calculated after movement.
         */
        val persistenceScore = updatePersistence(
            observer = observer,
            now = now
        )

        val presenceScore = 1f

        val proximityScore = calculateProximity(
            observer = observer,
            primary = primary
        )

        val poseScore =
            if (secondaryFacingScreen) 1f else 0f

        /*
         * Weighted threat score.
         */
        val rawScore =
            PRESENCE_WEIGHT * presenceScore +
                    PROXIMITY_WEIGHT * proximityScore +
                    POSE_WEIGHT * poseScore +
                    PERSISTENCE_WEIGHT * persistenceScore +
                    STABILITY_WEIGHT * stabilityScore

        val finalScore = smoothScore(rawScore)

        return createResult(
            score = finalScore,
            presenceScore = presenceScore,
            proximityScore = proximityScore,
            poseScore = poseScore,
            persistenceScore = persistenceScore,
            stabilityScore = stabilityScore
        )
    }

    /**
     * Finds the strongest non-primary face.
     */
    private fun findBestObserver(
        faces: List<Rect>,
        primaryIndex: Int,
        imageWidth: Int,
        imageHeight: Int
    ): RectF? {

        val primary = normalizeRect(
            RectF(faces[primaryIndex]),
            imageWidth,
            imageHeight
        )

        var bestFace: RectF? = null
        var bestScore = -1f

        for (index in faces.indices) {

            if (index == primaryIndex) {
                continue
            }

            val candidate = normalizeRect(
                RectF(faces[index]),
                imageWidth,
                imageHeight
            )

            val area =
                candidate.width() * candidate.height()

            /*
             * Ignore extremely tiny faces.
             */
            if (area < MIN_FACE_AREA) {
                continue
            }

            val proximity = calculateProximity(
                observer = candidate,
                primary = primary
            )

            val centerDistance = centerDistance(
                candidate,
                primary
            )

            val candidateScore =
                proximity * 0.65f +
                        (
                                1f -
                                        min(
                                            centerDistance / 0.75f,
                                            1f
                                        )
                                ) * 0.35f

            if (candidateScore > bestScore) {
                bestScore = candidateScore
                bestFace = candidate
            }
        }

        return bestFace
    }

    /**
     * Estimates how close the observer is.
     */
    private fun calculateProximity(
        observer: RectF,
        primary: RectF
    ): Float {

        val observerArea =
            observer.width() * observer.height()

        val primaryArea =
            primary.width() * primary.height()

        if (
            observerArea <= 0f ||
            primaryArea <= 0f
        ) {
            return 0f
        }

        val sizeScore =
            (
                    (observerArea - MIN_FACE_AREA) /
                            (CLOSE_FACE_AREA - MIN_FACE_AREA)
                    )
                .coerceIn(0f, 1f)

        val relativeSize =
            (
                    observerArea /
                            max(primaryArea, 0.0001f)
                    )
                .coerceIn(0f, 1f)

        return (
                sizeScore * 0.65f +
                        relativeSize * 0.35f
                ).coerceIn(0f, 1f)
    }

    /**
     * Measures observer movement between frames.
     *
     * Low movement = high stability.
     * High movement = low stability.
     */
    private fun calculateStability(
        currentObserver: RectF
    ): Float {

        val previous = previousObserverRect

        if (previous == null) {
            return 0.5f
        }

        val distance = centerDistance(
            currentObserver,
            previous
        )

        val stability =
            1f -
                    (distance / MAX_TRACK_DISTANCE)

        return stability.coerceIn(0f, 1f)
    }

    /**
     * Tracks how long the observer remains present.
     */
    private fun updatePersistence(
        observer: RectF,
        now: Long
    ): Float {

        val previous = previousObserverRect

        if (observerStartTime == 0L) {
            observerStartTime = now
        }

        /*
         * If the face jumps significantly,
         * treat it as a new observer candidate.
         */
        if (previous != null) {

            val movement = centerDistance(
                observer,
                previous
            )

            if (
                movement >
                MAX_TRACK_DISTANCE * 2f
            ) {
                observerStartTime = now
            }
        }

        /*
         * Store AFTER stability has been calculated.
         */
        previousObserverRect =
            RectF(observer)

        val duration =
            (now - observerStartTime)
                .coerceAtLeast(0L)

        return (
                duration.toFloat() /
                        PERSISTENCE_TARGET_MS.toFloat()
                ).coerceIn(0f, 1f)
    }

    /**
     * Converts pixel coordinates into normalized
     * 0..1 coordinates.
     */
    private fun normalizeRect(
        rect: RectF,
        imageWidth: Int,
        imageHeight: Int
    ): RectF {

        return RectF(
            (rect.left / imageWidth)
                .coerceIn(0f, 1f),

            (rect.top / imageHeight)
                .coerceIn(0f, 1f),

            (rect.right / imageWidth)
                .coerceIn(0f, 1f),

            (rect.bottom / imageHeight)
                .coerceIn(0f, 1f)
        )
    }

    /**
     * Calculates normalized distance between centers.
     */
    private fun centerDistance(
        a: RectF,
        b: RectF
    ): Float {

        val ax = a.centerX()
        val ay = a.centerY()

        val bx = b.centerX()
        val by = b.centerY()

        return hypot(
            (ax - bx).toDouble(),
            (ay - by).toDouble()
        ).toFloat()
    }

    /**
     * Smooths risk changes to avoid UI flickering.
     */
    private fun smoothScore(
        newScore: Float
    ): Float {

        val target =
            newScore.coerceIn(0f, 1f)

        val smoothing =
            if (target > smoothedScore) {
                RISE_SMOOTHING
            } else {
                FALL_SMOOTHING
            }

        smoothedScore +=
            (target - smoothedScore) *
                    smoothing

        return smoothedScore.coerceIn(
            0f,
            1f
        )
    }

    private fun getRiskLevel(
        score: Float
    ): RiskLevel {

        return when {
            score >= RISK_THRESHOLD ->
                RiskLevel.HIGH

            score >= WATCH_THRESHOLD ->
                RiskLevel.MEDIUM

            else ->
                RiskLevel.LOW
        }
    }

    /**
     * Creates the result expected by MainActivity.
     */
    private fun createResult(
        score: Float,
        presenceScore: Float,
        proximityScore: Float,
        poseScore: Float,
        persistenceScore: Float,
        stabilityScore: Float
    ): RiskResult {

        return RiskResult(
            score = score,
            level = getRiskLevel(score).name,
            presenceScore = presenceScore,
            proximityScore = proximityScore,
            poseScore = poseScore,
            persistenceScore = persistenceScore,
            stabilityScore = stabilityScore
        )
    }

    private fun resetObserver() {
        observerStartTime = 0L
        previousObserverRect = null
    }

    fun reset() {
        resetObserver()
        smoothedScore = 0f
    }
}
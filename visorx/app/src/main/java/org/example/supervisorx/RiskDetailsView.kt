package org.example.supervisorx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class RiskDetailsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var presence = 0f
    private var proximity = 0f
    private var pose = 0f
    private var persistence = 0f

    private val backgroundPaint = Paint().apply {
        color = Color.argb(210, 0, 0, 0)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
    }

    private val percentagePaint = Paint().apply {
        color = Color.WHITE
        textSize = 22f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    private val barBackgroundPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val barPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun updateSignals(
        presenceScore: Float,
        proximityScore: Float,
        poseScore: Float,
        persistenceScore: Float
    ) {
        presence = presenceScore.coerceIn(0f, 1f)
        proximity = proximityScore.coerceIn(0f, 1f)
        pose = poseScore.coerceIn(0f, 1f)
        persistence = persistenceScore.coerceIn(0f, 1f)

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 20f
        val panelHeight = 270f

        val panel = RectF(
            padding,
            0f,
            width - padding,
            panelHeight
        )

        canvas.drawRoundRect(
            panel,
            20f,
            20f,
            backgroundPaint
        )

        drawSignal(
            canvas,
            "Presence",
            presence,
            45f
        )

        drawSignal(
            canvas,
            "Proximity",
            proximity,
            100f
        )

        drawSignal(
            canvas,
            "Pose",
            pose,
            155f
        )

        drawSignal(
            canvas,
            "Persistence",
            persistence,
            210f
        )
    }

    private fun drawSignal(
        canvas: Canvas,
        label: String,
        value: Float,
        y: Float
    ) {

        val left = 40f
        val right = width - 40f

        canvas.drawText(
            label,
            left,
            y,
            textPaint
        )

        canvas.drawText(
            "${(value * 100).toInt()}%",
            right,
            y,
            percentagePaint
        )

        val barTop = y + 12f
        val barBottom = y + 30f

        val barLeft = left
        val barRight = right

        canvas.drawRoundRect(
            RectF(
                barLeft,
                barTop,
                barRight,
                barBottom
            ),
            10f,
            10f,
            barBackgroundPaint
        )

        barPaint.color = when {
            value >= 0.65f -> Color.RED
            value >= 0.35f -> Color.YELLOW
            else -> Color.GREEN
        }

        val progressRight =
            barLeft +
                    (barRight - barLeft) *
                    value

        canvas.drawRoundRect(
            RectF(
                barLeft,
                barTop,
                progressRight.coerceAtLeast(
                    barLeft + 1f
                ),
                barBottom
            ),
            10f,
            10f,
            barPaint
        )
    }
}
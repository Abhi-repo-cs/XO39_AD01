package org.example.supervisorx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class RiskIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var riskScore = 0f
    private var riskLevel = "LOW"

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 0, 0, 0)
        isAntiAlias = true
    }

    private val progressBackgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.DKGRAY
        isAntiAlias = true
    }

    private val progressPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val percentagePaint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        isAntiAlias = true
    }

    fun updateRisk(
        score: Float,
        level: String
    ) {
        riskScore = score.coerceIn(0f, 1f)
        riskLevel = level.uppercase()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 20f

        val panelWidth =
            width.toFloat() - padding * 2

        val panelHeight = 115f

        val panelRect = RectF(
            padding,
            0f,
            width.toFloat() - padding,
            panelHeight
        )

        // Background panel
        canvas.drawRoundRect(
            panelRect,
            20f,
            20f,
            backgroundPaint
        )

        // Risk colour
        progressPaint.color =
            when (riskLevel) {
                "HIGH" -> Color.RED
                "MEDIUM" -> Color.YELLOW
                else -> Color.GREEN
            }

        // Risk text
        canvas.drawText(
            "● $riskLevel RISK",
            padding + 20f,
            40f,
            textPaint
        )

        // Progress bar background
        val barLeft =
            padding + 20f

        val barRight =
            width.toFloat() - padding - 20f

        val barTop = 60f
        val barBottom = 82f

        val progressBackground =
            RectF(
                barLeft,
                barTop,
                barRight,
                barBottom
            )

        canvas.drawRoundRect(
            progressBackground,
            10f,
            10f,
            progressBackgroundPaint
        )

        // Progress amount
        val progressRight =
            barLeft +
                    (barRight - barLeft) *
                    riskScore

        val progressRect =
            RectF(
                barLeft,
                barTop,
                max(
                    barLeft + 1f,
                    progressRight
                ),
                barBottom
            )

        canvas.drawRoundRect(
            progressRect,
            10f,
            10f,
            progressPaint
        )

        // Percentage
        val percentage =
            (riskScore * 100).toInt()

        canvas.drawText(
            "$percentage%",
            barLeft,
            105f,
            percentagePaint
        )
    }
}
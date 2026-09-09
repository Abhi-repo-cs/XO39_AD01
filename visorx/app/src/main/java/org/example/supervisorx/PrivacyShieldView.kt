package org.example.supervisorx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class PrivacyShieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val backgroundPaint = Paint().apply {
        color = Color.argb(245, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val titlePaint = Paint().apply {
        color = Color.WHITE
        textSize = 52f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val messagePaint = Paint().apply {
        color = Color.LTGRAY
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Cover the entire screen.
        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            backgroundPaint
        )

        val centerX = width / 2f
        val centerY = height / 2f

        canvas.drawText(
            "🔒 PRIVACY SHIELD",
            centerX,
            centerY - 30f,
            titlePaint
        )

        canvas.drawText(
            "Screen protected",
            centerX,
            centerY + 30f,
            messagePaint
        )

        canvas.drawText(
            "Potential shoulder-surfing detected",
            centerX,
            centerY + 75f,
            messagePaint
        )
    }
}
package org.example.supervisorx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        textSize = 40f
        isAntiAlias = true
    }

    private var faces: List<Rect> = emptyList()
    private var primaryIndex: Int = -1

    fun updateFaces(
        newFaces: List<Rect>,
        newPrimaryIndex: Int
    ) {
        faces = newFaces
        primaryIndex = newPrimaryIndex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        faces.forEachIndexed { index, rect ->

            if (index == primaryIndex) {
                paint.setColor(android.graphics.Color.GREEN)
                textPaint.setColor(android.graphics.Color.GREEN)
            } else {
                paint.setColor(android.graphics.Color.RED)
                textPaint.setColor(android.graphics.Color.RED)
            }

            canvas.drawRect(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat(),
                paint
            )

            val label =
                if (index == primaryIndex) {
                    "PRIMARY"
                } else {
                    "SECONDARY"
                }

            canvas.drawText(
                label,
                rect.left.toFloat(),
                (rect.top - 10).toFloat(),
                textPaint
            )
        }
    }
}
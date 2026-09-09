package org.example.supervisorx

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val primaryPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val secondaryPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val primaryTextPaint = Paint().apply {
        color = Color.GREEN
        textSize = 32f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val secondaryTextPaint = Paint().apply {
        color = Color.RED
        textSize = 32f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private var faces: List<Rect> = emptyList()

    private var primaryIndex = -1

    private var imageWidth = 1
    private var imageHeight = 1

    private var rotationDegrees = 0

    private var mirror = true

    fun updateFaces(
        newFaces: List<Rect>,
        newPrimaryIndex: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        rotation: Int,
        shouldMirror: Boolean
    ) {
        faces = newFaces

        primaryIndex = newPrimaryIndex

        imageWidth = sourceWidth.coerceAtLeast(1)

        imageHeight = sourceHeight.coerceAtLeast(1)

        rotationDegrees = rotation

        mirror = shouldMirror

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        if (faces.isEmpty()) {
            return
        }

        /*
         * After rotation, determine the dimensions
         * of the displayed camera image.
         */
        val rotatedWidth: Float
        val rotatedHeight: Float

        if (
            rotationDegrees == 90 ||
            rotationDegrees == 270
        ) {
            rotatedWidth = imageHeight.toFloat()
            rotatedHeight = imageWidth.toFloat()
        } else {
            rotatedWidth = imageWidth.toFloat()
            rotatedHeight = imageHeight.toFloat()
        }

        /*
         * PreviewView uses FILL_CENTER.
         *
         * This means the image fills the view and
         * some edges may be cropped.
         */
        val scaleX =
            width.toFloat() / rotatedWidth

        val scaleY =
            height.toFloat() / rotatedHeight

        val scale =
            maxOf(scaleX, scaleY)

        val displayedWidth =
            rotatedWidth * scale

        val displayedHeight =
            rotatedHeight * scale

        val offsetX =
            (width - displayedWidth) / 2f

        val offsetY =
            (height - displayedHeight) / 2f

        faces.forEachIndexed { index, originalRect ->

            /*
             * Convert ML Kit's original camera
             * coordinates into rotated coordinates.
             */
            val rotatedRect =
                rotateRect(
                    originalRect,
                    rotationDegrees
                )

            var left =
                rotatedRect.left * scale + offsetX

            var right =
                rotatedRect.right * scale + offsetX

            val top =
                rotatedRect.top * scale + offsetY

            val bottom =
                rotatedRect.bottom * scale + offsetY

            /*
             * Front camera preview is mirrored.
             */
            if (mirror) {

                val newLeft =
                    width - right

                val newRight =
                    width - left

                left = newLeft

                right = newRight
            }

            val paint =
                if (index == primaryIndex) {
                    primaryPaint
                } else {
                    secondaryPaint
                }

            val textPaint =
                if (index == primaryIndex) {
                    primaryTextPaint
                } else {
                    secondaryTextPaint
                }

            val label =
                if (index == primaryIndex) {
                    "PRIMARY"
                } else {
                    "SECONDARY"
                }

            canvas.drawRect(
                left,
                top,
                right,
                bottom,
                paint
            )

            /*
             * Put the label above the face when possible.
             */
            val labelY =
                if (top > 40f) {
                    top - 10f
                } else {
                    bottom + 35f
                }

            canvas.drawText(
                label,
                left,
                labelY,
                textPaint
            )
        }
    }

    /*
     * Rotate a rectangle from the original camera
     * coordinate system into the rotated image.
     */
    private fun rotateRect(
        rect: Rect,
        rotation: Int
    ): Rect {

        return when (rotation) {

            90 -> {

                Rect(
                    rect.top,
                    imageWidth - rect.right,
                    rect.bottom,
                    imageWidth - rect.left
                )
            }

            180 -> {

                Rect(
                    imageWidth - rect.right,
                    imageHeight - rect.bottom,
                    imageWidth - rect.left,
                    imageHeight - rect.top
                )
            }

            270 -> {

                Rect(
                    imageHeight - rect.bottom,
                    rect.left,
                    imageHeight - rect.top,
                    rect.right
                )
            }

            else -> {

                Rect(rect)
            }
        }
    }
}
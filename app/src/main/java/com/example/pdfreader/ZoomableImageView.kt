package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var mode = NONE
    private var scale = 1f
    private var lastScaleFactor = 0f
    private var startX = 0f
    private var startY = 0f
    private var translateX = 0f
    private var translateY = 0f
    private var previousTranslateX = 0f
    private var previousTranslateY = 0f
    private var maxZoom = 10f // Increased max zoom for better pinch zoom range
    private var minZoom = 1f
    private var redundantXSpace = 0f
    private var redundantYSpace = 0f
    private var width = 0f
    private var height = 0f
    private var saveScale = 1f
    private var right = 0f
    private var bottom = 0f
    private var originalBitmapWidth = 0f
    private var originalBitmapHeight = 0f
    private var isSingleTapToggle = false

    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)
    private var gestureDetector: GestureDetector? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var onZoomChangeListener: ((Boolean) -> Unit)? = null
    private var onSingleTapToggle: ((Boolean) -> Unit)? = null

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }

    init {
        super.setClickable(true)
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        imageMatrix = matrix
        scaleType = ScaleType.MATRIX
    }

    fun setOnZoomChangeListener(listener: (Boolean) -> Unit) {
        onZoomChangeListener = listener
    }

    fun setOnSingleTapToggleListener(listener: (Boolean) -> Unit) {
        onSingleTapToggle = listener
    }

    // Add method to reset zoom
    fun resetZoom() {
        saveScale = minZoom
        matrix.setScale(scale, scale)
        matrix.postTranslate(redundantXSpace, redundantYSpace)
        imageMatrix = matrix
        onZoomChangeListener?.invoke(false)
        invalidate()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mode = ZOOM
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor

            val origScale = saveScale
            saveScale *= scaleFactor

            // Clamp the scale within bounds
            if (saveScale > maxZoom) {
                saveScale = maxZoom
                return true
            }
            if (saveScale < minZoom) {
                saveScale = minZoom
                return true
            }

            val focusX = detector.focusX
            val focusY = detector.focusY

            matrix.postScale(scaleFactor, scaleFactor, focusX, focusY)

            fixTranslation()

            // Sync drag state after scaling to prevent snapping
            matrix.getValues(matrixValues)
            translateX = matrixValues[Matrix.MTRANS_X]
            translateY = matrixValues[Matrix.MTRANS_Y]
            previousTranslateX = translateX
            previousTranslateY = translateY

            onZoomChangeListener?.invoke(saveScale > minZoom)
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            super.onSingleTapConfirmed(e)
            isSingleTapToggle = !isSingleTapToggle
            onSingleTapToggle?.invoke(isSingleTapToggle) // Return the toggled value
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val origScale = saveScale
            val targetScale = if (saveScale == minZoom) 2f else minZoom
            val deltaScale = targetScale / origScale

            matrix.postScale(deltaScale, deltaScale, e.x, e.y)

            saveScale = targetScale
            fixTranslation()

            // Sync drag state to matrix
            matrix.getValues(matrixValues)
            translateX = matrixValues[Matrix.MTRANS_X]
            translateY = matrixValues[Matrix.MTRANS_Y]
            previousTranslateX = translateX
            previousTranslateY = translateY

            onZoomChangeListener?.invoke(saveScale > minZoom)
            return true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector?.onTouchEvent(event)
        gestureDetector?.onTouchEvent(event)

        val currentX = event.x
        val currentY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastScaleFactor = 0f
                startX = currentX
                startY = currentY
                previousTranslateX = translateX
                previousTranslateY = translateY
                mode = DRAG
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && saveScale > minZoom) { // Only allow drag when zoomed in
                    translateX = previousTranslateX + currentX - startX
                    translateY = previousTranslateY + currentY - startY

                    matrix.getValues(matrixValues)
                    val x = matrixValues[Matrix.MTRANS_X]
                    val y = matrixValues[Matrix.MTRANS_Y]

                    val deltaX = translateX - x
                    val deltaY = translateY - y

                    matrix.postTranslate(deltaX, deltaY)
                    fixTranslation()
                }
            }

            MotionEvent.ACTION_UP -> {
                mode = NONE
                onZoomChangeListener?.invoke(saveScale > minZoom)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                mode = NONE
            }
        }

        imageMatrix = matrix
        invalidate()
        return true
    }

    private fun fixTranslation() {
        matrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]

        val fixTransX = getFixTranslation(transX, width, originalBitmapWidth * saveScale)
        val fixTransY = getFixTranslation(transY, height, originalBitmapHeight * saveScale)

        if (fixTransX != 0f || fixTransY != 0f) {
            matrix.postTranslate(fixTransX, fixTransY)
        }
    }

    private fun getFixTranslation(trans: Float, viewSize: Float, contentSize: Float): Float {
        val minTrans: Float
        val maxTrans: Float

        if (contentSize <= viewSize) {
            // Content smaller than view - center it
            minTrans = (viewSize - contentSize) / 2
            maxTrans = (viewSize - contentSize) / 2
        } else {
            // Content larger than view - allow full range
            minTrans = viewSize - contentSize
            maxTrans = 0f
        }

        return when {
            trans < minTrans -> -trans + minTrans
            trans > maxTrans -> -trans + maxTrans
            else -> 0f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        width = MeasureSpec.getSize(widthMeasureSpec).toFloat()
        height = MeasureSpec.getSize(heightMeasureSpec).toFloat()

        val drawable = drawable
        if (drawable != null && drawable.intrinsicWidth != 0 && drawable.intrinsicHeight != 0) {
            val bmWidth = drawable.intrinsicWidth.toFloat()
            val bmHeight = drawable.intrinsicHeight.toFloat()

            originalBitmapWidth = bmWidth
            originalBitmapHeight = bmHeight

            val scaleX = width / bmWidth
            val scaleY = height / bmHeight

            scale = min(scaleX, scaleY)
            matrix.setScale(scale, scale)

            saveScale = 1f

            redundantYSpace = height - (scale * bmHeight)
            redundantXSpace = width - (scale * bmWidth)
            redundantYSpace /= 2
            redundantXSpace /= 2

            matrix.postTranslate(redundantXSpace, redundantYSpace)

            originalBitmapWidth *= scale
            originalBitmapHeight *= scale

            right = width * saveScale - width - (2 * redundantXSpace * saveScale)
            bottom = height * saveScale - height - (2 * redundantYSpace * saveScale)

            imageMatrix = matrix

            onZoomChangeListener?.invoke(scale > minZoom)
        }
    }
}
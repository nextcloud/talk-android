/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Implements
 * - pinch-to-zoom
 * - double-tap-to-zoom (cycling min -> medium -> max scale)
 * - pan-when-zoomed with edge-aware
 * - parent-swipe handoff
 * - fling-after-pan momentum
 * - tap-on-photo vs tap-outside-photo distinction.
 */
class ZoomableImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {

    fun interface OnPhotoTapListener {
        // x, y are fractions (0f...1f) of the photo's currently displayed bounds.
        fun onPhotoTap(view: ImageView, x: Float, y: Float)
    }

    fun interface OnOutsidePhotoTapListener {
        fun onOutsidePhotoTap(view: ImageView)
    }

    var maximumScale: Float = DEFAULT_MAX_SCALE
        set(value) {
            checkZoomLevels(MINIMUM_SCALE, mediumScale, value)
            field = value
        }

    var mediumScale: Float = DEFAULT_MEDIUM_SCALE
        set(value) {
            checkZoomLevels(MINIMUM_SCALE, value, maximumScale)
            field = value
        }

    private var onPhotoTapListener: OnPhotoTapListener? = null
    private var onOutsidePhotoTapListener: OnOutsidePhotoTapListener? = null

    private val baseMatrix = Matrix()
    private val suppMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val displayRect = RectF()
    private val matrixValues = FloatArray(MATRIX_VALUES_SIZE)

    private var horizontalEdge = EDGE_BOTH
    private var verticalEdge = EDGE_BOTH
    private var blockParentIntercept = false

    private var activePointerId = INVALID_POINTER_ID
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private val maximumFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat()
    private var velocityTracker: VelocityTracker? = null
    private val flingRunnable = FlingRunnable()

    private val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                if (scaleFactor.isNaN() || scaleFactor.isInfinite()) return false
                if (scaleFactor >= 0f && (currentScale() < maximumScale || scaleFactor < 1f)) {
                    suppMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                    checkAndDisplayMatrix()
                }
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(context, GestureDetector.SimpleOnGestureListener()).apply {
        setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val rect = getDisplayRect() ?: return false
                val x = e.x
                val y = e.y
                return if (rect.contains(x, y)) {
                    onPhotoTapListener?.onPhotoTap(
                        this@ZoomableImageView,
                        (x - rect.left) / rect.width(),
                        (y - rect.top) / rect.height()
                    )
                    true
                } else {
                    onOutsidePhotoTapListener?.onOutsidePhotoTap(this@ZoomableImageView)
                    false
                }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val scale = currentScale()
                val target = when {
                    scale < mediumScale -> mediumScale
                    scale < maximumScale -> maximumScale
                    else -> MINIMUM_SCALE
                }
                animateScale(scale, target, e.x, e.y)
                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent) = false
        })
    }

    init {
        super.setScaleType(ScaleType.MATRIX)
    }

    fun setOnPhotoTapListener(listener: OnPhotoTapListener?) {
        onPhotoTapListener = listener
    }

    fun setOnOutsidePhotoTapListener(listener: OnOutsidePhotoTapListener?) {
        onOutsidePhotoTapListener = listener
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateBaseMatrix(drawable)
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        if (changed) updateBaseMatrix(drawable)
        return changed
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) return false
        var handled = false
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
            flingRunnable.cancel()
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            handled = handleTouchEnd()
        }

        val wasScaling = scaleGestureDetector.isInProgress
        val wasDragging = isDragging
        scaleGestureDetector.onTouchEvent(event)
        handled = processDrag(event) || handled
        blockParentIntercept = !wasScaling &&
            !scaleGestureDetector.isInProgress &&
            !wasDragging &&
            !isDragging

        if (gestureDetector.onTouchEvent(event)) handled = true
        return handled
    }

    private fun handleTouchEnd(): Boolean {
        val scale = currentScale()
        val rect = getDisplayRect()
        val handled = when {
            rect != null && scale < MINIMUM_SCALE -> {
                animateScale(scale, MINIMUM_SCALE, rect.centerX(), rect.centerY())
                true
            }
            rect != null && scale > maximumScale -> {
                animateScale(scale, maximumScale, rect.centerX(), rect.centerY())
                true
            }
            isDragging && !scaleGestureDetector.isInProgress -> startFlingFromVelocity()
            else -> false
        }
        velocityTracker?.recycle()
        velocityTracker = null
        return handled
    }

    // ---- drag / pan ----------------------------------------------------

    private fun processDrag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> handleDragMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activePointerId = INVALID_POINTER_ID
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(newIndex)
                    lastTouchX = event.getX(newIndex)
                    lastTouchY = event.getY(newIndex)
                }
            }
        }
        return true
    }

    private fun handleDragMove(event: MotionEvent) {
        val pointerIndex = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)
        val dx = x - lastTouchX
        val dy = y - lastTouchY

        if (!isDragging) {
            isDragging = sqrt((dx * dx + dy * dy).toDouble()) >= touchSlop
        }
        if (!isDragging) return

        if (!scaleGestureDetector.isInProgress) {
            suppMatrix.postTranslate(dx, dy)
            checkAndDisplayMatrix()
            when {
                blockParentIntercept -> parent?.requestDisallowInterceptTouchEvent(true)
                reachedPanEdge(dx, dy) -> parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        lastTouchX = x
        lastTouchY = y
    }

    private fun reachedPanEdge(dx: Float, dy: Float): Boolean {
        val reachedHorizontalEdge = horizontalEdge == EDGE_BOTH ||
            (horizontalEdge == EDGE_LEFT && dx >= 1f) ||
            (horizontalEdge == EDGE_RIGHT && dx <= -1f)
        val reachedVerticalEdge = (verticalEdge == EDGE_TOP && dy >= 1f) || (verticalEdge == EDGE_BOTTOM && dy <= -1f)
        return reachedHorizontalEdge || reachedVerticalEdge
    }

    private fun startFlingFromVelocity(): Boolean {
        val tracker = velocityTracker ?: return false
        tracker.computeCurrentVelocity(VELOCITY_UNIT, maximumFlingVelocity)
        val velocityX = tracker.xVelocity
        val velocityY = tracker.yVelocity
        val fastEnough = max(abs(velocityX), abs(velocityY)) >= minimumFlingVelocity
        val started = fastEnough && flingRunnable.start(velocityX.roundToInt(), velocityY.roundToInt())
        if (started) postOnAnimation(flingRunnable)
        return started
    }

    // ---- fling ------------------------------------------------------------

    private inner class FlingRunnable : Runnable {
        private val scroller = OverScroller(context)
        private var currentX = 0
        private var currentY = 0

        fun cancel() {
            scroller.forceFinished(true)
        }

        fun start(velocityX: Int, velocityY: Int): Boolean {
            val rect = getDisplayRect() ?: return false
            val viewWidth = width - paddingLeft - paddingRight
            val viewHeight = height - paddingTop - paddingBottom

            val startX = (-rect.left).roundToInt()
            val minX: Int
            val maxX: Int
            if (viewWidth < rect.width()) {
                minX = 0
                maxX = (rect.width() - viewWidth).roundToInt()
            } else {
                minX = startX
                maxX = startX
            }

            val startY = (-rect.top).roundToInt()
            val minY: Int
            val maxY: Int
            if (viewHeight < rect.height()) {
                minY = 0
                maxY = (rect.height() - viewHeight).roundToInt()
            } else {
                minY = startY
                maxY = startY
            }

            val hasPanRoom = startX != maxX || startY != maxY
            if (hasPanRoom) {
                currentX = startX
                currentY = startY
                // startX/startY are -rect.left/-rect.top, i.e. they move opposite to the drag/finger
                // direction, so the finger's velocity has to be negated to continue in the same
                // visual direction the pan was already moving.
                scroller.fling(startX, startY, -velocityX, -velocityY, minX, maxX, minY, maxY)
            }
            return hasPanRoom
        }

        override fun run() {
            if (scroller.isFinished || !scroller.computeScrollOffset()) return
            val newX = scroller.currX
            val newY = scroller.currY
            suppMatrix.postTranslate((currentX - newX).toFloat(), (currentY - newY).toFloat())
            imageMatrix = drawMatrix()
            currentX = newX
            currentY = newY
            postOnAnimation(this)
        }
    }

    // ---- scale animation -------------------------------------------------

    private fun animateScale(startScale: Float, endScale: Float, focalX: Float, focalY: Float) {
        ValueAnimator.ofFloat(startScale, endScale).apply {
            duration = ZOOM_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val frameScale = animator.animatedValue as Float
                val deltaScale = frameScale / currentScale()
                suppMatrix.postScale(deltaScale, deltaScale, focalX, focalY)
                checkAndDisplayMatrix()
            }
            start()
        }
    }

    // ---- matrix bookkeeping ----------------------------------------------

    private fun currentScale(): Float {
        suppMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun updateBaseMatrix(drawable: Drawable?) {
        if (drawable == null) return
        flingRunnable.cancel()
        val viewWidth = (width - paddingLeft - paddingRight).toFloat()
        val viewHeight = (height - paddingTop - paddingBottom).toFloat()
        baseMatrix.reset()
        baseMatrix.setRectToRect(
            RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat()),
            RectF(0f, 0f, viewWidth, viewHeight),
            Matrix.ScaleToFit.CENTER
        )
        suppMatrix.reset()
        imageMatrix = drawMatrix()
        checkMatrixBounds()
    }

    private fun checkAndDisplayMatrix() {
        if (checkMatrixBounds()) imageMatrix = drawMatrix()
    }

    private fun drawMatrix(): Matrix {
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(suppMatrix)
        return drawMatrix
    }

    private fun getDisplayRect(matrix: Matrix = drawMatrix()): RectF? {
        val d = drawable ?: return null
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        matrix.mapRect(displayRect)
        return displayRect
    }

    private fun checkMatrixBounds(): Boolean {
        val rect = getDisplayRect(drawMatrix()) ?: return false
        val height = rect.height()
        val width = rect.width()
        var deltaX = 0f
        var deltaY = 0f

        val viewHeight = this.height - paddingTop - paddingBottom
        if (height <= viewHeight) {
            deltaY = (viewHeight - height) / 2 - rect.top
            verticalEdge = EDGE_BOTH
        } else if (rect.top > 0) {
            verticalEdge = EDGE_TOP
            deltaY = -rect.top
        } else if (rect.bottom < viewHeight) {
            verticalEdge = EDGE_BOTTOM
            deltaY = viewHeight - rect.bottom
        } else {
            verticalEdge = EDGE_NONE
        }

        val viewWidth = this.width - paddingLeft - paddingRight
        if (width <= viewWidth) {
            deltaX = (viewWidth - width) / 2 - rect.left
            horizontalEdge = EDGE_BOTH
        } else if (rect.left > 0) {
            horizontalEdge = EDGE_LEFT
            deltaX = -rect.left
        } else if (rect.right < viewWidth) {
            horizontalEdge = EDGE_RIGHT
            deltaX = viewWidth - rect.right
        } else {
            horizontalEdge = EDGE_NONE
        }

        suppMatrix.postTranslate(deltaX, deltaY)
        return true
    }

    companion object {
        private const val MINIMUM_SCALE = 1.0f
        private const val DEFAULT_MEDIUM_SCALE = 1.75f
        private const val DEFAULT_MAX_SCALE = 3.0f
        private const val ZOOM_DURATION_MS = 200L
        private const val INVALID_POINTER_ID = -1
        private const val MATRIX_VALUES_SIZE = 9
        private const val VELOCITY_UNIT = 1000

        private const val EDGE_NONE = -1
        private const val EDGE_LEFT = 0
        private const val EDGE_TOP = 0
        private const val EDGE_RIGHT = 1
        private const val EDGE_BOTTOM = 1
        private const val EDGE_BOTH = 2

        private fun checkZoomLevels(min: Float, mid: Float, max: Float) {
            require(min < mid) { "Minimum zoom has to be less than medium zoom." }
            require(mid < max) { "Medium zoom has to be less than maximum zoom." }
        }
    }
}

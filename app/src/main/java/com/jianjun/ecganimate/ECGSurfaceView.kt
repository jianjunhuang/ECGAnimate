package com.jianjun.ecganimate

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.TextureView
import androidx.core.graphics.withTranslation
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.random.Random

class ECGSurfaceView : TextureView, TextureView.SurfaceTextureListener {

    private var surfaceCanvas: Canvas? = null
    private var drawingJob: Job? = null
    private var yStandard = 60f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lineColor = Color.parseColor("#FF8581")
    private val lineWidth = 4f
    private var transientX = 0f
    private var lastXPos = 0f
    private var heartBeatWidth = 0f
    private val linePath = Path()
    private val gradientPath = Path()
    private val straightLinePath = Path()
    private val heartBeatPath = Path()
    private val gradientStraightLinePath = Path()
    private val gradientHeartBeatPath = Path()

    /**
     * 控制 transient 动画
     */
    var isAnimateStart: AtomicBoolean = AtomicBoolean(false)

    /**
     * 控制线条显示
     */
    var isShow: AtomicBoolean = AtomicBoolean(false)
    private var isDestroy: AtomicBoolean = AtomicBoolean(false)
    private var isDisplayState = AtomicBoolean(false)
    val pulseArrayList = CopyOnWriteArrayList<Int>()

    private var createPathTask: ExecutorService? = Executors.newFixedThreadPool(1)

    companion object {
        private const val DEFAULT_REFRESH_NANO_TIME = 10000000
        private val COLOR_GRAPH_FILL =
            intArrayOf(Color.parseColor("#F2E08581"), Color.TRANSPARENT)
        private const val SMOOTHNESS = 0.5f
        private const val TRANSIENT_X_OVER = 30f
        private const val X_TRANSIENT = 10f
        private const val TAG_STRAIGHT = 0
        private const val TAG_HEART_RATE = 1
        private const val TAG = "ECG"
    }

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, -1)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        isFocusable = true
        keepScreenOn = true
        isFocusableInTouchMode = true
        linePaint.style = Paint.Style.STROKE
        linePaint.color = lineColor
        linePaint.strokeWidth = lineWidth
        gradientPaint.style = Paint.Style.FILL
        gradientPaint.color = Color.TRANSPARENT
        gradientPaint.alpha = 255
        gridPaint.color = lineColor
        gridPaint.alpha = 230
        gridPaint.strokeWidth = 1f
        gridPaint.style = Paint.Style.STROKE
        surfaceTextureListener = this
    }

    private fun startTransientX() {
        transientX -= X_TRANSIENT
    }

    private fun addStraightLine() {
        createPathTask?.execute {
            linePath.addPath(straightLinePath, lastXPos, 0f)
            gradientPath.addPath(gradientStraightLinePath, lastXPos, 0f)
            lastXPos += X_TRANSIENT
            if (!isDisplayState.get()) {
                pulseArrayList.add(TAG_STRAIGHT)
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        yStandard = height / 2f
        generateGradientPath()
        gradientPaint.shader =
            LinearGradient(
                0F,
                0F,
                0F,
                height.toFloat(),
                COLOR_GRAPH_FILL,
                null,
                Shader.TileMode.CLAMP
            )
    }

    private fun generateGradientPath() {
        straightLinePath.reset()
        straightLinePath.moveTo(0f, yStandard)
        straightLinePath.lineTo(X_TRANSIENT, yStandard)
        gradientStraightLinePath.reset()
        gradientStraightLinePath.addPath(straightLinePath)
        gradientStraightLinePath.lineTo(X_TRANSIENT, height.toFloat())
        gradientStraightLinePath.lineTo(0f, height.toFloat())
        gradientStraightLinePath.lineTo(0f, yStandard)
    }

    private fun draw() {
        try {
            //获得canvas对象
            surfaceCanvas = lockCanvas()
            if (isDisplayState.get()) {
                surfaceCanvas?.drawColor(Color.WHITE)
                val rowsSize = height / 11f
                val colsSize = width / 66f
                for (index in 0..11) {
                    val y = rowsSize * index
                    surfaceCanvas?.drawLine(0f, y, width.toFloat(), y, gridPaint)
                }
                for (index in 0..66) {
                    val x = colsSize * index
                    surfaceCanvas?.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                }
            } else {
                surfaceCanvas?.drawColor(Color.BLACK)
            }
            if (isShow.get()) {
                surfaceCanvas?.withTranslation(transientX, 0f) {
                    surfaceCanvas?.drawPath(linePath, linePaint)
                    surfaceCanvas?.drawPath(gradientPath, gradientPaint)
                }
            }
        } catch (e: Exception) {
        } finally {
            if (surfaceCanvas != null) {
                unlockCanvasAndPost(surfaceCanvas)
            }
        }
    }

    data class PointF(var x: Float, var y: Float)

    private fun shiftX() {
        if (!isAnimateStart.get()) {
            return
        }
        if (lastXPos < width) {
            addStraightLine()
            return
        }
        if (lastXPos + transientX < width) {
            addStraightLine()
        }
        startTransientX()
    }

    private fun calculateControlPoint(pointList: List<PointF>): ArrayList<PointF> {
        val controlPoints = ArrayList<PointF>()
        if (pointList.size <= 1) {
            return controlPoints
        }
        for ((i, point) in pointList.withIndex()) {
            when (i) {
                0 -> {//第一项
                    //添加后控制点
                    val nextPoint = pointList[i + 1]
                    val controlX = point.x + (nextPoint.x - point.x) * SMOOTHNESS
                    val controlY = point.y
                    controlPoints.add(PointF(controlX, controlY))
                }
                pointList.size - 1 -> {//最后一项
                    //添加前控制点
                    val lastPoint = pointList[i - 1]
                    val controlX = point.x - (point.x - lastPoint.x) * SMOOTHNESS
                    val controlY = point.y
                    controlPoints.add(PointF(controlX, controlY))
                }
                else -> {//中间项
                    val lastPoint = pointList[i - 1]
                    val nextPoint = pointList[i + 1]
                    val k = (nextPoint.y - lastPoint.y) / (nextPoint.x - lastPoint.x)
                    val b = point.y - k * point.x
                    //添加前控制点
                    val lastControlX = point.x - (point.x - lastPoint.x) * SMOOTHNESS
                    val lastControlY = k * lastControlX + b
                    controlPoints.add(PointF(lastControlX, lastControlY))
                    //添加后控制点
                    val nextControlX = point.x + (nextPoint.x - point.x) * SMOOTHNESS
                    val nextControlY = k * nextControlX + b
                    controlPoints.add(PointF(nextControlX, nextControlY))
                }
            }
        }
        return controlPoints
    }

    fun updateHeartRate() {
        pulseArrayList.add(TAG_HEART_RATE)
        addHeartBeatPath()
    }

    private fun addHeartBeatPath() {
        createPathTask?.execute {
            linePath.addPath(generateHeartBeatPath(), lastXPos, 0f)
            gradientPath.addPath(generateGradientHeartRatePath(), lastXPos, 0f)
            lastXPos += heartBeatWidth
        }
    }

    private fun generateHeartBeatPath(): Path {
        heartBeatPath.reset()
        val points = ArrayList<PointF>()
        val centerY = height / 2f
        val stand = centerY / 4f
        val amp = Random.nextInt(-1, 10) * stand * 0.10f
        val p1 = points.add(0f, centerY)
        val p2 = points.add(p1.x + stand * 0.40f, centerY - stand * 0.20f)
        val p21 = points.add(p2.x + stand * 0.30f, centerY + stand * 0.20f)
        val p22 = points.add(p21.x + stand * 0.10f, centerY - stand * 0.80f - amp)
        val p3 = points.add(p22.x + stand * 0.20f, centerY - stand * 0.160f - amp)
        val p4 = points.add(p3.x + stand * 0.15f, centerY + stand * 0.80f + amp)
        val p5 = points.add(p4.x + stand * 0.30f, centerY - stand * 0.40f)
        val p6 = points.add(p5.x + stand * 0.30f, centerY - stand * 0.20f)
        val p7 = points.add(p6.x + stand * 0.16f, centerY - stand * 0.30f)
        val p8 = points.add(p7.x + stand * 0.16f, centerY - stand * 0.20f)
        val p9 = points.add(p8.x + stand * 0.22f, centerY)
        val controlPoints = calculateControlPoint(points)
        val firstPoint = points.first()
        heartBeatPath.moveTo(firstPoint.x, firstPoint.y)
        for (i in 0 until (points.size * 2) step 2) {
            if (i >= controlPoints.size) {
                continue
            }
            val leftControlPoint = controlPoints[i]
            val rightControlPoint = controlPoints[i + 1]
            val rightPoint = points[i / 2 + 1]
            heartBeatPath.cubicTo(
                leftControlPoint.x,
                leftControlPoint.y,
                rightControlPoint.x,
                rightControlPoint.y,
                rightPoint.x,
                rightPoint.y
            )
        }
        val lastPoint = points.last()
        heartBeatPath.lineTo(lastPoint.x, lastPoint.y)
        heartBeatWidth = lastPoint.x
        return heartBeatPath
    }

    private fun generateGradientHeartRatePath(): Path {
        gradientHeartBeatPath.reset()
        gradientHeartBeatPath.addPath(heartBeatPath)
        gradientHeartBeatPath.lineTo(heartBeatWidth, height.toFloat())
        gradientHeartBeatPath.lineTo(0f, height.toFloat())
        gradientHeartBeatPath.lineTo(0f, height / 2f)
        return gradientHeartBeatPath
    }

    private fun MutableList<PointF>.add(x: Float, y: Float): PointF {
        val point = PointF(x, y)
        this.add(point)
        return point
    }

    fun goOn() {
        isAnimateStart.set(true)
    }

    fun pause() {
        isAnimateStart.set(false)
    }

    fun stop() {
        isShow.set(false)
    }

    fun start() {
        pulseArrayList.clear()
        isAnimateStart.set(true)
        isShow.set(true)
        transientX = 0f
        lastXPos = 0f
        linePath.reset()
        gradientPath.reset()
    }

    private var dx = 0f
    private var dy = 0f

    private fun restTransient() {
        if (isDisplayState.get()) {
            if (transientX > 0f) {
                transientX = 0f
            }
            if (transientX + lastXPos < width) {
                transientX = width - lastXPos
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_UP -> {
                if (abs(dx - event.x) < 5 && abs(dy - event.y) < 5) {
                    performClick()
                }
                restTransient()
            }
            MotionEvent.ACTION_CANCEL -> {
                restTransient()
            }
            MotionEvent.ACTION_DOWN -> {
                dx = event.x
                dy = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isAnimateStart.get() && lastXPos >= width) {
                    val moveX = transientX + event.x - dx
                    dx = event.x
                    if (moveX < TRANSIENT_X_OVER && lastXPos + moveX > width - TRANSIENT_X_OVER) {
                        transientX = moveX
                    }
                }
            }
        }
        return true
    }

    fun updateDisplay(pulseList: List<Int>) {
        linePath.reset()
        gradientPath.reset()
        pulseArrayList.clear()
        pulseArrayList.addAll(pulseList)
        var pulse = 0
        transientX = 0f
        lastXPos = 0f
        for (tag in pulseArrayList) {
            if (tag == TAG_HEART_RATE) {
                pulse++
            }
            if (pulse > 0) {
                if (tag == TAG_HEART_RATE) {
                    addHeartBeatPath()
                } else {
                    addStraightLine()
                }
            }
        }
        createPathTask?.submit {
            isShow.set(true)
            isAnimateStart.set(false)
            isDisplayState.set(true)
        }
    }

    override fun getBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val rowsSize = height / 11f
        val colsSize = width / 66f
        for (index in 0..11) {
            val y = rowsSize * index
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        for (index in 0..66) {
            val x = colsSize * index
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
        canvas.withTranslation(transientX, 0f) {
            canvas.drawPath(linePath, linePaint)
            canvas.drawPath(gradientPath, gradientPaint)
        }
        return bitmap
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        createPathTask?.shutdownNow()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        isDestroy.set(true)
        drawingJob?.cancel()
        return false
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        isDestroy.set(false)
        drawingJob = CoroutineScope(Dispatchers.Default).launch {
            while (!isDestroy.get()) {
//                val start = System.nanoTime()
                shiftX()
                draw()
//                val stop = System.nanoTime() - start
//                if (stop < DEFAULT_REFRESH_NANO_TIME) {
                delay(10)
//                }
            }
        }
    }
}
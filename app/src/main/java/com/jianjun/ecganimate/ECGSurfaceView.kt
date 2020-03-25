package com.jianjun.ecganimate

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.graphics.withTranslation
import kotlinx.coroutines.*
import java.util.*
import kotlin.random.Random

class ECGSurfaceView : SurfaceView, SurfaceHolder.Callback {

    private var surfaceHolder = holder
    private var surfaceCanvas: Canvas? = null
    private var drawingJob: Job? = null
    private val X_OFFSET = 40f
    private val Y_STANDARD = 60f
    private var y_standard = 60f
    private val X_TRANSIENT = 10f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lineColor = Color.parseColor("#FF8581")
    private val lineWidth = 4f
    private val COLOR_GRAPH_FILL = intArrayOf(Color.parseColor("#E08581"), Color.TRANSPARENT)
    private var transientX = 0f
    private val pathMatrix = Matrix()
    private var lastXPos = 0f
    private var heartBeatWidth = 0f
    private var linePathSize = 0f
    private val linePath = Path()
    private val gradientPath = Path()
    private val straightLinePath = Path()
    private val heartBeatPath = Path()
    private val gradientStraightLinePath = Path()
    private val gradientHeartBeatPath = Path()

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, -1)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        surfaceHolder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
        isFocusableInTouchMode = true
        linePaint.style = Paint.Style.STROKE
        linePaint.color = lineColor
        linePaint.strokeWidth = lineWidth
        gradientPaint.style = Paint.Style.FILL
        gradientPaint.color = Color.TRANSPARENT
        gradientPaint.alpha = 255
    }

    private fun startTransientX() {
        transientX -= X_TRANSIENT
    }

    private fun addStraightLine() {
        linePath.addPath(straightLinePath, lastXPos, 0f)
        gradientPath.addPath(gradientStraightLinePath, lastXPos, 0f)
        lastXPos += X_TRANSIENT
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        y_standard = height / 2f
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
        straightLinePath.moveTo(0f, y_standard)
        straightLinePath.lineTo(X_TRANSIENT, y_standard)
        gradientStraightLinePath.reset()
        gradientStraightLinePath.addPath(straightLinePath)
        gradientStraightLinePath.lineTo(X_TRANSIENT, height.toFloat())
        gradientStraightLinePath.lineTo(0f, height.toFloat())
        gradientStraightLinePath.lineTo(0f, y_standard)
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        drawingJob?.cancel()
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        drawingJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val start = System.currentTimeMillis()
                shiftX()
                draw()
                val stop = System.currentTimeMillis() - start
                if (stop < 100) {
                    delay(stop)
                }
            }
        }
    }

    private fun draw() {
        try {
            //获得canvas对象
            surfaceCanvas = surfaceHolder.lockCanvas()
            surfaceCanvas?.drawColor(Color.BLACK)
            surfaceCanvas?.withTranslation(transientX, 0f) {
                surfaceCanvas?.drawPath(linePath, linePaint)
                surfaceCanvas?.drawPath(gradientPath, gradientPaint)
            }
        } catch (e: Exception) {
        } finally {
            if (surfaceCanvas != null) {
                surfaceHolder.unlockCanvasAndPost(surfaceCanvas)
            }
        }
    }

    data class PointF(var x: Float, var y: Float)

    private fun shiftX() {
//        if (controlPoints.isEmpty() || controlPoints.last().x < width) {
//            return
//        }
        if (lastXPos < width) {
            addStraightLine()
            return
        }

        if (lastXPos + transientX < width) {
            addStraightLine()
        }

        startTransientX()

//        points.forEach {
//            it.x = it.x - X_OFFSET
//        }
    }

    private val SMOOTHNESS = 0.5f

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

    fun updateHeartRate(value: Int) {
//        points.add(PointF(points.last().x + X_OFFSET, y_standard + (value - Y_STANDARD) * 10))
        CoroutineScope(Dispatchers.Default).launch {
            linePath.addPath(generateHeartBeatPath(), lastXPos, 0f)
            gradientPath.addPath(generateGradientHeartRatePath(), lastXPos, 0f)
            lastXPos += heartBeatWidth
        }
    }

    private fun generateHeartBeatPath(): Path {
        heartBeatPath.reset()
        val points = ArrayList<PointF>()
        val amp = Random.nextInt(-1, 10) * 10
        val centerY = height / 2f
        val p1 = points.add(0f, centerY)
        val p2 = points.add(p1.x + 40, centerY - 20)
        val p21 = points.add(p2.x + 30, centerY + 20)
        val p22 = points.add(p21.x + 10, centerY - 80 - amp)
        val p3 = points.add(p22.x + 20, centerY - 160 - amp)
        val p4 = points.add(p3.x + 30, centerY + 80 + amp)
        val p5 = points.add(p4.x + 30, centerY - 40)
        val p6 = points.add(p5.x + 30, centerY - 20)
        val p7 = points.add(p6.x + 16, centerY - 30)
        val p8 = points.add(p7.x + 16, centerY - 20)
        val p9 = points.add(p8.x + 22, centerY)
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
}
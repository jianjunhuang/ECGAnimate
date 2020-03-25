package com.jianjun.ecganimate

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class ECGView : View {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lineColor = Color.parseColor("#FF8581")
    private val lineWidth = 4f
    private val linePath = Path()
    private val COLOR_GRAPH_FILL = intArrayOf(Color.parseColor("#E08581"), Color.TRANSPARENT)
    private val pathMatrix = Matrix()
    private var shiftRatio = 0.0f
    private var clipRatio = 0f
    private val clipRectF = RectF()
    private val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: BitmapShader? = null
    private var backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shiftAnimator = ValueAnimator.ofFloat(0f, 1f)
    private val clipAnimator = ValueAnimator.ofFloat(0f, 1f)
    private val clipToRightAnimator = ValueAnimator.ofFloat(0f, 1f)
    private val clipLinePath = Path()
    private var clipShader: BitmapShader? = null
    private var isStartShift = false

    constructor(context: Context?) : this(context, null)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 1)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        linePaint.color = lineColor
        linePaint.strokeWidth = lineWidth
        linePaint.style = Paint.Style.STROKE

        shiftAnimator.repeatMode = ValueAnimator.RESTART
        shiftAnimator.repeatCount = ValueAnimator.INFINITE
        shiftAnimator.interpolator = LinearInterpolator()
        shiftAnimator.duration = 5000
        shiftAnimator.addUpdateListener {
            shiftRatio = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        genPoints(width, height)
        calculateControlPoint(points)
        if (shader == null) {
            generateShader(width, height)
        }
        if (clipShader == null) {
            generateClipShader(width, height)
        }
        clipRectF.set(0f, 0f, width.toFloat(), height.toFloat())
    }

    private fun generateClipShader(width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.strokeWidth = lineWidth
        clipLinePath.reset()
        clipLinePath.moveTo(0f, height / 2f)
        clipLinePath.lineTo(width.toFloat(), height / 2f)
        //draw line
        paint.style = Paint.Style.STROKE
        paint.shader = null
        paint.color = lineColor
        canvas.drawColor(Color.BLACK)
        canvas.drawPath(clipLinePath, paint)
        //draw gradient
        clipLinePath.lineTo(width.toFloat(), height.toFloat())
        clipLinePath.lineTo(0f, height.toFloat())
        clipLinePath.lineTo(0f, height / 2f)
        paint.color = Color.TRANSPARENT
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.shader =
            LinearGradient(
                0F,
                0F,
                0F,
                height.toFloat(),
                COLOR_GRAPH_FILL,
                null,
                Shader.TileMode.CLAMP
            )
        canvas.drawPath(clipLinePath, paint)

        clipShader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        clipPaint.shader = clipShader
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (isStartShift) {
            pathMatrix.setTranslate(-shiftRatio * width, 0f)
            shader?.setLocalMatrix(pathMatrix)
            canvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            if (clipToRightAnimator.isRunning) {
                canvas?.drawRect(
                    width * clipRatio,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    clipPaint
                )
            }
        } else {
            canvas?.drawRect(0f, 0f, width * clipRatio, height.toFloat(), clipPaint)
        }
    }

    private fun generateShader(
        width: Int, height: Int
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        linePath.reset()
        val firstPoint = points.first()
        linePath.moveTo(firstPoint.x, firstPoint.y)
        for (i in 0 until (points.size * 2) step 2) {
            if (i >= controlPoints.size) {
                continue
            }
            val leftControlPoint = controlPoints[i]
            val rightControlPoint = controlPoints[i + 1]
            val rightPoint = points[i / 2 + 1]
            linePath.cubicTo(
                leftControlPoint.x,
                leftControlPoint.y,
                rightControlPoint.x,
                rightControlPoint.y,
                rightPoint.x,
                rightPoint.y
            )
        }
        val lastPoint = points.last()
        linePath.lineTo(lastPoint.x, height / 2f)
        //绘制全部路径
        linePaint.style = Paint.Style.STROKE
        linePaint.shader = null
        linePaint.color = lineColor
        canvas.drawPath(linePath, linePaint)
        //填充渐变色
        linePath.lineTo(lastPoint.x, height.toFloat())
        linePath.lineTo(firstPoint.x, height.toFloat())
        linePath.lineTo(firstPoint.x, firstPoint.y)
        linePaint.color = Color.TRANSPARENT
        linePaint.alpha = 255
        linePaint.style = Paint.Style.FILL
        linePaint.shader =
            LinearGradient(
                0F,
                0F,
                0F,
                height.toFloat(),
                COLOR_GRAPH_FILL,
                null,
                Shader.TileMode.CLAMP
            )
        canvas.drawPath(linePath, linePaint)

        shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
        backgroundPaint.shader = shader
    }


    private val points = ArrayList<PointF>()
    private fun genPoints(width: Int, height: Int) {
        val centerY = height / 2f
        var lastX = 0f
        for (size in 0..1) {
            val p1 = points.add(lastX, centerY)
            val p2 = points.add(p1.x + 40, centerY - 20)
            val p21 = points.add(p2.x + 30, centerY + 20)
            val p22 = points.add(p21.x + 10, centerY - 80)
            val p3 = points.add(p22.x + 20, centerY - 160)
            val p4 = points.add(p3.x + 30, centerY + 80)
            val p5 = points.add(p4.x + 30, centerY - 40)
            val p6 = points.add(p5.x + 30, centerY - 20)
            val p7 = points.add(p6.x + 16, centerY - 30)
            val p8 = points.add(p7.x + 16, centerY - 20)
            val p9 = points.add(p8.x + 22, centerY)
            val p10 = points.add(p9.x + 200, centerY)
            lastX = p10.x
        }
        if (lastX < width) {
            points.add(width.toFloat(), centerY)
        }
    }


    private fun ArrayList<PointF>.add(x: Float, y: Float): PointF {
        val point = PointF(x, y)
        this.add(point)
        return point
    }

    fun startAnimate() {
        startClipAnimate()
    }

    fun stopAnimate() {
        isStartShift = false
        clipRatio = 0f
        shiftAnimator.end()
        invalidate()
    }

    private fun startShiftAnimate() {
        if (!shiftAnimator.isRunning) {
            shiftAnimator.start()
        }
    }

    private fun startClipAnimate() {
        clipAnimator.duration = 5000
        clipAnimator.addUpdateListener {
            clipRatio = it.animatedValue as Float
            invalidate()
        }
        clipAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {
            }

            override fun onAnimationEnd(animation: Animator?) {
                isStartShift = true
                startShiftAnimate()
            }

            override fun onAnimationCancel(animation: Animator?) {
            }

            override fun onAnimationStart(animation: Animator?) {
            }
        })
        clipToRightAnimator.duration = 5000
        clipToRightAnimator.addUpdateListener {
            clipRatio = it.animatedValue as Float
            invalidate()
        }
        clipAnimator.start()
        clipToRightAnimator.startDelay = 5000
        clipToRightAnimator.start()
    }

    private val SMOOTHNESS = 0.5f
    private val controlPoints = ArrayList<PointF>()
    private fun calculateControlPoint(pointList: List<PointF>) {
        controlPoints.clear()
        if (pointList.size <= 1) {
            return
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
    }

    data class PointF(val x: Float, val y: Float)

}
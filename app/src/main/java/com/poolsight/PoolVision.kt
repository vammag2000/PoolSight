package com.poolsight

import android.graphics.PointF
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

data class VisionResult(
    val balls: List<BallDetected>,
    val pockets: List<PointF>,
    val cueBall: BallDetected?,
    val aimAngleRad: Float?
)

data class BallDetected(
    val center: PointF,
    val radius: Float,
    val color: BallColor
)

enum class BallColor { CUE, EIGHT, SOLID, STRIPE, UNKNOWN }

object PoolVision {

    private var tableRoi: Rect? = null

    fun setTableRoi(roi: Rect) { tableRoi = roi }

    fun analyze(frame: Mat): List<PointF> {
        val roi = tableRoi ?: Rect(0, 0, frame.cols(), frame.rows())
        val table = frame.submat(roi)

        val pockets = detectPockets(table).map {
            PointF(it.x + roi.x, it.y + roi.y)
        }

        val balls = detectBalls(table).map { ball ->
            BallDetected(
                center = PointF(ball.center.x + roi.x, ball.center.y + roi.y),
                radius = ball.radius,
                color = ball.color
            )
        }

        val cueBall = balls.firstOrNull { it.color == BallColor.CUE }

        val aimAngle = if (cueBall != null) {
            val nearest = balls.filter { it !== cueBall }
                .minByOrNull { hypot(it.center.x - cueBall.center.x, it.center.y - cueBall.center.y) }
            nearest?.let {
                kotlin.math.atan2(
                    (it.center.y - cueBall.center.y).toDouble(),
                    (it.center.x - cueBall.center.x).toDouble()
                ).toFloat()
            }
        } else null

        val engine = PhysicsEngine(
            tableWidth = roi.width.toFloat(),
            tableHeight = roi.height.toFloat(),
            ballRadius = balls.firstOrNull()?.radius ?: 12f
        )
        engine.setBalls(balls, pockets, offsetX = roi.x.toFloat(), offsetY = roi.y.toFloat())

        return if (cueBall != null) {
            engine.predictAllShots(cueBall, balls, aimAngle)
        } else {
            emptyList()
        }
    }

    private fun detectPockets(table: Mat): List<PointF> {
        val gray = Mat()
        Imgproc.cvtColor(table, gray, Imgproc.COLOR_RGBA2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val circles = Mat()
        Imgproc.HoughCircles(
            blurred, circles, Imgproc.HOUGH_GRADIENT, 1.2,
            table.rows() / 6.0,
            100.0, 30.0,
            (table.rows() * 0.03).toInt(),
            (table.rows() * 0.08).toInt()
        )

        val result = mutableListOf<PointF>()
        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i) ?: continue
            result.add(PointF(data[0].toFloat(), data[1].toFloat()))
        }
        gray.release(); blurred.release(); circles.release()
        return result
    }

    private fun detectBalls(table: Mat): List<BallDetectedInternal> {
        val gray = Mat()
        Imgproc.cvtColor(table, gray, Imgproc.COLOR_RGBA2GRAY)
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

        val circles = Mat()
        val minR = (table.rows() * 0.012).toInt().coerceAtLeast(6)
        val maxR = (table.rows() * 0.030).toInt().coerceAtLeast(12)

        Imgproc.HoughCircles(
            blurred, circles, Imgproc.HOUGH_GRADIENT, 1.0,
            minR * 2.0, 100.0, 25.0, minR, maxR
        )

        val result = mutableListOf<BallDetectedInternal>()
        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i) ?: continue
            val cx = data[0].toFloat()
            val cy = data[1].toFloat()
            val r = data[2].toFloat()

            val color = classifyColor(table, cx.toInt(), cy.toInt())
            result.add(BallDetectedInternal(PointF(cx, cy), r, color))
        }
        gray.release(); blurred.release(); circles.release()
        return result
    }

    private fun classifyColor(mat: Mat, x: Int, y: Int): BallColor {
        if (x < 0 || y < 0 || x >= mat.cols() || y >= mat.rows()) return BallColor.UNKNOWN
        val pixel = mat.get(y, x) ?: return BallColor.UNKNOWN
        val b = pixel[0]; val g = pixel[1]; val r = pixel[2]

        if (r > 200 && g > 200 && b > 200) return BallColor.CUE
        if (r < 60 && g < 60 && b < 60) return BallColor.EIGHT

        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val sat = if (max == 0.0) 0.0 else (max - min) / max
        return if (sat > 0.5) BallColor.SOLID else BallColor.STRIPE
    }

    private data class BallDetectedInternal(
        val center: PointF,
        val radius: Float,
        val color: BallColor
    )
}

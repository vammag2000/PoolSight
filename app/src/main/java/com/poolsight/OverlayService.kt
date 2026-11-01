package com.poolsight

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.IBinder
import android.view.View
import android.view.WindowManager

class OverlayService : Service() {

    companion object {
        @Volatile private var instance: OverlayService? = null
        @Volatile private var currentPredictions: List<PointF> = emptyList()

        fun updatePredictions(points: List<PointF>) {
            currentPredictions = points
            instance?.overlayView?.invalidate()
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
    }

    private fun addOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView = OverlayView(this)
        windowManager.addView(overlayView, params)
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class OverlayView(context: android.content.Context) : View(context) {

        private val paint = Paint().apply {
            color = Color.argb(220, 0, 255, 100)
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        private val pocketPaint = Paint().apply {
            color = Color.argb(200, 255, 180, 0)
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val points = currentPredictions
            if (points.size < 2) return

            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            canvas.drawPath(path, paint)

            for (i in 1 until points.size step 2) {
                if (i < points.size) {
                    canvas.drawCircle(points[i].x, points[i].y, 10f, pocketPaint)
                }
            }

            canvas.drawCircle(points[0].x, points[0].y, 8f, pocketPaint)
        }
    }
}

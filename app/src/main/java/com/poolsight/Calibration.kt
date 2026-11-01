package com.poolsight

import android.content.Context
import android.graphics.Rect
import org.opencv.core.Rect as CvRect

object Calibration {

    private const val PREFS = "poolsight_calib"

    fun saveRoi(context: Context, rect: Rect) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("left", rect.left)
            .putInt("top", rect.top)
            .putInt("right", rect.right)
            .putInt("bottom", rect.bottom)
            .apply()
    }

    fun loadRoi(context: Context, screenW: Int, screenH: Int): Rect {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Rect(
            prefs.getInt("left", (screenW * 0.05f).toInt()),
            prefs.getInt("top", (screenH * 0.15f).toInt()),
            prefs.getInt("right", (screenW * 0.95f).toInt()),
            prefs.getInt("bottom", (screenH * 0.85f).toInt())
        )
    }

    fun autoDetectTable(mat: org.opencv.core.Mat): Rect? {
        val hsv = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(mat, hsv, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)
        org.opencv.imgproc.Imgproc.cvtColor(hsv, hsv, org.opencv.imgproc.Imgproc.COLOR_RGB2HSV)

        val lower = org.opencv.core.Scalar(35.0, 80.0, 40.0)
        val upper = org.opencv.core.Scalar(85.0, 255.0, 220.0)
        val mask = org.opencv.core.Mat()
        org.opencv.core.Core.inRange(hsv, lower, upper, mask)

        val contours = mutableListOf<org.opencv.core.MatOfPoint>()
        org.opencv.imgproc.Imgproc.findContours(
            mask, contours, org.opencv.core.Mat(),
            org.opencv.imgproc.Imgproc.RETR_EXTERNAL,
            org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE
        )

        if (contours.isEmpty()) { hsv.release(); mask.release(); return null }

        val largest = contours.maxByOrNull { org.opencv.imgproc.Imgproc.contourArea(it) } ?: return null
        val rect = org.opencv.imgproc.Imgproc.boundingRect(largest)

        hsv.release(); mask.release()
        return Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
    }
}

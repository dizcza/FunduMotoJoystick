package de.dizcza.fundu_moto_joystick

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max

class LandmarksOverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs),
    HandsTracker.HandsTrackerListener {

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (results == null) {
            Log.w(TAG, "No results to draw.")
            return
        }

        val horizontalScale = imageWidth * scaleFactor
        val verticalScale = imageHeight * scaleFactor
        for (currentHandLandmarks in results!!.landmarks()) {
            if (currentHandLandmarks == null) {
                Log.w(TAG, "No hand landmarks")
                continue
            }
            if (currentHandLandmarks.size * 2 != jointsCoordinates.size) {
                Log.d(TAG, "Unexpected landmarks size: ${currentHandLandmarks.size}")
                continue
            }

            currentHandLandmarks.forEachIndexed { landmarkIndex, normalizedLandmark ->
                val xCoordinate = normalizedLandmark.x() * horizontalScale
                val yCoordinate = normalizedLandmark.y() * verticalScale

                jointsCoordinates[landmarkIndex * 2] = xCoordinate
                jointsCoordinates[landmarkIndex * 2 + 1] = yCoordinate
            }

            HandLandmarker.HAND_CONNECTIONS.forEachIndexed { connectionIndex, bone ->
                val startLandmarkIndex = bone.start()
                val endLandmarkIndex = bone.end()

                bonesCoordinates[connectionIndex * 4] = jointsCoordinates[startLandmarkIndex * 2]
                bonesCoordinates[connectionIndex * 4 + 1] =
                    jointsCoordinates[startLandmarkIndex * 2 + 1]
                bonesCoordinates[connectionIndex * 4 + 2] = jointsCoordinates[endLandmarkIndex * 2]
                bonesCoordinates[connectionIndex * 4 + 3] =
                    jointsCoordinates[endLandmarkIndex * 2 + 1]
            }

            canvas.drawLines(bonesCoordinates, linePaint)
            canvas.drawPoints(jointsCoordinates, pointPaint)
        }
    }

    override fun onHandsDetected(
        results: List<HandLandmarkerResult>,
        imageHeight: Int,
        imageWidth: Int,
        mpImage: MPImage,
    ) {
        this.results = results.first()
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    private var results: HandLandmarkerResult? = null

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val jointsCoordinates = FloatArray(21 * 2)
    private val bonesCoordinates = FloatArray(HandLandmarker.HAND_CONNECTIONS.size * 4)

    companion object {
        private var linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = LANDMARK_STROKE_WIDTH
            style = Paint.Style.STROKE
        }
        private var pointPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = LANDMARK_STROKE_WIDTH * 2
            style = Paint.Style.FILL
        }

        private const val LANDMARK_STROKE_WIDTH = 8F

        const val TAG = "OverlayView"
    }
}

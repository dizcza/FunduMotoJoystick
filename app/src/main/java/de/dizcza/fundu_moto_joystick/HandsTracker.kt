package de.dizcza.fundu_moto_joystick

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.ArrayList

class HandsTracker(context: Context) {
    private val handLandmarker: HandLandmarker
    private val handsTrackerListeners = ArrayList<HandsTrackerListener>()

    init {
        val baseOptions = BaseOptions.builder().setDelegate(Delegate.CPU)
            .setModelAssetPath(HAND_LANDMARKER_TASK_PATH).build()
        val options = HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(baseOptions)
            .setNumHands(1).setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::onResult).setErrorListener(this::onError).build()
        handLandmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun addHandsTrackerListener(listener: HandsTrackerListener) {
        handsTrackerListeners.add(listener)
    }

    operator fun invoke(imageProxy: ImageProxy) {
        val bitmapBuffer =
            Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        imageProxy.close()

        val transformation = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }
        val bitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, transformation, true
        )

        val mpImage = BitmapImageBuilder(bitmap).build()

        handLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
    }

    private fun onResult(handLandmarkerResult: HandLandmarkerResult, mpImage: MPImage) {
        for (listener in handsTrackerListeners) {
            listener.onHandsDetected(handLandmarkerResult, mpImage.height, mpImage.width)
        }
    }

    private fun onError(runtimeException: RuntimeException?) {
        Log.e(
            TAG,
            "onHandsDetectionError: ${runtimeException?.message ?: "Unknown error"}"
        )
    }

    interface HandsTrackerListener {
        fun onHandsDetected(
            results: HandLandmarkerResult, imageHeight: Int, imageWidth: Int
        )
    }

    companion object {
        private var HAND_LANDMARKER_TASK_PATH = "hand_landmarker.task"
        private const val TAG = "HandsTracker"
    }
}
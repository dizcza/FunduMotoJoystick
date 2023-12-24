package de.dizcza.fundu_moto_joystick.fragment

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class SonarView(context: Context?, attributeSet: AttributeSet?) : View(context, attributeSet) {
    private val mPaint = Paint()
    private val mPath = Path()

    init {
        mPaint.style = Paint.Style.FILL
        mPaint.color = Color.BLACK
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(mPath, mPaint)
    }

    fun drawCircle(x: Float, y: Float) {
        mPath.addCircle(x, y, POINT_RADIUS.toFloat(), Path.Direction.CW)
        invalidate()
    }

    fun drawRect(x: Float, y: Float) {
        mPath.addRect(x - RECT_SIZE, y - RECT_SIZE, x + RECT_SIZE, y + RECT_SIZE, Path.Direction.CW)
        invalidate()
    }

    fun clear() {
        mPath.reset()
        invalidate()
    }

    companion object {
        private const val POINT_RADIUS = 10
        private const val RECT_SIZE = 20
    }
}

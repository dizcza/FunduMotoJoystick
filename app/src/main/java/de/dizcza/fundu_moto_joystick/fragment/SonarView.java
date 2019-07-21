package de.dizcza.fundu_moto_joystick.fragment;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class SonarView extends View {
    private static final int POINT_RADIUS = 10;
    private final Paint mPaint = new Paint();
    private final Path mPath = new Path();

    public SonarView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.BLACK);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(mPath, mPaint);
    }

    public void drawCircle(float x, float y) {
        mPath.addCircle(x, y, POINT_RADIUS, Path.Direction.CW);
        invalidate();
    }

    public void clear() {
        mPath.reset();
        invalidate();
    }
}

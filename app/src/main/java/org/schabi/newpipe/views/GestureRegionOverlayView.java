package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class GestureRegionOverlayView extends View {
    public static final int SEGMENT_NONE = -1;
    public static final int SEGMENT_LEFT = 0;
    public static final int SEGMENT_MIDDLE = 1;
    public static final int SEGMENT_RIGHT = 2;

    @IntDef({SEGMENT_NONE, SEGMENT_LEFT, SEGMENT_MIDDLE, SEGMENT_RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Segment {
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motionLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motionDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Segment
    private int selectedSegment = SEGMENT_NONE;
    private float swipeMotionDeltaX = 0.0f;
    private float swipeMotionDeltaY = 0.0f;

    public GestureRegionOverlayView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final float density = getResources().getDisplayMetrics().density;

        backgroundPaint.setColor(Color.argb(28, 0, 0, 0));
        selectedPaint.setColor(Color.argb(54, 255, 255, 255));
        linePaint.setColor(Color.argb(132, 255, 255, 255));
        linePaint.setStrokeWidth(Math.max(1f, density));
        motionLinePaint.setColor(Color.argb(180, 255, 255, 255));
        motionLinePaint.setStrokeWidth(Math.max(2f, density * 2f));
        motionLinePaint.setStrokeCap(Paint.Cap.ROUND);
        motionDotPaint.setColor(Color.argb(220, 255, 255, 255));

        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        final float width = getWidth();
        final float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        final float oneThird = width / 3f;
        final float twoThird = oneThird * 2f;

        canvas.drawRect(0f, 0f, width, height, backgroundPaint);

        if (selectedSegment == SEGMENT_LEFT) {
            canvas.drawRect(0f, 0f, oneThird, height, selectedPaint);
        } else if (selectedSegment == SEGMENT_MIDDLE) {
            canvas.drawRect(oneThird, 0f, twoThird, height, selectedPaint);
        } else if (selectedSegment == SEGMENT_RIGHT) {
            canvas.drawRect(twoThird, 0f, width, height, selectedPaint);
        }

        canvas.drawLine(oneThird, 0f, oneThird, height, linePaint);
        canvas.drawLine(twoThird, 0f, twoThird, height, linePaint);

        if (selectedSegment != SEGMENT_NONE
                && (Math.abs(swipeMotionDeltaY) > 0f || Math.abs(swipeMotionDeltaX) > 0f)) {
            final float density = getResources().getDisplayMetrics().density;
            final float segmentCenterX;
            if (selectedSegment == SEGMENT_LEFT) {
                segmentCenterX = oneThird / 2f;
            } else if (selectedSegment == SEGMENT_MIDDLE) {
                segmentCenterX = width / 2f;
            } else {
                segmentCenterX = twoThird + oneThird / 2f;
            }

            final float anchorY = height / 2f;
            final float anchorX = segmentCenterX;
            final float maxTravelX = Math.min(oneThird * 0.32f, density * 72f);
            final float maxTravel = Math.min(height * 0.18f, density * 72f);
            final float deltaX = Math.max(-maxTravelX, Math.min(swipeMotionDeltaX, maxTravelX));
            final float delta = Math.max(-maxTravel, Math.min(swipeMotionDeltaY, maxTravel));
            final float targetX = anchorX + deltaX;
            final float targetY = anchorY + delta;
            final float baseRadius = Math.max(3f, density * 3f);
            final float targetRadius = Math.max(5f, density * 5f);

            canvas.drawLine(anchorX, anchorY, targetX, targetY, motionLinePaint);
            canvas.drawCircle(anchorX, anchorY, baseRadius, motionDotPaint);
            canvas.drawCircle(targetX, targetY, targetRadius, motionDotPaint);
        }
    }

    public void setSelectedSegment(@Segment final int segment) {
        if (selectedSegment == segment) {
            return;
        }
        selectedSegment = segment;
        invalidate();
    }

    public void setSwipeMotionDeltaY(final float deltaY) {
        if (swipeMotionDeltaY == deltaY) {
            return;
        }
        swipeMotionDeltaY = deltaY;
        invalidate();
    }

    public void setSwipeMotionDeltaX(final float deltaX) {
        if (swipeMotionDeltaX == deltaX) {
            return;
        }
        swipeMotionDeltaX = deltaX;
        invalidate();
    }

    public void setSwipeMotion(final float deltaX, final float deltaY) {
        if (swipeMotionDeltaX == deltaX && swipeMotionDeltaY == deltaY) {
            return;
        }
        swipeMotionDeltaX = deltaX;
        swipeMotionDeltaY = deltaY;
        invalidate();
    }

    public void resetSwipeMotion() {
        if (swipeMotionDeltaX == 0.0f && swipeMotionDeltaY == 0.0f) {
            return;
        }
        swipeMotionDeltaX = 0.0f;
        swipeMotionDeltaY = 0.0f;
        invalidate();
    }
}

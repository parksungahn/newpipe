package org.schabi.newpipe.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;

import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import static com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
import static com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;

public class ExpandableSurfaceView extends SurfaceView {
    private static final float MIN_GESTURE_ZOOM = 1.0f;
    private static final float MAX_GESTURE_ZOOM = 3.0f;

    private int resizeMode = RESIZE_MODE_FIT;
    private int baseHeight = 0;
    private int maxHeight = 0;
    private float videoAspectRatio = 0.0f;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float gestureZoom = 1.0f;
    private float gesturePanX = 0.0f;
    private float gesturePanY = 0.0f;

    public ExpandableSurfaceView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (videoAspectRatio == 0.0f) {
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        final boolean verticalVideo = videoAspectRatio < 1;
        // Use maxHeight only on non-fit resize mode and in vertical videos
        int height = maxHeight != 0
                && resizeMode != RESIZE_MODE_FIT
                && verticalVideo ? maxHeight : baseHeight;

        if (width == 0 || height == 0) {
            return;
        }

        final float viewAspectRatio = width / ((float) height);
        final float aspectDeformation = (videoAspectRatio / viewAspectRatio) - 1;
        scaleX = 1.0f;
        scaleY = 1.0f;

        if (resizeMode == RESIZE_MODE_FIT) {
            if (aspectDeformation > 0) {
                height = (int) (width / videoAspectRatio);
            } else {
                width = (int) (height * videoAspectRatio);
            }
        } else if (resizeMode == RESIZE_MODE_ZOOM) {
            if (aspectDeformation < 0) {
                scaleY = viewAspectRatio / videoAspectRatio;
            } else {
                scaleX = videoAspectRatio / viewAspectRatio;
            }
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    /**
     * Scale view only in {@link #onLayout} to make transition for ZOOM mode as smooth as possible.
     */
    @Override
    protected void onLayout(final boolean changed,
                            final int left, final int top, final int right, final int bottom) {
        setScaleX(scaleX * gestureZoom);
        setScaleY(scaleY * gestureZoom);
        clampGesturePanToBounds();
        setTranslationX(gesturePanX);
        setTranslationY(gesturePanY);
    }

    /**
     * @param base The height that will be used in every resize mode as a minimum height
     * @param max  The max height for vertical videos in non-FIT resize modes
     */
    public void setHeights(final int base, final int max) {
        if (baseHeight == base && maxHeight == max) {
            return;
        }
        baseHeight = base;
        maxHeight = max;
        requestLayout();
    }

    public void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int newResizeMode) {
        if (resizeMode == newResizeMode) {
            return;
        }

        resizeMode = newResizeMode;
        requestLayout();
    }

    @AspectRatioFrameLayout.ResizeMode
    public int getResizeMode() {
        return resizeMode;
    }

    public void setAspectRatio(final float aspectRatio) {
        if (videoAspectRatio == aspectRatio || aspectRatio == 0 || !Float.isFinite(aspectRatio)) {
            return;
        }

        videoAspectRatio = aspectRatio;
        requestLayout();
    }

    public void setGestureZoom(final float zoom) {
        final float clampedZoom = Math.max(MIN_GESTURE_ZOOM, Math.min(zoom, MAX_GESTURE_ZOOM));
        if (gestureZoom == clampedZoom) {
            return;
        }

        gestureZoom = clampedZoom;
        if (gestureZoom <= MIN_GESTURE_ZOOM) {
            gesturePanX = 0.0f;
            gesturePanY = 0.0f;
        } else {
            clampGesturePanToBounds();
        }
        requestLayout();
    }

    public void resetGestureZoom() {
        setGestureZoom(MIN_GESTURE_ZOOM);
    }

    public float getGestureZoom() {
        return gestureZoom;
    }

    public void panBy(final float deltaX, final float deltaY) {
        if (gestureZoom <= MIN_GESTURE_ZOOM) {
            return;
        }

        gesturePanX += deltaX;
        gesturePanY += deltaY;
        clampGesturePanToBounds();
        setTranslationX(gesturePanX);
        setTranslationY(gesturePanY);
    }

    private void clampGesturePanToBounds() {
        final float maxTranslationX = getMaxTranslationX();
        final float maxTranslationY = getMaxTranslationY();
        gesturePanX = Math.max(-maxTranslationX, Math.min(gesturePanX, maxTranslationX));
        gesturePanY = Math.max(-maxTranslationY, Math.min(gesturePanY, maxTranslationY));
    }

    private float getMaxTranslationX() {
        final int width = getMeasuredWidth();
        if (width == 0) {
            return 0.0f;
        }
        final float scaledWidth = width * scaleX * gestureZoom;
        return Math.max(0.0f, (scaledWidth - width) / 2.0f);
    }

    private float getMaxTranslationY() {
        final int height = getMeasuredHeight();
        if (height == 0) {
            return 0.0f;
        }
        final float scaledHeight = height * scaleY * gestureZoom;
        return Math.max(0.0f, (scaledHeight - height) / 2.0f);
    }
}

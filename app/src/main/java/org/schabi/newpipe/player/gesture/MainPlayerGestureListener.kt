package org.schabi.newpipe.player.gesture

import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.View.OnTouchListener
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import kotlin.math.abs
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.AudioReactor
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.ui.MainPlayerUi
import org.schabi.newpipe.util.ThemeHelper.getAndroidDimenPx
import org.schabi.newpipe.views.GestureRegionOverlayView

/**
 * GestureListener for the player
 *
 * While [BasePlayerGestureListener] contains the logic behind the single gestures
 * this class focuses on the visual aspect like hiding and showing the controls or changing
 * volume/brightness during scrolling for specific events.
 */
class MainPlayerGestureListener(
    private val playerUi: MainPlayerUi
) : BasePlayerGestureListener(playerUi), OnTouchListener {
    private var isMoving = false
    private var isMiddleSwipeCandidate = false
    private var hasTriggeredFullscreenSwipe = false
    private var isScaling = false
    private var lastMultiTouchFocusX = Float.NaN
    private var lastMultiTouchFocusY = Float.NaN
    private var touchDownY = Float.NaN
    private var activeGesturePortion = DisplayPortion.MIDDLE
    private val scaleGestureDetector = ScaleGestureDetector(
        player.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!playerUi.isFullscreen || player.currentState == Player.STATE_COMPLETED) {
                    return false
                }
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isScaling) {
                    return false
                }
                binding.surfaceView.setGestureZoom(
                    binding.surfaceView.gestureZoom * detector.scaleFactor
                )
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        }
    )

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val isMultiTouch = event.pointerCount > 1
        if (isMultiTouch || isScaling || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            scaleGestureDetector.onTouchEvent(event)
            handleMultiTouchPan(event)
            hideGestureRegionOverlay()

            if (isMoving) {
                isMoving = false
                onScrollEnd(event)
            }
            isMiddleSwipeCandidate = false

            return when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isScaling = false
                    resetMultiTouchFocus()
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    false
                }

                else -> {
                    v.parent?.requestDisallowInterceptTouchEvent(playerUi.isFullscreen)
                    true
                }
            }
        }

        super.onTouch(v, event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeGesturePortion = getDisplayPortion(event)
                isMiddleSwipeCandidate = activeGesturePortion == DisplayPortion.MIDDLE
                hasTriggeredFullscreenSwipe = false
                touchDownY = event.y
                binding.gestureRegionOverlay.resetSwipeMotion()
                resetMultiTouchFocus()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isMoving) {
                    isMoving = false
                    onScrollEnd(event)
                }
                isMiddleSwipeCandidate = false
                touchDownY = Float.NaN
                hideGestureRegionOverlay()
            }

            MotionEvent.ACTION_MOVE -> {
                maybeShowGestureRegionOverlay(event)
            }
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                v.parent?.requestDisallowInterceptTouchEvent(
                    playerUi.isFullscreen || isMiddleSwipeCandidate
                )
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
                false
            }

            else -> true
        }
    }

    private fun maybeShowGestureRegionOverlay(event: MotionEvent) {
        if (!playerUi.isFullscreen || player.currentState == Player.STATE_COMPLETED) {
            hideGestureRegionOverlay()
            return
        }

        if (touchDownY.isNaN() || abs(event.y - touchDownY) < OVERLAY_VISIBILITY_THRESHOLD) {
            return
        }

        binding.gestureRegionOverlay.setSelectedSegment(
            when (activeGesturePortion) {
                DisplayPortion.LEFT,
                DisplayPortion.LEFT_HALF -> GestureRegionOverlayView.SEGMENT_LEFT

                DisplayPortion.RIGHT,
                DisplayPortion.RIGHT_HALF -> GestureRegionOverlayView.SEGMENT_RIGHT

                DisplayPortion.MIDDLE -> GestureRegionOverlayView.SEGMENT_MIDDLE
            }
        )
        binding.gestureRegionOverlay.setSwipeMotionDeltaY(event.y - touchDownY)
        binding.gestureRegionActionText.text = getGestureActionLabel(activeGesturePortion)
        if (!binding.gestureRegionOverlay.isVisible) {
            binding.gestureRegionOverlay.isVisible = true
        }
        if (!binding.gestureRegionActionText.isVisible) {
            binding.gestureRegionActionText.isVisible = true
        }
    }

    private fun hideGestureRegionOverlay() {
        binding.gestureRegionOverlay.resetSwipeMotion()
        if (binding.gestureRegionOverlay.isVisible) {
            binding.gestureRegionOverlay.isVisible = false
        }
        if (binding.gestureRegionActionText.isVisible) {
            binding.gestureRegionActionText.isVisible = false
        }
    }

    private fun getGestureActionLabel(portion: DisplayPortion): String {
        val actionKey = when (portion) {
            DisplayPortion.LEFT,
            DisplayPortion.LEFT_HALF -> PlayerHelper.getActionForLeftGestureSide(player.context)

            DisplayPortion.RIGHT,
            DisplayPortion.RIGHT_HALF -> PlayerHelper.getActionForRightGestureSide(player.context)

            DisplayPortion.MIDDLE -> PlayerHelper.getActionForMiddleGesture(player.context)
        }

        return when (actionKey) {
            player.context.getString(R.string.brightness_control_key) ->
                player.context.getString(R.string.brightness)

            player.context.getString(R.string.volume_control_key) ->
                player.context.getString(R.string.volume)

            player.context.getString(R.string.maximize_control_key) ->
                player.context.getString(R.string.maximize)

            else -> player.context.getString(R.string.none)
        }
    }

    private fun handleMultiTouchPan(event: MotionEvent) {
        if (!playerUi.isFullscreen) {
            resetMultiTouchFocus()
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    lastMultiTouchFocusX = calculateMultiTouchFocusX(event)
                    lastMultiTouchFocusY = calculateMultiTouchFocusY(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount < 2) {
                    return
                }
                val focusX = calculateMultiTouchFocusX(event)
                val focusY = calculateMultiTouchFocusY(event)

                if (
                    !lastMultiTouchFocusX.isNaN() &&
                    !lastMultiTouchFocusY.isNaN() &&
                    binding.surfaceView.gestureZoom > MIN_ZOOM_FOR_PAN
                ) {
                    binding.surfaceView.panBy(
                        focusX - lastMultiTouchFocusX,
                        focusY - lastMultiTouchFocusY
                    )
                }
                lastMultiTouchFocusX = focusX
                lastMultiTouchFocusY = focusY
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                resetMultiTouchFocus()
            }
        }
    }

    private fun resetMultiTouchFocus() {
        lastMultiTouchFocusX = Float.NaN
        lastMultiTouchFocusY = Float.NaN
    }

    private fun calculateMultiTouchFocusX(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getX(i)
        }
        return sum / event.pointerCount
    }

    private fun calculateMultiTouchFocusY(event: MotionEvent): Float {
        var sum = 0f
        for (i in 0 until event.pointerCount) {
            sum += event.getY(i)
        }
        return sum / event.pointerCount
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (DEBUG) {
            Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")
        }

        if (isDoubleTapping) {
            return true
        }
        super.onSingleTapConfirmed(e)

        if (player.currentState != Player.STATE_BLOCKED) {
            onSingleTap()
        }
        return true
    }

    private fun onScrollVolume(distanceY: Float) {
        val bar: ProgressBar = binding.volumeProgressBar
        val audioReactor: AudioReactor = player.audioReactor

        // If we just started sliding, change the progress bar to match the system volume
        if (!binding.volumeRelativeLayout.isVisible) {
            val volumePercent: Float = audioReactor.volume / audioReactor.maxVolume.toFloat()
            bar.progress = (volumePercent * bar.max).toInt()
        }

        // Update progress bar
        binding.volumeProgressBar.incrementProgressBy(distanceY.toInt())

        // Update volume
        val currentProgressPercent: Float = bar.progress / bar.max.toFloat()
        val currentVolume = (audioReactor.maxVolume * currentProgressPercent).toInt()
        audioReactor.volume = currentVolume
        if (DEBUG) {
            Log.d(TAG, "onScroll().volumeControl, currentVolume = $currentVolume")
        }

        // Update player center image
        binding.volumeImageView.setImageDrawable(
            AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent <= 0 -> R.drawable.ic_volume_off
                    currentProgressPercent < 0.25 -> R.drawable.ic_volume_mute
                    currentProgressPercent < 0.75 -> R.drawable.ic_volume_down
                    else -> R.drawable.ic_volume_up
                }
            )
        )

        // Make sure the correct layout is visible
        if (!binding.volumeRelativeLayout.isVisible) {
            binding.volumeRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        binding.brightnessRelativeLayout.isVisible = false
    }

    private fun onScrollBrightness(distanceY: Float) {
        val parent: AppCompatActivity = playerUi.parentActivity.orElse(null) ?: return
        val window = parent.window
        val layoutParams = window.attributes
        val bar: ProgressBar = binding.brightnessProgressBar

        // Update progress bar
        val oldBrightness = layoutParams.screenBrightness
        bar.progress = (bar.max * oldBrightness.coerceIn(0f, 1f)).toInt()
        bar.incrementProgressBy(distanceY.toInt())

        // Update brightness
        val currentProgressPercent = bar.progress.toFloat() / bar.max
        layoutParams.screenBrightness = currentProgressPercent
        window.attributes = layoutParams

        // Save current brightness level
        PlayerHelper.setScreenBrightness(parent, currentProgressPercent)
        if (DEBUG) {
            Log.d(
                TAG,
                "onScroll().brightnessControl, " +
                    "currentBrightness = " + currentProgressPercent
            )
        }

        // Update player center image
        binding.brightnessImageView.setImageDrawable(
            AppCompatResources.getDrawable(
                player.context,
                when {
                    currentProgressPercent < 0.25 -> R.drawable.ic_brightness_low
                    currentProgressPercent < 0.75 -> R.drawable.ic_brightness_medium
                    else -> R.drawable.ic_brightness_high
                }
            )
        )

        // Make sure the correct layout is visible
        if (!binding.brightnessRelativeLayout.isVisible) {
            binding.brightnessRelativeLayout.animate(true, 200, AnimationType.SCALE_AND_ALPHA)
        }
        binding.volumeRelativeLayout.isVisible = false
    }

    override fun onScrollEnd(event: MotionEvent) {
        super.onScrollEnd(event)
        hideGestureRegionOverlay()
        if (binding.volumeRelativeLayout.isVisible) {
            binding.volumeRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
        if (binding.brightnessRelativeLayout.isVisible) {
            binding.brightnessRelativeLayout.animate(false, 200, AnimationType.SCALE_AND_ALPHA, 200)
        }
    }

    override fun onScroll(
        initialEvent: MotionEvent?,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (initialEvent == null) {
            return false
        }

        // Calculate heights of status and navigation bars
        val statusBarHeight = getAndroidDimenPx(player.context, "status_bar_height")
        val navigationBarHeight = getAndroidDimenPx(player.context, "navigation_bar_height")

        // Do not handle this event if initially it started from status or navigation bars
        val isTouchingStatusBar = initialEvent.y < statusBarHeight
        val isTouchingNavigationBar = initialEvent.y > (binding.root.height - navigationBarHeight)
        if (isTouchingStatusBar || isTouchingNavigationBar) {
            return false
        }

        val initialDisplayPortion = getDisplayPortion(initialEvent)
        val movedDistanceY = abs(movingEvent.y - initialEvent.y)
        val hasMoreHorizontalDistance = abs(distanceX) > abs(distanceY)

        if (initialDisplayPortion == DisplayPortion.MIDDLE) {
            if (!isMoving && (
                    hasMoreHorizontalDistance ||
                        movedDistanceY <= MIDDLE_MOVEMENT_THRESHOLD_DP
                        * player.context.resources.displayMetrics.density
                    )
            ) {
                return false
            }

            isMoving = true
            if (hasTriggeredFullscreenSwipe || player.currentState == Player.STATE_COMPLETED) {
                return true
            }

            // Keep legacy behavior: from non-fullscreen mini player, center swipe-up always opens
            // fullscreen even if left/right gesture action was changed.
            if (!playerUi.isFullscreen) {
                val isSwipingUp = initialEvent.y - movingEvent.y > MOVEMENT_THRESHOLD
                if (isSwipingUp) {
                    playerUi.performScreenRotationButtonAction()
                    hasTriggeredFullscreenSwipe = true
                }
                return true
            }

            val middleAction = PlayerHelper.getActionForMiddleGesture(player.context)
            if (middleAction == player.context.getString(R.string.none_control_key)) {
                return false
            }

            when (middleAction) {
                player.context.getString(R.string.brightness_control_key) -> {
                    onScrollBrightness(distanceY)
                }

                player.context.getString(R.string.volume_control_key) -> {
                    onScrollVolume(distanceY)
                }

                player.context.getString(R.string.maximize_control_key) -> {
                    toggleFullscreenBySwipeIfNeeded(initialEvent, movingEvent)
                }
            }
            return true
        }

        if (!isMoving && (movedDistanceY <= MOVEMENT_THRESHOLD || hasMoreHorizontalDistance)) {
            return false
        }

        if (!playerUi.isFullscreen || player.currentState == Player.STATE_COMPLETED) {
            return false
        }

        isMoving = true
        val sideAction = if (getDisplayHalfPortion(initialEvent) == DisplayPortion.RIGHT_HALF) {
            PlayerHelper.getActionForRightGestureSide(player.context)
        } else {
            PlayerHelper.getActionForLeftGestureSide(player.context)
        }

        if (sideAction == player.context.getString(R.string.maximize_control_key)) {
            if (!hasTriggeredFullscreenSwipe) {
                toggleFullscreenBySwipeIfNeeded(initialEvent, movingEvent)
            }
            return true
        }

        // -- Brightness and Volume control --
        if (getDisplayHalfPortion(initialEvent) == DisplayPortion.RIGHT_HALF) {
            when (PlayerHelper.getActionForRightGestureSide(player.context)) {
                player.context.getString(R.string.volume_control_key) ->
                    onScrollVolume(distanceY)

                player.context.getString(R.string.brightness_control_key) ->
                    onScrollBrightness(distanceY)
            }
        } else {
            when (PlayerHelper.getActionForLeftGestureSide(player.context)) {
                player.context.getString(R.string.volume_control_key) ->
                    onScrollVolume(distanceY)

                player.context.getString(R.string.brightness_control_key) ->
                    onScrollBrightness(distanceY)
            }
        }

        return true
    }

    private fun toggleFullscreenBySwipeIfNeeded(initialEvent: MotionEvent, movingEvent: MotionEvent) {
        val isSwipingUp = initialEvent.y - movingEvent.y > MOVEMENT_THRESHOLD
        val isSwipingDown = movingEvent.y - initialEvent.y > MOVEMENT_THRESHOLD
        if ((isSwipingUp && !playerUi.isFullscreen) || (isSwipingDown && playerUi.isFullscreen)) {
            playerUi.performScreenRotationButtonAction()
            hasTriggeredFullscreenSwipe = true
        }
    }

    override fun getDisplayPortion(e: MotionEvent): DisplayPortion {
        return when {
            e.x < binding.root.width / 3.0 -> DisplayPortion.LEFT
            e.x > binding.root.width * 2.0 / 3.0 -> DisplayPortion.RIGHT
            else -> DisplayPortion.MIDDLE
        }
    }

    override fun getDisplayHalfPortion(e: MotionEvent): DisplayPortion {
        return when {
            e.x < binding.root.width / 2.0 -> DisplayPortion.LEFT_HALF
            else -> DisplayPortion.RIGHT_HALF
        }
    }

    companion object {
        private val TAG = MainPlayerGestureListener::class.java.simpleName
        private val DEBUG = MainActivity.DEBUG
        private const val MOVEMENT_THRESHOLD = 40
        private const val MIN_ZOOM_FOR_PAN = 1.01f
        private const val OVERLAY_VISIBILITY_THRESHOLD = 10f
        private const val MIDDLE_MOVEMENT_THRESHOLD_DP = 72f
    }
}

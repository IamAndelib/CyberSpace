package com.cyberspace.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.abs

class WebViewSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var touchStartY = 0f
    private var declined = false
    private var directionDecided = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Kill nested scrolling entirely from the parent side.
    // WebView dispatches leftover fling momentum through this path,
    // which triggers false-positive refreshes on fast upward scrolls.
    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return false
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        // no-op
    }

    override fun onNestedScroll(
        target: View, dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int
    ) {
        // no-op
    }

    override fun onStopNestedScroll(target: View) {
        // no-op
    }

    // Only allow refresh on a deliberate downward pull.
    // Reject gestures until finger moves past touch slop so we can
    // reliably determine direction — prevents 1-2px jitter from
    // leaking through to super.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = ev.y
                declined = false
                directionDecided = false
                // Let super see ACTION_DOWN so it records mInitialDownY
                super.onInterceptTouchEvent(ev)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (declined) return false
                if (!directionDecided) {
                    val dy = ev.y - touchStartY
                    if (abs(dy) < touchSlop) {
                        // Not enough movement — block super from seeing this
                        return false
                    }
                    directionDecided = true
                    if (dy < 0) {
                        declined = true
                        return false
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}

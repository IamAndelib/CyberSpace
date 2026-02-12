package com.cyberspace.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class WebViewSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var touchStartY = 0f
    private var declined = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = ev.y
                declined = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (declined) return false
                // User is swiping upward — never intercept, let the WebView scroll
                if (ev.y < touchStartY) {
                    declined = true
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}

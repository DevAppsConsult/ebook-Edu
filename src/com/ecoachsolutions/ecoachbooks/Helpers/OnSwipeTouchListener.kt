package com.ecoachsolutions.ecoachbooks.Helpers

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

/**
 * Created by Daniel on 5/18/2014. (Source code from a forum but i forget which :( ). Likely StackOverflow
 */
open class OnSwipeTouchListener(ctx: Context) : View.OnTouchListener {
    private val gestureDetector: GestureDetector
    private val SWIPE_THRESHOLD = 100
    private val SWIPE_VELOCITY_THRESHOLD = 100

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        onTouchHandler()
        return gestureDetector.onTouchEvent(motionEvent)
    }

    init {
        gestureDetector = GestureDetector(ctx, GestureListener())
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            return false
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val result = false
            try {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                    }
                } else {
                    //let the webview handle its own scroll up and down options
                    super.onFling(e1, e2, velocityX, velocityY)
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }

            return result
        }

        //Handle scroll stop
        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            onScrollHandler()
            return false
        }

    }

    open fun onTouchHandler() {

    }


    fun onSwipeBottom() {

    }

    fun onSwipeTop() {

    }

    open fun onSwipeLeft() {

    }

    open fun onSwipeRight() {

    }

    open fun onScrollHandler() {

    }
}

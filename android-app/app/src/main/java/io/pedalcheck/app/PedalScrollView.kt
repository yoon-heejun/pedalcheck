package io.pedalcheck.app

import android.content.Context
import android.widget.ScrollView

/** A live status relayout must not scroll the driver back to the focused header. */
class PedalScrollView(context: Context) : ScrollView(context) {
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val previousY = scrollY
        super.onLayout(changed, left, top, right, bottom)
        if (!changed && previousY != scrollY) scrollTo(0, previousY)
    }
}

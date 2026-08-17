package com.havoc.rokidbus.plugin.otps

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OtpSettingsActivityUiTest {
    @Test
    fun backupButtonsRemainVisibleAfterLayout() {
        val activity = Robolectric.buildActivity(OtpSettingsActivity::class.java).setup().get()
        val root = activity.findViewById<View>(android.R.id.content)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)

        val export = findButton(root, "EXPORT SETTINGS")
        val import = findButton(root, "IMPORT SETTINGS")
        assertNotNull(export)
        assertNotNull(import)
        assertTrue("Export button must have visible width", export!!.measuredWidth > 0)
        assertTrue("Import button must have visible width", import!!.measuredWidth > 0)
    }

    private fun findButton(view: View, label: String): Button? {
        if (view is Button && view.text.toString() == label) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findButton(view.getChildAt(index), label)?.let { return it }
            }
        }
        return null
    }
}

package io.materia.examples.voxelcraft.android

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * Placeholder activity while the full VoxelCraft Vulkan renderer is ported to Android.
 * Launching the demo provides roadmap context instead of silently failing.
 */
class VoxelCraftActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = "VoxelCraft Android build is under active development.\n" +
                    "Current demo targets JVM & Web.\n\n" +
                    "Follow docs/private/voxelcraft-progress-tracking.md for status."
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundColor(0xFF101010.toInt())
            setPadding(48, 48, 48, 48)
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF101010.toInt())
            addView(
                message,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        setContentView(root)
    }
}

package io.github.lethe_labs.argos

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.lethe_labs.argos.ui.ArgosScreen
import io.github.lethe_labs.argos.ui.theme.ArgosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blocks screenshots and the recents thumbnail, so the seal words and
        // the reports never end up in the gallery or the task switcher of a
        // phone someone else may get their hands on.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            ArgosTheme {
                ArgosScreen()
            }
        }
    }
}

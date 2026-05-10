package org.debs.kalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.debs.kalog.feature.chat.presentation.platform.AndroidChatPlatformBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidChatPlatformBridge.register(this)

        setContent {
            App()
        }
    }
}

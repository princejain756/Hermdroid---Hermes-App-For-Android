package com.princejain.hermroid
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import com.princejain.hermroid.chat.ChatRoute
import com.princejain.hermroid.design.HermroidTheme
import com.princejain.hermroid.network.DefaultHermesConnectionManager
import com.princejain.hermroid.network.HermesConnectionState
import com.princejain.hermroid.onboarding.OnboardingRoute
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            HermroidTheme {
                val connection by DefaultHermesConnectionManager.state.collectAsState()
                when (val current = connection) {
                    is HermesConnectionState.Connected -> ChatRoute(
                        api = current.api,
                        onDisconnect = DefaultHermesConnectionManager::disconnect,
                    )
                    HermesConnectionState.Disconnected -> OnboardingRoute()
                }
            }
        }
    }
}

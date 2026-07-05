package com.princejain.hermroid
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.princejain.hermroid.design.HermroidTheme
import com.princejain.hermroid.onboarding.OnboardingRoute
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); WindowCompat.setDecorFitsSystemWindows(window, false); setContent { HermroidTheme { OnboardingRoute() } } }
}

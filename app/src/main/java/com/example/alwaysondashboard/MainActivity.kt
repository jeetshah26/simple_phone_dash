package com.example.alwaysondashboard

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.alwaysondashboard.ui.DashboardScreen
import com.example.alwaysondashboard.ui.theme.AlwaysOnDashboardTheme

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepScreenAwake()
        hideSystemBars()

        setContent {
            AlwaysOnDashboardTheme {
                DashboardScreen(
                    viewModel = dashboardViewModel
                )
            }
        }
    }

    private fun keepScreenAwake() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

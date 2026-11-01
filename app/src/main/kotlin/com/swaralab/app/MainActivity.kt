package com.swaralab.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.swaralab.app.nav.SwaraLabNav
import com.swaralab.ui.SwaraLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ink = Color.parseColor("#12110F")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ink),
            navigationBarStyle = SystemBarStyle.dark(ink),
        )
        val graph = (application as SwaraLabApplication).graph
        setContent {
            SwaraLabTheme {
                SwaraLabNav(graph)
            }
        }
    }
}

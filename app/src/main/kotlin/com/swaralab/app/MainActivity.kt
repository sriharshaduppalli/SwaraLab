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
        val paper = Color.parseColor("#F7F4EF")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(paper, paper),
            navigationBarStyle = SystemBarStyle.light(paper, paper),
        )
        val graph = (application as SwaraLabApplication).graph
        setContent {
            SwaraLabTheme {
                SwaraLabNav(graph)
            }
        }
    }
}

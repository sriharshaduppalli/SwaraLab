package com.swaralab.app

import android.app.Application
import com.swaralab.app.graph.SwaraLabGraph

class SwaraLabApplication : Application() {
    lateinit var graph: SwaraLabGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = SwaraLabGraph(this)
    }
}

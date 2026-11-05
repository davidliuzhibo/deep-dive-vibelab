package com.vrgames.deepsea.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.deepsea.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}

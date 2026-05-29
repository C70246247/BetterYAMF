package com.buildsession.betterYAMF

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.buildsession.betterYAMF.manager.utils.AppContext
import dagger.hilt.android.HiltAndroidApp

lateinit var application: Application

@HiltAndroidApp
open class Reyamf: Application() {

    init {
        application = this
        AppContext.context = this
    }

    override fun onCreate() {
        super.onCreate()

        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}

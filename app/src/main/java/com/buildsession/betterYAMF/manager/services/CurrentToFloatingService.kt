package com.buildsession.betterYAMF.manager.services

import android.content.Intent
import android.service.voice.VoiceInteractionService
import android.util.Log

class CurrentToFloatingService : VoiceInteractionService() {
    override fun onCreate() {
        super.onCreate()

        val intent = Intent()
        intent.action = "com.buildsession.betterYAMF.action.CURRENT_TO_WINDOW"
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        Log.d("reYAMFDWOO", "long pressed")

        sendBroadcast(intent)
    }

    override fun onReady() {
        super.onReady()

        Log.d("reYAMFWOO", "readyyyyyyyy")
    }
}

package com.buildsession.betterYAMF.manager.broadcastreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.buildsession.betterYAMF.application
import com.buildsession.betterYAMF.common.gson
import com.buildsession.betterYAMF.common.model.Config
import com.buildsession.betterYAMF.manager.applist.AppListWindow
import com.buildsession.betterYAMF.manager.services.YAMFManagerProxy
import com.buildsession.betterYAMF.manager.sidebar.Action
import com.buildsession.betterYAMF.manager.sidebar.SidebarMenuService
import com.buildsession.betterYAMF.manager.sidebar.SidebarService

class BootReceiver : BroadcastReceiver() {

    lateinit var config: Config

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            config = gson.fromJson(YAMFManagerProxy.configJson, Config::class.java)

            if (config.launchSideBarAtBoot) {
                application.startService(Intent(application, SidebarService::class.java))
            }
        }
    }
}

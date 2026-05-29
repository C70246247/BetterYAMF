package com.juanarton.batterysense.batterymonitorservice

import android.content.Context

private const val name = "SERVICE_KEY"
private const val key = "SERVICE_STATE"

fun setServiceState(context: Context, state: ServiceState) {
    val sharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    sharedPreferences.edit().let {
        it.putString(key, state.name)
        it.apply()
    }
}

fun getServiceState(context: Context): ServiceState {
    val sharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    val value = sharedPreferences.getString(key, ServiceState.STOPPED.name)?: ServiceState.STOPPED.name
    return ServiceState.valueOf(value)
}
package com.buildsession.betterYAMF.manager.core.data.source.local.room.dao

import android.content.ComponentName
import androidx.room.TypeConverter

class ComponentNameConverter {

    @TypeConverter
    fun fromComponentName(componentName: ComponentName?): String? {
        return componentName?.flattenToString()
    }

    @TypeConverter
    fun toComponentName(flattened: String?): ComponentName? {
        return flattened?.let { ComponentName.unflattenFromString(it) }
    }
}


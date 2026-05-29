package com.buildsession.betterYAMF.manager.core.data.source.local.room.dao

import android.content.pm.ActivityInfo
import android.os.Parcel
import android.util.Base64
import androidx.room.TypeConverter

class ActivityInfoConverter {
    @TypeConverter
    fun fromActivityInfo(activityInfo: ActivityInfo?): String? {
        if (activityInfo == null) return null
        val parcel = Parcel.obtain()
        activityInfo.writeToParcel(parcel, 0)
        val bytes = parcel.marshall()
        parcel.recycle()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    @TypeConverter
    fun toActivityInfo(data: String?): ActivityInfo? {
        if (data == null) return null
        val bytes = Base64.decode(data, Base64.DEFAULT)
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val activityInfo = ActivityInfo.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        return activityInfo
    }
}

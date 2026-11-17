package com.ilsecondodasinistra.parakeet

import android.app.Application
import com.ilsecondodasinistra.parakeet.alarm.AlarmSync
import com.ilsecondodasinistra.parakeet.alarm.Notifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ParakeetApp : Application() {
    @Inject lateinit var alarmSync: AlarmSync

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        alarmSync.start()
    }
}

package com.ilsecondodasinistra.parakeet

import android.app.Application
import com.ilsecondodasinistra.parakeet.alarm.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ParakeetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

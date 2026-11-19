package com.ilsecondodasinistra.parakeet.ui.postwake

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * The morning in progress: ring + list. Started by [com.ilsecondodasinistra.parakeet.ui.alarm.AlarmActivity]
 * right after Stop (over the lock screen when the user chose so), by the ongoing notification, or from the
 * main screen. Never shown without a session: it closes itself when none appears.
 */
@AndroidEntryPoint
class PostWakeActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    private val viewModel: PostWakeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // The theme window is light: without this a light frame flashes before the dark screen is drawn
        window.setBackgroundDrawable(ColorDrawable(0xFF080A06.toInt()))
        // One tiny read of a local preference: the flag must be set before the window is first shown
        setShowWhenLocked(runBlocking { settings.current().postWake.lockScreen })

        setContent {
            ParakeetTheme(darkTheme = true) {
                val ui by viewModel.state.collectAsStateWithLifecycle()
                val timeline = ui.timeline
                // The service creates the session right after Stop; if none shows up there is nothing to guide
                LaunchedEffect(ui.ready, timeline == null) {
                    if (ui.ready && timeline == null) {
                        delay(NO_SESSION_MILLIS)
                        finish()
                    }
                }
                LaunchedEffect(Unit) { viewModel.refresh() }
                if (timeline != null) {
                    PostWakeScreen(timeline, onAct = viewModel::act, onClose = ::finish)
                }
            }
        }
    }

    private companion object {
        const val NO_SESSION_MILLIS = 2_000L
    }
}

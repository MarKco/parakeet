package com.ilsecondodasinistra.parakeet.ui.postwake

import android.os.Bundle
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.Color
import android.app.KeyguardManager
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

    /** The phone is locked and this screen is above the lock screen: dark, like the alarm. Otherwise the app theme. */
    private val onLockScreen = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onLockScreen.value = isLocked()
        // One tiny read of a local preference: the flag must be set before the window is first shown
        setShowWhenLocked(runBlocking { settings.current().postWake.lockScreen })

        setContent {
            val dark = onLockScreen.value || isSystemInDarkTheme()
            ParakeetTheme(darkTheme = dark) {
                val background = MaterialTheme.colorScheme.background.toArgb()
                SideEffect {
                    // The theme window is light: without this a light frame flashes before the screen is drawn
                    window.setBackgroundDrawable((if (onLockScreen.value) LOCK_BACKGROUND else background).toDrawable())
                    enableEdgeToEdge(
                        statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                        navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    )
                }
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
                // Unlocking while this screen is up changes nothing in its lifecycle and the unlock broadcast does not
                // reach it: while it is above the lock screen, look at the lock once a second. From then on it is an
                // ordinary screen of the app.
                LaunchedEffect(onLockScreen.value) {
                    while (onLockScreen.value) {
                        delay(LOCK_POLL_MILLIS)
                        onLockScreen.value = isLocked()
                    }
                }
                if (timeline != null) {
                    PostWakeScreen(timeline, onLockScreen = onLockScreen.value, onAct = viewModel::act, onStart = viewModel::start, onClose = ::finish)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onLockScreen.value = isLocked()
    }

    private fun isLocked(): Boolean = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private companion object {
        const val NO_SESSION_MILLIS = 2_000L
        const val LOCK_POLL_MILLIS = 1_000L
        const val LOCK_BACKGROUND = 0xFF080A06.toInt()
    }
}

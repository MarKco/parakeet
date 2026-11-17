package com.ilsecondodasinistra.parakeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ilsecondodasinistra.parakeet.ui.about.AboutScreen
import com.ilsecondodasinistra.parakeet.ui.main.MainScreen
import com.ilsecondodasinistra.parakeet.ui.main.MainViewModel
import com.ilsecondodasinistra.parakeet.ui.onboarding.OnboardingScreen
import com.ilsecondodasinistra.parakeet.ui.routines.RoutineEditorScreen
import com.ilsecondodasinistra.parakeet.ui.routines.RoutinesScreen
import com.ilsecondodasinistra.parakeet.ui.settings.SettingsScreen
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Keeps the splash screen until the first state (after the one-time data migration) is ready. */
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParakeetTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ParakeetRoot(onReady = { ready = true })
                }
            }
        }
    }
}

@Serializable private data object MainKey : NavKey
@Serializable private data object SettingsKey : NavKey
@Serializable private data object AboutKey : NavKey
@Serializable private data object RoutinesKey : NavKey
/** [forDay]: epoch day the new routine is created for, or -1. */
@Serializable private data class RoutineEditorKey(val id: Long, val forDay: Long) : NavKey

private const val NAV_MILLIS = 320

@Composable
private fun ParakeetRoot(onReady: () -> Unit, viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.ready) { if (state.ready) onReady() }
    val backStack = rememberNavBackStack(MainKey)
    val onBack: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() }

    when {
        !state.ready -> Box(Modifier.fillMaxSize())
        state.showOnboarding -> OnboardingScreen(viewModel)
        else -> NavDisplay(
            backStack = backStack,
            onBack = onBack,
            // Forward: the new screen slides in from the right over the current one
            transitionSpec = {
                (slideInHorizontally(tween(NAV_MILLIS)) { it / 3 } + fadeIn(tween(NAV_MILLIS))) togetherWith
                    (slideOutHorizontally(tween(NAV_MILLIS)) { -it / 6 } + fadeOut(tween(NAV_MILLIS / 2)))
            },
            // Back (button or arrow): the opposite movement
            popTransitionSpec = {
                (slideInHorizontally(tween(NAV_MILLIS)) { -it / 6 } + fadeIn(tween(NAV_MILLIS))) togetherWith
                    (slideOutHorizontally(tween(NAV_MILLIS)) { it / 3 } + fadeOut(tween(NAV_MILLIS / 2)))
            },
            // Predictive back: while dragging, the screen shrinks and moves with the finger, staying opaque
            // (it fades only at the very end), and the screen underneath grows back into place.
            predictivePopTransitionSpec = {
                scaleIn(tween(NAV_MILLIS), initialScale = 0.94f) togetherWith
                    (scaleOut(tween(NAV_MILLIS), targetScale = 0.86f) +
                        slideOutHorizontally(tween(NAV_MILLIS)) { it / 8 } +
                        fadeOut(keyframes { durationMillis = NAV_MILLIS; 1f at NAV_MILLIS * 3 / 4 }))
            },
            entryProvider = entryProvider {
                entry<MainKey> {
                    MainScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenSettings = { backStack.add(SettingsKey) },
                        onOpenAbout = { backStack.add(AboutKey) },
                        onOpenRoutines = { backStack.add(RoutinesKey) },
                        onEditRoutine = { id, date -> backStack.add(RoutineEditorKey(id, date?.toEpochDay() ?: -1L)) },
                    )
                }
                entry<RoutinesKey> {
                    RoutinesScreen(state, viewModel, onBack = onBack, onEdit = { backStack.add(RoutineEditorKey(it, -1L)) })
                }
                entry<RoutineEditorKey> { key ->
                    RoutineEditorScreen(
                        routineId = key.id,
                        forDate = key.forDay.takeIf { it >= 0 }?.let(java.time.LocalDate::ofEpochDay),
                        state = state,
                        viewModel = viewModel,
                        onBack = onBack,
                    )
                }
                entry<SettingsKey> { SettingsScreen(state, viewModel, onBack = onBack) }
                entry<AboutKey> { AboutScreen(onBack = onBack) }
            },
        )
    }
}

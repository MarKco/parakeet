package com.ilsecondodasinistra.parakeet.ui.postwake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilsecondodasinistra.parakeet.alarm.PostWake
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.data.TaskRepository
import com.ilsecondodasinistra.parakeet.domain.PostWakeAction
import com.ilsecondodasinistra.parakeet.domain.TimelineState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [timeline] is null until a session exists; [ready] says the first read of the settings is done. */
data class PostWakeUi(val timeline: TimelineState? = null, val ready: Boolean = false)

@HiltViewModel
class PostWakeViewModel @Inject constructor(
    settings: SettingsRepository,
    tasks: TaskRepository,
    private val postWake: PostWake,
) : ViewModel() {

    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(1000) } }

    val state: StateFlow<PostWakeUi> = combine(
        settings.settings.map { it.postWakeSession }.distinctUntilChanged(),
        tasks.tasks,
        ticker,
    ) { session, catalog, now ->
        PostWakeUi(session?.let { PostWake.timeline(it, catalog, now) }, ready = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PostWakeUi())

    fun act(taskId: Long, action: PostWakeAction) {
        viewModelScope.launch { postWake.act(taskId, action) }
    }

    /** The user begins another activity without finishing the current one. */
    fun start(taskId: Long) {
        viewModelScope.launch { postWake.start(taskId) }
    }

    /** The screen is on: make sure the notification and the next wake-up match what it shows. */
    fun refresh() {
        viewModelScope.launch { postWake.refresh() }
    }
}

package com.tellyo.app.core.time

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One foreground clock for programme windows, highlighting and displayed time. */
@Singleton
class AppClock @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _now = MutableStateFlow(Instant.now())
    val now: StateFlow<Instant> = _now.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        tick()
        job = scope.launch {
            while (true) {
                delay(30_000L)
                tick()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    internal fun tick(instant: Instant = Instant.now()) {
        _now.value = instant
    }
}

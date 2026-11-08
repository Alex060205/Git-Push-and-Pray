package com.example.vitala.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * An exercise adjusted for one day (e.g. knee bends cut from 2 sets to 1).
 */
data class ExerciseItem(
    val exerciseId: String,              // PrescribedExercise.id
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val durationMinutes: Int? = null,
) {
    init {
        require(sets != null || reps != null || durationMinutes != null) {
            "Exercise item must have at least one of sets, reps, or duration"
        }
    }

    var status: SessionStatus = SessionStatus.PLANNED

    @OptIn(ExperimentalAtomicApi::class)
    private val isStarted = AtomicBoolean(false)
    private var timerJob: Job? = null

    @OptIn(ExperimentalAtomicApi::class)
    fun start(scope: CoroutineScope, onTick: (Long) -> Unit) {
        check(isStarted.compareAndSet(false, true)) { "Exercise already started" }

        timerJob = scope.launch {
            var seconds = 0L
            while (isActive) {
                delay(1000L)
                seconds++
                onTick(seconds)
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun stop() {
        if (isStarted.compareAndSet(true, false)) {
            timerJob?.cancel()
            timerJob = null
        }
    }
}

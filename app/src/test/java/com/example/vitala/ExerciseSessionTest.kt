package com.example.vitala

import com.example.vitala.data.ExerciseItem
import com.example.vitala.data.ExerciseSession
import com.example.vitala.data.PainFeeling
import com.example.vitala.data.SessionStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExerciseSessionTest {

    @Test
    fun testExerciseItemValidation() {
        val item = ExerciseItem(exerciseId = "1", name = "Squat", sets = 3, reps = 10)
        assertEquals("Squat", item.name)
        assertEquals(3, item.sets)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testExerciseItemInvalidThrowsException() {
        ExerciseItem(exerciseId = "1", name = "Invalid", sets = null, reps = null, durationMinutes = null)
    }

    @Test
    fun testExerciseSessionMarking() {
        val item = ExerciseItem(exerciseId = "1", name = "Stretch", durationMinutes = 5)
        val session = ExerciseSession(items = listOf(item))

        assertEquals(SessionStatus.PLANNED, session.status)
        assertNull(session.painDuring)

        session.markDone(PainFeeling.SOME, PainFeeling.NONE)
        assertEquals(SessionStatus.DONE, session.status)
        assertEquals(PainFeeling.SOME, session.painDuring)
        assertEquals(PainFeeling.NONE, session.painAfter)

        session.markSkipped()
        assertEquals(SessionStatus.SKIPPED, session.status)
    }

    @Test
    fun testExerciseItemTimer() = runBlocking {
        val item = ExerciseItem(exerciseId = "1", name = "Plank", durationMinutes = 1)
        item.start(this) { _ -> }
        item.stop()
    }
}

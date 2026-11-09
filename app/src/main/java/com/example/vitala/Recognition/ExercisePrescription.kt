package com.example.vitala.Recognition

/**
 * What the model extracts from one exercise prescription. In memory only.
 *
 * Designed around a real-style physio handout: time-based dosing with a
 * phased schedule, rules for adjusting the next session, daily tracking
 * instructions and tiered stop rules. Sets/reps fields stay for sheets
 * that use them.
 *
 * All amounts are Strings, copied as written ("Up to 3 minutes", "3x10", "BID").
 * Empty string / empty list = not on the sheet.
 */
data class ExercisePrescription(
    // Header
    val programName: String,
    val bodyArea: String,          // incl. side, e.g. "right hand/wrist"
    val clinicalContext: String,
    val treatmentGoals: String,
    val startDate: String,
    val reviewPlan: String,        // when / under what conditions to be reviewed
    val prescriber: String,        // copied as written, even if it looks wrong
    val clinicContact: String,
    val dateApproved: String,

    // What to do
    val exercises: List<Exercise>,

    // How much, and how it changes
    val schedule: List<SchedulePhase>,
    val limits: List<String>,                // hard caps and floors: "do not exceed 4 min"
    val adjustmentRules: List<AdjustmentRule>,
    val trackingInstructions: List<String>,  // what to record, when

    // Safety
    val stopRules: List<StopRule>,
    val generalPrecautions: List<String>,

    // Catch-alls
    val otherNotes: List<String>,
    val unreadable: List<String>
)

data class Exercise(
    val name: String,
    val setup: String,             // starting position, posture
    val steps: List<String>,       // movement steps in order
    val pace: String,              // "10-15 s per cycle"
    val sets: String,
    val reps: String,
    val holdOrDuration: String,
    val frequency: String,
    val intensityOrLoad: String,
    val precautions: List<String>
)

/** One row of a progression table, e.g. "Days 8-14: up to 3 minutes, once daily". */
data class SchedulePhase(
    val period: String,            // "Days 8-14", "Week 2"
    val dose: String,              // "Up to 3 minutes total session time"
    val frequency: String,         // "once daily"
    val note: String               // e.g. "ceiling, not a target"
)

/** One if/then rule for changing the next session, kept verbatim. */
data class AdjustmentRule(
    val label: String,             // "A", "B"... or empty
    val condition: String,
    val action: String
)

data class StopRule(
    val level: StopLevel,
    val trigger: String,               // copied in full from the sheet
    /** 2-4 word label shown in bold above [trigger]. Null for plans saved before labels existed. */
    val summary: String? = null
)

/** Ordered from most to least urgent. */
enum class StopLevel(val wire: String, val title: String) {
    URGENT_CARE("urgent_care", "Seek urgent care"),
    STOP_AND_SEEK_ASSESSMENT("stop_and_seek_assessment", "Stop and get assessed"),
    CONTACT_TEAM("contact_team", "Contact your physio team"),
    PAUSE("pause", "Pause the session");

    /** General title for a safety question when the plan has no short label of its own. */
    val questionTitle: String
        get() = when (this) {
            URGENT_CARE -> "Urgent warning signs"
            STOP_AND_SEEK_ASSESSMENT -> "Signs to get checked"
            CONTACT_TEAM -> "Problems that keep going"
            PAUSE -> "Discomfort that won't settle"
        }

    companion object {
        /** Unknown values map to the most cautious level the sheet could mean. */
        fun fromWire(s: String) = entries.firstOrNull { it.wire == s } ?: STOP_AND_SEEK_ASSESSMENT
    }
}
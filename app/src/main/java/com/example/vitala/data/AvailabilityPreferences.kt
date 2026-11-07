import com.example.vitala.TimeBlock
import java.time.DayOfWeek
import java.time.LocalTime

/** Times the patient does not want exercises suggested ("Times to avoid" screen).*/
data class AvailabilityPreferences(
    val blocksToAvoid: List<TimeBlock> = emptyList()
) {
    /**
     * determines whether or  not the give time block is available
     * @param day, in the type DayofWeek, e.g. DayOfWeek.MONDAY
     * @param time,
     */
    fun isAvoided(day: DayOfWeek, time: LocalTime): Boolean =
        blocksToAvoid.any { it.contains(day, time) }

    fun add(block: TimeBlock) = copy(blocksToAvoid = blocksToAvoid + block)

    fun remove(blockId: String) = copy(blocksToAvoid = blocksToAvoid.filterNot { it.id == blockId })
}
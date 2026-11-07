package com.example.vitala.data


/**
 * One finding, e.g. "You felt better after nights with 7 or more hours of sleep."
 * @param factor Factor that contributed to the finding
 * @param painDifference Difference in pain before and after the finding
 * @param text Text to display to the patient
 * @constructor Creates a new insight object with the given parameters
 */
data class Insight(
    val factor: InsightFactor,
    val painDifference: Double,          // avg pain without factor - avg pain with factor
    val text: String
)
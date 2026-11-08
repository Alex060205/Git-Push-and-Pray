package com.example.vitala.pacing

enum class Match {
    YES,
    NO,
    UNKNOWN
}

enum class Metric {
    PAIN,
    FATIGUE,
    INTERFERENCE,
    PAIN_CHANGE,
    FATIGUE_CHANGE,
    INTERFERENCE_CHANGE,
    PEG_PAIN,
    PEG_ENJOYMENT,
    PEG_ACTIVITY
}

enum class Category {
    SYMPTOMS,
    RECOVERY,
    CONTEXT_CHANGED
}

enum class Comparison {
    LESS_OR_EQUAL,
    GREATER_THAN
}

data class Facts(
    val numbers: Map<Metric, Double> = emptyMap(),
    val categories: Map<Category, String> = emptyMap(),
    val answers: Map<String, Boolean> = emptyMap()
)

sealed interface Condition {
    fun evaluate(facts: Facts): Match
}

data class NumberCondition(
    val metric: Metric,
    val comparison: Comparison,
    val threshold: Double
) : Condition {
    init {
        require(threshold.isFinite())
    }

    override fun evaluate(facts: Facts): Match {
        val value = facts.numbers[metric] ?: return Match.UNKNOWN

        val matches = when (comparison) {
            Comparison.LESS_OR_EQUAL -> value <= threshold
            Comparison.GREATER_THAN -> value > threshold
        }

        return if (matches) Match.YES else Match.NO
    }
}

data class CategoryCondition(
    val category: Category,
    val acceptedValues: Set<String>
) : Condition {
    init {
        require(acceptedValues.isNotEmpty())
    }

    override fun evaluate(facts: Facts): Match {
        val value = facts.categories[category] ?: return Match.UNKNOWN
        return if (value in acceptedValues) Match.YES else Match.NO
    }
}

data class AnswerCondition(
    val questionId: String,
    val expectedAnswer: Boolean = true
) : Condition {
    override fun evaluate(facts: Facts): Match {
        val answer = facts.answers[questionId] ?: return Match.UNKNOWN
        return if (answer == expectedAnswer) Match.YES else Match.NO
    }
}

data class AllConditions(
    val conditions: List<Condition>
) : Condition {
    init {
        require(conditions.isNotEmpty())
    }

    override fun evaluate(facts: Facts): Match {
        val results = conditions.map { it.evaluate(facts) }

        return when {
            Match.NO in results -> Match.NO
            Match.UNKNOWN in results -> Match.UNKNOWN
            else -> Match.YES
        }
    }
}

data class AnyCondition(
    val conditions: List<Condition>
) : Condition {
    init {
        require(conditions.isNotEmpty())
    }

    override fun evaluate(facts: Facts): Match {
        val results = conditions.map { it.evaluate(facts) }

        return when {
            Match.YES in results -> Match.YES
            Match.UNKNOWN in results -> Match.UNKNOWN
            else -> Match.NO
        }
    }
}
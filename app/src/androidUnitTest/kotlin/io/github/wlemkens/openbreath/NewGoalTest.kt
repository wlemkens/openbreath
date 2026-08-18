package io.github.wlemkens.openbreath

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What "Add a goal" hands you. A one-line rule, but the kind that drifts back the moment someone
 * tidies Goal's own defaults to match it — which is exactly what must not happen, since those are
 * also what a stored goal falls back to.
 */
class NewGoalTest {
    @Test
    fun `a new goal is one sitting a day`() {
        val goal = newGoal(emptyList())
        assertEquals(GoalMetric.SITTINGS, goal.metric)
        assertEquals(GoalPeriod.DAY, goal.period)
        assertEquals(1, goal.target)
        // and it survives the coercion every goal is put through on the way to being stored
        assertEquals(goal, goal.sane())
        assertEquals("1 sitting a day", goal.description)
    }

    @Test
    fun `each new goal takes an id no existing one is using`() {
        val existing = listOf(newGoal(emptyList()), Goal(id = 7, target = 5))
        assertEquals(8, newGoal(existing).id)
        // the first goal on a fresh phone still gets a real id rather than zero, which EVERY_DAY uses
        assertEquals(1, newGoal(emptyList()).id)
    }
}

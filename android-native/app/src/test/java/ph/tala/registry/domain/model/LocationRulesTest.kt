package ph.tala.registry.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bounds, finiteness and manual-text rules for D15 coordinate capture. */
class LocationRulesTest {
    @Test fun validGpsFixPasses() {
        assertNull(LocationRules.coordinateProblem(14.5995, 120.9842, 12.0))
        assertNull(LocationRules.coordinateProblem(0.0, 0.0, 0.0))
        assertNull(LocationRules.coordinateProblem(90.0, 180.0, null))
        assertNull(LocationRules.coordinateProblem(-90.0, -180.0, null))
    }

    @Test fun outOfRangeCoordinatesAreRejected() {
        assertNotNull(LocationRules.coordinateProblem(90.0001, 0.0, null))
        assertNotNull(LocationRules.coordinateProblem(-90.0001, 0.0, null))
        assertNotNull(LocationRules.coordinateProblem(0.0, 180.0001, null))
        assertNotNull(LocationRules.coordinateProblem(0.0, -180.0001, null))
    }

    @Test fun nonFiniteValuesAreRejected() {
        assertNotNull(LocationRules.coordinateProblem(Double.NaN, 0.0, null))
        assertNotNull(LocationRules.coordinateProblem(0.0, Double.POSITIVE_INFINITY, null))
        assertNotNull(LocationRules.coordinateProblem(0.0, 0.0, Double.NaN))
        assertNotNull(LocationRules.coordinateProblem(0.0, 0.0, Double.NEGATIVE_INFINITY))
    }

    @Test fun accuracyMustBeFiniteAndNonNegative() {
        assertNotNull(LocationRules.coordinateProblem(0.0, 0.0, -0.1))
        assertNull(LocationRules.coordinateProblem(0.0, 0.0, null))
    }

    @Test fun manualTextParsesStrictly() {
        assertEquals(14.5, LocationRules.parse(" 14.5 "))
        assertNull(LocationRules.parse(""))
        assertNull(LocationRules.parse("not a number"))
        assertNull(LocationRules.parse("1,2")) // No locale comma parsing.
        assertNull(LocationRules.parse(Double.NaN.toString()))
        assertNull(LocationRules.parse("1e400")) // Infinity text is not a coordinate.
    }

    @Test fun manualProblemsNameTheFirstBadField() {
        assertTrue(LocationRules.manualProblem("", "120.9")!!.startsWith("Latitude"))
        assertTrue(LocationRules.manualProblem("14.5", "abc")!!.startsWith("Longitude"))
        assertTrue(LocationRules.manualProblem("95", "120.9")!!.startsWith("Latitude"))
        assertTrue(LocationRules.manualProblem("14.5", "-200")!!.startsWith("Longitude"))
        assertNull(LocationRules.manualProblem("14.5995", "120.9842"))
    }
}

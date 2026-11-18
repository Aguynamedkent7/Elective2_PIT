package app.numlab.ui

import app.numlab.ui.workbench.decrementMaxIterations
import app.numlab.ui.workbench.isValidTolerance
import app.numlab.ui.workbench.toleranceDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbenchStateTest {
    @Test
    fun customToleranceIsDisplayedWithoutRounding() {
        assertEquals("10⁻⁵", toleranceDisplay("1e-5"))
        assertEquals("0.003", toleranceDisplay("0.003"))
        assertEquals("5e-4", toleranceDisplay("5e-4"))
    }

    @Test
    fun toleranceMustBePositiveAndFinite() {
        assertTrue(isValidTolerance("5e-4"))
        assertFalse(isValidTolerance("0"))
        assertFalse(isValidTolerance("-1e-5"))
        assertFalse(isValidTolerance("NaN"))
    }

    @Test
    fun maxIterationDecrementNeverIncreasesTheValue() {
        assertEquals(1, decrementMaxIterations(3))
        assertEquals(5, decrementMaxIterations(15))
        assertEquals(90, decrementMaxIterations(100))
    }
}

package app.numlab.ui

import app.numlab.domain.roots.RootRow
import app.numlab.ui.components.rootTable
import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #13: the xₙ column must use the shared formatter, not plain toFixed. */
class RootTableTest {

    private fun xCell(x: Double, dp: Int = 6): String {
        val (_, cells) = rootTable(1e-5, listOf(RootRow(n = 1, x = x, fx = 0.0, abs = null, rel = null)), dp)
        return cells.single()[1].text
    }

    @Test
    fun tinyRootUsesScientificNotationInsteadOfZeros() {
        assertEquals("1.000e-9", xCell(1e-9))
        assertEquals("-1.000e-9", xCell(-1e-9))
    }

    @Test
    fun hugeValueUsesScientificNotationSoItFitsTheColumn() {
        assertEquals("1.000e+12", xCell(1e12))
    }

    @Test
    fun ordinaryValuesKeepFixedDecimalsWithAtLeastFour() {
        assertEquals("1.521380", xCell(1.5213797068, dp = 6))
        assertEquals("1.5214", xCell(1.5213797068, dp = 2))
    }

    @Test
    fun exactZeroShowsZero() {
        assertEquals("0", xCell(0.0))
    }
}

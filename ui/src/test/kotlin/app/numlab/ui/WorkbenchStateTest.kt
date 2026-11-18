package app.numlab.ui

import app.numlab.domain.interp.PointRow
import app.numlab.ui.workbench.pointCount
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkbenchStateTest {
    @Test
    fun pointCountAcceptsConstantExpressionsAndUnicodeMinus() {
        assertEquals(
            2,
            pointCount(listOf(PointRow("pi/2", "−1"), PointRow("1", "sqrt(2)"), PointRow("x", "3"))),
        )
    }
}

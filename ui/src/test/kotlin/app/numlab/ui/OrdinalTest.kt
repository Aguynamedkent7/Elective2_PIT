package app.numlab.ui

import app.numlab.ui.components.ordinalRes
import org.junit.Assert.assertEquals
import org.junit.Test

/** Divided-difference headers: no "21th" once a table has more than 20 orders. */
class OrdinalTest {

    @Test
    fun suffixFollowsLastDigitExceptTeens() {
        mapOf(
            1 to R.string.ordinal_st, 2 to R.string.ordinal_nd, 3 to R.string.ordinal_rd, 4 to R.string.ordinal_th,
            11 to R.string.ordinal_th, 12 to R.string.ordinal_th, 13 to R.string.ordinal_th,
            21 to R.string.ordinal_st, 22 to R.string.ordinal_nd, 23 to R.string.ordinal_rd, 24 to R.string.ordinal_th,
            101 to R.string.ordinal_st, 111 to R.string.ordinal_th, 112 to R.string.ordinal_th,
        ).forEach { (n, res) -> assertEquals("n = $n", res, ordinalRes(n)) }
    }
}

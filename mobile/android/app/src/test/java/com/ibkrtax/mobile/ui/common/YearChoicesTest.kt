package com.ibkrtax.mobile.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class YearChoicesTest {
    @Test
    fun fiveYearsShowTheNewestThreeDirectly() {
        val choices = YearChoices.of(listOf(2022, 2023, 2024, 2025, 2026))
        assertEquals(listOf(2026, 2025, 2024), choices.direct)
        assertEquals(listOf(2023, 2022), choices.older)
    }

    @Test
    fun yearsMoreThanTwoBeforeTheNewestGoToTheSelector() {
        val choices = YearChoices.of(listOf(2021, 2023, 2026))
        assertEquals(listOf(2026), choices.direct)
        assertEquals(listOf(2023, 2021), choices.older)
    }

    @Test
    fun fewYearsAreAllDirectAndNoneIsEmpty() {
        assertEquals(YearChoices(listOf(2025, 2024), emptyList()), YearChoices.of(listOf(2024, 2025, 2025)))
        assertEquals(YearChoices(emptyList(), emptyList()), YearChoices.of(emptyList()))
    }
}

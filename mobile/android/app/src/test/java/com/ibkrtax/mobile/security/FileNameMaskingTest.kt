package com.ibkrtax.mobile.security

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameMaskingTest {
    @Test
    fun masksAccountNumbersInIbkrFileNames() {
        assertEquals("U•••4567_2023.csv", FileNameMasking.mask("U1234567_2023.csv"))
        assertEquals("U••••5678.csv", FileNameMasking.mask("U12345678.csv"))
        assertEquals("DU•••4567_activity.csv", FileNameMasking.mask("DU1234567_activity.csv"))
        assertEquals("Flex U•••4567 2023.csv", FileNameMasking.mask("Flex U1234567 2023.csv"))
    }

    @Test
    fun leavesOtherNamesAlone() {
        assertEquals("valid_basic.csv", FileNameMasking.mask("valid_basic.csv"))
        assertEquals("report_20230101_20231231.csv", FileNameMasking.mask("report_20230101_20231231.csv"))
        assertEquals("Q12.csv", FileNameMasking.mask("Q12.csv"))
    }
}

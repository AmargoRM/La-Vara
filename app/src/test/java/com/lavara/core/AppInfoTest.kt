package com.lavara.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppInfoTest {
    @Test
    fun versionLabel_muestraNombreYNumero() {
        assertEquals(
            "Versión 0.1.42 (compilación 42)",
            AppInfo.versionLabel("0.1.42", 42),
        )
    }
}

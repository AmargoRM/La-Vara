package com.lavara.system

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PrivacyTest {

    /** Si vuelve a estar encendido, Android sube automatizaciones y registros a la nube de Google. */
    @Test
    fun respaldoAutomaticoDeAndroid_apagado() {
        val flags = RuntimeEnvironment.getApplication().applicationInfo.flags
        assertEquals(0, flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}

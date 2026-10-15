package com.lavara.system.connectivity

import com.lavara.triggers.Trigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothReceiverFilterTest {

    private val carro = Trigger.Bluetooth("00:11:22:AA:BB:CC", "Carro")
    private val cualquiera = Trigger.Bluetooth()

    @Test
    fun sinAutomatizacionesDeBluetooth_noSeRegistraNada() {
        assertFalse(BluetoothReceiver.shouldHandle(bonded = true, address = "00:11:22:AA:BB:CC", activeTriggers = emptyList()))
    }

    @Test
    fun aparatoVinculado_cuenta() {
        assertTrue(BluetoothReceiver.shouldHandle(bonded = true, address = "99:99:99:99:99:99", activeTriggers = listOf(carro)))
    }

    @Test
    fun aparatoAjenoQuePasaCerca_seIgnora_aunqueHayaUnaDeCualquierAparato() {
        assertFalse(BluetoothReceiver.shouldHandle(bonded = false, address = "99:99:99:99:99:99", activeTriggers = listOf(carro, cualquiera)))
    }

    @Test
    fun aparatoNoVinculadoPeroElegidoEnUnaAutomatizacion_cuenta() {
        assertTrue(BluetoothReceiver.shouldHandle(bonded = false, address = "00:11:22:aa:bb:cc", activeTriggers = listOf(carro)))
    }
}

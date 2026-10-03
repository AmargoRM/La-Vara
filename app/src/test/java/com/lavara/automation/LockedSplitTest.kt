package com.lavara.automation

import com.lavara.actions.Action
import com.lavara.actions.MediaCommand
import com.lavara.actions.VolumeStream
import com.lavara.triggers.Trigger
import com.lavara.triggers.TriggerEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedSplitTest {

    private val clock = FakeClock()
    private val device = FakeDevice(locked = true)
    private val notify = Action.ShowNotification("Spotify")
    private val open = Action.OpenApp("com.spotify.music")
    private val wait = Action.Delay(5)
    private val volume = Action.SetVolume(VolumeStream.MEDIA, 65)
    private val play = Action.MediaControl(MediaCommand.PLAY)

    // Lo que el usuario armó: audífonos → notificación, abrir Spotify, esperar, volumen, reproducir.
    private val audifonos = Automation(
        id = "audifonos", name = "Audífonos", trigger = Trigger.Time("08:00"),
        actions = listOf(notify, open, wait, volume, play),
    )

    @Test
    fun separa_loQueNoNecesitaPantallaCorreYa() {
        val split = LockedSplit.of(audifonos.actions)!!
        assertEquals(setOf(0, 2, 3, 4), split.now)
        assertEquals(setOf(1), split.afterUnlock)
    }

    @Test
    fun esperasEntreAccionesQueAbren_vanConElDesbloqueo() {
        val tap = Action.TapInApp("com.whatsapp", "Enviar")
        val split = LockedSplit.of(listOf(volume, open, wait, tap, wait))!!
        assertEquals(setOf(1, 2, 3), split.afterUnlock)
        assertEquals(setOf(0, 2, 4), split.now)
    }

    @Test
    fun soloNotificacionYAbrir_esperaEntera() {
        assertNull(LockedSplit.of(listOf(notify, open)))
        assertNull(LockedSplit.of(listOf(notify, volume)))
        assertNull(LockedSplit.of(listOf(volume, open, Action.Delay(60))))
    }

    @Test
    fun bloqueado_reproduceYaYAbreAlDesbloquear() = runBlocking {
        val executor = RecordingExecutor()
        val engine = AutomationEngine(ListSource(listOf(audifonos)), executor, clock, device, sleep = {})

        val result = engine.handle(TriggerEvent.TimeReached(clock.now())).single()
        assertEquals(ExecutionStatus.EXECUTED, result.status)
        assertTrue(result.waitingUnlock)
        assertTrue(result.reason, result.reason.contains("1 espera el desbloqueo"))
        assertEquals(listOf(notify, volume, play), executor.done)

        device.locked = false
        val after = engine.runAfterUnlock("audifonos")
        assertEquals(ExecutionStatus.EXECUTED, after.status)
        assertFalse(after.waitingUnlock)
        assertEquals(listOf(notify, volume, play, open), executor.done)
        assertEquals(listOf(1), after.executedActions.map { it.index })
    }

    @Test
    fun fallaConStop_noDejaNadaEsperando() = runBlocking {
        val executor = RecordingExecutor(failing = setOf(volume))
        val engine = AutomationEngine(ListSource(listOf(audifonos)), executor, clock, device, sleep = {})
        val result = engine.handle(TriggerEvent.TimeReached(clock.now())).single()
        assertEquals(ExecutionStatus.FAILED, result.status)
        assertFalse(result.waitingUnlock)
    }
}

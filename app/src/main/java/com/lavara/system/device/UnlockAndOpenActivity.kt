package com.lavara.system.device

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.lavara.LaVaraApp
import kotlinx.coroutines.launch

/**
 * Pantalla invisible para abrir una app o un enlace con el teléfono bloqueado o la pantalla apagada.
 *
 * Android no deja que ninguna app se salte el PIN, la huella o el patrón. Lo legítimo es encender la pantalla,
 * pedirle al usuario que desbloquee y abrir lo pedido apenas lo hace. Si el bloqueo es solo deslizar, Android
 * lo quita sin pedir nada. Si el usuario no desbloquea, queda la notificación para abrirlo después.
 */
class UnlockAndOpenActivity : Activity() {

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_PROMPT, false)) {
            promptOnly()
            return
        }
        val target = targetOf(intent)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        if (target == null) {
            finish()
            return
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            open(target, name)
            return
        }
        log("Pantalla bloqueada: se encendió la pantalla y se pidió desbloquear para abrir $name")
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = open(target, name)

            override fun onDismissCancelled() = giveUp(target, name, "no se desbloqueó el teléfono")

            override fun onDismissError() = giveUp(target, name, "Android no pudo mostrar el desbloqueo")
        })
    }

    /**
     * Solo enciende la pantalla y muestra el pedido de desbloqueo. Lo que espera está en [UnlockQueue]: se
     * ejecuta al desbloquear, ahora o más tarde. Si el usuario no desbloquea ahora, no queda ninguna notificación.
     */
    private fun promptOnly() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked) {
            runPendingAndFinish("pantalla encendida sin bloqueo")
            return
        }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = runPendingAndFinish("desbloqueo")

            override fun onDismissCancelled() {
                log("No se desbloqueó ahora: lo pendiente se ejecuta cuando desbloquees.")
                finish()
            }

            override fun onDismissError() {
                log("Android no pudo mostrar el desbloqueo: lo pendiente se ejecuta cuando desbloquees.")
                finish()
            }
        })
    }

    private fun runPendingAndFinish(reason: String) {
        val container = container()
        container.appScope.launch { container.unlockQueue.runPending(reason) }
        finish()
    }

    private fun open(target: Intent, name: String) {
        if (done) return
        done = true
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log("Se abrió $name")
        } catch (e: RuntimeException) {
            container().logger.error(SOURCE, "Android no dejó abrir $name: ${e.message}")
        }
        finish()
    }

    private fun giveUp(target: Intent, name: String, why: String) {
        if (done) return
        done = true
        val shown = container().actionExecutor.notifyToOpen(target, name, requestCode = name.hashCode())
        container().logger.warn(
            SOURCE,
            "No se abrió $name: $why. " + if (shown == null) "Quedó una notificación para abrirlo." else "Tampoco se pudo avisar: $shown",
        )
        finish()
    }

    private fun log(message: String) = container().logger.info(SOURCE, message)

    private fun container() = (application as LaVaraApp).container

    companion object {
        private const val SOURCE = "Abrir"
        private const val EXTRA_TARGET = "com.lavara.extra.TARGET"
        private const val EXTRA_NAME = "com.lavara.extra.NAME"
        private const val EXTRA_PROMPT = "com.lavara.extra.PROMPT"

        /** Pantalla invisible que solo pide desbloquear (lo pendiente lo ejecuta [UnlockQueue]). */
        fun prompt(context: Context): Intent =
            Intent(context, UnlockAndOpenActivity::class.java)
                .putExtra(EXTRA_PROMPT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        fun intent(context: Context, target: Intent, name: String): Intent =
            Intent(context, UnlockAndOpenActivity::class.java)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        @Suppress("DEPRECATION")
        private fun targetOf(intent: Intent): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_TARGET, Intent::class.java)
            } else {
                intent.getParcelableExtra(EXTRA_TARGET)
            }
    }
}

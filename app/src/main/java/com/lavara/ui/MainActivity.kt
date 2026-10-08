package com.lavara.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.lavara.BuildConfig
import com.lavara.LaVaraApp
import com.lavara.R
import com.lavara.core.AppContainer
import com.lavara.automation.AutomationDraft
import com.lavara.core.AppInfo
import com.lavara.ui.editor.EditorScreen
import com.lavara.ui.history.HistoryScreen
import com.lavara.ui.theme.LaVaraTheme

class MainActivity : ComponentActivity() {

    // Sube en 1 cada vez que la app se abre desde la notificación de versión nueva.
    private var installRequest by mutableIntStateOf(0)

    // Sube cada vez que la app vuelve al frente: la pantalla vuelve a revisar permisos y ajustes.
    private var resumeCount by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_INSTALL_UPDATE, false)) installRequest = 1
        val container = (application as LaVaraApp).container
        setContent {
            LaVaraTheme {
                CompositionLocalProvider(LocalResumeCount provides resumeCount) {
                    HomeScreen(
                        versionLabel = AppInfo.versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        container = container,
                        installRequest = installRequest,
                        resumeCount = resumeCount,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_INSTALL_UPDATE, false)) installRequest++
    }

    companion object {
        const val EXTRA_INSTALL_UPDATE = "com.lavara.INSTALAR_ACTUALIZACION"
    }
}

@OptIn(ExperimentalMaterial3Api::class) // TopAppBar
@Composable
fun HomeScreen(versionLabel: String, container: AppContainer, installRequest: Int, resumeCount: Int) {
    var tab by rememberSaveable { mutableStateOf(Tab.INICIO) }
    // Editor abierto: null = cerrado; NEW = automatización nueva; si no, el id de la que se edita.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // Lo que armó "Grabar toques", para abrirlo en el editor como automatización nueva.
    var recorded by remember { mutableStateOf<AutomationDraft?>(null) }

    editing?.let { id ->
        EditorScreen(
            container,
            automationId = id.takeUnless { it == NEW || it == RECORDED },
            startWith = recorded.takeIf { id == RECORDED },
            onClose = {
                editing = null
                recorded = null
            },
        )
        return
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Si la app se abrió desde la notificación de versión nueva, abrir el menú, donde está el botón de instalar.
    LaunchedEffect(installRequest) { if (installRequest > 0) drawer.open() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = { AppDrawer(versionLabel, container, installRequest) },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(tab.label) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(painterResource(R.drawable.ic_menu), contentDescription = "Menú")
                        }
                    },
                )
            },
            floatingActionButton = {
                if (tab == Tab.INICIO) {
                    ExtendedFloatingActionButton(onClick = { editing = NEW }) { Text("+  Nueva") }
                }
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    Tab.INICIO -> StartTab(
                        container, resumeCount,
                        onEdit = { editing = it ?: NEW },
                        onRecorded = {
                            recorded = it
                            editing = RECORDED
                        },
                        onShowHistory = { tab = Tab.HISTORIAL },
                        onOpenMenu = { scope.launch { drawer.open() } },
                    )
                    Tab.HISTORIAL -> HistoryScreen(container)
                }
            }
        }
    }
}

private const val NEW = "__nueva__"
private const val RECORDED = "__grabada__"

/** Sube cada vez que La Vara vuelve al frente: las pantallas lo usan para volver a revisar permisos. */
val LocalResumeCount = compositionLocalOf { 0 }

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    INICIO("Inicio", R.drawable.ic_inicio),
    HISTORIAL("Historial", R.drawable.ic_historial),
}

@Composable
private fun StartTab(
    container: AppContainer,
    resumeCount: Int,
    onEdit: (String?) -> Unit,
    onRecorded: (AutomationDraft) -> Unit,
    onShowHistory: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val manager = container.updateManager
    var update by remember { mutableStateOf(manager.status()) }
    // Al volver a La Vara, se revisa en silencio si pasó más de una hora: así el aviso aparece solo cuando hay versión nueva.
    LaunchedEffect(resumeCount) {
        update = manager.status()
        if (manager.tokenStore.hasToken() && System.currentTimeMillis() - update.lastCheckMillis > 3_600_000L) {
            update = manager.check(notify = false)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NewVersionBanner(update, onOpen = onOpenMenu)
        RecordTapsButton(onCreate = onRecorded)
        AutomationsSection(container = container, resumeCount = resumeCount, onEdit = onEdit, onShowHistory = onShowHistory)
        // Espacio para que el botón "Nueva" no tape lo último de la lista.
        Spacer(Modifier.height(80.dp))
    }
}

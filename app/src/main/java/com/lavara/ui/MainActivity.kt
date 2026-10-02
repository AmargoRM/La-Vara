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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.BuildConfig
import com.lavara.LaVaraApp
import com.lavara.R
import com.lavara.core.AppContainer
import com.lavara.core.AppInfo
import com.lavara.ui.history.HistoryScreen
import com.lavara.ui.theme.LaVaraTheme

class MainActivity : ComponentActivity() {

    // Sube en 1 cada vez que la app se abre desde la notificación de versión nueva.
    private var installRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_INSTALL_UPDATE, false)) installRequest = 1
        val container = (application as LaVaraApp).container
        setContent {
            LaVaraTheme {
                HomeScreen(
                    versionLabel = AppInfo.versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    container = container,
                    installRequest = installRequest,
                )
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

@Composable
fun HomeScreen(versionLabel: String, container: AppContainer, installRequest: Int) {
    var tab by rememberSaveable { mutableStateOf(Tab.INICIO) }
    // Si la app se abrió desde la notificación de versión nueva, mostrar la tarjeta de actualizaciones.
    LaunchedEffect(installRequest) { if (installRequest > 0) tab = Tab.INICIO }

    Scaffold(
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
                Tab.INICIO -> StartTab(versionLabel, container, installRequest)
                Tab.HISTORIAL -> HistoryScreen(container)
            }
        }
    }
}

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    INICIO("Inicio", R.drawable.ic_inicio),
    HISTORIAL("Historial", R.drawable.ic_historial),
}

@Composable
private fun StartTab(versionLabel: String, container: AppContainer, installRequest: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = "LA VARA",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = versionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(32.dp))
        UpdateSection(container = container, installRequest = installRequest)
    }
}

package com.lavara.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lavara.BuildConfig
import com.lavara.LaVaraApp
import com.lavara.core.AppContainer
import com.lavara.core.AppInfo
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
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
}

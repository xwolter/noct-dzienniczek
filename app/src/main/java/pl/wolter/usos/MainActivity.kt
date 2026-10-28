package pl.wolter.usos

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.wolter.usos.data.Mode
import pl.wolter.usos.data.usos.UNIVERSITIES
import pl.wolter.usos.ui.AppRoot
import pl.wolter.usos.ui.AppViewModel
import pl.wolter.usos.ui.theme.UsosTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            val mode by vm.repo.mode.collectAsStateWithLifecycle()
            val data by vm.repo.data.collectAsStateWithLifecycle()
            val settings by vm.repo.settings.collectAsStateWithLifecycle()
            LaunchedEffect(mode) {
                if (mode != Mode.NONE && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            UsosTheme(settings.theme) {
                AppRoot(vm, mode, data, settings, ::openUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val uri = i?.data
        if (uri != null && uri.scheme == "usos-student") {
            val base = getSharedPreferences("usos", MODE_PRIVATE).getString("pendingBase", "") ?: ""
            val uni = UNIVERSITIES.firstOrNull { it.baseUrl == base }?.code ?: Uri.parse(base).host ?: "USOS"
            vm.handleCallback(uri, uni)
            i.data = null
        }
        i?.getStringExtra("open")?.let { vm.handleOpen(it); i.removeExtra("open") }
    }

    private fun openUrl(url: String) {
        val tab = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFF4F5F7.toInt()).build())
            .setShowTitle(true)
            .build()
        runCatching { tab.launchUrl(this, Uri.parse(url)) }.onFailure {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}

package com.kimgdecierdo.kimp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kimgdecierdo.kimp.Prefs
import com.kimgdecierdo.kimp.R
import com.kimgdecierdo.kimp.ui.components.Rule
import com.kimgdecierdo.kimp.ui.theme.Kimp
import com.kimgdecierdo.kimp.ui.theme.KimpFonts
import com.kimgdecierdo.kimp.ui.theme.KimpTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)  // for the download progress notification
        }
        handleShare(intent)
        setContent {
            val theme by Prefs.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            KimpTheme(dark) { KimpRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::sharedText)
    }
}

private val TABS = listOf("Music", "Video", "Settings", "About")

/** Header like the website: logo centered, then a centered strip of tabs (active one inverted). */
@Composable
fun KimpRoot(vm: MainViewModel) {
    val c = Kimp.colors
    var tab by rememberSaveable { mutableStateOf("Music") }
    Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().imePadding()) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.kimp_logo), contentDescription = null, modifier = Modifier.size(26.dp))
                Text("KIMP", Modifier.padding(start = 8.dp), color = c.inkStrong, fontFamily = KimpFonts.brand, fontSize = 22.sp)
            }
        }
        Rule()
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
            TABS.forEach { name ->
                val on = name == tab
                Text(
                    name.uppercase(),
                    Modifier
                        .padding(horizontal = 2.dp)
                        .background(if (on) c.invertBg else Color.Transparent)
                        .clickable { tab = name }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    color = if (on) c.invertFg else c.muted, fontFamily = KimpFonts.mono, fontWeight = FontWeight.Medium,
                    fontSize = 11.sp, letterSpacing = 0.15.em,
                )
            }
        }
        Rule()
        Box(Modifier.weight(1f)) {
            when (tab) {
                "Music" -> MusicScreen(vm)
                "Video" -> VideoScreen(vm)
                "Settings" -> SettingsScreen(vm)
                else -> AboutScreen()
            }
        }
    }
}

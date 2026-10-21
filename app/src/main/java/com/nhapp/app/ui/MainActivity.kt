package com.nhapp.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nhapp.app.Prefs
import com.nhapp.app.data.Library
import com.nhapp.app.util.Updater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Library.load()
        Home.init(lifecycleScope)
        setContent {
            AppTheme {
                val holder = rememberSaveableStateHolder()
                BackHandler(enabled = Nav.stack.size > 1) { Nav.pop() }
                Box(Modifier.fillMaxSize().background(Background)) {
                    val route = Nav.top
                    holder.SaveableStateProvider(route.key) {
                        when (route) {
                            Route.Home -> HomeScreen()
                            is Route.Results -> ResultsScreen(route, lifecycleScope)
                            is Route.Detail -> DetailScreen(route)
                            is Route.Reader -> ReaderScreen(route)
                            Route.Settings -> SettingsScreen()
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    runCatching { Updater.check() }.getOrNull()
                        ?.takeIf { it.versionCode != Prefs.skippedVersion }
                        ?.let { Updater.pending = it }
                }
                Updater.pending?.let { release ->
                    UpdateDialog(
                        release = release,
                        onDismiss = { Updater.pending = null },
                        onSkip = {
                            Prefs.skippedVersion = release.versionCode
                            Updater.pending = null
                        },
                    )
                }
            }
        }
    }
}

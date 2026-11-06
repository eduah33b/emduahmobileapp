package com.emduah.personal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.emduah.personal.wallpaper.RotationScheduler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emduah.personal.data.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PersonalApp(vm: PersonalViewModel, onActivateMotion: () -> Unit) {
    EmduahTheme {
        val signedIn = vm.connection.token.isNotBlank()
        val applicationContext = LocalContext.current.applicationContext
        LaunchedEffect(signedIn, vm.profiles) {
            if (signedIn) vm.profiles.values.forEach { RotationScheduler.refreshConstraints(applicationContext, it) }
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.notice) {
            if (vm.notice.isNotBlank()) {
                val message = vm.notice
                snackbar.showSnackbar(message)
                vm.clearNotice()
            }
        }
        // Keep authentication outside the shell; clearing a session also clears its navigation state.
        key(signedIn) {
            var location by rememberSaveable(stateSaver = listSaver(
                save = { listOf(it.page.name, it.origin.name) },
                restore = { AppLocation(AppPage.valueOf(it[0]), AppPage.valueOf(it[1])) }
            )) { mutableStateOf(AppLocation()) }
            var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
            var dirty by remember { mutableStateOf(false) }
            var discard by rememberSaveable { mutableStateOf(false) }
            val stateHolder = rememberSaveableStateHolder()
            val editorKey = "editor-${screen.key}"
            fun leaveEditor() {
                stateHolder.removeState(editorKey)
                dirty = false
                location = location.back()
            }
            fun back() {
                if (location.page == AppPage.EDITOR && dirty) discard = true
                else if (location.page == AppPage.EDITOR) leaveEditor()
                else location = location.back()
            }
            BackHandler(signedIn && location.page != AppPage.HOME) { back() }
            Scaffold(
                contentColor = MaterialTheme.colorScheme.onBackground,
                containerColor = if (signedIn) MaterialTheme.colorScheme.background else BrandPaper,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    if (signedIn) TopAppBar(
                        title = { if (location.isRoot) BrandMark() else Text(when (location.page) {
                            AppPage.WALLPAPERS -> "Wallpapers"
                            AppPage.EDITOR -> if (screen == Screen.HOME) "Home screen setup" else "Lock screen setup"
                            else -> "Settings"
                        }, style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { if (!location.isRoot) IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                        actions = {
                            if (location.isRoot) IconButton(onClick = { location = location.open(AppPage.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Settings") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Panel)
                    )
                },
                bottomBar = {
                    if (signedIn && location.isRoot) NavigationBar(containerColor = Panel, tonalElevation = 0.dp) {
                        listOf(Triple(AppPage.HOME, "Home", Icons.Outlined.Home), Triple(AppPage.APPS, "Apps", Icons.Outlined.Apps)).forEach { (page, label, icon) ->
                            NavigationBarItem(selected = location.page == page, onClick = { location = AppLocation(page, page) },
                                icon = { Icon(icon, null) }, label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant))
                        }
                    }
                }
            ) { padding ->
                if (!signedIn) {
                    SignInPage(vm, Modifier.padding(padding))
                } else stateHolder.SaveableStateProvider(if (location.page == AppPage.EDITOR) editorKey else location.page.name) {
                    Column(
                        Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        when (location.page) {
                            AppPage.HOME -> HomePage(vm) { location = location.open(AppPage.WALLPAPERS) }
                            AppPage.APPS -> AppsPage { location = location.open(AppPage.WALLPAPERS) }
                            AppPage.WALLPAPERS -> WallpaperPage(vm, screen, { screen = it }, onActivateMotion) { location = location.open(AppPage.EDITOR) }
                            AppPage.EDITOR -> ProfileEditor(vm.profiles.getValue(screen), vm.options, onActivateMotion,
                                onSave = { if (vm.save(it)) leaveEditor() }, onCancel = ::back, onDirtyChange = { dirty = it })
                            AppPage.SETTINGS -> SettingsPage(vm)
                        }
                    }
                }
            }
            if (discard) AlertDialog(
                onDismissRequest = { discard = false },
                title = { Text("Discard changes?") }, text = { Text("Your wallpaper settings have not been saved.") },
                confirmButton = { TextButton(onClick = { discard = false; leaveEditor() }) { Text("Discard") } },
                dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } }
            )
        }
    }
}

@Composable internal fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(7.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Text("E", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        }
        Text("EMDuah", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable internal fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Panel, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable internal fun PageHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(description, style = MaterialTheme.typography.bodyLarge, color = Muted)
    }
}

@Composable private fun HomePage(vm: PersonalViewModel, openWallpapers: () -> Unit) {
    PageHeading("Home", "A little space for your everyday.")
    Text("On this phone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureIcon(Icons.Outlined.Wallpaper)
            Column(Modifier.weight(1f)) {
                Text("Wallpapers", style = MaterialTheme.typography.titleLarge)
                Text("Your screens, your media.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        Screen.entries.filter { it == Screen.HOME || vm.separateLock }.forEach { screen ->
            val p = vm.profiles.getValue(screen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (screen == Screen.HOME) if (vm.separateLock) "Home screen" else "Home & lock" else "Lock screen", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(if (p.enabled) "Every ${p.minutes} min" else "Paused", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        Button(onClick = openWallpapers, modifier = Modifier.fillMaxWidth()) {
            Text("Open wallpapers")
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
        }
    }
}

@Composable private fun AppsPage(openWallpapers: () -> Unit) {
    PageHeading("Apps", "Your personal tools, in one place.")
    Text("On this phone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    OutlinedCard(onClick = openWallpapers, shape = RoundedCornerShape(12.dp), colors = CardDefaults.outlinedCardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FeatureIcon(Icons.Outlined.Wallpaper)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Wallpapers", style = MaterialTheme.typography.titleLarge)
                Text("Images and videos for your home and lock screens.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable private fun FeatureIcon(icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, null) }
    }
}

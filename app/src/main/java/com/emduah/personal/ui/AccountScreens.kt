package com.emduah.personal.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable internal fun SignInPage(vm: PersonalViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var base by rememberSaveable { mutableStateOf(vm.connection.baseUrl.ifBlank { "https://mediavault.emduah.xyz" }) }
    var email by rememberSaveable { mutableStateOf("") }
    // Never save passwords to instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var serverExpanded by rememberSaveable { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onBackground
    Column(modifier.fillMaxSize().background(BrandPaper).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BrandMark()
        Canvas(Modifier.fillMaxWidth().height(88.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawOval(ink.copy(alpha = .18f), topLeft = center - Offset(82.dp.toPx(), 27.dp.toPx()), size = Size(164.dp.toPx(), 54.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawCircle(ink.copy(alpha = .16f), radius = 36.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
            drawCircle(ink, radius = 5.dp.toPx(), center = center + Offset(72.dp.toPx(), -13.dp.toPx()))
        }
        PageHeading("Welcome home.", "Sign in to your personal space.")
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide password" else "Show password") } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        if (vm.signInError.isNotBlank()) Text(vm.signInError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { vm.login(base, email.trim(), password) { password = "" } }, enabled = !vm.busy && email.isNotBlank() && password.isNotBlank() && base.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            if (vm.busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (vm.busy) "Signing in…" else "Sign in")
        }
        TextButton(onClick = { vm.accountLink(base, true) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }, enabled = !vm.busy) { Text("Forgot password?") }
        Text("Use your existing verified EMDuah account. You’ll stay signed in on this phone.", style = MaterialTheme.typography.bodyMedium, color = Muted)
        TextButton(onClick = { serverExpanded = !serverExpanded }) { Text(if (serverExpanded) "Hide connection settings" else "Connection settings") }
        if (serverExpanded) {
            OutlinedTextField(base, { base = it }, label = { Text("Server URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { vm.accountLink(base, false) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }) { Text("Verify email / open web sign-in") }
        }
    }
}

@Composable internal fun SettingsPage(vm: PersonalViewModel) {
    val context = LocalContext.current
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Text("Signed in", style = MaterialTheme.typography.titleMedium)
    CardBlock {
        Text(vm.connection.name.ifBlank { "EMDuah account" }, style = MaterialTheme.typography.titleLarge)
        Text(vm.connection.baseUrl, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text("Appearance follows your phone’s light or dark theme.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { vm.accountLink(vm.connection.baseUrl, false) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }) { Text("Manage web account") }
    }
    CardBlock {
        Text("Connection", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = vm::loadOptions, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh wallpaper filter options") }
    }
    val devices = vm.devices.optJSONArray("sessions")
    if (devices != null) CardBlock {
        Text("Signed-in devices", style = MaterialTheme.typography.titleMedium)
        for (index in 0 until devices.length()) {
            val device = devices.getJSONObject(index)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(device.getString("deviceName"), style = MaterialTheme.typography.bodyLarge)
                    Text(if (device.optBoolean("current")) "This phone" else device.optString("lastUsedAt"), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (!device.optBoolean("current")) TextButton(onClick = { vm.revokeDevice(device.getString("id")) }) { Text("Sign out") }
            }
        }
        TextButton(onClick = vm::loadDevices) { Text("Refresh devices") }
    }
    OutlinedButton(onClick = { confirmSignOut = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out of this phone") }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out?") }, text = { Text("Wallpaper rotation will pause. Your settings will stay on this phone.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.disconnect() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } })
}

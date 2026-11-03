// Lockd In account (R50): create an account or sign in (first run and Settings › Account).
package app.lockdin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import app.lockdin.account.AccountRules
import app.lockdin.account.AccountService
import app.lockdin.account.AccountVault
import app.lockdin.state.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Create an account or sign in. Calls [onSignedIn] once this phone has an account. */
@Composable
fun AccountForm(store: Store, startWithSignIn: Boolean = false, onSignedIn: () -> Unit = {}) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var signIn by rememberSaveable { mutableStateOf(startWithSignIn) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val problem = when {
            !signIn && name.isBlank() -> "Add your first name."
            else -> AccountRules.emailProblem(email) ?: if (signIn) (if (password.isEmpty()) "Enter your password." else null) else AccountRules.passwordProblem(password)
        }
        if (problem != null) { error = problem; return }
        busy = true; error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val (acct, token) = if (signIn) AccountService.signIn(email, password) else AccountService.signUp(name.trim().take(40), email, password)
                    AccountVault.save(ctx, token)
                    acct
                }
            }
            busy = false
            r.onSuccess { a -> password = ""; store.update { it.copy(account = a) }; onSignedIn() }
                .onFailure { error = it.message ?: "Something went wrong. Try again." }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
        SegmentedChoice(listOf(false to "Create account", true to "Sign in"), signIn, { signIn = it; error = null }, label = "Account")
        if (!signIn) OutlinedTextField(
            name, { name = it.take(40); error = null }, singleLine = true, label = { Text("First name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            email, { email = it.take(254); error = null }, singleLine = true, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            password, { password = it.take(200); error = null }, singleLine = true, label = { Text("Password") },
            supportingText = if (!signIn) ({ Text("At least 8 characters") }) else null,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show", style = UiType.caption, color = c.ink) } },
            shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, style = UiType.caption, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        if (busy) CircularProgressIndicator(color = c.ink, modifier = Modifier.semantics { contentDescription = if (signIn) "Signing in" else "Creating account" })
        else MainButton(if (signIn) "Sign in" else "Create account", { submit() }, modifier = Modifier.fillMaxWidth())
        Text("Your account connects this phone to Lockd In online, and later to your computer. We store your email, first name and a scrambled copy of your password. Sessions, apps and history stay on this phone.",
            style = UiType.caption, color = c.inkMuted)
    }
}

/** Settings › Account. */
@Composable
fun AccountSettings(store: Store) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = store.state.account
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (account == null) { AccountForm(store); return }

    SettingsGroup {
        SettingRow(account.name.ifBlank { "Signed in" }, "", icon = LIcon.Person, supporting = account.email, chevron = null)
    }
    Text("This phone is signed in. Pairing with your computer will use this account when it arrives.", style = UiType.caption, color = c.inkMuted)
    error?.let { Text(it, style = UiType.caption, color = c.danger) }
    SettingsGroup {
        Row(Modifier.padding(horizontal = Dim.s2), verticalAlignment = Alignment.CenterVertically) {
            TextAction("Sign out", {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { AccountVault.token(ctx)?.let { AccountService.signOut(it) } } }
                    AccountVault.clear(ctx)
                    store.update { it.copy(account = null) }
                }
            })
        }
        GroupDivider()
        Row(Modifier.padding(horizontal = Dim.s2)) { TextAction("Delete account", { confirmDelete = true }, danger = true) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = c.surface,
        title = { Text("Delete your account?", style = Type.title) },
        text = { Text("Your email, name and password are deleted from Lockd In and every device is signed out. Sessions, profiles and history on this phone stay. A partner you added keeps working.", style = UiType.body) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { AccountService.delete(AccountVault.token(ctx) ?: error("Signed out")) } }
                    r.onSuccess { AccountVault.clear(ctx); store.update { it.copy(account = null) } }
                        .onFailure { error = it.message ?: "Couldn’t delete the account. Try again." }
                }
            }) { Text("Delete", style = UiType.label, color = c.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", style = UiType.label, color = c.ink) } },
    )
}

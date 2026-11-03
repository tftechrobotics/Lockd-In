// Partner code, ending early and messaging admin (R49).
package app.lockdin.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lockdin.BuildConfig
import app.lockdin.enforce.Commitment
import app.lockdin.partner.AdminMessage
import app.lockdin.partner.PartnerCode
import app.lockdin.partner.PartnerRules
import app.lockdin.partner.PartnerService
import app.lockdin.partner.PartnerVault
import app.lockdin.state.ActiveSession
import app.lockdin.state.Partner
import app.lockdin.state.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun linkUrl(p: Partner) = "${BuildConfig.PARTNER_URL}/p/${p.linkId}"

private fun share(ctx: android.content.Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Setting up (or managing) the partner: in setup step 4 and Settings › Ending early. */
@Composable
fun PartnerSetup(store: Store) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val partner = store.state.partner
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun invite(p: Partner) = share(ctx,
        "I’m using Lockd In to stay focused. Can you be my partner? Open this link and choose a 4-digit PIN. If I ever need to end a focus session early, I’ll send you a request and you can allow it or not: ${linkUrl(p)}",
        "Send the link to ${p.name}")

    fun checkClaimed() {
        val p = store.state.partner ?: return
        busy = true; error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { PartnerService.thread(p.accountId, PartnerVault.token(ctx) ?: error("no token")).partnerClaimed } }
            busy = false
            r.onSuccess { claimed -> if (claimed) store.update { s -> s.copy(partner = s.partner?.copy(claimed = true)) } else error = "${p.name} hasn’t opened the link yet." }
                .onFailure { error = it.message ?: "Couldn’t check. Try again." }
        }
    }
    // Look once when the screen opens, in case the partner claimed the link meanwhile.
    LaunchedEffect(partner?.accountId) { if (partner != null && !partner.claimed) checkClaimed().also { error = null } }

    if (partner == null) {
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
            OutlinedTextField(name, { name = it.take(40); error = null }, singleLine = true, label = { Text("Partner’s first name") },
                shape = RoundedCornerShape(Dim.radius), colors = fieldColors(), modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Partner’s first name" })
            SettingsGroup {
                Column(Modifier.padding(Dim.s4), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    Step(1, "Send them a private link.")
                    Step(2, "They open it and choose a 4-digit PIN.")
                    Step(3, "When you need out, you send a request. They enter their PIN and tap Allow.")
                }
            }
            error?.let { Text(it, style = UiType.caption, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (busy) CircularProgressIndicator(color = c.ink, modifier = Modifier.semantics { contentDescription = "Setting up" })
            else MainButton(if (name.isBlank()) "Send the link" else "Send the link to ${name.trim()}", {
                val n = name.trim()
                if (n.isEmpty()) { error = "Add your partner’s first name."; return@MainButton }
                busy = true; error = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            val key = PartnerVault.newKey()
                            val acct = PartnerService.createAccount(key, n, app.lockdin.account.AccountVault.token(ctx))
                            PartnerVault.save(ctx, key, acct.token)
                            Partner(n, acct.accountId, acct.linkId)
                        }
                    }
                    busy = false
                    r.onSuccess { p -> store.update { it.copy(partner = p) }; invite(p) }.onFailure { error = it.message ?: "Couldn’t set this up. Try again." }
                }
            })
            Text("Lockd In stores your partner’s first name and an encrypted key, so their page can work. No phone numbers or emails.",
                style = UiType.caption, color = c.inkMuted)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
                Box(Modifier.padding(top = 2.dp)) { StatusDot(partner.claimed) }
                Text(if (partner.claimed) "${partner.name} is your partner." else "Waiting for ${partner.name} to open the link.",
                    style = UiType.body, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (partner.claimed) Text("Delete the message with their link from your phone, so only ${partner.name} can use it.",
                style = UiType.caption, color = c.inkMuted)
            error?.let { Text(it, style = UiType.caption, color = c.inkMuted) }
            FlowRowCompat {
                if (!partner.claimed) TextAction(if (busy) "Checking…" else "Check again", { if (!busy) checkClaimed() })
                if (!partner.claimed) TextAction("Send the link again", { invite(partner) })
                TextAction("Remove partner", { confirmRemove = true }, danger = true)
            }
        }
    }
    if (confirmRemove && partner != null) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        containerColor = c.surface,
        title = { Text("Remove ${partner.name}?", style = Type.title) },
        text = { Text("Their link stops working and the code key is deleted, here and on the Lockd In service. Sessions that are already running keep their rules.", style = UiType.body) },
        confirmButton = {
            TextButton(onClick = {
                confirmRemove = false
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { PartnerVault.token(ctx)?.let { PartnerService.deleteAccount(partner.accountId, it) } } }
                    PartnerVault.clear(ctx)
                    store.update { s -> s.copy(partner = null, commitment = if (s.commitment == Commitment.PARTNER) Commitment.LOCKED else s.commitment) }
                }
            }) { Text("Remove", style = UiType.label, color = c.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep", style = UiType.label, color = c.ink) } },
    )
}

@Composable
private fun Step(n: Int, text: String) {
    val c = LocalColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(Dim.s3)) {
        Surface(color = c.taupeSoft, shape = RoundedCornerShape(12.dp)) {
            Text("$n", style = UiType.caption, color = c.ink, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Text(text, style = UiType.body, color = c.ink)
    }
}

@Composable
private fun StatusDot(on: Boolean) {
    val c = LocalColors.current
    Surface(color = if (on) c.ink else c.statusDot, shape = RoundedCornerShape(5.dp), modifier = Modifier.height(10.dp).widthIn(min = 10.dp, max = 10.dp)) {}
}

/**
 * Session options for a partner-code session: end with the code, ask the partner, message admin,
 * or start an emergency end that waits 3/4 of the time left (calls and messages unlock at once).
 */
@Composable
fun PartnerSessionOptions(store: Store, session: ActiveSession, now: Long) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val partnerName = store.state.partner?.name ?: "your partner"
    var endEarly by rememberSaveable { mutableStateOf(false) }
    var chat by rememberSaveable { mutableStateOf(false) }
    val pendingAt = session.controls.emergencyRecoveryAt?.takeIf { it > now }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
        if (pendingAt != null) {
            Text("Emergency end in ${Fmt.countdown(pendingAt - now)}", style = UiType.label, color = c.ink,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text("Calls and messages are unlocked. Everything else stays blocked until then.", style = UiType.caption, color = c.inkMuted)
            FlowRowCompat {
                TextAction("Ask $partnerName", { endEarly = true })
                TextAction("Cancel and keep focusing", { store.setControls(session.id) { it.copy(emergencyRecoveryAt = null) } })
            }
        } else {
            Text(if (PartnerAsk.pending(session.id) != null) "Partner session: waiting for $partnerName to answer."
                else "Partner session: it ends early when $partnerName allows it.", style = UiType.caption, color = c.inkMuted)
            SecondaryButton("End early", { endEarly = true }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (endEarly) EndEarlyDialog(store, session, now, onMessageAdmin = { endEarly = false; chat = true }, onClose = { endEarly = false })
    if (chat) AdminChatDialog(store, session, onClose = { chat = false })
}

/** The request sent for each session (kept while the app runs; a new one can always be sent). */
object PartnerAsk {
    private val sent = androidx.compose.runtime.mutableStateMapOf<String, app.lockdin.partner.PartnerRequest>()
    fun pending(sessionId: String) = sent[sessionId]
    fun set(sessionId: String, r: app.lockdin.partner.PartnerRequest?) { if (r == null) sent.remove(sessionId) else sent[sessionId] = r }
}

@Composable
private fun EndEarlyDialog(store: Store, session: ActiveSession, now: Long, onMessageAdmin: () -> Unit, onClose: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val partner = store.state.partner
    val partnerName = partner?.name ?: "your partner"
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var askError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var withCode by rememberSaveable { mutableStateOf(false) }
    var confirmEmergency by remember { mutableStateOf(false) }
    val occEnd = app.lockdin.engine.Session.occurrenceFrom(session.start, session.segments).end
    val waitMin = ((PartnerRules.emergencyWaitMs(occEnd, now) + 59_999) / 60_000).toInt()
    val leftMin = (((occEnd - now).coerceAtLeast(0) + 59_999) / 60_000).toInt()
    val request = PartnerAsk.pending(session.id)
    fun endNow() { store.setControls(session.id) { it.copy(emergencyRecoveryAt = store.clock()) }; PartnerAsk.set(session.id, null); onClose() }
    fun sendLink(r: app.lockdin.partner.PartnerRequest) = share(ctx,
        "Can I end my focus session early? Open this, enter your Lockd In PIN and tap Allow if it’s okay: ${r.url}", "Ask $partnerName")

    // Owner 2026-11-03: the partner just opens the request and taps Allow; the phone checks for the answer.
    LaunchedEffect(request?.id) {
        val r = request ?: return@LaunchedEffect
        val key = withContext(Dispatchers.IO) { PartnerVault.key(ctx) }
        while (true) {
            val token = withContext(Dispatchers.IO) { PartnerVault.token(ctx) }
            if (partner == null || token == null || key == null) { askError = "This phone doesn’t have the partner key any more. Use an emergency end or message admin."; return@LaunchedEffect }
            val got = withContext(Dispatchers.IO) { runCatching { PartnerService.answer(partner.accountId, token, r.id) } }
            got.onSuccess { (st, grant) ->
                status = st; askError = null
                if (st == "allowed" && grant != null && PartnerCode.checkGrant(key, session.id, grant)) { endNow(); return@LaunchedEffect }
                if (st != "pending") { PartnerAsk.set(session.id, null); return@LaunchedEffect }
            }.onFailure { askError = it.message }
            delay(4_000)
        }
    }

    FullScreenDialog(onClose, "End this session early") {
        Text("This session ends early when $partnerName allows it. Send a request; they enter their PIN and tap Allow.", style = UiType.body, color = c.inkMuted)
        when {
            request != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s3),
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    CircularProgressIndicator(color = c.ink, strokeWidth = 2.dp, modifier = Modifier.height(20.dp).widthIn(max = 20.dp))
                    Text("Waiting for $partnerName to answer…", style = UiType.label, color = c.ink)
                }
                Text("This screen ends the session as soon as they allow it. You can close it and come back.", style = UiType.caption, color = c.inkMuted)
                SecondaryButton("Send the request again", { sendLink(request) }, modifier = Modifier.fillMaxWidth())
            }
            asking -> CircularProgressIndicator(color = c.ink, modifier = Modifier.semantics { contentDescription = "Sending" })
            else -> {
                status?.let {
                    Text(when (it) { "denied" -> "$partnerName didn’t allow it. Your session keeps going."; "expired" -> "That request expired. You can ask again."; else -> "" },
                        style = UiType.label, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                MainButton("Ask $partnerName", {
                    val p = partner ?: return@MainButton
                    asking = true; askError = null
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { PartnerService.ask(p.accountId, PartnerVault.token(ctx) ?: error("no token"), session.id, occEnd) }
                        }
                        asking = false
                        r.onSuccess { req -> status = null; PartnerAsk.set(session.id, req); sendLink(req) }
                            .onFailure { askError = if (it is IllegalStateException) "This phone doesn’t have the partner key any more." else it.message }
                    }
                })
            }
        }
        askError?.let { Text(it, style = UiType.caption, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }

        if (!withCode) TextAction("$partnerName is with you? Use their code", { withCode = true })
        else {
            Text("They can open their Lockd In link and read you the code shown there.", style = UiType.caption, color = c.inkMuted)
            OutlinedTextField(
                code, { code = it.filter { ch -> ch.isDigit() }.take(6); error = null }, singleLine = true, label = { Text("6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), isError = error != null,
                shape = RoundedCornerShape(Dim.radius), colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Partner’s code" },
            )
            error?.let { Text(it, style = UiType.caption, color = c.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
            SecondaryButton("End with the code", {
                val key = PartnerVault.key(ctx)
                when {
                    key == null -> error = "This phone doesn’t have the partner key any more. Use an emergency end or message admin."
                    PartnerCode.check(key, code, System.currentTimeMillis()) -> endNow()
                    else -> error = "That code isn’t right. Codes change every 30 seconds, so ask for the one shown now."
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Dim.s2))
        Text("Can’t reach $partnerName?", style = UiType.label, color = c.ink, modifier = Modifier.semantics { heading() })
        SecondaryButton("Message admin", onMessageAdmin, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Emergency end without $partnerName", { confirmEmergency = true }, modifier = Modifier.fillMaxWidth())
        Text("Ends after a $waitMin-minute wait (¾ of the $leftMin minutes left). Calls and messages unlock straight away.", style = UiType.caption, color = c.inkMuted)
    }
    if (confirmEmergency) AlertDialog(
        onDismissRequest = { confirmEmergency = false },
        containerColor = c.surface,
        title = { Text("Start an emergency end?", style = Type.title) },
        text = { Text("The session ends in $waitMin minutes. Calls and messages unlock now. You can cancel the wait, and if $partnerName allows it, it ends at once.", style = UiType.body) },
        confirmButton = {
            TextButton(onClick = {
                val t = store.clock()
                store.setControls(session.id) { it.copy(emergencyRecoveryAt = t + PartnerRules.emergencyWaitMs(occEnd, t)) }
                confirmEmergency = false; onClose()
            }) { Text("Start the wait", style = UiType.label, color = c.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmEmergency = false }) { Text("Keep going", style = UiType.label, color = c.ink) } },
    )
}

/** Messages to admin from this phone's own account; admin can send an unlock for this session. */
@Composable
fun AdminChatDialog(store: Store, session: ActiveSession?, onClose: () -> Unit) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val partner = store.state.partner
    var messages by remember { mutableStateOf<List<AdminMessage>?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    suspend fun load(send: String? = null) {
        val token = withContext(Dispatchers.IO) { PartnerVault.token(ctx) }
        if (partner == null || token == null) { error = "Set up a partner first: admin messages come from that account."; messages = emptyList(); return }
        val r = withContext(Dispatchers.IO) { runCatching { PartnerService.thread(partner.accountId, token, send, session?.id) } }
        r.onSuccess { messages = it.messages; error = null; if (send != null) text = "" }.onFailure { error = it.message }
    }
    LaunchedEffect(Unit) { while (true) { load(); delay(15_000) } }
    LaunchedEffect(messages?.size) { messages?.let { if (it.isNotEmpty()) list.animateScrollToItem(it.size - 1) } }

    val key = remember { PartnerVault.key(ctx) }
    val unlock = messages?.lastOrNull { it.kind == "unlock" && session != null && it.sessionId == session.id && key != null && PartnerCode.checkGrant(key, session.id, it.body) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = c.ground, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onClose) { LockdIcon(LIcon.Back, description = "Back") }
                    Column(Modifier.weight(1f)) {
                        Text("Lockd In admin", style = UiType.title, color = c.ink, modifier = Modifier.semantics { heading() })
                        Text("Sent from your account", style = UiType.caption, color = c.inkMuted)
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Dim.gutter), state = list, verticalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    if (messages == null) item { CircularProgressIndicator(color = c.ink, modifier = Modifier.padding(Dim.s4).semantics { contentDescription = "Loading messages" }) }
                    if (messages?.isEmpty() == true) item {
                        Text("Explain what’s happening. Admin can end this session for you. Messages come from this phone’s Lockd In account, so admin knows it’s you.",
                            style = UiType.body, color = c.inkMuted, modifier = Modifier.padding(vertical = Dim.s4))
                    }
                    items(messages.orEmpty().filter { it.kind == "text" }, key = { it.id }) { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromAdmin) Arrangement.Start else Arrangement.End) {
                            Surface(color = if (m.fromAdmin) c.surface else c.ink, shape = RoundedCornerShape(16.dp),
                                border = if (m.fromAdmin) androidx.compose.foundation.BorderStroke(1.dp, c.line) else null,
                                modifier = Modifier.widthIn(max = 300.dp)) {
                                Text(m.body, style = UiType.body, color = if (m.fromAdmin) c.ink else c.onInk, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                        }
                    }
                }
                if (unlock != null && session != null) Surface(color = c.taupeSoft, shape = RoundedCornerShape(Dim.radius), modifier = Modifier.fillMaxWidth().padding(Dim.gutter)) {
                    Row(Modifier.padding(Dim.s3), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Unlock received", style = UiType.label, color = c.ink)
                            Text("For “${session.intention ?: "this session"}”", style = UiType.caption, color = c.inkMuted)
                        }
                        MainButton("End now", { store.setControls(session.id) { it.copy(emergencyRecoveryAt = store.clock()) }; onClose() })
                    }
                }
                error?.let { Text(it, style = UiType.caption, color = c.danger, modifier = Modifier.padding(horizontal = Dim.gutter)) }
                Row(Modifier.fillMaxWidth().padding(Dim.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dim.s2)) {
                    OutlinedTextField(text, { text = it.take(1000) }, placeholder = { Text("Message") }, shape = RoundedCornerShape(24.dp), colors = fieldColors(),
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Message to admin" })
                    androidx.compose.material3.IconButton(onClick = {
                        if (text.isBlank() || sending) return@IconButton
                        sending = true
                        scope.launch { load(text.trim()); sending = false }
                    }) { LockdIcon(LIcon.Arrow, description = "Send") }
                }
            }
        }
    }
}

@Composable
private fun FullScreenDialog(onClose: () -> Unit, title: String, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = c.ground, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onClose) { LockdIcon(LIcon.Back, description = "Back to the session") }
                    Text(title, style = UiType.title, color = c.ink, modifier = Modifier.semantics { heading() })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter, vertical = Dim.s2),
                    verticalArrangement = Arrangement.spacedBy(Dim.s3)) { content() }
            }
        }
    }
}

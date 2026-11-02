package app.lockdin.enforce

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lockdin.MainActivity
import app.lockdin.engine.PhaseKind
import app.lockdin.state.Store
import app.lockdin.ui.Dim
import app.lockdin.ui.LIcon
import app.lockdin.ui.LocalColors
import app.lockdin.ui.LockdIcon
import app.lockdin.ui.LockdInTheme
import app.lockdin.ui.MainButton
import app.lockdin.ui.Moment
import app.lockdin.ui.SecondaryButton
import app.lockdin.ui.Type
import app.lockdin.ui.UiType
import app.lockdin.ui.isDark
import app.lockdin.ui.message
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * The block screen. Brought to the front over a restricted app during focus. The app is
 * behind it, still open; nothing is closed. Shows the task, when the next break or the end
 * comes, a way back to work, and the way to the session's own options (unlock countdown or
 * emergency recovery, per the session's commitment). Closes itself when restriction lifts.
 */
class BlockActivity : ComponentActivity() {
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        requestAudioFocus()
        val store = Store.get(this)
        setContent {
            LockdInTheme(isDark(store.state.theme)) {
                BlockScreen(
                    appLabel = intent.getStringExtra(EXTRA_SITE) ?: InstalledApps.label(this, intent.getStringExtra(EXTRA_PKG) ?: ""),
                    isSite = intent.getStringExtra(EXTRA_SITE) != null,
                    onReturn = ::goHome,
                    onOptions = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    },
                    onLifted = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showing = true
    }

    override fun onPause() {
        showing = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        super.onDestroy()
    }

    /**
     * Asks other apps to stop playing sound. Media apps that follow Android's audio focus
     * rules pause; apps that ignore it keep playing. This is a request, not a guarantee.
     */
    private fun requestAudioFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { getSystemService(AudioManager::class.java).requestAudioFocus(req) }
        focusRequest = req
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        /** The block screen is in front right now (process-local). */
        @Volatile var showing = false
            private set

        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_SITE = "site"

        fun intent(ctx: Context, pkg: String, site: String? = null): Intent = Intent(ctx, BlockActivity::class.java)
            .putExtra(EXTRA_PKG, pkg)
            .apply { if (site != null) putExtra(EXTRA_SITE, site) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}

@Composable
private fun BlockScreen(appLabel: String, isSite: Boolean, onReturn: () -> Unit, onOptions: () -> Unit, onLifted: () -> Unit) {
    val c = LocalColors.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = Store.get(ctx)
    val session = store.state.active
    val now = System.currentTimeMillis()
    val snap = session?.let { FocusService.evaluate(it, now) }

    // Leave as soon as restriction lifts (break, unlock, end, emergency recovery).
    LaunchedEffect(session?.id) {
        while (true) {
            val s = Store.get(ctx).state.active
            if (s == null || !FocusService.evaluate(s, System.currentTimeMillis()).restricting) { onLifted(); break }
            delay(1_000)
        }
    }

    val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
    val phase = snap?.phase
    val nextIsBreak = session != null && phase != null && session.segments.getOrNull(phase.index + 1)?.kind == PhaseKind.BREAK
    val whenLine = when {
        phase == null -> null
        nextIsBreak -> "Break at ${fmt.format(Date(phase.end))}"
        else -> "Session ends at ${fmt.format(Date(phase.end))}"
    }

    Column(
        Modifier.fillMaxSize().background(c.ground).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(horizontal = Dim.gutter, vertical = Dim.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dim.s3)) {
            LockdIcon(LIcon.Focus, size = 40.dp, tint = c.ink)
            Text(
                "$appLabel is blocked during focus", style = Type.title, color = c.ink, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            session?.intention?.let {
                Text("You’re working on: $it", style = UiType.body, color = c.ink, textAlign = TextAlign.Center)
            }
            whenLine?.let { Text(it, style = UiType.body, color = c.inkMuted, textAlign = TextAlign.Center) }
            message(store.state.tone, Moment.FOCUS_START, session?.intention)?.let {
                Text(it, style = UiType.caption, color = c.inkMuted, textAlign = TextAlign.Center)
            }
            Text(
                if (isSite) "The page is still open behind this screen. Nothing is lost." else "It’s still open behind this screen, not closed. Nothing is lost.",
                style = UiType.caption, color = c.inkMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dim.s4))
            MainButton("Return to work", onReturn)
            SecondaryButton(
                if (session?.commitment == Commitment.LOCKED) "Emergency options" else "Session options",
                onOptions, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// DEBUG BUILDS ONLY (src/debug): lets CI start and inspect a session from adb, so the emulator
// enforcement test exercises the real store, service and block screen. Protected by the DUMP
// permission, which only the shell (adb) holds. Not compiled into release builds.
package app.lockdin.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.lockdin.enforce.AppRules
import app.lockdin.enforce.Commitment
import app.lockdin.enforce.RuleMode
import app.lockdin.engine.Session
import app.lockdin.state.LastChoice
import app.lockdin.state.Store

class TestControl : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        when (intent.getStringExtra("cmd")) {
            "start" -> {
                val focus = intent.getIntExtra("focus", 1)
                val brk = intent.getIntExtra("break", 1)
                val rounds = intent.getIntExtra("rounds", 1)
                val pkgs = intent.getStringExtra("block")?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                val mode = if (intent.getStringExtra("mode") == "approved") RuleMode.APPROVED_ONLY else RuleMode.BLOCK_LIST
                store.update { s ->
                    s.copy(
                        profiles = s.profiles.map { if (it.id == "study") it.copy(rules = AppRules(mode, pkgs)) else it },
                        commitment = Commitment.DELAYED,
                    )
                }
                store.startSession(
                    System.currentTimeMillis(), Session.roundsPlan(rounds, focus, brk), "CI enforcement test", "study",
                    LastChoice(focus, brk, rounds, "study"),
                )
            }
            "emergency" -> store.state.active?.let { a ->
                store.setControls(a.id) { it.copy(emergencyRecoveryAt = System.currentTimeMillis()) }
            }
            "finish" -> store.finish()
        }
        Log.i("LockdInTest", "cmd=${intent.getStringExtra("cmd")} active=${store.state.active?.id}")
    }
}

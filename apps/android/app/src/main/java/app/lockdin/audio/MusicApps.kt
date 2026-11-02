// Which installed apps can be chosen for music access (R48). An app qualifies only if it offers
// a media browser (the way Android's media controls and Android Auto talk to music apps) and has
// a home-screen icon. That's a technical test, not a promise that the app is safe: the person
// still picks each app, and the editor explains that a chosen app's other content (videos,
// feeds) is reachable too.
package app.lockdin.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object MusicApps {
    private val ACTIONS = listOf("android.media.browse.MediaBrowserService", "androidx.media3.session.MediaSessionService")

    fun eligiblePackages(ctx: Context): Set<String> = runCatching {
        val pm = ctx.packageManager
        ACTIONS.flatMap { a -> pm.queryIntentServices(Intent(a), PackageManager.MATCH_ALL).mapNotNull { it.serviceInfo?.packageName } }
            .filter { it != ctx.packageName && pm.getLaunchIntentForPackage(it) != null }
            .toSet()
    }.getOrDefault(emptySet())
}

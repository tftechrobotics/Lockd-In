// Study audio (roadmap step 3, B2): a playlist in the person's own music app. Lockd In does not
// play this music itself; it opens the playlist in that app and sends standard media keys
// (play/pause, next) and media volume, which every music app on Android accepts.
package app.lockdin.audio

data class MusicProvider(val name: String, val pkg: String)

object Music {
    val SPOTIFY = MusicProvider("Spotify", "com.spotify.music")
    val YOUTUBE_MUSIC = MusicProvider("YouTube Music", "com.google.android.apps.youtube.music")
    val APPLE_MUSIC = MusicProvider("Apple Music", "com.apple.android.music")
    val SOUNDCLOUD = MusicProvider("SoundCloud", "com.soundcloud.android")

    /**
     * The service a playlist link belongs to, or null if it isn't one we recognise. Accepts
     * share links (https://open.spotify.com/playlist/…, music.youtube.com/playlist?list=…,
     * music.apple.com/…/playlist/…, soundcloud.com/…/sets/…) and spotify: URIs.
     */
    fun providerOf(link: String?): MusicProvider? {
        val l = link?.trim() ?: return null
        if (l.length > 500) return null
        if (l.startsWith("spotify:")) return SPOTIFY
        val host = Regex("^https?://([^/?#]+)", RegexOption.IGNORE_CASE).find(l)?.groupValues?.get(1)?.lowercase()?.removePrefix("www.") ?: return null
        return when {
            host == "open.spotify.com" || host == "spotify.link" -> SPOTIFY
            host == "music.youtube.com" -> YOUTUBE_MUSIC
            host == "music.apple.com" -> APPLE_MUSIC
            host == "soundcloud.com" || host == "m.soundcloud.com" || host == "on.soundcloud.com" -> SOUNDCLOUD
            else -> null
        }
    }

    /** A cleaned link (trimmed, https only) or null if it isn't a supported playlist link. */
    fun clean(link: String?): String? {
        val l = link?.trim() ?: return null
        if (providerOf(l) == null) return null
        return if (l.startsWith("http://", ignoreCase = true)) "https://" + l.substring(7) else l
    }
}

// Study audio (roadmap step 3, B2): playlists in the person's own music app. Lockd In does not
// play this music itself. With notification access it asks the music app's player to play the
// playlist (MediaController.playFromUri); otherwise it resumes the app with a media key, and the
// person can open the playlist in its app. Controls are Android's standard media keys and volume.
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

    /** A name for a link the person didn't name. */
    fun defaultName(link: String): String = providerOf(link)?.let { "${it.name} playlist" } ?: "Playlist"

    private val SPOTIFY_PATH = Regex("^https?://open\\.spotify\\.com/(?:intl-[a-z-]+/)?(playlist|album|track|artist|show|episode)/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
    private val YTM_LIST = Regex("[?&]list=([A-Za-z0-9_-]+)")

    /**
     * The URI handed to the music app's player: spotify:playlist:ID for Spotify share links
     * (the form Spotify's player accepts), the link itself for the others.
     */
    fun playUri(link: String): String {
        SPOTIFY_PATH.find(link)?.let { m -> return "spotify:${m.groupValues[1].lowercase()}:${m.groupValues[2]}" }
        return link
    }

    /**
     * The link used to open the playlist in its app. For YouTube Music this is the "watch" form,
     * which starts playing the list on its own; the others open the playlist page.
     */
    fun openUri(link: String): String {
        if (providerOf(link) == YOUTUBE_MUSIC) YTM_LIST.find(link)?.let { return "https://music.youtube.com/watch?list=${it.groupValues[1]}" }
        return link
    }
}

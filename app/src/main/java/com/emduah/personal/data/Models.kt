package com.emduah.personal.data

import org.json.JSONObject

enum class Screen(val key: String) { HOME("home"), LOCK("lock") }
data class Profile(
    val screen: Screen,
    val enabled: Boolean = false,
    val minutes: Long = 20,
    val wifiOnly: Boolean = true,
    val motion: Boolean = false,
    val filters: String = "{\"sort\":\"random\"}",
    val revision: String = "initial",
    val maxDownloadMb: Int = 120,
    val videoSeconds: Int = 60
) {
    fun validated(): Profile {
        require(minutes in 15..10080) { "Choose an interval between 15 minutes and 7 days." }
        require(maxDownloadMb in 1..1024) { "Download limit must be between 1 and 1024 MB." }
        require(videoSeconds in 0..3600) { "Video loop length must be 0 (full video) or up to 3600 seconds." }
        val json = JSONObject(filters)
        require(!json.has("trash") || !json.optBoolean("trash")) { "Trashed media cannot be used as wallpaper." }
        require(!motion || json.optString("type") != "image") { "Choose videos or mixed media for motion mode." }
        return this
    }
    fun requestFilters(): JSONObject = JSONObject(filters).apply {
        if (!motion) put("type", "image")
        if (optString("seed").isBlank()) put("seed", "mediavault")
        put("trash", false)
        put("max_bytes", maxDownloadMb.toLong() * 1024 * 1024)
    }
}
data class Connection(val baseUrl: String = "", val token: String = "", val name: String = "", val refreshToken: String = "", val sessionId: String = "")
data class CurrentMedia(val id: Long = 0, val name: String = "", val type: String = "", val file: String = "", val changedAt: Long = 0, val position: Int = 0, val message: String = "Ready when you are", val sequenceId: Long = 0)
data class Selection(val id: Long, val name: String, val type: String, val url: String, val nextPosition: Int)

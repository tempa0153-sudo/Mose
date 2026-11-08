package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    val name: String = "Cinema Fan",
    val customAvatarPath: String? = null,
    val defaultAction: String = "player",
    val autoplayTrailer: Boolean = true,
    val trailerMuted: Boolean = false,
    val preferredQuality: String = "Auto",
    val librarySortOrder: String = "custom",
    val libraryTileSize: String = "compact",
    val accentColorHex: String = "#1A73E8", // Google Blue default
    val hdhubEnabled: Boolean = true,
    val penguEnabled: Boolean = true,
    val isYoutubeConnected: Boolean = false,
    val youtubeAccountName: String? = null,
    val showTrailerDebug: Boolean = false,
    val runForegroundService: Boolean = false,
    val startOnBoot: Boolean = false
)

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cinestream_user_prefs", Context.MODE_PRIVATE)

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private fun loadProfile(): UserProfile {
        return UserProfile(
            name = prefs.getString("user_name", "Cinema Fan") ?: "Cinema Fan",
            customAvatarPath = prefs.getString("custom_avatar_path", null),
            defaultAction = prefs.getString("default_action", "player") ?: "player",
            autoplayTrailer = prefs.getBoolean("autoplay_trailer", true),
            trailerMuted = prefs.getBoolean("trailer_muted", false),
            preferredQuality = prefs.getString("preferred_quality", "Auto") ?: "Auto",
            librarySortOrder = prefs.getString("library_sort_order", "custom") ?: "custom",
            accentColorHex = prefs.getString("accent_color_hex", "#1A73E8") ?: "#1A73E8",
            hdhubEnabled = prefs.getBoolean("hdhub_enabled", true),
            penguEnabled = prefs.getBoolean("pengu_enabled", true),
            libraryTileSize = prefs.getString("library_tile_size", "compact") ?: "compact",
            isYoutubeConnected = prefs.getBoolean("yt_connected", false),
            youtubeAccountName = prefs.getString("yt_account_name", null),
            showTrailerDebug = prefs.getBoolean("show_trailer_debug", false),
            runForegroundService = prefs.getBoolean("run_foreground_service", false),
            startOnBoot = prefs.getBoolean("start_on_boot", false)
        )
    }

    fun updateRunForegroundService(enabled: Boolean) {
        prefs.edit().putBoolean("run_foreground_service", enabled).apply()
        _userProfile.value = _userProfile.value.copy(runForegroundService = enabled)
    }

    fun updateStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean("start_on_boot", enabled).apply()
        _userProfile.value = _userProfile.value.copy(startOnBoot = enabled)
    }

    fun updateShowTrailerDebug(show: Boolean) {
        prefs.edit().putBoolean("show_trailer_debug", show).apply()
        _userProfile.value = _userProfile.value.copy(showTrailerDebug = show)
    }

    fun updateAccentColor(hex: String) {
        val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
        prefs.edit().putString("accent_color_hex", cleanHex).apply()
        _userProfile.value = _userProfile.value.copy(accentColorHex = cleanHex)
    }

    fun updateName(name: String) {
        prefs.edit().putString("user_name", name.trim()).apply()
        _userProfile.value = _userProfile.value.copy(name = name.trim())
    }

    fun updateCustomAvatarPath(path: String?) {
        if (path != null) {
            prefs.edit().putString("custom_avatar_path", path).apply()
        } else {
            prefs.edit().remove("custom_avatar_path").apply()
        }
        _userProfile.value = _userProfile.value.copy(customAvatarPath = path)
    }

    fun updateDefaultAction(action: String) {
        prefs.edit().putString("default_action", action).apply()
        _userProfile.value = _userProfile.value.copy(defaultAction = action)
    }

    fun updateAutoplayTrailer(autoplay: Boolean) {
        prefs.edit().putBoolean("autoplay_trailer", autoplay).apply()
        _userProfile.value = _userProfile.value.copy(autoplayTrailer = autoplay)
    }

    fun updateTrailerMuted(muted: Boolean) {
        prefs.edit().putBoolean("trailer_muted", muted).apply()
        _userProfile.value = _userProfile.value.copy(trailerMuted = muted)
    }

    fun updatePreferredQuality(quality: String) {
        prefs.edit().putString("preferred_quality", quality).apply()
        _userProfile.value = _userProfile.value.copy(preferredQuality = quality)
    }

    fun updateLibrarySortOrder(sortOrder: String) {
        prefs.edit().putString("library_sort_order", sortOrder).apply()
        _userProfile.value = _userProfile.value.copy(librarySortOrder = sortOrder)
    }

    fun updateLibraryTileSize(size: String) {
        val clean = size.lowercase().trim()
        prefs.edit().putString("library_tile_size", clean).apply()
        _userProfile.value = _userProfile.value.copy(libraryTileSize = clean)
    }

    fun updateHdhubEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hdhub_enabled", enabled).apply()
        _userProfile.value = _userProfile.value.copy(hdhubEnabled = enabled)
    }

    fun updatePenguEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("pengu_enabled", enabled).apply()
        _userProfile.value = _userProfile.value.copy(penguEnabled = enabled)
    }

    fun updateYoutubeConnected(connected: Boolean, accountName: String? = null) {
        prefs.edit().putBoolean("yt_connected", connected).putString("yt_account_name", accountName).apply()
        _userProfile.value = _userProfile.value.copy(
            isYoutubeConnected = connected,
            youtubeAccountName = accountName
        )
    }

    fun saveCustomMeta(id: String, jsonStr: String) {
        prefs.edit().putString("custom_meta_$id", jsonStr).apply()
    }

    fun getCustomMeta(id: String): String? {
        return prefs.getString("custom_meta_$id", null)
    }

    fun saveCustomStreams(id: String, jsonStr: String) {
        prefs.edit().putString("custom_streams_$id", jsonStr).apply()
    }

    fun getCustomStreams(id: String): String? {
        return prefs.getString("custom_streams_$id", null)
    }

    fun saveCustomSlideshow(id: String, jsonStr: String) {
        prefs.edit().putString("custom_slideshow_$id", jsonStr).apply()
    }

    fun getCustomSlideshow(id: String): String? {
        return prefs.getString("custom_slideshow_$id", null)
    }

    fun hideStream(id: String, streamKey: String) {
        val set = prefs.getStringSet("hidden_streams_$id", emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(streamKey)
        prefs.edit().putStringSet("hidden_streams_$id", set).apply()
    }

    fun getHiddenStreams(id: String): Set<String> {
        return prefs.getStringSet("hidden_streams_$id", emptySet()) ?: emptySet()
    }

    fun clearCustomStreams(id: String) {
        prefs.edit().remove("custom_streams_$id").apply()
    }

    fun clearCustomMeta(id: String) {
        prefs.edit().remove("custom_meta_$id").apply()
    }

    fun clearCustomSlideshow(id: String) {
        prefs.edit().remove("custom_slideshow_$id").apply()
    }

    fun clearHiddenStreams(id: String) {
        prefs.edit().remove("hidden_streams_$id").apply()
    }

    fun saveCustomHomeCategories(jsonStr: String) {
        prefs.edit().putString("custom_home_categories", jsonStr).apply()
    }

    fun clearCustomHomeCategories() {
        prefs.edit().remove("custom_home_categories").apply()
    }

    fun getCustomHomeCategories(): String? {
        return prefs.getString("custom_home_categories", null)
    }

    fun saveCustomHomeHeroes(jsonStr: String) {
        prefs.edit().putString("custom_home_heroes", jsonStr).apply()
    }

    fun clearCustomHomeHeroes() {
        prefs.edit().remove("custom_home_heroes").apply()
    }

    fun getCustomHomeHeroes(): String? {
        return prefs.getString("custom_home_heroes", null)
    }
}

package com.reelsvault.app

import android.content.Context
import org.json.JSONObject

/** Tiny key-value store, keyed by video filename, for your own local notes/likes/saves.
 *  Nothing here ever leaves the phone - there's no real backend to comment on. */
class VideoStore(context: Context) {
    private val prefs = context.getSharedPreferences("reels_vault_meta", Context.MODE_PRIVATE)

    data class Meta(val liked: Boolean = false, val saved: Boolean = false, val comment: String = "")

    fun get(filename: String): Meta {
        val raw = prefs.getString(filename, null) ?: return Meta()
        return try {
            val json = JSONObject(raw)
            Meta(
                liked = json.optBoolean("liked", false),
                saved = json.optBoolean("saved", false),
                comment = json.optString("comment", "")
            )
        } catch (_: Exception) {
            Meta()
        }
    }

    fun set(filename: String, meta: Meta) {
        val json = JSONObject()
        json.put("liked", meta.liked)
        json.put("saved", meta.saved)
        json.put("comment", meta.comment)
        prefs.edit().putString(filename, json.toString()).apply()
    }
}

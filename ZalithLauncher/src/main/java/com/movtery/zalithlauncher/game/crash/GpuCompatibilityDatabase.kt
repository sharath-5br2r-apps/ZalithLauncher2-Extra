/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: offline GPU/renderer compatibility database
 */

package com.movtery.zalithlauncher.game.crash

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.movtery.zalithlauncher.game.crash.model.GpuCompatibility
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "GpuCompatibilityDatabase"
private const val ASSET_PATH = "gpu_compatibility.json"

/**
 * Loads updateable, local GPU compatibility rules. No network access is performed.
 */
object GpuCompatibilityDatabase {
    private var entries: List<GpuCompatibility> = emptyList()

    @Synchronized
    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        try {
            val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            // Deserialize element-by-element to avoid Gson returning LinkedTreeMap objects
            // when the JVM cannot verify the element type through type erasure at runtime.
            val gson = Gson()
            val array = gson.fromJson(json, JsonArray::class.java) ?: JsonArray()
            entries = (0 until array.size()).map { gson.fromJson(array[it], GpuCompatibility::class.java) }
            Logger.info(TAG, "Loaded ${entries.size} GPU compatibility entries.")
        } catch (error: Exception) {
            Logger.error(TAG, "Unable to load GPU compatibility database.", error)
            entries = emptyList()
        }
    }

    @Synchronized
    fun reload(context: Context) {
        entries = emptyList()
        load(context)
    }

    fun findMatch(gpu: String?, manufacturer: String?, renderer: String?): GpuCompatibility? {
        val haystack = listOf(gpu, manufacturer, renderer)
            .filterNotNull()
            .joinToString(" ")
            .lowercase()
        return entries
            .filter { entry ->
                entry.matchTerms.any { term -> haystack.contains(term.lowercase()) }
            }
            .maxByOrNull { entry ->
                entry.matchTerms.maxOfOrNull { term ->
                    if (haystack.contains(term.lowercase())) term.length else 0
                } ?: 0
            }
    }
}
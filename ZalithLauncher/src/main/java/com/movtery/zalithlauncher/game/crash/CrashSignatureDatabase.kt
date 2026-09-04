/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Signature Database — loads and parses crash_signatures.json
 */

package com.movtery.zalithlauncher.game.crash

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.movtery.zalithlauncher.game.crash.model.CrashSignature
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "CrashSignatureDatabase"
private const val ASSET_PATH = "crash_signatures.json"

/**
 * Loads the crash signature database from assets.
 * The database is updateable independently from launcher releases — in the future a remote
 * refresh path can be added here without changing any other code.
 */
object CrashSignatureDatabase {

    private var _signatures: List<CrashSignature> = emptyList()
    val signatures: List<CrashSignature> get() = _signatures

    /** Call once from application init or lazily from the analyzer. Thread-safe. */
    @Synchronized
    fun load(context: Context) {
        if (_signatures.isNotEmpty()) return
        try {
            val json = context.assets.open(ASSET_PATH).bufferedReader().readText()
            val type = object : TypeToken<List<CrashSignature>>() {}.type
            _signatures = Gson().fromJson(json, type) ?: emptyList()
            Logger.info(TAG, "Loaded ${_signatures.size} crash signatures.")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to load crash signatures — offline analysis will use empty DB.", e)
            _signatures = emptyList()
        }
    }

    /** Force reload (e.g. after a remote update). */
    @Synchronized
    fun reload(context: Context) {
        _signatures = emptyList()
        load(context)
    }
}

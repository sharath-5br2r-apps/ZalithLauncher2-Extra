/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Mod Analyzer
 */

package com.movtery.zalithlauncher.game.crash.analyzers

import com.movtery.zalithlauncher.game.crash.model.CrashEvidenceItem
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.game.crash.model.RepairAction

/**
 * Inspects mod-related crash causes: missing dependencies, conflicts, loader errors, etc.
 */
object ModAnalyzer {

    data class Result(
        val hasModIssue: Boolean,
        val confidence: Int,
        val evidence: List<CrashEvidenceItem>,
        val offendingMod: String?,
        val repairs: List<RepairAction>
    )

    private val MISSING_DEP_PATTERNS = listOf(
        Regex("""java\.lang\.NoClassDefFoundError:\s*([\w/\${'$'}\.]+)"""),
        Regex("""ClassNotFoundException:\s*([\w/\${'$'}\.]+)"""),
        Regex("""NoSuchMethodError:\s*([\w/\${'$'}\.]+)"""),
        Regex("""NoSuchFieldError:\s*([\w/\${'$'}\.]+)""")
    )

    private val FABRIC_ERROR_PATTERNS = listOf(
        "net.fabricmc.loader",
        "fabric.loader",
        "FabricLoader",
        "Incompatible mod set!",
        "Error loading mods",
        "net.fabricmc.api"
    )

    private val FORGE_ERROR_PATTERNS = listOf(
        "net.minecraftforge.fml",
        "cpw.mods.fml",
        "FMLLoader",
        "ModLoadingException",
        "forge loading"
    )

    private val NEOFORGE_ERROR_PATTERNS = listOf(
        "net.neoforged",
        "neoforge.fml",
        "FMLLoader"
    )

    private val MOD_CONFLICT_PATTERNS = listOf(
        "mixin conflict",
        "MixinException",
        "ClassTransformerException",
        "Cannot apply mixin",
        "Mixin apply for mod",
        "duplicate mod",
        "org.spongepowered.asm.mixin"
    )

    private val KNOWN_INCOMPATIBLE_PAIRS = listOf(
        Pair("sodium", "optifine"),
        Pair("iris", "optifine"),
        Pair("lithium", "optifine")
    )

    fun analyze(session: CrashSession): Result {
        val allLogs = "${session.gameLog}\n${session.debugLog}\n${session.jvmLog}\n${session.crashReportContent}"
        val evidence = mutableListOf<CrashEvidenceItem>()
        var score = 0
        var offendingMod: String? = null

        // ── Missing dependency detection ──────────────────────────────────────
        for (pattern in MISSING_DEP_PATTERNS) {
            val match = pattern.find(allLogs)
            if (match != null) {
                val missingClass = match.groupValues.getOrElse(1) { "" }
                evidence.add(CrashEvidenceItem(
                    text = "Missing class/method detected: $missingClass",
                    weight = 0.85f,
                    source = CrashEvidenceItem.EvidenceSource.MOD_ANALYZER
                ))
                score += 50

                // Try to infer which mod the missing class belongs to
                offendingMod = inferModFromClass(missingClass, session.installedMods)
                if (offendingMod != null) {
                    evidence.add(CrashEvidenceItem(
                        text = "Likely offending mod: $offendingMod",
                        weight = 0.7f,
                        source = CrashEvidenceItem.EvidenceSource.MOD_ANALYZER
                    ))
                    score += 15
                }
                break
            }
        }

        // ── Fabric loader errors ──────────────────────────────────────────────
        val fabricHits = FABRIC_ERROR_PATTERNS.count { allLogs.contains(it, ignoreCase = true) }
        if (fabricHits > 0) {
            evidence.add(CrashEvidenceItem(
                text = "Fabric loader error detected ($fabricHits indicators)",
                weight = 0.8f,
                source = CrashEvidenceItem.EvidenceSource.MOD_ANALYZER
            ))
            score += fabricHits * 12
        }

        // ── Forge/NeoForge loader errors ──────────────────────────────────────
        val forgeHits = FORGE_ERROR_PATTERNS.count { allLogs.contains(it, ignoreCase = true) }
        val neoForgeHits = NEOFORGE_ERROR_PATTERNS.count { allLogs.contains(it, ignoreCase = true) }
        if (forgeHits > 0 || neoForgeHits > 0) {
            val loaderName = if (neoForgeHits > forgeHits) "NeoForge" else "Forge"
            evidence.add(CrashEvidenceItem(
                text = "$loaderName loader error detected",
                weight = 0.8f,
                source = CrashEvidenceItem.EvidenceSource.MOD_ANALYZER
            ))
            score += (forgeHits + neoForgeHits) * 10
        }

        // ── Mixin / mod conflict detection ────────────────────────────────────
        val conflictHits = MOD_CONFLICT_PATTERNS.count { allLogs.contains(it, ignoreCase = true) }
        if (conflictHits > 0) {
            evidence.add(CrashEvidenceItem(
                text = "Mod conflict or Mixin error detected ($conflictHits indicators)",
                weight = 0.75f,
                source = CrashEvidenceItem.EvidenceSource.MOD_ANALYZER
            ))
            score += conflictHits * 15
        }

        // ── Known incompatible pair detection ─────────────────────────────────
        val modNames = session.installedMods.map { it.lowercase() }
        for ((a, b) in KNOWN_INCOMPATIBLE_PAIRS) {
            val hasA = modNames.any { it.contains(a) }
            val hasB = modNames.any { it.contains(b) }
            if (hasA && hasB) {
                evidence.add(CrashEvidenceItem(
                    text = "Known incompatible mods installed together: $a + $b",
                    weight = 0.9f,
                    source = CrashEvidenceItem.EvidenceSource.MOD_ANALYZER
                ))
                score += 45
                if (offendingMod == null) offendingMod = b
            }
        }

        // ── Missing Fabric API ────────────────────────────────────────────────
        val hasFabricLoader = session.loader?.contains("fabric", ignoreCase = true) == true
        val hasFabricApi = modNames.any { it.contains("fabric-api") || it.contains("fabric_api") }
        val needsFabricApi = modNames.size > 1  // heuristic: any mods beyond just the loader
        if (hasFabricLoader && !hasFabricApi && needsFabricApi && allLogs.contains("fabric", ignoreCase = true)) {
            evidence.add(CrashEvidenceItem(
                text = "Fabric API not found in mods directory — many mods require it",
                weight = 0.6f,
                source = CrashEvidenceItem.EvidenceSource.MOD_ANALYZER
            ))
            score += 20
        }

        // ── Repairs ───────────────────────────────────────────────────────────
        val repairs = mutableListOf<RepairAction>()
        if (offendingMod != null) {
            repairs.add(RepairAction(
                type = RepairAction.RepairType.DISABLE_SELECTED_MOD,
                label = "Disable $offendingMod",
                description = "Temporarily disable the suspected offending mod: $offendingMod",
                rationale = "Disabling the mod that matches the missing class or conflict pattern can confirm if it is the root cause.",
                isReversible = true,
                estimatedSuccessRate = 65,
                difficulty = 1,
                extraData = mapOf("modName" to (offendingMod ?: ""))
            ))
        }

        return Result(
            hasModIssue = score >= 25,
            confidence = minOf(score, 98),
            evidence = evidence,
            offendingMod = offendingMod,
            repairs = repairs
        )
    }

    private fun inferModFromClass(className: String, installedMods: List<String>): String? {
        // Strip class path to rough package root
        val packageRoot = className.replace('/', '.').split(".").firstOrNull() ?: return null
        return installedMods.firstOrNull { mod ->
            mod.lowercase().contains(packageRoot.lowercase())
        }
    }
}

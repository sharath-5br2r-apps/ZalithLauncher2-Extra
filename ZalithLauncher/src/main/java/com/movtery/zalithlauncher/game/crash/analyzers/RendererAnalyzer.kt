/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Renderer Analyzer
 */

package com.movtery.zalithlauncher.game.crash.analyzers

import com.movtery.zalithlauncher.game.crash.model.CrashCategory
import com.movtery.zalithlauncher.game.crash.model.CrashEvidenceItem
import com.movtery.zalithlauncher.game.crash.model.CrashSession
import com.movtery.zalithlauncher.game.crash.model.GpuCompatibility
import com.movtery.zalithlauncher.game.crash.model.RepairAction

/**
 * Inspects renderer-related crash indicators and contributes evidence.
 */
object RendererAnalyzer {

    data class Result(
        val isRendererCrash: Boolean,
        val confidence: Int,
        val evidence: List<CrashEvidenceItem>,
        val suggestedRenderer: String?,
        val repairs: List<RepairAction>
    )

    private val RENDERER_CRASH_SIGNALS = listOf(
        "SIGSEGV", "SIGABRT", "SIGFPE", "SIGBUS",
        "libEGL", "libGLES", "libgl4es", "libmobileglues",
        "EGL_BAD", "GL_INVALID", "eglCreateContext", "eglMakeCurrent",
        "OpenGL ES", "Vulkan", "VkResult", "vkCreate",
        "RendererPlugin", "renderer init", "renderer crash",
        "Could not initialize EGL", "EGL error", "Failed to create EGL context",
        "GLSurfaceView", "GLContext", "glGetError"
    )

    private val RENDERER_INIT_FAILURES = listOf(
        "renderer initialization failed",
        "failed to initialize renderer",
        "unable to initialize opengl",
        "egl init failed",
        "Failed to create EGL context",
        "Could not initialize EGL"
    )

    private val NATIVE_CRASH_SIGNALS = listOf(
        "Fatal signal 11 (SIGSEGV)",
        "Fatal signal 6 (SIGABRT)",
        "Fatal signal 7 (SIGBUS)",
        "Fatal signal 8 (SIGFPE)"
    )

    fun analyze(session: CrashSession, gpuCompatibility: GpuCompatibility? = null): Result {
        val allLogs = "${session.gameLog}\n${session.debugLog}\n${session.jvmLog}\n${session.hsErrLog}"
        val evidence = mutableListOf<CrashEvidenceItem>()
        var score = 0

        // Check for native crash signals
        for (signal in NATIVE_CRASH_SIGNALS) {
            if (allLogs.contains(signal, ignoreCase = true)) {
                evidence.add(CrashEvidenceItem(
                    text = "Native crash signal detected: $signal",
                    weight = 0.8f,
                    source = CrashEvidenceItem.EvidenceSource.RENDERER_ANALYZER
                ))
                score += 25
                break
            }
        }

        // Check for renderer-specific crash indicators
        var rendererHits = 0
        for (signal in RENDERER_CRASH_SIGNALS) {
            if (allLogs.contains(signal, ignoreCase = true)) {
                rendererHits++
            }
        }
        if (rendererHits > 0) {
            evidence.add(CrashEvidenceItem(
                text = "Renderer-related crash indicators found ($rendererHits matches)",
                weight = 0.6f,
                source = CrashEvidenceItem.EvidenceSource.RENDERER_ANALYZER
            ))
            score += minOf(rendererHits * 8, 30)
        }

        // Check for explicit renderer init failure
        for (failure in RENDERER_INIT_FAILURES) {
            if (allLogs.contains(failure, ignoreCase = true)) {
                evidence.add(CrashEvidenceItem(
                    text = "Renderer initialization failure detected",
                    weight = 0.9f,
                    source = CrashEvidenceItem.EvidenceSource.RENDERER_ANALYZER
                ))
                score += 35
                break
            }
        }

        // Renderer-specific evidence
        val activeRenderer = session.renderer?.lowercase() ?: ""
        if (activeRenderer.isNotEmpty()) {
            evidence.add(CrashEvidenceItem(
                text = "Active renderer at time of crash: ${session.renderer}",
                weight = 0.5f,
                source = CrashEvidenceItem.EvidenceSource.RENDERER_ANALYZER
            ))
        }

        gpuCompatibility?.let { compatibility ->
            val active = session.renderer
            val isAvoided = active != null && compatibility.avoidRenderers.any {
                active.contains(it, ignoreCase = true)
            }
            evidence.add(CrashEvidenceItem(
                text = if (isAvoided) {
                    "${compatibility.gpuFamily} compatibility data recommends ${compatibility.recommendedRenderer ?: "another renderer"} instead of ${active}."
                } else {
                    "GPU compatibility profile matched: ${compatibility.gpuFamily}."
                },
                weight = if (isAvoided) 0.85f else 0.55f,
                source = CrashEvidenceItem.EvidenceSource.RENDERER_ANALYZER
            ))
            if (isAvoided) score += 20
        }

        // Check GPU string for known problematic combinations
        val gpuRenderer = session.gpuRenderer?.lowercase() ?: ""
        if (gpuRenderer.contains("adreno") && allLogs.contains("angle", ignoreCase = true)) {
            evidence.add(CrashEvidenceItem(
                text = "Adreno GPU with ANGLE renderer — known compatibility issues on some drivers",
                weight = 0.7f,
                source = CrashEvidenceItem.EvidenceSource.RENDERER_ANALYZER
            ))
            score += 15
        }

        // Exit code 139 = SIGSEGV (segfault), 132 = SIGILL
        if (session.exitCode == 139 || (session.isSignal && session.exitCode == 11)) {
            evidence.add(CrashEvidenceItem(
                text = "SIGSEGV (segmentation fault) — often caused by renderer or native library crash",
                weight = 0.75f,
                source = CrashEvidenceItem.EvidenceSource.RENDERER_ANALYZER
            ))
            score += 20
        }

        val isRendererCrash = score >= 30
        val confidence = minOf(score, 98)

        // Build repairs
        val repairs = mutableListOf<RepairAction>()
        if (isRendererCrash) {
            repairs.add(RepairAction(
                type = RepairAction.RepairType.SWITCH_RENDERER,
                label = "Switch Renderer",
                description = "Change the active renderer to a known-compatible alternative",
                rationale = "The crash evidence strongly suggests the current renderer failed. Switching to a different renderer often resolves this immediately.",
                isReversible = true,
                estimatedSuccessRate = 75,
                difficulty = 1
            ))
            repairs.add(RepairAction(
                type = RepairAction.RepairType.RESET_RENDERER_SETTINGS,
                label = "Reset Renderer Settings",
                description = "Restore renderer configuration to safe defaults",
                rationale = "A corrupted or misconfigured renderer setting can cause initialization failures.",
                isReversible = true,
                estimatedSuccessRate = 55,
                difficulty = 1
            ))
        }

        return Result(
            isRendererCrash = isRendererCrash,
            confidence = confidence,
            evidence = evidence,
            suggestedRenderer = gpuCompatibility?.recommendedRenderer,
            repairs = repairs
        )
    }

    /** Detect which renderer was active from log content (fallback when not passed explicitly) */
    fun detectRendererFromLogs(logs: String): String? {
        return when {
            logs.contains("mobileglues", ignoreCase = true) -> "MobileGlues"
            logs.contains("gl4es", ignoreCase = true) -> "GL4ES"
            logs.contains("angle", ignoreCase = true) -> "ANGLE"
            logs.contains("virgl", ignoreCase = true) -> "VirGL"
            logs.contains("zink", ignoreCase = true) -> "Zink"
            logs.contains("panfrost", ignoreCase = true) -> "Panfrost"
            logs.contains("freedreno", ignoreCase = true) -> "Freedreno"
            else -> null
        }
    }
}

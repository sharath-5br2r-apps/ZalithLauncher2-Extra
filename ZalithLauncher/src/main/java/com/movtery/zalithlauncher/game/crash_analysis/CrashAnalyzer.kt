package com.movtery.zalithlauncher.game.crash_analysis

object CrashAnalyzer {
    fun analyze(context: CrashContext): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()
        val log = context.logContent
        val mods = context.mods
        val modIds = mods.map { it.id.lowercase() }
        val modNames = mods.map { it.name.lowercase() }

        fun hasMod(id: String) = modIds.any { it.contains(id.lowercase()) }
        fun hasModName(name: String) = modNames.any { it.contains(name.lowercase()) }

        tips.addAll(analyzeExitCode(context))
        tips.addAll(analyzeJvmCrashes(log))
        tips.addAll(analyzeMemoryIssues(log, context))
        tips.addAll(analyzeModLoadingIssues(log, mods))
        tips.addAll(analyzeFabricIssues(log, mods))
        tips.addAll(analyzeForgeIssues(log, mods))
        tips.addAll(analyzeRenderingIssues(log, mods, context))
        tips.addAll(analyzeWorldIssues(log))
        tips.addAll(analyzeModSpecificIssues(log, mods, modIds, modNames))
        tips.addAll(analyzeNetworkIssues(log))
        tips.addAll(analyzeFileSystemIssues(log))
        tips.addAll(analyzeAndroidIssues(log))

        return tips
    }

    private fun analyzeExitCode(ctx: CrashContext): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()
        when (ctx.exitCode) {
            137, 134 -> tips.add(
                CrashTip(Severity.ERROR,
                    "Out of Memory (OOM Killer)",
                    "The game process was killed by the operating system because it ran out of memory (exit code ${ctx.exitCode}).",
                    "Increase allocated RAM in launcher settings. Current allocation: ${ctx.allocatedRamMb}MB. Try 4096MB or higher.")
            )
            139 -> tips.add(
                CrashTip(Severity.ERROR,
                    "Segmentation Fault (SIGSEGV)",
                    "The game crashed with a segmentation fault (exit code 139). This usually indicates a GPU driver issue or hardware instability.",
                    "Update your GPU drivers. If you are using Zink or a custom renderer, try switching to a different renderer.")
            )
            132 -> tips.add(
                CrashTip(Severity.ERROR,
                    "Illegal Instruction (SIGILL)",
                    "The game crashed with an illegal instruction exception (exit code 132). Your CPU may not support instructions required by the Minecraft version or Java runtime.",
                    "Try using an older Java version, or update your Java runtime. If on Android, try a different JVM build.")
            )
        }
        if (ctx.exitCode != 0 && ctx.isSignal) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Process Terminated by Signal",
                    "The game JVM was terminated by an external signal. This can happen due to OOM killer, system shutdown, or a crash in native code.",
                    "Check launcher logs for details. Ensure your device has enough free RAM and storage.")
            )
        }
        if (ctx.exitCode == 1 && ctx.logContent.isBlank()) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "JVM Failed to Start",
                    "The Java Virtual Machine failed to start (exit code 1) with no log output. Java may not be found or is corrupted.",
                    "Reinstall Java runtime in launcher settings. Make sure the selected Java path is valid.")
            )
        }
        return tips
    }

    private fun analyzeJvmCrashes(log: String): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("# Internal Error") || log.contains("# A fatal error has been detected")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "JVM Internal Error",
                    "The Java Virtual Machine encountered an internal error. This can be caused by hardware instability, a Java bug, or a native library crash.",
                    "Try a different Java version. If the issue persists, your device may have hardware issues (RAM, CPU).")
            )
        }

        if (log.contains("UnsupportedClassVersionError")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Mod Compiled for Newer Java",
                    "A mod was compiled for a newer version of Java than the one you are using (${"${'$'}{ctx.javaVersion}" ?: "unknown"}).",
                    "Update your Java runtime to the latest version, or update the mod to a version compatible with your Java version.")
            )
        }

        if (log.contains("StackOverflowError")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Stack Overflow",
                    "A mod caused infinite recursion, exhausting the call stack. This is typically a bug in a mod.",
                    "Identify the mod from the stack trace and report the issue to the mod author. Try removing recently added mods.")
            )
        }

        if (log.contains("UnsatisfiedLinkError")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Native Library Missing",
                    "A native library required by a mod or Minecraft is missing or failed to load.",
                    "Reinstall the launcher. If the issue persists, update your GPU drivers or try a different renderer.")
            )
        }

        if (log.contains("NoClassDefFoundError") || log.contains("ClassNotFoundException")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Missing Class or Dependency",
                    "A required class could not be found. This usually means a mod dependency is missing or a mod is corrupted.",
                    "Check the error message for the missing class name. Look up which mod provides it and install the correct version.")
            )
        }

        if (log.contains("Could not find or load main class")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Corrupted Minecraft Installation",
                    "Minecraft's main class could not be found. The game JAR is missing or corrupted.",
                    "Reinstall the Minecraft version from the launcher's version management screen.")
            )
        }

        if (log.contains("Invalid or corrupt jarfile")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Corrupted JAR File",
                    "A JAR file (Minecraft or a mod) is corrupted and cannot be read.",
                    "Reinstall the Minecraft version. If the issue is mod-related, redownload the problematic mod.")
            )
        }

        return tips
    }

    private fun analyzeMemoryIssues(log: String, ctx: CrashContext): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("OutOfMemoryError: Java heap space")) {
            val suggestion = if (ctx.allocatedRamMb < 4096) {
                "Increase allocated RAM to at least 4096MB. Current: ${ctx.allocatedRamMb}MB."
            } else {
                "You have ${ctx.allocatedRamMb}MB allocated which should be enough. This may be a memory leak in a mod. Try removing recently added mods."
            }
            tips.add(
                CrashTip(Severity.ERROR,
                    "Out of Memory (Heap Space)",
                    "Minecraft ran out of allocated memory (Java heap space).",
                    suggestion)
            )
        }

        if (log.contains("OutOfMemoryError: Metaspace")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Out of Memory (Metaspace)",
                    "Minecraft ran out of Metaspace memory. This usually happens with too many mods loaded.",
                    "Remove some mods or increase Metaspace by adding '-XX:MaxMetaspaceSize=256M' to JVM arguments.")
            )
        }

        if (log.contains("OutOfMemoryError: GC overhead limit exceeded")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "GC Overhead Limit Exceeded",
                    "The garbage collector spent too much time (98%) collecting very little memory (2%). The game is effectively out of memory.",
                    "Increase allocated RAM. If already high, a mod may have a memory leak.")
            )
        }

        if (log.contains("Could not reserve enough space for object heap")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "System Out of Memory",
                    "The system does not have enough free memory to allocate the requested heap size.",
                    "Close other applications. Reduce allocated RAM in launcher settings. Restart your device.")
            )
        }

        if (log.contains("Unable to create native thread")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Thread Limit Reached",
                    "The operating system thread limit has been reached. This can happen with very large modpacks.",
                    "Remove some mods. On Linux, you can increase the thread limit with 'ulimit -u'.")
            )
        }

        if (log.contains("OutOfMemoryError") && (log.contains("mipmap") || log.contains("texture"))) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Texture Memory Issue",
                    "Minecraft ran out of memory while loading textures. This is often caused by high-resolution texture packs.",
                    "Reduce texture pack resolution, disable mipmapping, or increase allocated RAM.")
            )
        }

        if (log.contains("OutOfMemoryError") && (log.contains("world") || log.contains("chunk"))) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "World Loading Memory Issue",
                    "Minecraft ran out of memory while loading world chunks. The world may be corrupted or render distance is too high.",
                    "Reduce render distance. If the issue persists, the world may be corrupted — try loading a backup.")
            )
        }

        if (log.contains("Direct buffer memory")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "GPU Out of Memory",
                    "Minecraft ran out of GPU memory (direct buffer memory). This often happens with shaders or high-resolution resource packs.",
                    "Disable shaders, reduce render distance, or lower texture quality.")
            )
        }

        return tips
    }

    private fun analyzeModLoadingIssues(log: String, mods: List<ModInfo>): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("Duplicate mods") || log.contains("found two jars")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Duplicate Mod Detected",
                    "The same mod was found twice in the mods folder. This causes conflicts.",
                    "Check your mods folder for duplicate JAR files and remove the extra copy.")
            )
        }

        if (log.contains("Conflicting mods")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Conflicting Mods",
                    "Two or more mods conflict with each other and cannot be loaded together.",
                    "Check the error message for which mods conflict. Remove or replace one of them.")
            )
        }

        if (log.contains("Unmet dependency") || log.contains("Missing dependency")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Missing Mod Dependency",
                    "A mod requires another mod (dependency) that is not installed.",
                    "Read the error message carefully — it lists which mod is needed. Install the required mod.")
            )
        }

        if (log.contains("requires version")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Incompatible Mod Version",
                    "A mod requires a specific version of another mod that does not match what is installed.",
                    "Update the mods listed in the error to compatible versions.")
            )
        }

        if (mods.size > 150) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Very Large Modpack",
                    "You have ${mods.size} mods installed. Large modpacks require more RAM and have higher chance of conflicts.",
                    "Increase allocated RAM. If crashes persist, try disabling mods in groups to find the culprit.")
            )
        }

        if (log.contains("Mod resolution failed") || log.contains("ModResolutionException")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Mod Resolution Error",
                    "Fabric/Quilt failed to resolve all mod dependencies. One or more mods have incompatible version requirements.",
                    "Check the error details for specific version conflicts. Update all mods to their latest versions.")
            )
        }

        if (log.contains("Incompatible modset")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Incompatible Mod Set",
                    "Forge/NeoForge detected an incompatible set of mods or a version mismatch with the mod loader.",
                    "Update Forge/NeoForge and all mods to versions compatible with your Minecraft version.")
            )
        }

        if (log.contains("Could not execute entrypoint stage") || log.contains("Could not execute entrypoint")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Mod Failed to Initialize",
                    "A mod failed during its initialization phase. The crash log should indicate which mod caused this.",
                    "Look for the mod name in the stack trace. Update or remove the problematic mod.")
            )
        }

        return tips
    }

    private fun analyzeFabricIssues(log: String, mods: List<ModInfo>): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()
        val fabricLoader = mods.find { it.id == "fabricloader" || it.id == "fabric-loader" }

        if (log.contains("mixin") && (log.contains("apply failed") || log.contains("Mixin apply failed") || log.contains("MixinCancellationException"))) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Mixin Application Failed",
                    "A mod could not apply its mixin transformations. This usually means the mod is incompatible with your Minecraft version or another mod conflicts with it.",
                    "Update the mod whose mixin failed. If the issue persists, check for incompatible mod combinations.")
            )
        }

        if (log.contains("InjectionError") || log.contains("injection failed")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Mixin Injection Failure",
                    "A mixin injection failed because the target class or method was not found. The mod needs to be updated for your Minecraft version.",
                    "Update the mod mentioned in the error. If no update is available, you may need to remove it.")
            )
        }

        if (log.contains("Mixin target") && log.contains("was not found")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Missing Mixin Target",
                    "A mod expects a class that does not exist in your Minecraft version. The mod is likely outdated.",
                    "Update the mod to a version compatible with your Minecraft version.")
            )
        }

        if (log.contains("does not specify `minVersion`")) {
            tips.add(
                CrashTip(Severity.INFO,
                    "Outdated Mixin Config",
                    "A mod uses an outdated mixin configuration format. This is usually harmless but indicates the mod may be outdated.",
                    "Update the mod if a newer version is available.")
            )
        }

        if (fabricLoader != null && fabricLoader.version.startsWith("0.")) {
            val minor = fabricLoader.version.split(".").getOrNull(1)?.toIntOrNull() ?: 0
            if (minor < 15) {
                tips.add(
                    CrashTip(Severity.WARNING,
                        "Outdated Fabric Loader",
                        "Your Fabric Loader version (${fabricLoader.version}) is outdated. Newer mods may require a more recent version.",
                        "Update Fabric Loader to the latest version.")
                )
            }
        }

        return tips
    }

    private fun analyzeForgeIssues(log: String, mods: List<ModInfo>): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("Loading errors encountered")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Forge Loading Errors",
                    "Forge encountered one or more errors while loading mods. Check the log for a list of failed mods.",
                    "Update the mods listed in the error. If the issue persists, try removing half of your mods to isolate the problem.")
            )
        }

        if (log.contains("is missing `META-INF/mods.toml`")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Corrupted or Invalid Mod",
                    "A file in your mods folder is not a valid Forge mod (missing META-INF/mods.toml).",
                    "Remove the corrupted JAR file from the mods folder. Redownload if needed.")
            )
        }

        if (log.contains("Mod is missing `META-INF/mods.toml`")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Fabric Mod in Forge",
                    "A Fabric mod was placed in a Forge mods folder. Fabric and Forge mods are not compatible.",
                    "Remove Fabric mods from the folder. Use either Fabric or Forge, not both.")
            )
        }

        if (log.contains("Registry snapshot validation failed")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Registry Validation Failed",
                    "The world's registry data does not match your current mods. This happens when mods are added/removed from an existing world.",
                    "Load the world with the same mods that were installed when it was created. Alternatively, create a new world.")
            )
        }

        if (log.contains("Capability token already registered")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Capability Registration Conflict",
                    "Two mods are trying to register the same capability. This is a mod compatibility issue.",
                    "Check for duplicate mods or mods that provide the same functionality. Remove one of the conflicting mods.")
            )
        }

        if (log.contains("Feature order cycle")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "World Generation Cycle",
                    "There is a circular dependency in world generation features. This is caused by incompatible world generation mods.",
                    "Disable world generation mods (biome, dimension, structure mods) one by one to find the conflict.")
            )
        }

        return tips
    }

    private fun analyzeRenderingIssues(log: String, mods: List<ModInfo>, ctx: CrashContext): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("GL error") || log.contains("OpenGL error") || log.contains("GL_ERROR")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "OpenGL Error",
                    "An OpenGL error occurred during rendering. This usually indicates a GPU driver issue or shader problem.",
                    "Update your GPU drivers. If using shaders, try disabling them. If using Zink, try switching to a different renderer.")
            )
        }

        if (log.contains("GL_OUT_OF_MEMORY")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "GPU Out of Memory",
                    "Your GPU ran out of video memory. This often happens with shaders, high-resolution texture packs, or high render distances.",
                    "Reduce render distance, disable shaders, or lower texture quality.")
            )
        }

        if (log.contains("Invalid frame buffer operation")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Framebuffer Error",
                    "An invalid framebuffer operation occurred. This is usually caused by incompatible shader packs.",
                    "Try a different shader pack or disable shaders entirely.")
            )
        }

        if (log.contains("Failed to create graphics context") || log.contains("Pixel format not accelerated")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Graphics Context Failed",
                    "Minecraft could not create a graphics context. Your GPU driver may not support the required OpenGL version.",
                    "Update GPU drivers. If on Android, try switching between Zink / OpenGLES / Vulkan renderers in launcher settings.")
            )
        }

        if (log.contains("LVIII Illegal Instruction") || log.contains("illegal instruction")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "CPU Instruction Not Supported",
                    "Your CPU does not support an instruction required by the renderer or game version.",
                    "Try a different renderer (e.g., switch from Zink to OpenGLES). If on an older device, some mods may not work.")
            )
        }

        return tips
    }

    private fun analyzeWorldIssues(log: String): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("Loading NBT data") && (log.contains("crash") || log.contains("error") || log.contains("Exception"))) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Corrupted World NBT Data",
                    "The game crashed while loading NBT data (world save data). The world may be corrupted.",
                    "Restore a backup of the world. Alternatively, use tools like MCA Selector to repair corrupted chunks.")
            )
        }

        if (log.contains("Tried to load world with data version")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "World Version Mismatch",
                    "The world was saved with a different version of Minecraft and cannot be loaded.",
                    "Load the world with the Minecraft version it was created in, then upgrade it properly.")
            )
        }

        if (log.contains("Biome ID overflow") || log.contains("Block ID") && log.contains("already registered") || log.contains("Item ID overflow")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "ID Overflow (Legacy Minecraft)",
                    "On Minecraft 1.12.2 and older, there is a limit on block/item IDs. Too many content mods have exceeded this limit.",
                    "Remove some content mods. For 1.12.2, use JustEnoughIDs mod to increase the ID limit.")
            )
        }

        if (log.contains("Missing mapping")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Missing Block/Item Mapping",
                    "The world contains blocks or items from a mod that is no longer installed.",
                    "Reinstall the missing mod. If you intentionally removed it, use NBT editing tools to remove the affected blocks/items.")
            )
        }

        if (log.contains("Found BlockEntity tag without a block")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Orphaned Block Entity",
                    "A block entity exists without its corresponding block. The world data is slightly corrupted.",
                    "Run '/fix' commands if available, or use MCA Selector to repair the affected chunks.")
            )
        }

        if (log.contains("Unknown biome")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Unknown Biome",
                    "The world contains a biome from a mod that is no longer installed. This can cause world loading issues.",
                    "Reinstall the biome mod, or regenerate the affected chunks.")
            )
        }

        return tips
    }

    private fun analyzeModSpecificIssues(
        log: String,
        mods: List<ModInfo>,
        modIds: List<String>,
        modNames: List<String>
    ): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        fun hasMod(id: String) = modIds.any { it.contains(id.lowercase()) }
        fun hasModName(name: String) = modNames.any { it.contains(name.lowercase()) }
        fun isModInstalled(id: String) = mods.any { it.id.lowercase() == id.lowercase() }
        fun modVersion(id: String): String? = mods.find { it.id.lowercase() == id.lowercase() }?.version

        // Sodium + Iris version mismatch
        if (hasMod("sodium") && hasMod("iris")) {
            val sodiumVer = modVersion("sodium")
            val irisVer = modVersion("iris")
            if (sodiumVer != null && irisVer != null) {
                tips.add(
                    CrashTip(Severity.INFO,
                        "Sodium + Iris Detected",
                        "Iris $irisVer and Sodium $sodiumVer are installed. Make sure they are compatible versions.",
                        "Iris usually requires a specific Sodium build. Check the Iris Modrinth page for compatible Sodium versions.")
                )
            }
        }

        // OptiFine detected
        if (hasModName("optifine") || hasMod("optifine")) {
            val hasSodium = hasMod("sodium") || hasMod("rubidium") || hasMod("embeddium")
            val hasIris = hasMod("iris") || hasMod("oculus")
            if (hasSodium || hasIris) {
                tips.add(
                    CrashTip(Severity.ERROR,
                        "OptiFine Incompatible with Sodium/Iris",
                        "OptiFine is installed alongside Sodium, Iris, or their forks. These mods are incompatible with OptiFine.",
                        "Remove OptiFine. Use Sodium (performance) and Iris (shaders) as modern alternatives.")
                )
            }
            tips.add(
                CrashTip(Severity.WARNING,
                    "OptiFine Detected",
                    "OptiFine is installed. It is known to cause many compatibility issues with other mods.",
                    "Consider replacing OptiFine with Sodium (performance) + Iris (shaders) + Entity Culling (entity optimization) + Lithium (general optimization).")
            )
        }

        // Rubidium → Embeddium
        if (hasMod("rubidium")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Rubidium is Abandoned",
                    "Rubidium is no longer maintained and has known issues. Embeddium is the actively maintained fork.",
                    "Replace Rubidium with Embeddium (same functionality, actively maintained).")
            )
        }

        // Oculus needs Embeddium
        if (hasMod("oculus") && !hasMod("embeddium")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Oculus Requires Embeddium",
                    "Oculus (shaders for Forge) requires Embeddium as a dependency. Embeddium is not installed.",
                    "Install Embeddium (the actively maintained fork of Rubidium).")
            )
        }

        // Indium missing
        if (log.contains("Indium") && log.contains("not found") || log.contains("MissingIndium") || (log.contains("FRAPI") && !hasMod("indium"))) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Indium Required",
                    "A mod requires Indium (Fabric Rendering API compatibility layer) but it is not installed.",
                    "Install Indium mod. This provides compatibility for mods that need the Fabric Rendering API alongside Sodium.")
            )
        }

        // Create + Flywheel
        if (hasMod("create") && !hasMod("flywheel") && !hasMod("forge") && !hasMod("neoforge")) {
            val createVer = modVersion("create")
            tips.add(
                CrashTip(Severity.WARNING,
                    "Create Requires Flywheel",
                    "Create mod requires Flywheel as a dependency. On newer versions, Flywheel may be bundled inside Create.",
                    "If using Create 6+, Flywheel is bundled. If using older Create, install Flywheel separately.")
            )
        }

        // JEI + REI/EMI conflict
        val recipeMods = listOf("jei", "rei", "emi").filter { hasMod(it) }
        if (recipeMods.size > 1) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Multiple Recipe Mods",
                    "You have ${recipeMods.joinToString(", ")} installed. Multiple recipe mods can conflict.",
                    "Keep only one recipe mod. JEI is the most compatible, EMI is the most modern.")
            )
        }

        // Sinytra Connector
        if (hasMod("connectormod") || hasMod("sinytra_connector")) {
            if (log.contains("Incompatible") || log.contains("Connector")) {
                tips.add(
                    CrashTip(Severity.ERROR,
                        "Sinytra Connector Incompatibility",
                        "Sinytra Connector tried to load a Fabric mod on Forge but the mod is incompatible with Connector.",
                        "Check the Connector compatibility list. Some Fabric mods (especially render-related) don't work on Forge via Connector.")
                )
            }
        }

        // GeckoLib Oculus Compat
        if (hasMod("geckolib") && hasMod("geckolib_compat") || hasMod("geckoliboculus")) {
            tips.add(
                CrashTip(Severity.INFO,
                    "GeckoLib Oculus Compat No Longer Needed",
                    "GeckoLib Oculus Compat mod was needed for older versions. On Minecraft 1.20.1+, it is no longer required and may cause crashes.",
                    "Remove GeckoLib Oculus Compat mod.")
            )
        }

        // KubeJS
        if (hasMod("kubejs")) {
            if (hasMod("kubejs") && log.contains("KubeJS") && (log.contains("error") || log.contains("Exception"))) {
                tips.add(
                    CrashTip(Severity.ERROR,
                        "KubeJS Script Error",
                        "A KubeJS script has an error. Check the KubeJS logs for details.",
                        "Look in the 'kubejs/logs/' directory for error details. Fix the script based on the error message.")
                )
            }
            if (hasMod("kubejs") && !hasMod("rhino")) {
                tips.add(
                    CrashTip(Severity.ERROR,
                        "KubeJS Requires Rhino",
                        "KubeJS requires the Rhino library but it is not installed (or is a wrong version).",
                        "Install the correct version of Rhino for your KubeJS version.")
            )
            }
        }

        // Epic Fight version compatibility
        if (hasMod("epicfight")) {
            tips.add(
                CrashTip(Severity.INFO,
                    "Epic Fight Detected",
                    "Epic Fight mod is installed. Make sure your addons are compatible with this version.",
                    "Epic Fight addons must match the Epic Fight version. Check Modrinth/CurseForge for compatible versions.")
            )
        }

        // ModernFix watchdog
        if (hasMod("modernfix") || hasMod("modern_fix")) {
            if (log.contains("watchdog") || log.contains("deadlocked") || log.contains("ServerHangWatchdog")) {
                tips.add(
                    CrashTip(Severity.ERROR,
                        "Integrated Server Deadlocked",
                        "The integrated (single-player) server has deadlocked. ModernFix watchdog detected the hang.",
                        "Increase the watchdog timeout in ModernFix config. If the issue persists, remove some mods causing the lag.")
                )
            }
        }

        // FerriteCore neighbor table
        if (hasMod("ferritecore") && log.contains("neighbor")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "FerriteCore Neighbor Table Issue",
                    "A mod is improperly accessing FerriteCore's neighbor table. This is a known compatibility issue.",
                    "Add 'ferritecore.fixNeighborTable=true' to FerriteCore config as a workaround.")
            )
        }

        // Lithium + Roadrunner
        if (hasMod("lithium") && hasMod("roadrunner")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Lithium + RoadRunner Duplicate",
                    "RoadRunner duplicates many of Lithium's optimizations. Having both can cause conflicts.",
                    "Remove RoadRunner — Lithium is more actively maintained and compatible.")
            )
        }

        // Starlight / Phosphor on modern MC
        if ((hasMod("starlight") || hasMod("phosphor")) && !hasMod("forge")) {
            tips.add(
                CrashTip(Severity.INFO,
                    "Light Engine Mod Not Needed",
                    "Starlight and Phosphor's lighting optimizations are largely built into Minecraft 1.20+.",
                    "You can remove Starlight/Phosphor. The vanilla lighting engine in 1.20+ is already optimized.")
            )
        }

        // Continuity + OptiFine
        if (hasMod("continuity") && hasModName("optifine")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Continuity + OptiFine",
                    "Continuity provides OptiFine's connected textures functionality. Don't use both together.",
                    "Remove OptiFine — Continuity works with Sodium + Fabric API.")
            )
        }

        // Spark + Java 22+
        if (hasMod("spark") && log.contains("Spark") && (log.contains("java.lang.Exception") || log.contains("version"))) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Spark Incompatible with Java 22+",
                    "Spark profiler may crash on Java 22 or newer due to internal JVM changes.",
                    "Update Spark to the latest version. If the issue persists, downgrade to Java 21.")
            )
        }

        return tips
    }

    private fun analyzeNetworkIssues(log: String): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("ConnectException: Connection refused")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Connection Refused",
                    "The server refused the connection. The server may be offline, not started, or blocking your IP.",
                    "Verify the server is running and the IP address is correct. Check if the server has a firewall blocking connections.")
            )
        }

        if (log.contains("UnknownHostException")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Unknown Host (DNS Error)",
                    "The server address could not be resolved. DNS lookup failed.",
                    "Check the server address for typos. Check your internet connection. Try using the server IP directly instead of the domain name.")
            )
        }

        if (log.contains("SocketTimeoutException")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Connection Timeout",
                    "The connection to the server timed out. The server may be overloaded or unreachable.",
                    "Try again later. If the issue persists, the server may be down or your firewall may be blocking the connection.")
            )
        }

        if (log.contains("Authentication servers are down") || log.contains("Authentication servers down")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Auth Servers Down",
                    "Minecraft/Microsoft authentication servers are currently unavailable.",
                    "Wait and try again later. You can check server status at https://downdetector.com.")
            )
        }

        if (log.contains("Invalid session")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Invalid Session",
                    "Your game session has expired or is invalid.",
                    "Restart the launcher and log in again. If using offline mode, make sure you have signed in at least once.")
            )
        }

        if (log.contains("SSLHandshakeException")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "SSL Handshake Failed",
                    "Secure connection failed. This can be caused by incorrect system time, outdated Java, or network interception.",
                    "Check that your device date and time are correct. Update Java runtime.")
            )
        }

        if (log.contains("Protocol version mismatch") || log.contains("protocol mismatch")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Protocol Version Mismatch",
                    "Your Minecraft version does not match the server's version.",
                    "Use the same Minecraft version as the server. Check the server's version in the server list.")
            )
        }

        if (log.contains("Server returned HTTP response code: 403")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Download Blocked (403)",
                    "A HTTP 403 error occurred while downloading. A firewall or antivirus may be blocking the launcher.",
                    "Disable firewall/antivirus temporarily, or add the launcher to exceptions.")
            )
        }

        if (log.contains("Server returned HTTP response code: 502") || log.contains("502") || log.contains("504")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Download Server Error",
                    "The download server returned a temporary error (502 Bad Gateway / 504 Gateway Timeout).",
                    "Try again later. The server may be experiencing high load.")
            )
        }

        return tips
    }

    private fun analyzeFileSystemIssues(log: String): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("No space left on device")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "No Disk Space",
                    "Your device has run out of storage space. Minecraft cannot write to disk.",
                    "Free up space on your device. Check your Downloads folder, screenshots, and old worlds for large files.")
            )
        }

        if (log.contains("Permission denied")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "File Permission Denied",
                    "Minecraft does not have permission to access a file or directory.",
                    "Check storage permissions for the launcher in system settings. On Android, ensure 'Manage all files' permission is granted.")
            )
        }

        if (log.contains("The file is being used by another process") || log.contains("UsedByAnotherProcess")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "File Locked",
                    "A file is locked by another program. Another Minecraft instance may be running.",
                    "Close other Minecraft instances. Restart your device if the issue persists.")
            )
        }

        if (log.contains("invalid CEN header") || log.contains("error in opening zip file") || log.contains("ZipException")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Corrupted ZIP/JAR File",
                    "A ZIP or JAR file is corrupted. This usually affects a mod or Minecraft asset.",
                    "Reinstall Minecraft version. If the error mentions a specific mod, redownload it.")
            )
        }

        if (log.contains("CRC check failed") || log.contains("unexpected end of ZLIB input stream")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Corrupted Download",
                    "A downloaded file is corrupted (CRC check failed). The download may have been interrupted.",
                    "Reinstall Minecraft version. Ensure stable internet connection.")
            )
        }

        if (log.contains("Too many open files")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Too Many Open Files",
                    "The operating system's file descriptor limit has been reached. Too many files are open simultaneously.",
                    "This is usually caused by a mod leaking file handles. Try removing mods to isolate the issue.")
            )
        }

        if (log.contains("FileNotFoundException")) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "File Not Found",
                    "A required file was not found. Check the error message for the missing file path.",
                    "Reinstall Minecraft version. If it's a mod file, reinstall the mod.")
            )
        }

        return tips
    }

    private fun analyzeAndroidIssues(log: String): List<CrashTip> {
        val tips = mutableListOf<CrashTip>()

        if (log.contains("pojavSwapBuffers")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Screen Recorder / Buffer Swap Crash",
                    "The game crashed in the native buffer swap function (pojavSwapBuffers). This often happens when starting or stopping a screen recording, or after a GL context loss.",
                    "Avoid starting/stopping screen recordings rapidly. If the crash persists, restart the launcher before recording. Try switching between Zink and OpenGLES renderers.")
            )
        }

        if (log.contains("Could not initialize class org.lwjgl")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "LWJGL Initialization Failed",
                    "LWJGL (Lightweight Java Game Library) failed to initialize. This is critical for rendering.",
                    "Reinstall the Minecraft version. If the issue persists, try switching between Zink and OpenGLES renderers.")
            )
        }

        if (log.contains("Failed to load GLFW")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "GLFW Load Failed",
                    "GLFW library failed to load. Your device may not support the required OpenGL version.",
                    "Try switching renderers in launcher settings. Update GPU drivers if available for your device.")
            )
        }

        if (log.contains("Native method not found")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "Native Library Missing",
                    "A native library method was not found. The library may be incompatible with your device architecture.",
                    "Reinstall the Minecraft version. Make sure you are using the correct launcher build for your device architecture (arm64/x86_64).")
            )
        }

        if (log.contains("EGL_BAD_CONFIG") || log.contains("Could not find compatible EGLConfig") || log.contains("Failed to initialize EGL")) {
            tips.add(
                CrashTip(Severity.ERROR,
                    "EGL Initialization Failed",
                    "The EGL (OpenGL ES) configuration failed. Your device may not support the required graphics features.",
                    "Try switching between Zink and OpenGLES renderers. Update GPU drivers if available. Some older devices may not support Minecraft.")
            )
        }

        if (log.contains("Font") && (log.contains("crash") || log.contains("Exception"))) {
            tips.add(
                CrashTip(Severity.WARNING,
                    "Font Rendering Issue",
                    "A font rendering error occurred. This can happen with certain language settings or font mods.",
                    "Reduce GUI scale in Minecraft settings. Switch to English if using a non-English locale that has font issues.")
            )
        }

        return tips
    }
}

package com.movtery.zalithlauncher.game.crash_analysis

import java.io.File
import java.util.jar.JarFile

object ModScanner {
    fun scanMods(gameHome: String): List<ModInfo> {
        val modsDir = File(gameHome, "mods")
        if (!modsDir.exists() || !modsDir.isDirectory) return emptyList()

        return modsDir.listFiles { f -> f.extension == "jar" }
            ?.mapNotNull { f -> parseModJar(f) }
            ?: emptyList()
    }

    private fun parseModJar(file: File): ModInfo? {
        return try {
            JarFile(file).use { jar ->
                val fabricEntry = jar.getJarEntry("fabric.mod.json")
                if (fabricEntry != null) {
                    val json = jar.getInputStream(fabricEntry).bufferedReader().readText()
                    parseFabricModJson(json)
                } else {
                    val quiltEntry = jar.getJarEntry("quilt.mod.json")
                    if (quiltEntry != null) {
                        val json = jar.getInputStream(quiltEntry).bufferedReader().readText()
                        parseQuiltModJson(json)
                    } else {
                        val forgeEntry = jar.getJarEntry("META-INF/mods.toml")
                        if (forgeEntry != null) {
                            val toml = jar.getInputStream(forgeEntry).bufferedReader().readText()
                            parseForgeModToml(toml)
                        } else {
                            val neoForgeEntry = jar.getJarEntry("META-INF/neoforge.mods.toml")
                            if (neoForgeEntry != null) {
                                val toml = jar.getInputStream(neoForgeEntry).bufferedReader().readText()
                                parseForgeModToml(toml)
                            } else null
                        }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFabricModJson(json: String): ModInfo? {
        val id = extractString(json, "\"id\"") ?: return null
        val name = extractString(json, "\"name\"") ?: id
        val version = extractString(json, "\"version\"") ?: "unknown"
        return ModInfo(id, name, version)
    }

    private fun parseQuiltModJson(json: String): ModInfo? {
        val idMatch = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(json)
        val id = idMatch?.groupValues?.get(1) ?: return null
        val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(json)
        val name = nameMatch?.groupValues?.get(1) ?: id
        val version = extractString(json, "\"version\"") ?: "unknown"
        return ModInfo(id, name, version)
    }

    private fun parseForgeModToml(toml: String): ModInfo? {
        val modIdMatch = Regex("modId\\s*=\\s*\"([^\"]+)\"").find(toml)
        val id = modIdMatch?.groupValues?.get(1) ?: return null
        val nameMatch = Regex("displayName\\s*=\\s*\"([^\"]+)\"").find(toml)
        val name = nameMatch?.groupValues?.get(1) ?: id
        val versionMatch = Regex("version\\s*=\\s*\"([^\"]+)\"").find(toml)
        val version = versionMatch?.groupValues?.get(1) ?: "unknown"
        return ModInfo(id, name, version)
    }

    private fun extractString(text: String, key: String): String? {
        val regex = Regex(
            "$key\\s*:\\s*\"([^\"]+)\"".replace("\"", "\\\"")
        )
        return regex.find(text)?.groupValues?.get(1)
    }
}

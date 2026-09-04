package com.movtery.zalithlauncher.game.account.wardrobe

import com.movtery.zalithlauncher.path.PathManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.UUID

@Serializable
data class CapeEntry(
    val id: String,
    var name: String,
    var favorite: Boolean = false,
    val source: String = "gallery",
    val labyCapeId: String? = null,
    val ext: String = "png"
)

@Serializable
data class CapeManifest(
    val activeCapeId: String? = null,
    val capes: List<CapeEntry> = emptyList()
)

object AccountCapeCollection {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun getCollectionDir(accountUUID: String): File =
        File(PathManager.DIR_ACCOUNT_CAPE, accountUUID)

    private fun getManifestFile(accountUUID: String): File =
        File(getCollectionDir(accountUUID), "capes.json")

    private fun getCapeTextureFile(accountUUID: String, entry: CapeEntry): File =
        File(getCollectionDir(accountUUID), "${entry.id}.${entry.ext}")

    fun loadManifest(accountUUID: String): CapeManifest {
        val file = getManifestFile(accountUUID)
        if (!file.exists()) return CapeManifest()
        return runCatching {
            json.decodeFromString<CapeManifest>(file.readText())
        }.getOrDefault(CapeManifest())
    }

    private fun saveManifest(accountUUID: String, manifest: CapeManifest) {
        val file = getManifestFile(accountUUID)
        getCollectionDir(accountUUID).mkdirs()
        file.writeText(json.encodeToString(manifest))
    }

    fun generateAutoName(accountUUID: String): String {
        val manifest = loadManifest(accountUUID)
        val count = manifest.capes.size + 1
        return "My Cape $count"
    }

    fun addCape(
        accountUUID: String,
        textureFile: File,
        name: String,
        source: String = "gallery",
        labyCapeId: String? = null,
        ext: String = "png"
    ): CapeEntry {
        val manifest = loadManifest(accountUUID)
        val id = UUID.randomUUID().toString().lowercase()
        val entry = CapeEntry(id = id, name = name, source = source, labyCapeId = labyCapeId, ext = ext)
        val targetFile = getCapeTextureFile(accountUUID, entry)
        getCollectionDir(accountUUID).mkdirs()
        FileUtils.copyFile(textureFile, targetFile)
        val updated = manifest.copy(
            capes = manifest.capes + entry,
            activeCapeId = if (manifest.capes.isEmpty()) id else manifest.activeCapeId
        )
        saveManifest(accountUUID, updated)
        return entry
    }

    fun addCape(
        accountUUID: String,
        name: String,
        source: String = "gallery",
        labyCapeId: String? = null,
        imageBytes: ByteArray,
        ext: String = "png"
    ): CapeEntry {
        val tempFile = File.createTempFile("cape_import_", ".$ext")
        try {
            tempFile.writeBytes(imageBytes)
            return addCape(
                accountUUID = accountUUID,
                textureFile = tempFile,
                name = name,
                source = source,
                labyCapeId = labyCapeId,
                ext = ext
            )
        } finally {
            tempFile.delete()
        }
    }

    fun removeCape(accountUUID: String, capeId: String) {
        val manifest = loadManifest(accountUUID)
        val entry = manifest.capes.find { it.id == capeId }
        val updated = manifest.copy(
            capes = manifest.capes.filter { it.id != capeId },
            activeCapeId = if (manifest.activeCapeId == capeId) {
                manifest.capes.firstOrNull { it.id != capeId }?.id
            } else manifest.activeCapeId
        )
        saveManifest(accountUUID, updated)
        if (entry != null) getCapeTextureFile(accountUUID, entry).delete()
    }

    fun setActiveCape(accountUUID: String, capeId: String) {
        val manifest = loadManifest(accountUUID)
        if (manifest.capes.any { it.id == capeId }) {
            saveManifest(accountUUID, manifest.copy(activeCapeId = capeId))
        }
    }

    fun clearActiveCape(accountUUID: String) {
        saveManifest(accountUUID, loadManifest(accountUUID).copy(activeCapeId = null))
        File(PathManager.DIR_ACCOUNT_CAPE, "$accountUUID.png").delete()
    }

    fun toggleFavorite(accountUUID: String, capeId: String) {
        val manifest = loadManifest(accountUUID)
        val updated = manifest.copy(
            capes = manifest.capes.map {
                if (it.id == capeId) it.copy(favorite = !it.favorite) else it
            }
        )
        saveManifest(accountUUID, updated)
    }

    fun renameCape(accountUUID: String, capeId: String, newName: String) {
        val manifest = loadManifest(accountUUID)
        val updated = manifest.copy(
            capes = manifest.capes.map {
                if (it.id == capeId) it.copy(name = newName) else it
            }
        )
        saveManifest(accountUUID, updated)
    }

    fun getActiveCapeFile(accountUUID: String): File? {
        val manifest = loadManifest(accountUUID)
        val activeId = manifest.activeCapeId ?: return null
        val entry = manifest.capes.find { it.id == activeId } ?: return null
        val file = getCapeTextureFile(accountUUID, entry)
        return if (file.exists()) file else null
    }

    fun migrateLegacy(accountUUID: String) {
        val legacyFile = File(PathManager.DIR_ACCOUNT_CAPE, "$accountUUID.png")
        if (!legacyFile.exists()) return
        val manifest = loadManifest(accountUUID)
        if (manifest.capes.isNotEmpty()) return
        val id = UUID.randomUUID().toString().lowercase()
        val entry = CapeEntry(id = id, name = "My Cape", source = "gallery", ext = "png")
        val targetFile = getCapeTextureFile(accountUUID, entry)
        getCollectionDir(accountUUID).mkdirs()
        FileUtils.copyFile(legacyFile, targetFile)
        saveManifest(accountUUID, CapeManifest(activeCapeId = id, capes = listOf(entry)))
    }
}

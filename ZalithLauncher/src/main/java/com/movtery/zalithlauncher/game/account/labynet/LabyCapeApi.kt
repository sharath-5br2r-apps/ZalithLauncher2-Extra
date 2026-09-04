package com.movtery.zalithlauncher.game.account.labynet

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class OfficialCape(
    val name: String = "",
    val alias: String = "",
    val texture: String? = null,
    val wiki: String? = null
)

@Serializable
data class GalleryCapeTexture(
    val preview: String? = null
)

@Serializable
data class GalleryCape(
    val hash: String = "",
    val title: String = "",
    val type: Int = 0,
    val type_name: String = "",
    val texture: GalleryCapeTexture? = null
)

@Serializable
data class GalleryResponse(
    val gallery: GalleryData? = null
)

@Serializable
data class GalleryData(
    val data: List<GalleryCape> = emptyList(),
    val totalPages: Int = 1,
    val current_page: Int = 1,
    val hasNextPage: Boolean = false,
    val hasPrevPage: Boolean = false
)

@Serializable
data class OfficialCapesResponse(
    val success: Boolean = false,
    val data: List<OfficialCape> = emptyList()
)

object LabyCapeApi {
    private const val API_BASE = "https://api.minecraftcapes.net"
    private const val TEXTURE_BASE = "https://textures.minecraftcapes.net"

    suspend fun fetchOfficialCapes(client: HttpClient): List<OfficialCape> {
        val response = client.get("$API_BASE/api/gallery/officialcapes")
        val result = response.body<OfficialCapesResponse>()
        return result.data
    }

    suspend fun fetchGalleryCapes(client: HttpClient, page: Int = 1): GalleryResponse {
        val response = client.get("$API_BASE/api/gallery/get") {
            parameter("page", page.toString())
        }
        return response.body<GalleryResponse>()
    }

    suspend fun downloadCapeImage(
        client: HttpClient,
        imageUrl: String,
        targetFile: File
    ) {
        val response = client.get(imageUrl)
        val channel = response.bodyAsChannel()
        FileUtils.copyInputStreamToFile(channel.toInputStream(), targetFile)
    }
}

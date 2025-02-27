package dev.datlag.dxvkotool.model

import dev.datlag.dxvkotool.dxvk.DxvkStateCache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class RepoStructure(
    @SerialName("sha") val sha: String,
    @SerialName("url") val url: String,
    @SerialName("tree") val tree: List<StructureItem>
) {
    fun findMatchingGameItem(game: Game): Map<DxvkStateCache, StructureItem?> {
        val matchingItems = tree.mapNotNull { item ->
            val pathSplit = item.path.split('/')
            val anyMatching = pathSplit.any {
                it.equals(game.name, true)
            }
            if (anyMatching) {
                item
            } else {
                null
            }
        }

        val associated = game.caches.value.associateWith { entry ->
            (matchingItems.firstOrNull { item ->
                item.path.endsWith(entry.file.name, true)
            } ?: matchingItems.firstOrNull { item ->
                item.path.endsWith("${entry.file.name}.md", true)
            } ?: matchingItems.firstOrNull { item ->
                item.path.endsWith("${entry.file.name}.txt", true)
            })
        }

        return associated
    }
}

@Serializable
data class StructureItem(
    @SerialName("path") val path: String,
    @SerialName("mode") val mode: String,
    @SerialName("type") val type: String,
    @SerialName("sha") val sha: String,
    @SerialName("size") val size: Int = -1,
    @SerialName("url") val url: String
)

@Serializable
data class StructureItemContent(
    @SerialName("sha") val sha: String,
    @SerialName("content") private val content: String,
    @SerialName("encoding") val encoding: String
) {
    private fun getContent(): String? {
        return if (encoding.equals("base64", true)) {
            val decoded = runCatching {
                Base64.getDecoder().decode(content)
            }.getOrNull() ?: runCatching {
                Base64.getMimeDecoder().decode(content)
            }.getOrNull() ?: runCatching {
                Base64.getDecoder().decode(content.replace("\n", String()))
            }.getOrNull() ?: runCatching {
                Base64.getMimeDecoder().decode(content.replace("\n", String()))
            }.getOrNull()
            decoded?.let { String(it) }
        } else {
            null
        }
    }

    fun getUrlInContent(): String? {
        val decoded = getContent()
        val preferredRegex = Regex("(?<=(Download:\\s)|(File:\\s))(http(s)?://\\S+)", setOf(RegexOption.IGNORE_CASE))
        val secondaryRegex = Regex("(http(s)?://\\S+)", setOf(RegexOption.IGNORE_CASE))
        return if (decoded != null) {
            preferredRegex.findAll(decoded).map {
                it.value
            }.firstOrNull() ?: secondaryRegex.findAll(decoded).map {
                it.value
            }.firstOrNull()
        } else {
            null
        }
    }
}

fun Collection<RepoStructure>.findMatchingGameItem(game: Game): Map<DxvkStateCache, StructureItem?> {
    return game.caches.value.associateWith { cache ->
        this.firstNotNullOfOrNull { it.findMatchingGameItem(game)[cache] }
    }
}
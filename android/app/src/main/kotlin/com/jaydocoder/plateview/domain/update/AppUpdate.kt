package com.jaydocoder.plateview.domain.update

import java.io.File

data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
)

data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let { downloadedBytes.toFloat() / it }
}

interface AppUpdateRepository {
    suspend fun findAvailableUpdate(): AppUpdate?

    suspend fun download(
        update: AppUpdate,
        onProgress: (UpdateDownloadProgress) -> Unit,
    ): File
}

internal data class AppVersion(private val values: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val length = maxOf(values.size, other.values.size)
        repeat(length) { index ->
            val result = (values.getOrElse(index) { 0 }).compareTo(other.values.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    companion object {
        fun parse(value: String): AppVersion? {
            val normalized = value.trim().removePrefix("v").substringBefore('-')
            val components = normalized.split('.')
            if (components.isEmpty() || components.any { it.toIntOrNull() == null }) return null
            return AppVersion(components.map(String::toInt))
        }
    }
}

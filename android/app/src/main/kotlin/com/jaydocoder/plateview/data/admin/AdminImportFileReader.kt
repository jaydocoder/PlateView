package com.jaydocoder.plateview.data.admin

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AdminImportFileReader {
    suspend fun read(uri: Uri): SelectedAdminImportFile
}

data class SelectedAdminImportFile(
    val fileName: String,
    val content: ByteArray,
)

@Singleton
class ContentResolverAdminImportFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : AdminImportFileReader {
    override suspend fun read(uri: Uri): SelectedAdminImportFile = withContext(Dispatchers.IO) {
        val fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            .use { cursor -> cursor?.displayName() }
            ?: "upload.xlsx"
        val content = context.contentResolver.openInputStream(uri)?.use(::readAtMostMaximumSize)
            ?: throw IllegalArgumentException("无法读取所选Excel文件")
        SelectedAdminImportFile(fileName, content)
    }

    private fun Cursor.displayName(): String? = if (moveToFirst()) {
        getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(::getString)
    } else {
        null
    }

    private fun readAtMostMaximumSize(input: java.io.InputStream): ByteArray {
        val buffer = ByteArray(BUFFER_SIZE_BYTES)
        var totalSize = 0
        return ByteArrayOutputStream().use { output ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                totalSize += count
                require(totalSize <= MAX_IMPORT_FILE_SIZE_BYTES) { "Excel 文件不能超过10MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private companion object {
        const val MAX_IMPORT_FILE_SIZE_BYTES = 10 * 1024 * 1024
        const val BUFFER_SIZE_BYTES = 8 * 1024
    }
}

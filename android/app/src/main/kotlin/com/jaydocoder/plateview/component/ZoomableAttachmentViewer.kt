package com.jaydocoder.plateview.component

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ZoomableAttachmentViewer(
    file: File?,
    kind: String,
    variant: String,
    pageCount: Int,
    modifier: Modifier = Modifier,
    viewportHeight: Dp = 420.dp,
) {
    when {
        file == null -> Box(modifier.fillMaxWidth().height(viewportHeight), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        kind == "PDF" && variant == "original" -> PdfFileViewer(file, pageCount, modifier, viewportHeight)
        else -> ZoomableViewport(modifier = modifier.height(viewportHeight), contentDescription = if (kind == "PDF") "PDF首页预览" else "微信图片预览") { contentModifier ->
            FullResolutionImage(file, if (kind == "PDF") "PDF首页预览" else "微信图片预览", contentModifier)
        }
    }
}

@Composable
fun AttachmentThumbnail(
    file: File?,
    kind: String,
    variant: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    when {
        file == null -> Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)), contentAlignment = Alignment.Center) {
            Text("附件正在后台缓存", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        kind == "PDF" && variant == "original" -> RenderedPdfPage(
            file = file,
            pageIndex = 0,
            contentDescription = contentDescription,
            modifier = modifier,
            longestEdge = PDF_THUMBNAIL_LONGEST_EDGE,
        )
        else -> FullResolutionImage(file, contentDescription, modifier)
    }
}

@Composable
private fun PdfFileViewer(file: File, pageCount: Int, modifier: Modifier, viewportHeight: Dp) {
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    val actualPageCount by produceState(pageCount.coerceAtLeast(1), file) {
        value = withContext(Dispatchers.IO) { readPdfPageCount(file) } ?: pageCount.coerceAtLeast(1)
    }
    val safePageIndex = pageIndex.coerceIn(0, actualPageCount - 1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ZoomableViewport(
            modifier = Modifier.height(viewportHeight),
            contentDescription = "PDF第${safePageIndex + 1}页",
        ) { contentModifier ->
            RenderedPdfPage(
                file = file,
                pageIndex = safePageIndex,
                contentDescription = "PDF第${safePageIndex + 1}页",
                modifier = contentModifier,
                longestEdge = PDF_RENDER_LONGEST_EDGE,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = safePageIndex > 0,
                onClick = { pageIndex = safePageIndex - 1 },
            ) {
                Text("上一页")
            }
            Text(
                "第 ${safePageIndex + 1} / $actualPageCount 页",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                enabled = safePageIndex + 1 < actualPageCount,
                onClick = { pageIndex = safePageIndex + 1 },
            ) {
                Text("下一页")
            }
        }
    }
}

@Composable
private fun RenderedPdfPage(
    file: File,
    pageIndex: Int,
    contentDescription: String,
    modifier: Modifier,
    longestEdge: Int,
) {
    val pageImage by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file, pageIndex, longestEdge) {
        val bitmap = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex, longestEdge) }
        value = bitmap?.asImageBitmap()
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        pageImage?.let { image ->
            Image(image, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } ?: CircularProgressIndicator()
    }
}

@Composable
private fun FullResolutionImage(file: File, contentDescription: String, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(file) {
        ImageRequest.Builder(context)
            .data(file)
            .size(Size.ORIGINAL)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ZoomableViewport(
    modifier: Modifier = Modifier,
    contentDescription: String,
    content: @Composable (Modifier) -> Unit,
) {
    var scale by remember(contentDescription) { mutableFloatStateOf(1f) }
    var offset by remember(contentDescription) { mutableStateOf(Offset.Zero) }
    val viewportColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
    fun updateScale(value: Float) {
        scale = value.coerceIn(1f, 5f)
        if (scale == 1f) offset = Offset.Zero
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(viewportColor)
            .testTag("zoomable_attachment_viewer")
            .pointerInput(contentDescription) {
                detectTransformGestures { _, pan, zoom, _ ->
                    updateScale(scale * zoom)
                    if (scale > 1f) offset += pan
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(
            Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}

private fun readPdfPageCount(file: File): Int? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
    }
}.getOrNull()

private fun renderPdfPage(file: File, pageIndex: Int, targetLongestEdge: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
            renderer.openPage(pageIndex).use { page ->
                val longestEdge = max(page.width, page.height).coerceAtLeast(1)
                val scale = (targetLongestEdge.toFloat() / longestEdge).coerceAtMost(3f)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}.getOrNull()

private const val PDF_RENDER_LONGEST_EDGE = 2_400
private const val PDF_THUMBNAIL_LONGEST_EDGE = 1_200

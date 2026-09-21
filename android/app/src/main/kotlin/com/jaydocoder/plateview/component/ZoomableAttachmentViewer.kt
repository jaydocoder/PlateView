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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

@Composable
fun ZoomableAttachmentViewer(
    file: File?,
    kind: String,
    variant: String,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    when {
        file == null -> Box(modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        kind == "PDF" && variant == "original" -> PdfFileViewer(file, pageCount, modifier)
        else -> ZoomableViewport(modifier = modifier, contentDescription = if (kind == "PDF") "PDF首页预览" else "微信图片预览") { contentModifier ->
            AsyncImage(
                model = file,
                contentDescription = if (kind == "PDF") "PDF首页预览" else "微信图片预览",
                modifier = contentModifier,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun PdfFileViewer(file: File, pageCount: Int, modifier: Modifier) {
    var page by remember(file) { mutableIntStateOf(0) }
    val safePageCount = pageCount.coerceAtLeast(1)
    val pageImage by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file, page) {
        val bitmap = withContext(Dispatchers.IO) { renderPdfPage(file, page) }
        value = bitmap?.asImageBitmap()
        try {
            awaitCancellation()
        } finally {
            bitmap?.recycle()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ZoomableViewport(contentDescription = "PDF第${page + 1}页") { contentModifier ->
            pageImage?.let { image ->
                Image(image, "PDF第${page + 1}页", contentModifier, contentScale = ContentScale.Fit)
            } ?: CircularProgressIndicator()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${page + 1} / $safePageCount 页", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(enabled = page > 0, onClick = { page-- }) { Text("上一页") }
                TextButton(enabled = page + 1 < safePageCount, onClick = { page++ }) { Text("下一页") }
            }
        }
    }
}

@Composable
private fun ZoomableViewport(
    modifier: Modifier = Modifier,
    contentDescription: String,
    content: @Composable (Modifier) -> Unit,
) {
    var scale by remember(contentDescription) { mutableFloatStateOf(1f) }
    var offset by remember(contentDescription) { mutableStateOf(Offset.Zero) }
    fun updateScale(value: Float) {
        scale = value.coerceIn(1f, 5f)
        if (scale == 1f) offset = Offset.Zero
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(Color.Black)
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
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.58f)),
        ) {
            IconButton(onClick = { updateScale(scale - 0.5f) }, enabled = scale > 1f) {
                Icon(Icons.Outlined.ZoomOut, "缩小", tint = Color.White)
            }
            IconButton(onClick = { updateScale(scale + 0.5f) }, enabled = scale < 5f) {
                Icon(Icons.Outlined.ZoomIn, "放大", tint = Color.White)
            }
            IconButton(onClick = { updateScale(1f) }) {
                Icon(Icons.Outlined.Refresh, "复位缩放", tint = Color.White)
            }
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
            renderer.openPage(pageIndex).use { page ->
                val longestEdge = max(page.width, page.height).coerceAtLeast(1)
                val scale = (PDF_RENDER_LONGEST_EDGE.toFloat() / longestEdge).coerceAtMost(2f)
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

private const val PDF_RENDER_LONGEST_EDGE = 1_800

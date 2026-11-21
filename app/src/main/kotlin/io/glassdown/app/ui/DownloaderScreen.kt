package io.glassdown.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.glassdown.app.data.DownloadStatus
import io.glassdown.app.data.DownloadTask
import io.glassdown.app.data.FormatOption
import io.glassdown.app.ui.glass.GlassCard
import io.glassdown.app.ui.glass.GlassPalette
import io.glassdown.app.ui.glass.GlassPill
import io.glassdown.app.ui.glass.GlassVariant
import io.glassdown.app.ui.glass.glassEffect
import kotlin.math.roundToInt

@Composable
fun DownloaderScreen(
    viewModel: DownloaderViewModel,
    initialUrl: String?,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var pendingDownload by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (pendingDownload) {
            pendingDownload = false
            viewModel.startDownload()
        }
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.setUrl(initialUrl)
            viewModel.resolve()
        }
    }

    val bottomInsets = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GlassPalette.Obsidian),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topInset + 16.dp,
            bottom = bottomInsets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "GlassDown",
                color = GlassPalette.Pearl,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Descargas simples. Sin ruido.",
                color = GlassPalette.Titanium,
                fontSize = 14.sp,
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                variant = GlassVariant.Regular,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enlace del video", color = GlassPalette.Titanium, fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .glassEffect(GlassVariant.Clear, RoundedCornerShape(16.dp), interactive = false)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = GlassPalette.Titanium, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = ui.url,
                            onValueChange = viewModel::setUrl,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(color = GlassPalette.Pearl, fontSize = 15.sp),
                            cursorBrush = SolidColor(GlassPalette.Pearl),
                            decorationBox = { inner ->
                                if (ui.url.isBlank()) {
                                    Text("https://…", color = GlassPalette.Titanium.copy(alpha = 0.7f), fontSize = 15.sp)
                                }
                                inner()
                            }
                        )
                        if (ui.url.isNotBlank()) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Limpiar",
                                tint = GlassPalette.Titanium,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { viewModel.setUrl("") }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ActionPill(
                            label = "Pegar",
                            icon = { Icon(Icons.Rounded.ContentPaste, null, tint = GlassPalette.Pearl, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                val text = clipboard.getText()?.text?.trim().orEmpty()
                                if (text.isNotBlank()) viewModel.setUrl(text)
                            },
                        )
                        ActionPill(
                            label = if (ui.resolving) "Analizando…" else "Analizar",
                            icon = { Icon(Icons.Rounded.Refresh, null, tint = GlassPalette.Pearl, modifier = Modifier.size(16.dp)) },
                            onClick = { if (!ui.resolving) viewModel.resolve() },
                            variant = GlassVariant.Clear,
                        )
                    }

                    ui.error?.let {
                        Text(it, color = GlassPalette.Danger, fontSize = 13.sp)
                    }
                }
            }
        }

        ui.videoInfo?.let { info ->
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.Regular,
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(info.title, color = GlassPalette.Pearl, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        info.uploader?.let {
                            Text(it, color = GlassPalette.Titanium, fontSize = 13.sp)
                        }
                        Text("Selecciona la calidad", color = GlassPalette.Titanium, fontSize = 13.sp)
                        info.formats.forEach { format ->
                            FormatRow(
                                format = format,
                                selected = ui.selectedFormatId == format.id,
                                onClick = { viewModel.selectFormat(format.id) },
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            ActionPill(
                                label = "Descargar",
                                icon = { Icon(Icons.Rounded.ArrowDownward, null, tint = GlassPalette.Pearl, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    val needed = buildList {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                        ) add(Manifest.permission.POST_NOTIFICATIONS)
                                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                        ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                    }
                                    if (needed.isNotEmpty()) {
                                        pendingDownload = true
                                        permissionLauncher.launch(needed.toTypedArray())
                                    } else {
                                        viewModel.startDownload()
                                    }
                                },
                                variant = GlassVariant.Clear,
                            )
                        }
                    }
                }
            }
        }

        if (tasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Descargas", color = GlassPalette.Pearl, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    if (tasks.any { it.status.isTerminal() }) {
                        Text(
                            "Limpiar",
                            color = GlassPalette.Titanium,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.clearTerminal() }
                        )
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                DownloadTaskCard(
                    task = task,
                    onCancel = { viewModel.cancelDownload(task.id) },
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    variant: GlassVariant = GlassVariant.Clear,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(40.dp)
            .glassEffect(variant = variant, shape = RoundedCornerShape(50), interactive = true)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(label, color = GlassPalette.Pearl, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FormatRow(format: FormatOption, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val variant = if (selected) GlassVariant.Regular else GlassVariant.Clear
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .glassEffect(variant = variant, shape = shape, interactive = true)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(format.label, color = GlassPalette.Pearl, fontSize = 14.sp)
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(GlassPalette.Pearl)
            )
        }
    }
}

@Composable
private fun DownloadTaskCard(task: DownloadTask, onCancel: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        variant = GlassVariant.Regular,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, color = GlassPalette.Pearl, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(task.format.qualityLabel + " · " + task.format.container.uppercase(), color = GlassPalette.Titanium, fontSize = 12.sp)
                }
                if (!task.status.isTerminal()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Cancelar",
                        tint = GlassPalette.Titanium,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
                    )
                }
            }

            val statusText = when (task.status) {
                DownloadStatus.QUEUED -> "En cola"
                DownloadStatus.RESOLVING -> "Analizando…"
                DownloadStatus.DOWNLOADING -> "Descargando ${(task.progress * 100).roundToInt()}%"
                DownloadStatus.MERGING -> "Mezclando…"
                DownloadStatus.COMPLETED -> "Completada"
                DownloadStatus.FAILED -> task.error ?: "Falló"
                DownloadStatus.CANCELED -> "Cancelada"
            }
            Text(statusText, color = if (task.status == DownloadStatus.FAILED) GlassPalette.Danger else GlassPalette.Titanium, fontSize = 12.sp)

            if (!task.status.isTerminal()) {
                ProgressBar(progress = task.progress)
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(GlassPalette.Pearl.copy(alpha = 0.10f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(shape)
                .background(GlassPalette.Pearl.copy(alpha = 0.85f))
        )
    }
}

private fun DownloadStatus.isTerminal(): Boolean =
    this == DownloadStatus.COMPLETED || this == DownloadStatus.FAILED || this == DownloadStatus.CANCELED

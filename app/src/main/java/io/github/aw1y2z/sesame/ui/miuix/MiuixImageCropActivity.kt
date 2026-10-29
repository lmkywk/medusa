package io.github.aw1y2z.sesame.ui.miuix

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 简单裁切页：固定比例的取景框，图片可拖动与双指缩放，确认后按框内内容输出。
 * 只做 Hero / 卡片背景这类固定比例，不追求修图软件的旋转与网格。
 */
class MiuixImageCropActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_ASPECT_W = "aspectW"
        const val EXTRA_ASPECT_H = "aspectH"
        const val EXTRA_OUT_NAME = "outName"
        const val EXTRA_RESULT_PATH = "resultPath"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { CropScreen(this) }
    }
}

@Composable
private fun CropScreen(activity: MiuixImageCropActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aspectW = remember { activity.intent.getIntExtra(MiuixImageCropActivity.EXTRA_ASPECT_W, 2).coerceAtLeast(1) }
    val aspectH = remember { activity.intent.getIntExtra(MiuixImageCropActivity.EXTRA_ASPECT_H, 1).coerceAtLeast(1) }
    val outName = remember { activity.intent.getStringExtra(MiuixImageCropActivity.EXTRA_OUT_NAME) ?: "custom_bg.jpg" }
    val uri = remember { activity.intent.getStringExtra(MiuixImageCropActivity.EXTRA_URI)?.let(Uri::parse) }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var ready by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        val size = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()
        } ?: 0L
        if (size > 3L * 1024L * 1024L) {
            ToastUtil.show(context, "图片超过 3MB，请先压小再选")
            activity.finish()
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    var sample = 1
                    while (options.outWidth / sample > 2400 || options.outHeight / sample > 2400) sample *= 2
                    context.contentResolver.openInputStream(uri)?.use { again ->
                        BitmapFactory.decodeStream(again, null, BitmapFactory.Options().apply {
                            inSampleSize = sample
                        })
                    }
                }
            }.getOrNull()
        }
    }

    val image = bitmap
    val aspect = aspectW.toFloat() / aspectH.toFloat()

    fun clamp(next: Offset, nextScale: Float): Offset {
        val img = image ?: return next
        val base = max(frame.width / img.width.toFloat(), frame.height / img.height.toFloat())
        val drawW = img.width * base * nextScale
        val drawH = img.height * base * nextScale
        val minX = frame.width - drawW
        val minY = frame.height - drawH
        return Offset(
            next.x.coerceIn(min(minX, 0f), 0f),
            next.y.coerceIn(min(minY, 0f), 0f)
        )
    }

    fun centerFor(nextScale: Float): Offset {
        val img = image ?: return Offset.Zero
        val base = max(frame.width / img.width.toFloat(), frame.height / img.height.toFloat())
        val drawW = img.width * base * nextScale
        val drawH = img.height * base * nextScale
        return Offset((frame.width - drawW) / 2f, (frame.height - drawH) / 2f)
    }

    // 图片解码完成后再居中一次：onSizeChanged 往往先于图片到达
    androidx.compose.runtime.LaunchedEffect(image, frame) {
        if (image != null && frame.width > 0) {
            scale = 1f
            offset = centerFor(1f)
            ready = true
        }
    }

    fun save() {
        val img = image ?: return
        if (frame.width <= 0 || frame.height <= 0) return
        val outW = 1440
        val outH = (outW / aspect).toInt().coerceAtLeast(1)
        val base = max(frame.width / img.width.toFloat(), frame.height / img.height.toFloat())
        val s = base * scale
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val k = outW / frame.width.toFloat()
        canvas.scale(k, k)
        canvas.translate(offset.x, offset.y)
        canvas.scale(s, s)
        canvas.drawBitmap(img, 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.filesDir, outName)
                    file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    file
                }.getOrNull()
            }
            out.recycle()
            if (ok == null) {
                ToastUtil.show(context, "保存失败，请重试")
                return@launch
            }
            activity.setResult(Activity.RESULT_OK, Intent().putExtra(MiuixImageCropActivity.EXTRA_RESULT_PATH, ok.absolutePath))
            activity.finish()
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0F14)).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "取消",
                fontSize = MedusaType.Body,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { activity.finish() }
                    }
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            )
            Spacer(Modifier.weight(1f))
            Text("拖动图片，双指缩放", fontSize = MedusaType.Caption, color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.weight(1f))
            Text(
                "完成",
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2ED47A),
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { save() }
                    }
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            )
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            val frameW = maxWidth
            val frameH = maxWidth / aspect
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(frameH)
                    .background(Color.Black)
                    .onSizeChanged { size ->
                        frame = size
                    }
                    .pointerInput(image, frame) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (image == null) return@detectTransformGestures
                            val nextScale = (scale * zoom).coerceIn(1f, 4f)
                            scale = nextScale
                            offset = clamp(offset + pan, nextScale)
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val img = image ?: return@Canvas
                    val base = max(size.width / img.width.toFloat(), size.height / img.height.toFloat())
                    val s = base * scale
                    drawContext.canvas.save()
                    drawContext.canvas.clipRect(0f, 0f, size.width, size.height)
                    drawContext.canvas.translate(offset.x, offset.y)
                    drawContext.canvas.scale(s, s)
                    drawImage(img.asImageBitmap())
                    drawContext.canvas.restore()
                    // 取景框的四角与描边
                    drawRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(size.width, size.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        Text(
            text = "输出比例 $aspectW:$aspectH · 完成会覆盖当前自定义背景",
            fontSize = MedusaType.Caption,
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MiuixBackupActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
    }

    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        ConfigPreload.prepare(userId)
        setAppContent {
            BackupContent(this, userId)
        }
    }
}

@Composable
private fun BackupContent(activity: MiuixBackupActivity, userId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backups by remember { mutableStateOf(BackupManager.listFullBackups()) }
    var domains by remember { mutableStateOf(BackupManager.listDomainExports()) }
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<BackupManager.FullBackup?>(null) }

    fun refresh() {
        backups = BackupManager.listFullBackups()
        domains = BackupManager.listDomainExports()
    }

    fun createBackup() {
        if (busy) return
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { BackupManager.createFullBackup(userId) }
            busy = false
            result.onSuccess {
                refresh()
                ToastUtil.show(context, "已备份：${it.dir.name}")
            }.onFailure {
                ToastUtil.show(context, "备份失败：${it.message ?: "未知错误"}")
            }
        }
    }

    fun restore(backup: BackupManager.FullBackup) {
        if (busy) return
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { BackupManager.restoreFull(backup, userId) }
            busy = false
            result.onSuccess {
                ConfigPreload.prepare(userId)
                ConfigSync.notifyRestart(context, userId)
                refresh()
                ToastUtil.show(context, "已恢复「${backup.label}」，已通知支付宝重载")
            }.onFailure {
                ToastUtil.show(context, "恢复失败：${it.message ?: "未知错误"}")
            }
        }
    }

    Scaffold(
        topBar = {
            LogTopBar(title = "备份与恢复", onBack = { activity.finish() })
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE7EDF3), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("备份目录", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink)
                    Text(
                        BackupManager.root.absolutePath,
                        fontSize = MedusaType.Secondary,
                        color = MedusaColors.Muted,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        "全量备份含配置、计划、历史、统计；域配置单独放在「域」目录，按域和导出时间识别。",
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Muted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        "注意：全量备份包含账号信息与 http_token/token_config，请只保存在本机；诊断导出不含 token。",
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Warning,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton("立即全量备份", true, Modifier.weight(1f)) { createBackup() }
                ActionButton("刷新", false, Modifier.weight(1f)) { refresh() }
            }
            if (busy) {
                Text(
                    "处理中，请稍候…",
                    fontSize = MedusaType.Secondary,
                    color = MedusaColors.GreenDeep,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("全量备份 · ${backups.size}", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink)
            if (backups.isEmpty()) {
                EmptyHint("还没有全量备份")
            } else {
                backups.forEach { backup ->
                    BackupRow(
                        title = backup.label,
                        subtitle = "${formatTime(backup.createdAt)} · V${backup.version} · ${backup.fileCount} 个文件 · ${formatSize(backup.bytes)}",
                        onClick = { pendingRestore = backup }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("域配置 · ${domains.size}", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink)
            if (domains.isEmpty()) {
                EmptyHint("还没有域配置导出；在任意域页点「导出」即可")
            } else {
                domains.forEach { export ->
                    BackupRow(
                        title = export.title,
                        subtitle = "${formatTime(export.createdAt)} · ${formatSize(export.bytes)} · ${export.domainKey}",
                        onClick = {
                            ToastUtil.show(context, "文件：${export.file.absolutePath}")
                        }
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    pendingRestore?.let { backup ->
        ConfirmDialog(
            title = "恢复全量备份",
            text = "将用「${backup.label}」（${formatTime(backup.createdAt)}）覆盖当前配置、计划、历史和统计。\n恢复前会自动再做一份「恢复前」备份。",
            confirmText = "恢复",
            dismissText = "取消",
            onConfirm = {
                pendingRestore = null
                restore(backup)
            },
            onDismiss = { pendingRestore = null }
        )
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .background(if (primary) MedusaColors.Green else Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, if (primary) MedusaColors.Green else Color(0xFFE7EDF3), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = MedusaType.Body,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Color.White else MedusaColors.Ink
        )
    }
}

@Composable
private fun BackupRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFE7EDF3), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = MedusaType.Body, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = MedusaType.Caption, color = MedusaColors.Muted, maxLines = 2)
        }
        Text("恢复", fontSize = MedusaType.Caption, color = MedusaColors.GreenDeep)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        fontSize = MedusaType.Secondary,
        color = MedusaColors.Muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(14.dp)
    )
}

private fun formatTime(millis: Long): String =
    if (millis <= 0) "未知时间"
    else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.BuildConfig
import io.github.aw1y2z.sesame.util.FileUtil
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 只读系统状态页：数据来自注入进程写的 system_status.json。 */
class MiuixSystemStatusActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            SystemStatusScreen(this)
        }
    }
}

@Composable
private fun SystemStatusScreen(activity: MiuixSystemStatusActivity) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        val file = File(FileUtil.MAIN_DIRECTORY_FILE, "system_status.json")
        val before = if (file.exists()) runCatching { file.readText() }.getOrNull() else null
        runCatching {
            context.sendBroadcast(
                Intent("com.eg.android.AlipayGphone.sesame.status")
                    .setPackage("com.eg.android.AlipayGphone")
                    .putExtra("detail", true)
            )
        }.onFailure {
            error = "无法向宿主发送刷新请求：${it.message ?: "未知错误"}"
        }

        var parsed: JSONObject? = null
        for (i in 0 until 10) {
            delay(300)
            val text = if (file.exists()) runCatching { file.readText() }.getOrNull() else null
            if (!text.isNullOrBlank() && text != before) {
                parsed = runCatching { JSONObject(text) }.getOrNull()
                if (parsed != null) {
                    break
                }
            }
        }
        if (parsed == null) {
            val text = if (file.exists()) runCatching { file.readText() }.getOrNull() else null
            parsed = text?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
        report = parsed
        if (parsed == null) {
            error = error ?: "未收到宿主报告。请确认支付宝正在运行，或点“刷新状态”。"
        }
        loading = false
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "系统状态",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Text(
                text = "只读报告，不会修改任何设置或任务。",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted
            )
            Spacer(Modifier.height(8.dp))

            report?.let { ReportHeader(it) }

            if (loading) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "正在读取宿主状态…",
                    fontSize = MedusaType.Body,
                    color = MedusaColors.Muted
                )
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    fontSize = MedusaType.Body,
                    color = MedusaColors.Danger
                )
            }

            val current = report
            if (current != null) {
                Spacer(Modifier.height(12.dp))
                AccountSection(current)
                Spacer(Modifier.height(12.dp))
                CapabilitySection(current)
                Spacer(Modifier.height(12.dp))
                SchedulerSection(current)
                Spacer(Modifier.height(12.dp))
                RiskSection(current)
                Spacer(Modifier.height(12.dp))
                DataSection(current)
                Spacer(Modifier.height(12.dp))
                StatusSection(current)
                Spacer(Modifier.height(12.dp))
                DeviceSection(current)
            }

            Spacer(Modifier.height(12.dp))
            SmallTitle(text = "操作")
            CardColumn {
                ArrowPreference(
                    title = "刷新状态",
                    summary = "向支付宝宿主请求最新报告",
                    onClick = { refresh++ }
                )
            }
        }
    }
}

@Composable
private fun ReportHeader(report: JSONObject) {
    val generatedAt = report.optLong("generatedAt", 0L)
    val ageSeconds = if (generatedAt > 0L) {
        ((System.currentTimeMillis() - generatedAt) / 1000L).coerceAtLeast(0L)
    } else {
        -1L
    }
    val ageText = when {
        ageSeconds < 0L -> "未知时间"
        ageSeconds < 60L -> "刚刚"
        ageSeconds < 300L -> "${ageSeconds / 60L} 分钟前"
        else -> "${ageSeconds / 60L} 分钟前 · 数据可能已过期"
    }
    val trigger = when (report.optString("trigger", "")) {
        "startup" -> "宿主启动"
        "command" -> "手动刷新"
        "postRun" -> "计划轮次后"
        else -> report.optString("trigger", "—")
    }
    CardColumn {
        StatusRow(
            title = "报告",
            value = formatTime(generatedAt),
            detail = ageText,
            valueColor = if (ageSeconds >= 300L) MedusaColors.Warning else MedusaColors.Muted
        )
        StatusRow(
            title = "来源",
            value = trigger,
            detail = "schema ${report.optInt("schema", 0)}"
        )
    }
}

@Composable
private fun AccountSection(report: JSONObject) {
    val process = report.optJSONObject("process")
    val account = report.optJSONObject("account")
    val uid = account?.optString("uid", "").orEmpty()
    val label = account?.optString("label", "").orEmpty().ifBlank { "—" }
    val accountValue = if (uid.isBlank()) label else "$label ($uid)"
    val login = account?.optBoolean("login", false) == true
    val host = process?.optString("hostVersion", "").orEmpty().ifBlank { "—" }
    val module = process?.optString("moduleVersion", "").orEmpty().ifBlank { "—" }
    val hook = process?.optBoolean("hookReady", false) == true
    val pid = process?.optInt("pid", 0) ?: 0
    val uptime = process?.optLong("uptimeMs", 0L) ?: 0L

    SmallTitle(text = "账号与进程")
    CardColumn {
        StatusRow(
            title = "账号",
            value = accountValue,
            detail = if (login) "已登录" else "未登录",
            valueColor = if (login) MedusaColors.Ink else MedusaColors.Warning
        )
        StatusRow(
            title = "宿主 / 模块",
            value = host,
            detail = "模块 $module"
        )
        StatusRow(
            title = "Hook",
            value = if (hook) "正常" else "未加载",
            detail = "PID $pid · 运行 ${formatDuration(uptime)}",
            valueColor = if (hook) MedusaColors.GreenDeep else MedusaColors.Danger
        )
    }
    val installed = BuildConfig.VERSION_NAME.orEmpty()
    if (module.isNotBlank() && installed.isNotBlank() && module != installed) {
        Spacer(Modifier.height(8.dp))
        CardColumn {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MedusaColors.CooldownSoft)
                    .padding(14.dp)
            ) {
                Text(
                    text = "宿主与模块版本不一致",
                    fontSize = MedusaType.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Warning
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "宿主内嵌 $module，已安装模块 $installed。请安装配套版本，否则界面与注入逻辑可能对不上。",
                    fontSize = MedusaType.Body,
                    color = MedusaColors.Ink
                )
            }
        }
    }
}

@Composable
private fun CapabilitySection(report: JSONObject) {
    val capabilities = report.optJSONArray("capabilities")
    SmallTitle(text = "运行能力")
    CardColumn {
        if (capabilities == null || capabilities.length() == 0) {
            StatusRow("能力", "—", "宿主未返回能力列表")
        } else {
            for (i in 0 until capabilities.length()) {
                val item = capabilities.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                val state = item.optString("state", "UNKNOWN")
                StatusRow(
                    title = capabilityLabel(id),
                    value = stateLabel(state),
                    detail = item.optString("detail", ""),
                    valueColor = stateTone(state)
                )
            }
        }
    }
}

@Composable
private fun SchedulerSection(report: JSONObject) {
    val scheduler = report.optJSONObject("scheduler")
    val lastRun = scheduler?.optJSONObject("lastRun")
    val health = scheduler?.optJSONObject("alarmHealth")

    val nextTarget = scheduler?.optString("nextTarget", "").orEmpty().ifBlank { "—" }
    val nextTime = scheduler?.optString("nextTime", "").orEmpty()
    val lastTarget = lastRun?.optString("target", "").orEmpty().ifBlank { "—" }
    val lastStatus = lastRun?.optString("status", "").orEmpty().ifBlank { "—" }
    val lastReason = lastRun?.optString("reason", "").orEmpty()
    val lastDuration = lastRun?.optLong("durationMs", 0L) ?: 0L
    val missed = scheduler?.optInt("missedToday", 0) ?: 0
    val suspectedKill = health?.optBoolean("suspectedKill", false) == true
    val lastSetAt = health?.optLong("lastSetAt", 0L) ?: 0L
    val lastFiredAt = health?.optLong("lastFiredAt", 0L) ?: 0L

    SmallTitle(text = "计划与闹钟")
    CardColumn {
        StatusRow(
            title = "下一跳",
            value = if (nextTime.isBlank()) nextTarget else "$nextTarget $nextTime",
            detail = if (nextTime.isBlank()) "当前没有下一跳" else "定时 $nextTime"
        )
        StatusRow(
            title = "上次运行",
            value = "$lastTarget · $lastStatus",
            detail = listOfNotNull(
                lastReason.ifBlank { null },
                if (lastDuration > 0L) formatDuration(lastDuration) else null
            ).joinToString(" · ")
        )
        StatusRow(
            title = "今日错过",
            value = "$missed 轮",
            detail = "窗口已结束、未执行的轮次"
        )
        StatusRow(
            title = "闹钟健康",
            value = if (suspectedKill) "疑似被系统清理" else "正常",
            detail = "上次设置 ${formatTime(lastSetAt)} · 上次触发 ${formatTime(lastFiredAt)}",
            valueColor = if (suspectedKill) MedusaColors.Warning else MedusaColors.GreenDeep
        )
    }
}

@Composable
private fun RiskSection(report: JSONObject) {
    val risk = report.optJSONObject("risk")
    val paused = risk?.optBoolean("globalPaused", false) == true
    val verification = risk?.optBoolean("verificationRequired", false) == true
    val scopes = risk?.optJSONArray("scopes")
    val lastSignal = risk?.optJSONObject("lastSignal")
    val method = lastSignal?.optString("method", "").orEmpty().ifBlank { "—" }
    val reason = lastSignal?.optString("reason", "").orEmpty()
    val at = lastSignal?.optLong("at", 0L) ?: 0L

    SmallTitle(text = "风控")
    CardColumn {
        StatusRow(
            title = "账号",
            value = if (paused || verification) "暂停 / 待验证" else "正常",
            detail = if (verification) "需要完成支付宝验证" else "",
            valueColor = if (paused || verification) MedusaColors.Warning else MedusaColors.GreenDeep
        )
        StatusRow(
            title = "作用域",
            value = "${scopes?.length() ?: 0} 项",
            detail = scopeSummary(scopes)
        )
        StatusRow(
            title = "最后信号",
            value = method,
            detail = listOfNotNull(
                reason.ifBlank { null },
                if (at > 0L) formatTime(at) else null
            ).joinToString(" · ")
        )
    }
}

@Composable
private fun DataSection(report: JSONObject) {
    val data = report.optJSONObject("data")
    val reportSummary = data?.optJSONObject("report")
    val log = data?.optJSONObject("log")
    val ledgerDay = data?.optString("ledgerDay", "").orEmpty().ifBlank { "—" }
    val lastSync = data?.optLong("lastSyncAt", 0L) ?: 0L
    val complete = data?.optBoolean("complete", false) == true
    val total = reportSummary?.optInt("total", 0) ?: 0
    val pending = reportSummary?.optInt("pending", 0) ?: 0
    val manual = reportSummary?.optInt("manual", 0) ?: 0
    val files = log?.optInt("files", 0) ?: 0
    val bytes = log?.optLong("bytes", 0L) ?: 0L

    SmallTitle(text = "数据")
    CardColumn {
        StatusRow(
            title = "账本",
            value = ledgerDay,
            detail = "同步 ${formatTime(lastSync)} · ${if (complete) "完整" else "不完整"}"
        )
        StatusRow(
            title = "任务",
            value = "$total 项",
            detail = "待处理 $pending · 需手动 $manual"
        )
        StatusRow(
            title = "日志",
            value = "$files 个文件",
            detail = formatBytes(bytes)
        )
    }
}

@Composable
private fun StatusSection(report: JSONObject) {
    val data = report.optJSONObject("data") ?: return
    val statuses = data.optJSONArray("statuses")
    val retryDay = data.optString("statusRetryDay", "").orEmpty()
    val retryCount = data.optInt("statusRetryCount", 0)
    val ledgerDay = data.optString("ledgerDay", "").orEmpty()

    SmallTitle(text = "首页状态")
    CardColumn {
        if (statuses == null || statuses.length() == 0) {
            StatusRow("状态", "—", "宿主未返回状态快照")
        } else {
            for (i in 0 until statuses.length()) {
                val item = statuses.optJSONObject(i) ?: continue
                val present = item.optBoolean("present", false)
                val at = item.optLong("at", 0L)
                val age = item.optLong("ageMinutes", -1L)
                StatusRow(
                    title = homeStatusLabel(item.optString("name", "")),
                    value = if (present) "今天" else "缺失",
                    detail = if (present) {
                        "${formatTime(at)} · " + if (age >= 0L) "$age 分钟前" else "时间未知"
                    } else {
                        "今天未生成，补抓后自动出现"
                    },
                    valueColor = if (present) MedusaColors.Green else MedusaColors.Danger
                )
            }
        }
        if (retryCount > 0) {
            StatusRow(
                title = "状态补抓",
                value = "$retryCount 次",
                detail = if (retryDay == ledgerDay) "今天已尝试（最多 2 次）"
                else "上次尝试 $retryDay"
            )
        }
    }
}

private fun homeStatusLabel(name: String): String = when (name) {
    "ocean" -> "神奇海洋"
    "farm" -> "蚂蚁庄园"
    "forest" -> "蚂蚁森林"
    "species" -> "神奇物种"
    "orchard" -> "芭芭农场"
    "protect" -> "生态保护"
    "green" -> "绿色行动"
    else -> name
}

@Composable
private fun DeviceSection(report: JSONObject) {
    val device = report.optJSONObject("device")
    val oem = report.optJSONObject("oem")
    val manufacturer = device?.optString("manufacturer", "").orEmpty()
    val model = device?.optString("model", "").orEmpty()
    val android = device?.optString("android", "").orEmpty().ifBlank { "—" }
    val oneUi = device?.optString("oneUI", "").orEmpty().ifBlank { "—" }
    val idle = device?.optBoolean("idle", false) == true
    val powerSave = device?.optBoolean("powerSave", false) == true
    val network = device?.optString("networkType", "").orEmpty().ifBlank { "—" }
    val guidance = oem?.optString("guidance", "").orEmpty()

    SmallTitle(text = "设备与 OEM")
    CardColumn {
        StatusRow(
            title = "设备",
            value = listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "—" },
            detail = "Android $android · One UI $oneUi"
        )
        StatusRow(
            title = "状态",
            value = if (idle) "Doze 空闲" else "正常",
            detail = "省电 ${if (powerSave) "是" else "否"} · 网络 $network"
        )
    }
    if (guidance.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        CardColumn {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MedusaColors.CooldownSoft)
                    .padding(14.dp)
            ) {
                Text(
                    text = "可能被系统休眠",
                    fontSize = MedusaType.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Warning
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = guidance,
                    fontSize = MedusaType.Body,
                    color = MedusaColors.Ink
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    value: String,
    detail: String = "",
    valueColor: Color = MedusaColors.Ink
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = MedusaType.Body,
                color = MedusaColors.Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
        }
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted
            )
        }
    }
}

private fun capabilityLabel(id: String): String = when (id) {
    "HOOK" -> "宿主 Hook"
    "LOGIN" -> "登录"
    "STORAGE_PERMISSION" -> "存储权限"
    "STORAGE_WRITABLE" -> "共享目录"
    "EXACT_ALARM" -> "精确闹钟"
    "BATTERY_OPTIMIZATION" -> "电池白名单"
    "NOTIFICATION" -> "通知"
    "FOREGROUND_SERVICE" -> "常驻通知"
    "NETWORK" -> "网络"
    "RPC_READY" -> "RPC"
    "ALARM_HEALTH" -> "下次闹钟"
    "OEM_BACKGROUND" -> "OEM 后台限制"
    "DEVICE_IDLE" -> "Doze / 省电"
    else -> id
}

private fun stateLabel(state: String): String = when (state) {
    "OK" -> "正常"
    "MISSING" -> "缺失"
    "DEGRADED" -> "降级"
    "UNKNOWN" -> "未知"
    else -> state
}

private fun stateTone(state: String): Color = when (state) {
    "OK" -> MedusaColors.GreenDeep
    "MISSING" -> MedusaColors.Danger
    "DEGRADED" -> MedusaColors.Warning
    else -> MedusaColors.Muted
}

private fun scopeSummary(scopes: JSONArray?): String {
    if (scopes == null || scopes.length() == 0) {
        return "没有暂停的域"
    }
    val names = ArrayList<String>()
    for (i in 0 until scopes.length()) {
        if (names.size >= 4) {
            break
        }
        val item = scopes.optJSONObject(i) ?: continue
        val scope = item.optString("scope", "")
        if (scope.isNotBlank()) {
            names.add(scope)
        }
    }
    return if (names.isEmpty()) "没有暂停的域" else names.joinToString(" · ")
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) {
        return "—"
    }
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) {
        return "—"
    }
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}分${seconds}秒" else "${seconds}秒"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) {
        return "0 B"
    }
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.CHINA, "%.1f KB", kb)
    }
    val mb = kb / 1024.0
    if (mb < 1024.0) {
        return String.format(Locale.CHINA, "%.1f MB", mb)
    }
    return String.format(Locale.CHINA, "%.2f GB", mb / 1024.0)
}

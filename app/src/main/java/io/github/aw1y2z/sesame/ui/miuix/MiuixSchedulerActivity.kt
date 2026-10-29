package io.github.aw1y2z.sesame.ui.miuix

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.ListModelField
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import java.util.Locale

/**
 * 定时计划页：把原先「逗号分隔、手写 HHmm」的三个原始列表换成时间选择器。
 * 只改内存，退出/保存时由 [ConfigSync] 统一落盘并通知注入进程重排闹钟。
 */
class MiuixSchedulerActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
    }

    private var userId: String? = null
    internal var saveHandler: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        ConfigPreload.prepare(userId)
        setAppContent {
            SchedulerContent(activity = this, userId = userId)
        }
    }

    override fun onBackPressed() {
        if (saveHandler?.invoke() != false) {
            super.onBackPressed()
        }
    }

    fun saveAndFinish() {
        if (saveHandler?.invoke() != false) {
            finish()
        }
    }
}

@Composable
private fun SchedulerContent(activity: MiuixSchedulerActivity, userId: String?) {
    val baseFields = remember { ConfigV2.INSTANCE.getModelFields("BaseModel") }
    val execField = remember { baseFields?.get("execAtTimeList") as? ListModelField }
    val wakeField = remember { baseFields?.get("wakenAtTimeList") as? ListModelField }
    val restartField = remember { baseFields?.get("restartAtTimeList") as? ListModelField }
    val intervalField: ModelField<*>? = remember { baseFields?.get("checkInterval") }

    var execTimes by remember { mutableStateOf(execField.readTimes()) }
    var wakeTimes by remember { mutableStateOf(wakeField.readTimes()) }
    var restartTimes by remember { mutableStateOf(restartField.readTimes()) }

    val saveNow: () -> Boolean = {
        val result = ConfigSync.save(activity, userId, false)
        if (!result.ok) {
            ToastUtil.show(activity, ConfigSync.failureMessage(result))
        }
        result.ok
    }
    DisposableEffect(Unit) {
        activity.saveHandler = saveNow
        onDispose { activity.saveHandler = null }
    }

    fun update(field: ListModelField?, times: List<String>) {
        field?.setObjectValue(times)
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "定时计划",
                onBack = { activity.saveAndFinish() },
                actions = {
                    IconButton(onClick = {
                        if (saveNow()) ToastUtil.show(activity, "定时计划已保存")
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "保存",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            SmallTitle(text = "基础节拍")
            CardColumn {
                if (intervalField != null) {
                    FieldItem(field = intervalField)
                }
            }
            Text(
                text = "定时执行在执行链存活时到点优先跑一轮；定时唤醒会单起一次执行广播；定时重载会在到点后重新挂载模块并立即执行一轮，等效软重启，不会杀掉支付宝进程，因此后续闹钟仍然有效。",
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TimePlanCard(
                title = "定时执行",
                hint = "执行链存活时，到点优先跑一轮",
                times = execTimes,
                onTimesChange = {
                    execTimes = it
                    update(execField, it)
                }
            )
            TimePlanCard(
                title = "定时唤醒",
                hint = "到点触发一次执行链路",
                times = wakeTimes,
                onTimesChange = {
                    wakeTimes = it
                    update(wakeField, it)
                }
            )
            TimePlanCard(
                title = "定时重载模块",
                hint = "到点重载模块并立即执行",
                times = restartTimes,
                onTimesChange = {
                    restartTimes = it
                    update(restartField, it)
                }
            )

            SmallTitle(text = "下一次")
            CardColumn {
                SummaryRow("自动执行", nextOccurrenceText(execTimes))
                SummaryRow("定时唤醒", nextOccurrenceText(wakeTimes))
                SummaryRow("重载模块", nextOccurrenceText(restartTimes))
            }
            Text(
                text = "闹钟由支付宝进程内的系统 AlarmManager 触发；修改后请保持「所有文件访问权限」，保存时会把计划同步到支付宝内的模块。",
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun ListModelField?.readTimes(): List<String> =
    this?.value.orEmpty().mapNotNull(::normalizeTime).distinct().sorted()

@Composable
private fun TimePlanCard(
    title: String,
    hint: String,
    times: List<String>,
    onTimesChange: (List<String>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SmallTitle(text = title)
    CardColumn {
        if (times.isEmpty()) {
            ArrowPreference(
                title = "尚未设置",
                summary = hint,
                onClick = {}
            )
        }
        times.forEach { time ->
            ArrowPreference(
                title = displayTime(time),
                summary = "点击移除",
                onClick = {
                    onTimesChange(times.filterNot { it == time })
                    ToastUtil.show(context, "已移除 ${displayTime(time)}")
                }
            )
        }
        ArrowPreference(
            title = "＋ 添加时间",
            summary = hint,
            onClick = {
                val now = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val value = String.format(Locale.US, "%02d%02d", hour, minute)
                        if (value !in times) {
                            onTimesChange((times + value).sorted())
                        }
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true
                ).show()
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onBackground
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary
        )
    }
}

/** HHmm / HHmmss；非法项返回 null（旧默认值里的 "24" 会在编辑时被自然剔除） */
private fun normalizeTime(raw: String): String? {
    val digits = raw.filter { it.isDigit() }
    return try {
        when (digits.length) {
            2 -> {
                val hour = digits.toInt()
                if (hour !in 0..23) null else String.format(Locale.US, "%02d00", hour)
            }
            4 -> {
                val hour = digits.substring(0, 2).toInt()
                val minute = digits.substring(2, 4).toInt()
                if (hour !in 0..23 || minute !in 0..59) null
                else String.format(Locale.US, "%02d%02d", hour, minute)
            }
            6 -> {
                val hour = digits.substring(0, 2).toInt()
                val minute = digits.substring(2, 4).toInt()
                val second = digits.substring(4, 6).toInt()
                if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) null
                else String.format(Locale.US, "%02d%02d%02d", hour, minute, second)
            }
            else -> null
        }
    } catch (t: Throwable) {
        Log.printStackTrace(t)
        null
    }
}

private fun displayTime(value: String): String = try {
    if (value.length == 6) {
        "${value.substring(0, 2)}:${value.substring(2, 4)}:${value.substring(4, 6)}"
    } else {
        "${value.substring(0, 2)}:${value.substring(2, 4)}"
    }
} catch (t: Throwable) {
    value
}

private fun nextOccurrenceText(times: List<String>): String {
    if (times.isEmpty()) {
        return "未设置"
    }
    val now = Calendar.getInstance()
    var best: Calendar? = null
    for (raw in times) {
        val time = normalizeTime(raw) ?: continue
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, time.substring(0, 2).toInt())
        calendar.set(Calendar.MINUTE, time.substring(2, 4).toInt())
        calendar.set(Calendar.SECOND, if (time.length == 6) time.substring(4, 6).toInt() else 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (!calendar.after(now)) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        if (best == null || calendar.before(best)) {
            best = calendar
        }
    }
    val next = best ?: return "未设置"
    val isToday = next.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) &&
        next.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val label = if (isToday) "今天" else "明天"
    return "$label ${displayTime(String.format(Locale.US, "%02d%02d", next.get(Calendar.HOUR_OF_DAY), next.get(Calendar.MINUTE)))}"
}

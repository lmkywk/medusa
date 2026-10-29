package io.github.aw1y2z.sesame.ui.miuix

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.plan.Plan
import io.github.aw1y2z.sesame.data.plan.PlanStore
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * 组编辑器：一组（日常）＝若干叶子任务 + 一个开始时间。
 * 保存时落成一个组记录 + 组内每个目标一条计划；执行器仍是「一个计划一个目标」。
 */
class MiuixPlanComposeActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { PlanComposeScreen(this) }
    }

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_TARGETS = "targets"
        const val EXTRA_GROUP_ID = "groupId"
    }
}

@Composable
private fun PlanComposeScreen(activity: MiuixPlanComposeActivity) {
    val context = LocalContext.current
    val userId = remember { activity.intent.getStringExtra(MiuixPlanComposeActivity.EXTRA_USER_ID).orEmpty() }
    val groupId = remember { activity.intent.getStringExtra(MiuixPlanComposeActivity.EXTRA_GROUP_ID)?.takeIf { it.isNotBlank() } }
    val editing = groupId != null

    val existingMembers = remember {
        if (groupId == null) emptyList()
        else PlanStore.load(userId).filter { it.groupId == groupId }.sortedBy { it.windowStart }
    }
    var targets by remember {
        mutableStateOf(
            (if (editing) existingMembers.map { it.target }
            else (activity.intent.getStringArrayExtra(MiuixPlanComposeActivity.EXTRA_TARGETS) ?: emptyArray()).toList())
                .filter { PlanTargets.isValidTarget(it) }
                .distinct()
        )
    }
    var groupName by remember {
        mutableStateOf(
            if (editing) TaskGroupStore.loadGroupNames(userId)[groupId].orEmpty() else ""
        )
    }
    var startTime by remember {
        mutableStateOf(if (editing) existingMembers.firstOrNull()?.windowStart ?: defaultStartTime() else defaultStartTime())
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val picked = result.data?.getStringArrayExtra(MiuixTaskPickerActivity.EXTRA_RESULT) ?: return@rememberLauncherForActivityResult
            targets = (targets + picked.toList()).filter { PlanTargets.isValidTarget(it) }.distinct()
        }
    }

    fun minutesFor(target: String): Int =
        MeasuredDurations.minutesOf(userId, target, TaskCatalog.minutesOf(target))

    val totalMinutes = targets.sumOf { minutesFor(it) }
    val startMinute = timeToMinutes(startTime) ?: 0
    val overflow = startMinute + totalMinutes > 23 * 60 + 59
    val fallbackName = remember(targets, startTime) { autoGroupName(targets, startTime) }

    fun openPicker() {
        val intent = Intent(context, MiuixTaskPickerActivity::class.java)
            .putExtra(MiuixTaskPickerActivity.EXTRA_TITLE, "继续添加")
            .putExtra(MiuixTaskPickerActivity.EXTRA_USER_ID, userId)
            .putExtra(MiuixTaskPickerActivity.EXTRA_SELECTED, targets.toTypedArray())
        picker.launch(intent)
    }

    fun save() {
        if (targets.isEmpty()) return
        if (overflow) {
            ToastUtil.show(context, "今天剩余时间排不下，请把开始时间提前")
            return
        }
        if (userId.isBlank()) {
            ToastUtil.show(context, "尚未识别支付宝账号")
            return
        }
        val id = groupId ?: UUID.randomUUID().toString()
        val all = PlanStore.load(userId)
        all.removeAll { plan -> plan.groupId == id && plan.target !in targets }
        var cursor = startMinute
        targets.forEach { target ->
            val minutes = minutesFor(target)
            val existing = all.firstOrNull { it.groupId == id && it.target == target }
            val plan = existing ?: Plan().apply {
                setTarget(target)
                setEnabled(true)
                setDefaultPlan(false)
                setFilters("")
                setGroupId(id)
                all.add(this)
            }
            plan.setGroupId(id)
            val oldStart = timeToMinutes(plan.windowStart) ?: cursor
            val delta = cursor - oldStart
            val oldRounds = plan.roundTimes.mapNotNull { timeToMinutes(it) }
            plan.setWindowStart(minutesToTime(cursor))
            plan.setWindowEnd(minutesToTime(cursor + minutes))
            plan.setRoundTimes(
                if (oldRounds.isEmpty()) listOf(minutesToTime(cursor))
                else oldRounds.map { minutesToTime((it + delta).coerceIn(0, 23 * 60 + 59)) }
            )
            cursor += minutes
        }
        if (!PlanStore.save(userId, all)) {
            ToastUtil.show(context, "保存失败，请检查文件访问权限")
            return
        }
        val finalName = groupName.trim().ifEmpty { fallbackName }
        TaskGroupStore.saveGroupName(userId, id, finalName)
        context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
        ToastUtil.show(context, "已保存「$finalName」· ${targets.size} 项")
        activity.finish()
    }

    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        LogTopBar(
            title = if (editing) "编辑组" else "新建组",
            onBack = { activity.finish() }
        )
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "组只回答「做什么」；开始时间决定这一组几点跑。成员按顺序执行，时间窗自动铺开。",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 15.dp, vertical = 13.dp)
                ) {
                    TextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = if (fallbackName.isEmpty()) "组名" else "组名（默认：$fallbackName）",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "开始时间",
                            fontSize = MedusaType.Body,
                            fontWeight = FontWeight.SemiBold,
                            color = MedusaColors.Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            startTime,
                            fontSize = MedusaType.CardTitle,
                            fontWeight = FontWeight.Bold,
                            color = MedusaColors.GreenDeep,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MedusaColors.Green.copy(alpha = 0.10f))
                                .clickable {
                                    val parts = startTime.split(":")
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            startTime = String.format(Locale.US, "%02d:%02d", h, m)
                                        },
                                        parts.getOrNull(0)?.toIntOrNull() ?: 10,
                                        parts.getOrNull(1)?.toIntOrNull() ?: 0,
                                        true
                                    ).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${targets.size} 项 · 预计约 $totalMinutes 分钟"
                            + if (overflow) " · 今天排不下" else "",
                        fontSize = MedusaType.Secondary,
                        color = if (overflow) MedusaColors.Stopped else MedusaColors.Muted
                    )
                }
                Spacer(Modifier.height(14.dp))

                if (targets.isEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                            .padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("还没有选择任务", fontSize = MedusaType.Body, color = MedusaColors.Muted)
                        Text(
                            "先想清楚要做什么，再交给它排时间",
                            fontSize = MedusaType.Secondary,
                            color = Color(0xFFA2ACBC),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    var cursor = startMinute
                    targets.forEachIndexed { index, target ->
                        val minutes = minutesFor(target)
                        val from = minutesToTime(cursor)
                        val to = minutesToTime(cursor + minutes)
                        val measured = MeasuredDurations.samples(userId, target)
                        cursor += minutes
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MedusaColors.Green.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = MedusaType.Caption,
                                    fontWeight = FontWeight.Bold,
                                    color = MedusaColors.GreenDeep
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = PlanTargets.labelOf(target),
                                    fontSize = MedusaType.Body,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MedusaColors.Ink
                                )
                                Text(
                                    text = "$from–$to · " + durationLabel(minutes, measured),
                                    fontSize = MedusaType.Caption,
                                    color = MedusaColors.Muted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { targets = targets.filterIndexed { i, _ -> i != index } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "移除",
                                    tint = MedusaColors.Muted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openPicker() }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MedusaColors.GreenDeep,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "继续添加",
                            fontSize = MedusaType.Secondary,
                            fontWeight = FontWeight.SemiBold,
                            color = MedusaColors.GreenDeep
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MedusaActionButton(
                    text = "再选一次",
                    onClick = { openPicker() },
                    tone = MedusaButtonTone.Secondary,
                    modifier = Modifier.weight(1f),
                    height = 42.dp
                )
                MedusaActionButton(
                    text = when {
                        targets.isEmpty() -> "请选择任务"
                        editing -> "保存这一组"
                        else -> "保存为组"
                    },
                    onClick = { save() },
                    modifier = Modifier.weight(1.4f),
                    enabled = targets.isNotEmpty() && !overflow,
                    height = 42.dp
                )
            }
        }
    }
}

private fun durationLabel(minutes: Int, measured: Pair<Int, Int>?): String =
    if (measured == null) "约 $minutes 分钟（估算）"
    else "约 $minutes 分钟（实测 ${measured.second} 次）"

/** 组名默认值：同域叫「域＋日常」，跨域按时段叫「早间 N 件套」。 */
internal fun autoGroupName(targets: List<String>, startTime: String): String {
    if (targets.isEmpty()) return ""
    val hour = startTime.split(":").getOrNull(0)?.toIntOrNull() ?: 8
    val slot = when (hour) {
        in 5..10 -> "早间"
        in 11..13 -> "午间"
        in 14..18 -> "下午"
        in 19..23 -> "晚间"
        else -> "夜间"
    }
    val domains = targets.map { TaskCatalog.domainLabelOf(it) }.distinct()
    return if (domains.size == 1) slot + domains[0] + "日常" else slot + targets.size + "件套"
}

private fun defaultStartTime(): String {
    val calendar = Calendar.getInstance().apply { add(Calendar.MINUTE, 10) }
    val minute = (calendar.get(Calendar.MINUTE) / 5) * 5
    return String.format(
        Locale.US, "%02d:%02d",
        calendar.get(Calendar.HOUR_OF_DAY), minute
    )
}

private fun timeToMinutes(time: String): Int? {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun minutesToTime(minutes: Int): String {
    val clamped = minutes.coerceIn(0, 23 * 60 + 59)
    return String.format(Locale.US, "%02d:%02d", clamped / 60, clamped % 60)
}

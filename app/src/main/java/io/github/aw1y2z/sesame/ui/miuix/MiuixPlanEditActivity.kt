package io.github.aw1y2z.sesame.ui.miuix

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.plan.Plan
import io.github.aw1y2z.sesame.data.plan.PlanStore
import io.github.aw1y2z.sesame.data.plan.PlanTargetLedgers
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class MiuixPlanEditActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_PLAN_ID = "planId"
    }

    private var userId: String? = null
    internal var planId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        planId = intent.getStringExtra(EXTRA_PLAN_ID)
        setAppContent { PlanEditContent(this, userId) }
    }
}

@Composable
private fun PlanEditContent(activity: MiuixPlanEditActivity, userId: String?) {
    val context = LocalContext.current
    val existing = remember {
        PlanStore.load(userId ?: "").firstOrNull { it.id == activity.planId }
    }
    val mappedGroupId = remember {
        activity.planId?.let { TaskGroupStore.loadPlanGroups(userId ?: "")[it] }
    }
    var groupRevision by remember { mutableIntStateOf(0) }
    val customGroups = remember(userId, groupRevision) {
        TaskGroupStore.loadCustomGroups(userId ?: "")
    }
    val allGroups = remember(customGroups) { TaskGroups.all + customGroups }
    val initialGroup = remember {
        if (existing == null) {
            allGroups.firstOrNull { it.id == "forest.collect" } ?: allGroups.firstOrNull()
        } else {
            allGroups.firstOrNull { it.id == mappedGroupId }
        }
    }
    val existingPlanTasks = remember {
        activity.planId?.let { TaskGroupStore.loadPlanTasks(userId ?: "")[it] }
    }
    var selectedGroup by remember { mutableStateOf(initialGroup) }
    var selectedTasks by remember {
        mutableStateOf(existingPlanTasks ?: initialGroup?.tasks ?: emptyList())
    }
    var target by remember { mutableStateOf(initialGroup?.target ?: existing?.target ?: PlanTargets.FOREST_COLLECT) }
    var windowStart by remember { mutableStateOf(existing?.windowStart ?: "07:00") }
    var windowEnd by remember { mutableStateOf(existing?.windowEnd ?: "09:00") }
    var roundTimes by remember { mutableStateOf(existing?.roundTimes?.toList() ?: listOf("07:00")) }
    var paceMinutes by remember { mutableStateOf(existing?.paceMinutes ?: 30) }
    var targetDialog by remember { mutableStateOf(false) }
    var groupDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val taskListTarget = PlanTargetLedgers.hasTaskList(target)

    fun pickTime(current: String, onPicked: (String) -> Unit) {
        val parts = current.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(context, { _, h, m ->
            onPicked(String.format(Locale.US, "%02d:%02d", h, m))
        }, hour, minute, true).show()
    }

    fun rebuildRounds(count: Int) {
        val start = timeToMinutes(windowStart)
        val end = timeToMinutes(windowEnd)
        if (start == null || end == null || end <= start) {
            ToastUtil.show(context, "结束时间需要晚于开始时间")
            return
        }
        val result = ArrayList<String>()
        if (count <= 1) {
            result.add(windowStart)
        } else {
            for (i in 0 until count) {
                val minute = start + (end - start) * i / (count - 1)
                result.add(minutesToTime(minute))
            }
        }
        roundTimes = result
    }

    fun persistPlan() {
        val plan = existing ?: Plan()
        selectedGroup?.let { target = it.target }
        plan.target = target
        plan.windowStart = windowStart
        plan.windowEnd = windowEnd
        plan.setRoundTimes(roundTimes)
        plan.setPaceMinutes(paceMinutes)
        plan.setDefaultPlan(false)
        // 任务组 → 按类型执行；直接选目标时不带过滤器（整域全跑）
        plan.setFilters(selectedGroup?.filterJson() ?: "")
        plan.setEnabled(existing?.isEnabled ?: true)
        val all = PlanStore.load(userId ?: "")
        val index = all.indexOfFirst { it.id == plan.id }
        if (index >= 0) all[index] = plan else all.add(plan)
        if (!PlanStore.save(userId ?: "", all)) {
            ToastUtil.show(context, "保存失败，请检查文件访问权限")
            return
        }
        // 原型侧车：只记录展示用的任务组与任务组合，不改 plans.json 与执行器
        TaskGroupStore.setPlanGroup(userId ?: "", plan.id, selectedGroup?.id)
        TaskGroupStore.setPlanTasks(
            userId ?: "",
            plan.id,
            if (selectedGroup != null) selectedTasks else null
        )
        context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
        ToastUtil.show(context, if (selectedGroup != null) "计划已保存（任务组原型）" else "计划已保存")
        activity.finish()
    }

    fun save() {
        if (!PlanTargets.isValidTarget(target)) {
            ToastUtil.show(context, "请选择执行目标")
            return
        }
        val start = timeToMinutes(windowStart)
        val end = timeToMinutes(windowEnd)
        if (start == null || end == null || end <= start) {
            ToastUtil.show(context, "结束时间需要晚于开始时间")
            return
        }
        if (roundTimes.isEmpty()) {
            ToastUtil.show(context, "至少设置一轮")
            return
        }
        for (time in roundTimes) {
            val at = timeToMinutes(time)
            if (at == null || at < start || at > end) {
                ToastUtil.show(context, "第 " + (roundTimes.indexOf(time) + 1) + " 轮不在时间段内")
                return
            }
        }
        // 时间冲突只提醒，不阻止保存：同一时段允许排多个，由调度串行排队。
        val conflicts = overlappingPlans(
            windowStart,
            windowEnd,
            PlanStore.load(userId ?: ""),
            excludeId = existing?.id
        )
        if (conflicts.isNotEmpty()) {
            val names = conflicts.take(3).joinToString("；") {
                PlanTargets.labelOf(it.target) + " " + it.windowStart + "–" + it.windowEnd
            } + if (conflicts.size > 3) " 等 ${conflicts.size} 项" else ""
            AlertDialog.Builder(context)
                .setTitle("时间段有重叠")
                .setMessage(
                    "与 $names 的时间窗重叠。\n" +
                        "同一时段会串行排队；只提醒，不阻止保存。仍要保存吗？"
                )
                .setPositiveButton("仍然保存") { _, _ -> persistPlan() }
                .setNegativeButton("返回调整", null)
                .show()
            return
        }
        persistPlan()
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = if (existing == null) "新增计划" else "编辑计划",
                onBack = { activity.finish() },
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
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
                .padding(16.dp)
        ) {
            SmallTitle(text = "任务组")
            CardColumn {
                ArrowPreference(
                    title = selectedGroup?.name ?: ("直接执行：" + PlanTargets.labelOf(target)),
                    summary = selectedGroup?.let { "约 ${it.estimatedMinutes} 分钟 · ${it.risk.label}" }
                        ?: "点击选择任务组，或直接指定执行目标",
                    onClick = { groupDialog = true }
                )
            }
            if (selectedGroup != null) {
                val group = selectedGroup!!
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.risk.label,
                        fontSize = MedusaType.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = group.risk.color,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(group.risk.color.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "预计 ${group.estimatedMinutes} 分钟",
                        fontSize = MedusaType.Caption,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = group.composition,
                    fontSize = MedusaType.Body,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = "已选 " + selectedTasks.size + " 项：" +
                        selectedTasks.joinToString("、").ifBlank { "未选择" },
                    fontSize = MedusaType.Caption,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "调整任务 ›",
                    fontSize = MedusaType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.GreenDeep,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showTaskDialog = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
                if (group.note.isNotBlank()) {
                    Text(
                        text = group.note,
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Cooldown,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SmallTitle(text = "时间段")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MedusaFieldCard(
                    label = "开始",
                    value = windowStart,
                    modifier = Modifier.weight(1f)
                ) {
                    pickTime(windowStart) { windowStart = it; rebuildRounds(roundTimes.size) }
                }
                MedusaFieldCard(
                    label = "结束",
                    value = windowEnd,
                    modifier = Modifier.weight(1f)
                ) {
                    pickTime(windowEnd) { windowEnd = it; rebuildRounds(roundTimes.size) }
                }
            }
            Spacer(Modifier.height(16.dp))

            SmallTitle(text = "轮次")
            if (taskListTarget) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MedusaFieldCard(
                        label = "轮数",
                        value = "1 轮",
                        modifier = Modifier.weight(1f)
                    ) {
                        ToastUtil.show(context, "任务列表目标只跑一轮")
                    }
                    MedusaFieldCard(
                        label = "执行时间",
                        value = roundTimes.firstOrNull() ?: windowStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        val current = roundTimes.firstOrNull() ?: windowStart
                        pickTime(current) { picked -> roundTimes = listOf(picked) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MedusaFieldCard(
                        label = "轮数",
                        value = roundTimes.size.toString() + " 轮",
                        modifier = Modifier.weight(1f)
                    ) {
                        val options = arrayOf("1 轮", "2 轮", "3 轮", "4 轮", "5 轮")
                        AlertDialog.Builder(context)
                            .setTitle("选择轮数")
                            .setItems(options) { _, which -> rebuildRounds(which + 1) }
                            .show()
                    }
                    MedusaFieldCard(
                        label = "第 1 轮",
                        value = roundTimes.firstOrNull() ?: windowStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        val time = roundTimes.firstOrNull() ?: windowStart
                        pickTime(time) { picked ->
                            val next = roundTimes.toMutableList()
                            if (next.isNotEmpty()) next[0] = picked
                            roundTimes = next
                        }
                    }
                }
                val rest = roundTimes.drop(1)
                var offset = 0
                while (offset < rest.size) {
                    val rowOffset = offset
                    val firstIndex = rowOffset + 1
                    val secondIndex = rowOffset + 2
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MedusaFieldCard(
                            label = "第 " + (firstIndex + 1) + " 轮",
                            value = rest[rowOffset],
                            modifier = Modifier.weight(1f)
                        ) {
                            pickTime(rest[rowOffset]) { picked ->
                                val next = roundTimes.toMutableList()
                                if (firstIndex in next.indices) next[firstIndex] = picked
                                roundTimes = next
                            }
                        }
                        if (rowOffset + 1 < rest.size) {
                            MedusaFieldCard(
                                label = "第 " + (secondIndex + 1) + " 轮",
                                value = rest[rowOffset + 1],
                                modifier = Modifier.weight(1f)
                            ) {
                                pickTime(rest[rowOffset + 1]) { picked ->
                                    val next = roundTimes.toMutableList()
                                    if (secondIndex in next.indices) next[secondIndex] = picked
                                    roundTimes = next
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    offset += 2
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (taskListTarget) {
                    "这个目标带任务列表：只跑一轮，进度按任务数计算，做完即结束。"
                } else {
                    "时间按保存后的先后顺序执行；与其他计划重叠时会串行排队。"
                },
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(Modifier.height(16.dp))

            SmallTitle(text = "完成节奏")
            CardColumn {
                ArrowPreference(
                    title = "任务铺开时长",
                    summary = if (paceMinutes <= 0) "不限制（按模块自身速度）"
                    else "$paceMinutes 分钟（任务平均分配）",
                    onClick = {
                        val options = arrayOf("不限制", "15 分钟", "30 分钟", "60 分钟")
                        AlertDialog.Builder(context)
                            .setTitle("任务铺开时长")
                            .setItems(options) { _, which ->
                                paceMinutes = when (which) {
                                    1 -> 15
                                    2 -> 30
                                    3 -> 60
                                    else -> 0
                                }
                            }
                            .show()
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "只影响任务列表类目标；森林收能量这类持续收集不按此节奏。",
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.height(16.dp))
            if (existing != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MedusaActionButton(
                        text = "保存计划",
                        onClick = { save() },
                        modifier = Modifier.weight(1f)
                    )
                    MedusaActionButton(
                        text = "删除计划",
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        tone = MedusaButtonTone.Danger
                    )
                }
            } else {
                MedusaActionButton(
                    text = "保存计划",
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (groupDialog) {
        TaskGroupPickerDialog(
            groups = allGroups,
            selectedId = selectedGroup?.id,
            onSelect = { group ->
                selectedGroup = group
                target = group.target
                selectedTasks = group.tasks
                if (PlanTargetLedgers.hasTaskList(target)) {
                    roundTimes = listOf(roundTimes.firstOrNull() ?: windowStart)
                }
                groupDialog = false
            },
            onCreateGroup = {
                groupDialog = false
                showCreateGroup = true
            },
            onAdvanced = {
                groupDialog = false
                targetDialog = true
            },
            onDismiss = { groupDialog = false }
        )
    }

    if (showCreateGroup) {
        val base = selectedGroup ?: allGroups.firstOrNull()
        if (base != null) {
            TaskGroupCreateDialog(
                base = base,
                onCreate = { name, risk, minutes, mechanisms ->
                    val spec = base.copy(
                        id = "custom-" + UUID.randomUUID().toString(),
                        name = name,
                        composition = if (mechanisms.isEmpty()) base.tasks.joinToString(" · ")
                        else mechanisms.joinToString(" · ") { TaskGroups.mechanismLabel(it) },
                        risk = risk,
                        estimatedMinutes = minutes,
                        note = "自定义任务组：按所选类型执行",
                        executesFullTarget = true,
                        mechanisms = mechanisms
                    )
                    TaskGroupStore.saveCustomGroup(userId ?: "", spec)
                    groupRevision++
                    selectedGroup = spec
                    target = spec.target
                    selectedTasks = spec.tasks
                    showCreateGroup = false
                },
                onDismiss = { showCreateGroup = false }
            )
        }
    }

    if (showTaskDialog) {
        selectedGroup?.let { group ->
            TaskSelectionDialog(
                group = group,
                selected = selectedTasks,
                onSave = {
                    selectedTasks = it
                    showTaskDialog = false
                },
                onDismiss = { showTaskDialog = false }
            )
        }
    }

    if (targetDialog) {
        TaskTargetPickerDialog(
            targets = PlanTargets.all().filterNot { it.endsWith(".external") },
            selectedTarget = if (selectedGroup == null) target else null,
            currentGroupName = selectedGroup?.name,
            onSelect = { picked ->
                selectedGroup = null
                selectedTasks = emptyList()
                target = picked
                if (PlanTargetLedgers.hasTaskList(target)) {
                    roundTimes = listOf(roundTimes.firstOrNull() ?: windowStart)
                }
                targetDialog = false
            },
            onBackToGroups = {
                targetDialog = false
                groupDialog = true
            },
            onDismiss = { targetDialog = false }
        )
    }

    if (showDeleteConfirm) {
        val plan = existing
        if (plan != null) {
            ConfirmDialog(
                title = "删除计划",
                text = "删除「" + PlanTargets.labelOf(plan.target) + "」计划？",
                confirmText = "删除",
                dismissText = "取消",
                confirmTone = MedusaButtonTone.Danger,
                onConfirm = {
                    val all = PlanStore.load(userId ?: "").filter { it.id != plan.id }
                    PlanStore.save(userId ?: "", all)
                    TaskGroupStore.setPlanGroup(userId ?: "", plan.id, null)
                    TaskGroupStore.setPlanTasks(userId ?: "", plan.id, null)
                    context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
                    showDeleteConfirm = false
                    activity.finish()
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

private fun timeToMinutes(time: String): Int? {
    if (!PlanTargets.isValidTime(time)) return null
    return time.substring(0, 2).toInt() * 60 + time.substring(3, 5).toInt()
}

private fun minutesToTime(minutes: Int): String {
    return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
}

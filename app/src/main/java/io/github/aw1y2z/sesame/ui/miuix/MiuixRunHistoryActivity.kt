package io.github.aw1y2z.sesame.ui.miuix

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Calendar

/** 只读运行记录页：计划页只留今日概览，完整当日历史在这里看。 */
class MiuixRunHistoryActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "user_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            RunHistoryScreen(this)
        }
    }
}

@Composable
private fun RunHistoryScreen(activity: MiuixRunHistoryActivity) {
    val userId = remember { activity.intent.getStringExtra(MiuixRunHistoryActivity.EXTRA_USER_ID).orEmpty() }
    var history by remember { mutableStateOf<List<PlanHistory.Record>>(emptyList()) }
    var filter by remember { mutableStateOf("ALL") }
    var skippedExpanded by remember { mutableStateOf(false) }
    var expandedSkippedGroups by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(userId) {
        while (true) {
            history = PlanHistory.load(userId).sortedByDescending { it.startedAt }
            delay(3000L)
        }
    }

    val todayHistory = remember(history) {
        val now = Calendar.getInstance()
        history.filter {
            it.status != PlanHistory.RUNNING && run {
                val c = Calendar.getInstance().apply { timeInMillis = it.startedAt }
                c.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
            }
        }
    }
    val completedHistory = remember(todayHistory) {
        todayHistory.filter { it.status == PlanHistory.COMPLETED }.sortedByDescending { it.startedAt }
    }
    val skippedHistory = remember(todayHistory) {
        todayHistory.filter { it.status == PlanHistory.SKIPPED }.sortedByDescending { it.startedAt }
    }
    val stoppedHistory = remember(todayHistory) {
        todayHistory.filter {
            it.status == PlanHistory.RISK_STOPPED || it.status == PlanHistory.STOPPED
        }.sortedByDescending { it.startedAt }
    }
    val unfinishedHistory = remember(todayHistory) {
        todayHistory.filter {
            it.status != PlanHistory.COMPLETED && it.status != PlanHistory.SKIPPED &&
                it.status != PlanHistory.RISK_STOPPED && it.status != PlanHistory.STOPPED
        }.sortedByDescending { it.startedAt }
    }
    val riskHistory = remember(stoppedHistory, unfinishedHistory) {
        (stoppedHistory + unfinishedHistory).sortedByDescending { it.startedAt }
    }
    val skippedGroups = remember(skippedHistory) {
        skippedHistory.groupBy { it.target to it.roundTime }
            .map { (key, records) -> key to records.sortedByDescending { it.startedAt } }
            .sortedByDescending { it.second.first().startedAt }
    }
    val mainHistory = remember(completedHistory, riskHistory) {
        (completedHistory + riskHistory).sortedByDescending { it.startedAt }
    }

    Scaffold(
        topBar = { LogTopBar(title = "运行记录", onBack = { activity.finish() }) },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 24.dp)
        ) {
            Text(
                text = "执行记录保留 3 天 · 今天 ${todayHistory.size} 轮",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted
            )
            Spacer(Modifier.height(10.dp))

            if (todayHistory.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                        .padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有执行记录", fontSize = MedusaType.Body, color = MedusaColors.Muted)
                    Text(
                        "到点执行后，这里会显示每一轮的结果",
                        fontSize = MedusaType.Secondary,
                        color = Color(0xFFA2ACBC)
                    )
                }
            } else {
                val skippedGroupKey: (Pair<String, String>) -> String = { it.first + "|" + it.second }
                HistoryStatusChips(
                    total = todayHistory.size,
                    completed = completedHistory.size,
                    skipped = skippedHistory.size,
                    risk = riskHistory.size,
                    selected = filter,
                    onSelect = {
                        filter = it
                        if (it != "SKIP") skippedExpanded = false
                    }
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                ) {
                    when (filter) {
                        "DONE" -> {
                            if (completedHistory.isEmpty()) {
                                HistoryEmpty("今天还没有已完成记录")
                            } else {
                                completedHistory.forEachIndexed { index, record ->
                                    HistoryRow(record)
                                    if (index != completedHistory.lastIndex) Separator()
                                }
                            }
                        }
                        "SKIP" -> {
                            if (skippedGroups.isEmpty()) {
                                HistoryEmpty("今天没有跳过记录")
                            } else {
                                skippedGroups.forEachIndexed { index, group ->
                                    SkippedGroupRow(
                                        target = group.first.first,
                                        roundTime = group.first.second,
                                        records = group.second,
                                        expanded = expandedSkippedGroups.contains(skippedGroupKey(group.first)),
                                        onToggle = {
                                            val key = skippedGroupKey(group.first)
                                            expandedSkippedGroups = if (expandedSkippedGroups.contains(key)) {
                                                expandedSkippedGroups - key
                                            } else {
                                                expandedSkippedGroups + key
                                            }
                                        }
                                    )
                                    if (index != skippedGroups.lastIndex) Separator()
                                }
                            }
                        }
                        "RISK" -> {
                            if (riskHistory.isEmpty()) {
                                HistoryEmpty("今天没有风控或未完成记录")
                            } else {
                                riskHistory.forEachIndexed { index, record ->
                                    HistoryRow(record)
                                    if (index != riskHistory.lastIndex) Separator()
                                }
                            }
                        }
                        else -> {
                            mainHistory.forEachIndexed { index, record ->
                                HistoryRow(record)
                                if (index != mainHistory.lastIndex || skippedHistory.isNotEmpty()) Separator()
                            }
                            if (skippedHistory.isNotEmpty()) {
                                SkippedSummaryRow(
                                    count = skippedHistory.size,
                                    groupCount = skippedGroups.size,
                                    expanded = skippedExpanded,
                                    onToggle = { skippedExpanded = !skippedExpanded }
                                )
                                if (skippedExpanded) {
                                    skippedGroups.forEachIndexed { _, group ->
                                        Separator()
                                        SkippedGroupRow(
                                            target = group.first.first,
                                            roundTime = group.first.second,
                                            records = group.second,
                                            expanded = expandedSkippedGroups.contains(skippedGroupKey(group.first)),
                                            onToggle = {
                                                val key = skippedGroupKey(group.first)
                                                expandedSkippedGroups = if (expandedSkippedGroups.contains(key)) {
                                                    expandedSkippedGroups - key
                                                } else {
                                                    expandedSkippedGroups + key
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: PlanHistory.Record) {
    val completed = record.status == PlanHistory.COMPLETED
    val skipped = record.status == PlanHistory.SKIPPED
    val stopped = record.status == PlanHistory.RISK_STOPPED || record.status == PlanHistory.STOPPED
    val tone = when {
        completed -> MedusaColors.Green
        skipped -> Color(0xFF9AA5B5)
        stopped -> MedusaColors.Stopped
        else -> MedusaColors.Cooldown
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(7.dp)
                .background(tone, CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = PlanTargets.labelOf(record.target) + " · " + record.roundTime,
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Ink
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        completed -> "已完成"
                        skipped -> "已跳过"
                        record.status == PlanHistory.RISK_STOPPED -> "风控停止"
                        record.status == PlanHistory.STOPPED -> "已停止"
                        else -> "未完成"
                    },
                    fontSize = MedusaType.Caption,
                    fontWeight = FontWeight.SemiBold,
                    color = tone,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tone.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = dateText(record.startedAt) + " · 用时 " + durationText(record.durationMs())
                    + " · 动作 " + record.actionCount + " 项 · 能量 +" + record.energyDelta + "g"
                    + (if (record.trigger.isNotBlank()) " · " + triggerText(record.trigger) else ""),
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 3.dp)
            )
            if (record.reason.isNotBlank()) {
                Text(
                    text = record.reason,
                    fontSize = MedusaType.Secondary,
                    color = if (completed) MedusaColors.Muted
                    else if (stopped) MedusaColors.Stopped
                    else MedusaColors.Cooldown,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HistoryStatusChips(
    total: Int,
    completed: Int,
    skipped: Int,
    risk: Int,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HistoryStatusChip("全部 $total", selected == "ALL", MedusaColors.GreenDeep) { onSelect("ALL") }
        HistoryStatusChip("完成 $completed", selected == "DONE", MedusaColors.Green) { onSelect("DONE") }
        HistoryStatusChip("跳过 $skipped", selected == "SKIP", Color(0xFF9AA5B5)) { onSelect("SKIP") }
        HistoryStatusChip("风控/未完成 $risk", selected == "RISK", MedusaColors.Stopped) { onSelect("RISK") }
    }
}

@Composable
private fun HistoryStatusChip(text: String, selected: Boolean, tone: Color, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = MedusaType.Caption,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) tone else MedusaColors.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) tone.copy(alpha = 0.13f) else Color(0xFFF2F4F7))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun HistoryEmpty(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
    }
}

@Composable
private fun SkippedSummaryRow(count: Int, groupCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    val tone = Color(0xFF9AA5B5)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(tone, CircleShape))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "已跳过 $count 轮",
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            Text(
                "$groupCount 组重复尝试 · 默认收起",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            if (expanded) "收起" else "展开",
            fontSize = MedusaType.Caption,
            fontWeight = FontWeight.SemiBold,
            color = tone
        )
    }
}

@Composable
private fun SkippedGroupRow(
    target: String,
    roundTime: String,
    records: List<PlanHistory.Record>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val tone = Color(0xFF9AA5B5)
    val latest = records.firstOrNull() ?: return
    Column(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .size(7.dp)
                    .background(tone, CircleShape)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        PlanTargets.labelOf(target) + " · " + roundTime,
                        fontSize = MedusaType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = MedusaColors.Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "×" + records.size,
                        fontSize = MedusaType.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = tone,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tone.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    "最近 " + dateText(latest.startedAt) + " · 用时 " + durationText(latest.durationMs())
                        + (if (latest.reason.isNotBlank()) " · " + latest.reason else ""),
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (expanded) {
                    records.forEach { record ->
                        Text(
                            dateText(record.startedAt) + " · " + triggerText(record.trigger)
                                + " · 用时 " + durationText(record.durationMs())
                                + (if (record.reason.isNotBlank()) " · " + record.reason else ""),
                            fontSize = MedusaType.Caption,
                            color = MedusaColors.Muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            Text(
                if (expanded) "收起" else "展开",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = tone,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

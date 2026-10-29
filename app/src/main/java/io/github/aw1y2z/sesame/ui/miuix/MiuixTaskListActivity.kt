package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ledger.LedgerStatus
import io.github.aw1y2z.sesame.data.ledger.LedgerTask
import io.github.aw1y2z.sesame.data.ledger.OceanTaskLedger
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.data.task.EndpointStateStore
import io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 通用任务账本列表；第一版只接海洋奖励。 */
class MiuixTaskListActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_DOMAIN = "ledgerDomain"
        const val EXTRA_TITLE = "ledgerTitle"
        const val EXTRA_USER_ID = "ledgerUserId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: OceanTaskLedger.DOMAIN
        val title = intent.getStringExtra(EXTRA_TITLE)
            ?: if (domain == TaskLedgerStore.DOMAIN_MANUAL) "待你操作" else "今日任务"
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        ConfigPreload.prepare(userId)
        setAppContent {
            TaskListScreen(this, domain, title, userId)
        }
    }
}

private data class TaskFilter(val key: String, val label: String)

private val taskFilters = listOf(
    TaskFilter("all", "全部"),
    TaskFilter("claimable", "待领"),
    TaskFilter("untouched", "未做"),
    TaskFilter("in_progress", "进行中"),
    TaskFilter("manual", "需手动"),
    TaskFilter("claimed", "已领"),
    TaskFilter("skipped", "已跳过")
)

@Composable
private fun TaskListScreen(
    activity: MiuixTaskListActivity,
    domain: String,
    title: String,
    userId: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("medusa_task_list", Context.MODE_PRIVATE) }
    val syncKey = "sync_$domain"
    var syncOn by remember {
        mutableStateOf(prefs.getBoolean(syncKey, domain != "sports.kinetic" && domain != TaskLedgerStore.DOMAIN_MANUAL))
    }
    var reload by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val manualMode = domain == TaskLedgerStore.DOMAIN_MANUAL
    var filter by remember { mutableStateOf(if (manualMode) "action" else "all") }
    val allTasks = remember(userId, reload, manualMode) {
        if (manualMode) {
            TaskLedgerStore.loadManualTasks(userId ?: "", TaskLedgerStore.today())
        } else {
            TaskLedgerStore.loadTasks(userId ?: "", TaskLedgerStore.today(), domain)
        }
    }
    val actionable = remember(allTasks, manualMode) {
        if (manualMode) allTasks.filter { TaskLedgerStore.isActionableManual(it) } else allTasks
    }
    val summary = remember(userId, reload, manualMode) {
        if (manualMode) null
        else TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), domain)
    }
    val filtered = remember(allTasks, actionable, filter, manualMode) {
        val base = if (manualMode) {
            when (filter) {
                "action" -> actionable
                "blacklist" -> allTasks.filter { !TaskLedgerStore.isActionableManual(it) }
                else -> allTasks
            }
        } else {
            allTasks
        }
        base.filter { matchesFilter(it, filter) }
            .sortedWith(compareBy({ if (it.group == "sign") 0 else 1 }, { statusOrder(it.status) }))
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        val intent = Intent("com.eg.android.AlipayGphone.sesame.ledgerRefresh").apply {
            if (!manualMode) putExtra("domain", domain)
            putExtra("userId", userId)
        }
        context.sendBroadcast(intent)
        scope.launch {
            delay(if (manualMode) 5000 else 1800)
            reload++
            refreshing = false
        }
    }

    fun markDone(task: LedgerTask) {
        val uid = userId ?: return
        if (task.domain.isBlank()) return
        TaskLedgerStore.markManualDone(uid, TaskLedgerStore.today(), task.domain, task.id)
        context.sendBroadcast(
            Intent("com.eg.android.AlipayGphone.sesame.ledgerRefresh").apply {
                putExtra("domain", task.domain)
                putExtra("userId", uid)
            }
        )
        ToastUtil.show(context, "已标记完成，正在复核…")
        scope.launch {
            delay(2500)
            reload++
        }
    }

    fun openTask(task: LedgerTask) {
        if (task.actionUrl.isNotBlank()) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(task.actionUrl))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
        }
        val (key, title) = domainDestination(task.domain)
        context.startActivity(
            Intent(context, MiuixDomainActivity::class.java)
                .putExtra("domainKey", key)
                .putExtra("domainTitle", title)
        )
    }

    LaunchedEffect(Unit) {
        if (syncOn) refresh()
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = title,
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (manualMode) {
                        val byClass = actionable
                            .groupBy { TaskCapabilityRegistry.mechanismLabel(it.mechanism) }
                            .entries.sortedByDescending { it.value.size }
                            .take(4)
                            .joinToString(" · ") { "${it.key} ${it.value.size}" }
                        val failing = EndpointStateStore.failingSummary(3)
                        "待操作 ${actionable.size} 项 · 低优先 ${allTasks.size - actionable.size} 项 · 涉及 ${actionable.map { domainLabel(it.domain) }.distinct().size} 个域" +
                            (if (byClass.isEmpty()) "" else "\n按类：$byClass") +
                            (if (failing.isEmpty()) "" else "\n端点：$failing")
                    } else if (summary == null) {
                        "今日账本未建立"
                    } else {
                        "待处理 ${summary.optInt("pending", 0)} · 需手动 ${summary.optInt("manual", 0)} · 已领 ${summary.optInt("claimed", 0)}"
                    },
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .background(Color(0xFFE6F7EE), RoundedCornerShape(8.dp))
                        .clickable { refresh() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        if (refreshing) "刷新中…" else "刷新",
                        fontSize = 10.sp,
                        color = MedusaColors.GreenDeep
                    )
                }
            }

            if (!manualMode) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "同步任务名称",
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Muted,
                        modifier = Modifier.weight(1f)
                    )
                    TinySwitch(syncOn) { checked ->
                        syncOn = checked
                        prefs.edit().putBoolean(syncKey, checked).apply()
                        if (checked) refresh()
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val filters = if (manualMode) listOf(
                    TaskFilter("action", "待操作"),
                    TaskFilter("blacklist", "低优先"),
                    TaskFilter("all", "全部")
                ) else taskFilters
                filters.forEach { item ->
                    val selected = filter == item.key
                    Box(
                        Modifier
                            .background(
                                if (selected) MedusaColors.GreenDeep else Color(0xFFF1F4F7),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { filter = item.key }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            item.label,
                            fontSize = 9.5.sp,
                            color = if (selected) Color.White else MedusaColors.Ink.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                if (filtered.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (allTasks.isEmpty()) "还没有任务账本，点右上角刷新" else "这个筛选下没有任务",
                            fontSize = MedusaType.Secondary,
                            color = MedusaColors.Muted
                        )
                    }
                } else {
                    filtered.forEach { task ->
                        TaskRow(
                            task = task,
                            manualMode = manualMode,
                            onDone = { markDone(task) },
                            onOpen = { openTask(task) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: LedgerTask,
    manualMode: Boolean,
    onDone: () -> Unit,
    onOpen: () -> Unit
) {
    val color = statusColor(task.status)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE7EDF3), RoundedCornerShape(14.dp))
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                task.title.ifBlank { task.id },
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.Medium,
                color = MedusaColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                task.status.label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
        val details = buildList {
            if (manualMode) add(domainLabel(task.domain))
            if (task.mechanism.isNotEmpty()) {
                val label = TaskCapabilityRegistry.mechanismLabel(task.mechanism)
                val cost = TaskCapabilityRegistry.costLabel(task.costType)
                val endpointState = EndpointStateStore.stateLabel(
                    EndpointStateStore.stateOfTaskEndpoint(task.endpoint)
                )
                val capability = if (cost.isEmpty()) label else "$label · $cost"
                add(if (endpointState.isEmpty()) capability else "$capability · $endpointState")
            }
            if (task.progressText().isNotEmpty()) add("进度 ${task.progressText()}")
            if (task.award.isNotEmpty()) add(task.award)
            if (task.manualReason.isNotEmpty() && task.status != LedgerStatus.CLAIMED) {
                add(reasonLabel(task.manualReason))
            }
        }
        if (details.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Text(
                details.joinToString(" · "),
                fontSize = 9.5.sp,
                color = MedusaColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (manualMode && task.status == LedgerStatus.MANUAL) {
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MedusaColors.GreenDeep)
                        .clickable(onClick = onOpen)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("去处理", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE6F7EE))
                        .clickable(onClick = onDone)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (task.userDoneAt > 0L) "重新标记" else "我已完成",
                        fontSize = 10.sp,
                        color = MedusaColors.GreenDeep,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun domainLabel(domain: String): String = when {
    domain.startsWith("ocean") -> "海洋"
    domain.startsWith("farm") -> "庄园"
    domain.startsWith("forest") -> "森林"
    domain.startsWith("orchard") -> "果园"
    domain.startsWith("species") -> "物种"
    domain == "golden" -> "金豆"
    domain == "sports.kinetic" -> "运动"
    else -> domain.ifBlank { "其他" }
}

private fun domainDestination(domain: String): Pair<String, String> = when {
    domain.startsWith("ocean") -> "ocean" to "神奇海洋"
    domain.startsWith("farm") -> "farm" to "蚂蚁庄园"
    domain.startsWith("forest") -> "forest" to "蚂蚁森林"
    domain.startsWith("orchard") -> "orchard" to "芭芭农场"
    domain.startsWith("species") -> "species" to "神奇物种"
    domain == "golden" -> "golden" to "金豆夺宝"
    domain == "sports.kinetic" -> "sports" to "医疗健康·运动"
    else -> "forest" to "蚂蚁森林"
}

private fun matchesFilter(task: LedgerTask, filter: String): Boolean = when (filter) {
    "claimable" -> task.status == LedgerStatus.CLAIMABLE
    "untouched" -> task.status == LedgerStatus.UNTOUCHED
    "in_progress" -> task.status == LedgerStatus.IN_PROGRESS
    "manual" -> task.status == LedgerStatus.MANUAL
    "claimed" -> task.status == LedgerStatus.CLAIMED
    "skipped" -> task.status == LedgerStatus.SKIPPED
    else -> true
}

private fun statusOrder(status: LedgerStatus): Int = when (status) {
    LedgerStatus.CLAIMABLE -> 0
    LedgerStatus.IN_PROGRESS -> 1
    LedgerStatus.UNTOUCHED -> 2
    LedgerStatus.MANUAL -> 3
    LedgerStatus.CLAIMED -> 4
    LedgerStatus.SKIPPED -> 5
}

private fun statusColor(status: LedgerStatus): Color = when (status) {
    LedgerStatus.CLAIMED -> MedusaColors.Green
    LedgerStatus.CLAIMABLE -> MedusaColors.Gold
    LedgerStatus.IN_PROGRESS -> MedusaColors.Blue
    LedgerStatus.MANUAL -> Color(0xFFE57373)
    LedgerStatus.SKIPPED -> MedusaColors.Muted
    LedgerStatus.UNTOUCHED -> MedusaColors.Ink.copy(alpha = 0.6f)
}

@Composable
private fun TinySwitch(checked: Boolean, onToggle: (Boolean) -> Unit) {
    MedusaPillSwitch(checked = checked, onCheckedChange = onToggle)
}

private fun reasonLabel(reason: String): String = when (reason) {
    "RPC_UNSUPPORTED" -> "接口不支持"
    "REAL_GAMEPLAY" -> "需真实游戏"
    "TRANSACTION" -> "需缴费/履约"
    "CLAIM_FAILED" -> "领奖未确认"
    "AUTO_FAILED_3X" -> "连续失败"
    "PRESET_BLACKLIST" -> "预置黑名单"
    "BLACKLISTED" -> "已拉黑"
    "BOUNTY_CARD" -> "悬赏卡"
    "SERVER_GONE" -> "服务端已下线"
    "EXTERNAL_JUMP" -> "需外跳"
    "AUTH_REQUIRED" -> "需授权"
    "USER_DONE_PENDING" -> "已标记，待复核"
    "USER_DONE_UNCONFIRMED" -> "复核未通过，请手动检查"
    "REAL_ACTION" -> "需真实操作"
    else -> reason
}

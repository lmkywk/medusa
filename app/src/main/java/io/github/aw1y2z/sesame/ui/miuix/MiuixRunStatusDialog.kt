package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.data.plan.DefaultSchedule
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanRuntime
import io.github.aw1y2z.sesame.util.RiskGuard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import java.util.Calendar

/** 3.0-alpha：运行状态页。只呈现状态、建议和记录，所有操作由用户决定。 */
@Composable
internal fun RunStatusDialog(
    userId: String?,
    runtime: PlanRuntime,
    now: Long,
    riskPaused: Boolean,
    riskGlobal: Boolean,
    riskScope: String,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onRerun: (String) -> Unit
) {
    var showStopConfirm by remember { mutableStateOf(false) }
    val target = runtime.lastTarget.takeIf { it.isNotBlank() }
    val remaining = manualCooldownRemaining(runtime, target, now)
    val visual = resolveRunVisual(runtime, now, riskPaused, remaining)
    val policy = if (AppConfig.INSTANCE.riskPolicy == AppConfig.RISK_POLICY_NOTIFY_ONLY) {
        "仅提示（用户选择继续）"
    } else {
        "停止当前域并提示"
    }
    val pausedScopes = RiskGuard.getPausedScopes()
    val running = PlanRuntime.RUNNING == runtime.state &&
        now - runtime.updatedAt < 35L * 60L * 1000L
    val report = remember(userId) {
        TaskLedgerStore.loadReport(userId ?: "", TaskLedgerStore.today())
    }
    val reportDomains = report?.optJSONObject("domains")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        SideEffect {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            dialogWindow.setDimAmount(0f)
            dialogWindow.statusBarColor = MedusaColors.PageBg.toArgb()
            dialogWindow.navigationBarColor = MedusaColors.PageBg.toArgb()
            WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .background(MedusaColors.PageBg)
                .navigationBarsPadding()
        ) {
            LogTopBar(title = "运行状态", onBack = onDismiss)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF101633), Color(0xFF173A52))))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(visual.tone, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            visual.label,
                            fontSize = MedusaType.Secondary,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            PlanRuntime.RUNNING == runtime.state ->
                                PlanTargetsLabel(runtime.target) + " · " + runtime.roundTime
                            runtime.state == PlanRuntime.RISK_STOPPED || runtime.state == PlanRuntime.STOPPED ->
                                runtime.reason.ifBlank { "本轮已停止" }
                            else -> if (target != null) ManualTargetLabel(target) else "当前没有正在运行的任务"
                        },
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            PlanRuntime.RUNNING == runtime.state ->
                                "已运行 " + formatElapsed(now - runtime.startedAt) +
                                    (if (runtime.runId.isNotBlank()) " · " + runtime.runId else "")
                            else ->
                                "最近一次 " + statusText(runtime.lastStatus) +
                                    (if (runtime.lastDurationMs > 0) " · 用时 " + formatElapsed(runtime.lastDurationMs) else "")
                        },
                        fontSize = MedusaType.Secondary,
                        color = MedusaColors.OnDarkSecondary
                    )
                }
                Spacer(Modifier.height(16.dp))

                SmallTitle(text = "默认编排")
                CardColumn {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("时间", modifier = Modifier.width(56.dp), fontSize = 10.sp, color = MedusaColors.Muted)
                            Text("内容", modifier = Modifier.weight(1f), fontSize = 10.sp, color = MedusaColors.Muted)
                            Text("方式", modifier = Modifier.width(36.dp), fontSize = 10.sp, color = MedusaColors.Muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        DefaultSchedule.entries().forEach { entry ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    entry.time,
                                    modifier = Modifier.width(56.dp),
                                    fontSize = MedusaType.Secondary,
                                    color = MedusaColors.Ink
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.label,
                                        fontSize = MedusaType.Secondary,
                                        fontWeight = FontWeight.Medium,
                                        color = MedusaColors.Ink
                                    )
                                    Text(
                                        entry.note,
                                        fontSize = 9.5.sp,
                                        color = MedusaColors.Muted
                                    )
                                }
                                Text(
                                    modeText(entry.mode),
                                    modifier = Modifier.width(36.dp),
                                    fontSize = 9.5.sp,
                                    color = modeColor(entry.mode)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                SmallTitle(text = "今日账本")
                CardColumn {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (report == null) {
                            Text(
                                "今日尚未同步；00:05 会自动完成全量账本。",
                                fontSize = MedusaType.Body,
                                color = MedusaColors.Muted
                            )
                        } else {
                            Text(
                                "同步 " + timeText(report.optLong("syncedAt", 0L)) +
                                    " · 任务 " + report.optInt("total", 0) +
                                    " · 待处理 " + report.optInt("pending", 0) +
                                    " · 需手动 " + report.optInt("manual", 0),
                                fontSize = MedusaType.Secondary,
                                color = MedusaColors.Muted
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth()) {
                                Text("域", modifier = Modifier.weight(1f), fontSize = 10.sp, color = MedusaColors.Muted)
                                Text("待处理", modifier = Modifier.width(44.dp), fontSize = 10.sp, color = MedusaColors.Muted)
                                Text("需手动", modifier = Modifier.width(44.dp), fontSize = 10.sp, color = MedusaColors.Muted)
                                Text("新增", modifier = Modifier.width(32.dp), fontSize = 10.sp, color = MedusaColors.Muted)
                            }
                            Spacer(Modifier.height(4.dp))
                            val keys = ArrayList<String>()
                            val iterator = reportDomains?.keys()
                            while (iterator != null && iterator.hasNext()) {
                                keys.add(iterator.next())
                            }
                            keys.sort()
                            keys.forEach { key ->
                                val item = reportDomains?.optJSONObject(key) ?: return@forEach
                                val pending = item.optInt("pending", 0)
                                val manual = item.optInt("manual", 0)
                                val added = item.optInt("newCount", 0)
                                val removed = item.optInt("removedCount", 0)
                                if (pending == 0 && manual == 0 && added == 0 && removed == 0) {
                                    return@forEach
                                }
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(
                                        ledgerDomainLabel(key),
                                        modifier = Modifier.weight(1f),
                                        fontSize = MedusaType.Secondary,
                                        color = MedusaColors.Ink
                                    )
                                    Text("$pending", modifier = Modifier.width(44.dp), fontSize = MedusaType.Secondary, color = MedusaColors.Gold)
                                    Text("$manual", modifier = Modifier.width(44.dp), fontSize = MedusaType.Secondary, color = if (manual > 0) MedusaColors.Stopped else MedusaColors.Muted)
                                    Text(
                                        if (added > 0) "+$added" else if (removed > 0) "-$removed" else "—",
                                        modifier = Modifier.width(32.dp),
                                        fontSize = MedusaType.Secondary,
                                        color = if (added > 0) MedusaColors.Green else MedusaColors.Muted
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                SmallTitle(text = "节律建议")
                CardColumn {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (remaining > 0L) "还可以再等 " + formatRemaining(remaining) else "当前不在建议等待窗口",
                            fontSize = MedusaType.CardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remaining > 0L) MedusaColors.Cooldown else MedusaColors.Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (remaining > 0L) {
                                "这是建议，不是限制：仍可手动执行，计划和手动执行由你决定。"
                            } else {
                                "上次手动执行：" + timeText(runtime.lastManualAt)
                            },
                            fontSize = MedusaType.Body,
                            color = MedusaColors.Muted
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                SmallTitle(text = "风控")
                CardColumn {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            when {
                                riskGlobal -> "账号级风控暂停中"
                                riskPaused -> "当前页面风控暂停中"
                                else -> "当前没有风控暂停"
                            },
                            fontSize = MedusaType.CardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = if (riskPaused) MedusaColors.Cooldown else MedusaColors.Ink
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1009 后：" + policy,
                            fontSize = MedusaType.Body,
                            color = MedusaColors.Muted
                        )
                        if (pausedScopes.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "暂停页面：" + pausedScopes.joinToString("、") { ManualTargetLabel(it) },
                                fontSize = MedusaType.Secondary,
                                color = MedusaColors.Cooldown
                            )
                        }
                        if (RiskGuard.getLastSignalAt() > 0L) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "最近信号：" + timeText(RiskGuard.getLastSignalAt()) +
                                    " · " + RiskGuard.getLastSignalReason().ifBlank { "风控" } +
                                    (RiskGuard.getLastSignalMethod().takeIf { it.isNotBlank() }?.let { " · " + it } ?: ""),
                                fontSize = MedusaType.Secondary,
                                color = MedusaColors.Muted
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                SmallTitle(text = "最近运行")
                CardColumn {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (runtime.lastStatus.isBlank()) "暂无运行记录" else statusText(runtime.lastStatus),
                            fontSize = MedusaType.CardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = if (runtime.lastStatus == PlanHistory.RISK_STOPPED || runtime.lastStatus == PlanHistory.STOPPED) {
                                MedusaColors.Stopped
                            } else {
                                MedusaColors.Ink
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (runtime.lastStatus.isBlank()) {
                                "执行后会在这里显示结果、动作、能量和原因。"
                            } else {
                                (if (runtime.lastTarget.isNotBlank()) ManualTargetLabel(runtime.lastTarget) else "—") +
                                    " · 动作 " + runtime.lastActionCount + " 项 · 能量 +" + runtime.lastEnergyDelta + "g" +
                                    " · " + triggerText(runtime.lastPlanId, runtime.trigger)
                            },
                            fontSize = MedusaType.Body,
                            color = MedusaColors.Muted
                        )
                        if (runtime.reason.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(runtime.reason, fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
                        }
                        if (runtime.notice.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(runtime.notice, fontSize = MedusaType.Secondary, color = MedusaColors.Cooldown, maxLines = 4)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                CardColumn {
                    if (running) {
                        ArrowPreference(
                            title = "停止当前运行",
                            summary = "关闭写闸门，取消已启动的模块；下一次运行自动恢复",
                            onClick = { showStopConfirm = true }
                        )
                    }
                    if (riskPaused) {
                        ArrowPreference(
                            title = when {
                                riskGlobal -> "恢复账号"
                                riskScope.isNotBlank() -> "恢复此页面"
                                else -> "恢复风控暂停"
                            },
                            summary = when {
                                riskGlobal -> "清除账号级风控暂停；恢复后仍可手动执行"
                                riskScope.isNotBlank() -> "只清除「" + ManualTargetLabel(riskScope) + "」的暂停，其他页面不受影响"
                                else -> "清除风控暂停；恢复后仍可手动执行"
                            },
                            onClick = onResume
                        )
                    }
                    if (target != null) {
                        ArrowPreference(
                            title = "重新执行",
                            summary = "按上次目标再跑一轮；会记录为手动覆盖建议",
                            onClick = { onRerun(target) }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showStopConfirm) {
        ConfirmDialog(
            title = "停止当前运行",
            text = "停止后不会再有新的写 RPC；下次执行时自动恢复。确定停止吗？",
            confirmText = "停止",
            onConfirm = {
                showStopConfirm = false
                onStop()
            },
            onDismiss = { showStopConfirm = false }
        )
    }
}

private fun PlanTargetsLabel(target: String): String =
    io.github.aw1y2z.sesame.data.plan.PlanTargets.labelOf(target)

private fun ManualTargetLabel(target: String): String =
    io.github.aw1y2z.sesame.data.plan.PlanTargets.labelOf(target)

private fun statusText(status: String): String = when (status) {
    PlanHistory.COMPLETED -> "已完成"
    PlanHistory.SKIPPED -> "已跳过"
    PlanHistory.RUNNING -> "执行中"
    PlanHistory.RISK_STOPPED -> "风控停止"
    PlanHistory.STOPPED -> "已停止"
    PlanHistory.INCOMPLETE -> "未完成"
    else -> "未完成"
}

private fun triggerText(planId: String, trigger: String): String = when {
    trigger == PlanHistory.TRIGGER_MANUAL -> "手动"
    trigger == PlanHistory.TRIGGER_FALLBACK -> "兜底"
    trigger == PlanHistory.TRIGGER_CATCHUP -> "补跑"
    trigger == PlanHistory.TRIGGER_WINDOW -> "时段巡检"
    planId.startsWith("manual-") -> "手动"
    else -> "计划"
}

private fun modeText(mode: String): String = when (mode) {
    DefaultSchedule.MODE_SYSTEM -> "系统"
    "COLLECT" -> "收集"
    else -> "一次"
}

private fun modeColor(mode: String): Color = when (mode) {
    DefaultSchedule.MODE_SYSTEM -> MedusaColors.Blue
    "COLLECT" -> MedusaColors.Green
    else -> MedusaColors.Gold
}

private fun ledgerDomainLabel(key: String): String = when (key) {
    "ocean" -> "海洋·奖励"
    "ocean.fish" -> "海洋·摸鱼"
    "farm" -> "庄园·饲料"
    "farm.draw" -> "庄园·抽抽乐"
    "forest.reward" -> "森林·奖励"
    "forest.park" -> "森林·乐园"
    "forest.patrol" -> "森林·巡护"
    "species.task" -> "物种·任务"
    "orchard.task" -> "果园·肥料"
    "orchard.park" -> "果园·乐园"
    "orchard.draw" -> "果园·寻宝"
    "orchard.challenge" -> "果园·挑战"
    else -> key
}

private fun timeText(time: Long): String {
    if (time <= 0L) return "—"
    val c = Calendar.getInstance().apply { timeInMillis = time }
    return String.format(
        java.util.Locale.CHINA, "%02d:%02d",
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
    )
}

private fun formatElapsed(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes < 60) {
        String.format(java.util.Locale.CHINA, "%02d:%02d", minutes, rest)
    } else {
        String.format(java.util.Locale.CHINA, "%d 小时 %02d 分", minutes / 60, minutes % 60)
    }
}

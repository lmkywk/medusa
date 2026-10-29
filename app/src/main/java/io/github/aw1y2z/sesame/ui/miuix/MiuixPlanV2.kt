package io.github.aw1y2z.sesame.ui.miuix

import android.app.AlertDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.plan.Plan
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanRuntime
import io.github.aw1y2z.sesame.data.plan.PlanStore
import io.github.aw1y2z.sesame.data.plan.PlanTargetLedgers
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.data.ledger.TaskProgress
import io.github.aw1y2z.sesame.util.RiskGuard
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.delay
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private data class NextRun(val label: String, val time: String, val tomorrow: Boolean, val plan: Plan? = null)

private data class PlanBucket(val plan: Plan, val today: Boolean, val nextMinute: Int?)

/** 计划页的一条：独立计划，或一个组（日常）。 */
private sealed interface PlanEntry {
    data class Single(val bucket: PlanBucket) : PlanEntry
    data class Group(
        val id: String,
        val name: String,
        val members: List<PlanBucket>
    ) : PlanEntry
}

private fun bucketKey(bucket: PlanBucket): Int =
    if (bucket.today) bucket.nextMinute ?: Int.MAX_VALUE
    else 1440 + (bucket.nextMinute ?: Int.MAX_VALUE)

private fun entryKey(entry: PlanEntry): Int = when (entry) {
    is PlanEntry.Single -> bucketKey(entry.bucket)
    is PlanEntry.Group -> entry.members.minOfOrNull { bucketKey(it) } ?: Int.MAX_VALUE
}

private fun PlanEntry.isEnabledEntry(): Boolean = when (this) {
    is PlanEntry.Single -> bucket.plan.isEnabled
    is PlanEntry.Group -> members.any { it.plan.isEnabled }
}

private fun planLabel(target: String): String =
    TaskCatalog.domainLabelOf(target) + "·" + PlanTargets.labelOf(target)

/** 计划卡上的任务进度：运行中由 progress.json 实时推进，空闲时为账本快照。 */
private data class PlanTaskProgress(
    val done: Int,
    val total: Int,
    val title: String,
    val status: String
) {
    val percent: Int get() = if (total <= 0) 0 else ((done * 100f / total).roundToInt()).coerceIn(0, 100)
}

/** 计划卡唯一进度条：时间优先，其次任务数，最后执行轮次。 */
private data class PlanProgressSpec(
    val fraction: Float,
    val label: String,
    val rightText: String,
    val hint: String?
)

private fun percentText(fraction: Float): String =
    "${(fraction.coerceIn(0f, 1f) * 100f).toInt()}%"

@Composable
fun PlanTabV2(activity: MiuixMainActivity) {
    val context = LocalContext.current
    val userId = activity.currentAccountId
    var refresh by remember { mutableIntStateOf(0) }
    var runtime by remember { mutableStateOf(PlanRuntime.load(userId ?: "")) }
    var history by remember(userId, activity.planRevision) {
        mutableStateOf(PlanHistory.load(userId ?: "").sortedByDescending { it.startedAt })
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingManual by remember { mutableStateOf(false) }
    var manualAt by remember { mutableLongStateOf(0L) }
    var manualIgnored by remember { mutableStateOf(false) }
    var showRestoreDefaults by remember { mutableStateOf(false) }
    var showRunStatus by remember { mutableStateOf(false) }
    var pendingCooldownPlan by remember { mutableStateOf<Plan?>(null) }
    var showCooldownConfirm by remember { mutableStateOf(false) }
    var pendingRiskRunTarget by remember { mutableStateOf<String?>(null) }
    var pendingRiskRunIgnore by remember { mutableStateOf(false) }
    var showRiskResumeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(runtime.noticeAt) { maybeShowRunNotice(context, runtime) }

    val plans = remember(userId, activity.planRevision, refresh) {
        PlanStore.load(userId ?: "").sortedBy { it.roundTimes.firstOrNull() ?: "99:99" }
    }
    val planGroupMap = remember(userId, activity.planRevision, refresh) {
        TaskGroupStore.loadPlanGroups(userId ?: "")
    }
    val planTaskMap = remember(userId, activity.planRevision, refresh) {
        TaskGroupStore.loadPlanTasks(userId ?: "")
    }
    val customGroups = remember(userId, activity.planRevision, refresh) {
        TaskGroupStore.loadCustomGroups(userId ?: "")
    }
    val groupNames = remember(userId, activity.planRevision, refresh) {
        TaskGroupStore.loadGroupNames(userId ?: "")
    }

    LaunchedEffect(userId, activity.planRevision, refresh) {
        while (true) {
            runtime = PlanRuntime.load(userId ?: "")
            history = PlanHistory.load(userId ?: "").sortedByDescending { it.startedAt }
            now = System.currentTimeMillis()
            // 手动执行只写入支付宝注入进程的运行快照；这里用快照是否在 10 秒内变成 RUNNING
            // 来判断广播到底有没有被接住，否则用户点了按钮完全不知道有没有生效。
            if (pendingManual) {
                // 3.0：开始时会先写一条 RUNNING 历史；短任务在两次轮询之间结束也能被认领。
                val accepted = runtime.state == PlanRuntime.RUNNING ||
                    history.any { it.trigger == PlanHistory.TRIGGER_MANUAL && it.startedAt >= manualAt }
                if (accepted) {
                    pendingManual = false
                } else if (now - manualAt > 10_000L) {
                    pendingManual = false
                    manualIgnored = true
                }
            }
            // 运行中任务切换较快，1 秒跟随一次；空闲时保持 2 秒，避免无意义读盘。
            delay(if (runtime.state == PlanRuntime.RUNNING) 1000 else 2000)
        }
    }

    val next = remember(plans, now, runtime.nextTarget, runtime.nextTime, runtime.updatedAt) {
        val hostNext = runtime.nextTarget.takeIf {
            it.isNotBlank() && runtime.nextTime.isNotBlank() && now - runtime.updatedAt < 10L * 60L * 1000L
        }?.let {
            val minute = timeToMinutes(runtime.nextTime)
            val nowMinute = Calendar.getInstance().let {
                it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
            }
            NextRun(runtime.nextTarget, runtime.nextTime, minute == null || minute <= nowMinute)
        }
        nextRun(plans) ?: hostNext
    }
    val focusTarget = runtime.target.takeIf { it.isNotBlank() }
        ?: next?.plan?.target
        ?: plans.firstOrNull { it.isEnabled }?.target
        ?: ""
    val riskGlobal = RiskGuard.isGlobalPaused()
    val riskScope = focusTarget.takeIf {
        !riskGlobal && it.isNotBlank() && RiskGuard.isTargetPaused(it)
    } ?: ""
    val riskPaused = riskGlobal || riskScope.isNotBlank()
    val riskReason = if (riskGlobal) {
        RiskGuard.getReason()
    } else if (riskScope.isNotBlank()) {
        RiskGuard.getScopeReason(riskScope)
    } else {
        ""
    }
    fun pauseAll() {
        AlertDialog.Builder(context)
            .setTitle("暂停全部自动化")
            .setMessage("暂停后不会发起任何任务 RPC，只读状态仍可手动刷新。确认暂停？")
            .setPositiveButton("暂停") { _, _ ->
                RiskGuard.pauseAll("手动暂停全部自动化")
                context.sendBroadcast(
                    Intent("com.eg.android.AlipayGphone.sesame.reloadConfig")
                        .setPackage("com.eg.android.AlipayGphone")
                )
                runtime = PlanRuntime.load(userId ?: "")
                refresh++
                ToastUtil.show(context, "已暂停全部自动化")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun resumeRisk() {
        val manualPause = RiskGuard.isManualPause()
        val interrupted = if (!manualPause) {
            history.firstOrNull {
                (it.status == PlanHistory.INCOMPLETE || it.status == PlanHistory.RISK_STOPPED) &&
                    it.target.isNotBlank() && !it.target.endsWith(".external") &&
                    System.currentTimeMillis() - it.startedAt < 2L * 60L * 60L * 1000L
            }
        } else {
            null
        }
        if (riskGlobal) {
            RiskGuard.resume()
        } else if (riskScope.isNotBlank()) {
            RiskGuard.resumeScope(riskScope)
        }
        // 风控暂停期间宿主可能把被闸门拦下的探活当成登录失败，留下 offline 标记；
        // 恢复时一并清除，否则后面的请求仍会全部返回 null。
        context.sendBroadcast(
            Intent("com.eg.android.AlipayGphone.sesame.reloadConfig")
                .setPackage("com.eg.android.AlipayGphone")
                .putExtra("clearOffline", true)
        )
        runtime = PlanRuntime.load(userId ?: "")
        if (interrupted != null) {
            val time = java.text.SimpleDateFormat("HH:mm", Locale.CHINA)
                .format(java.util.Date())
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                context.sendMedusaBroadcast(
                    Intent("com.eg.android.AlipayGphone.sesame.planRun")
                        .setPackage("com.eg.android.AlipayGphone")
                        .putExtra("target", interrupted.target)
                        .putExtra("roundTime", time)
                        .putExtra("ignoreCooldown", true)
                )
            }, 1_200L)
            ToastUtil.show(
                context,
                "验证已恢复，正在补跑「" + PlanTargets.labelOf(interrupted.target) + "」的未完成项"
            )
        } else {
            ToastUtil.show(
                context,
                if (manualPause) "已恢复全部自动化"
                else if (riskGlobal) "已恢复账号，下一轮开始执行"
                else "已恢复「" + PlanTargets.labelOf(riskScope) + "」，下一轮开始执行"
            )
        }
    }
    val lastByPlan = remember(history) {
        history.groupBy { it.planId }.mapValues { it.value.firstOrNull() }
    }
    val planConflicts = remember(plans) { collectPlanConflicts(plans) }
    // 任务进度只读当日快照 + 执行覆盖层；运行中每秒刷新，空闲 4 秒，避免无意义读盘。
    val progressBucket = now / if (runtime.state == PlanRuntime.RUNNING) 1_000L else 4_000L
    val ledgerSnapshot = remember(userId, activity.planRevision, progressBucket) {
        if (userId.isNullOrEmpty()) null
        else TaskLedgerStore.loadSnapshot(userId, TaskLedgerStore.today())
    }
    val resolvedOverlay = remember(userId, activity.planRevision, progressBucket) {
        if (userId.isNullOrEmpty()) null
        else TaskLedgerStore.loadResolved(userId, TaskLedgerStore.today())
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val picked = result.data?.getStringArrayExtra(MiuixTaskPickerActivity.EXTRA_RESULT)
            if (!picked.isNullOrEmpty()) {
                context.startActivity(
                    Intent(context, MiuixPlanComposeActivity::class.java)
                        .putExtra(MiuixPlanComposeActivity.EXTRA_USER_ID, userId)
                        .putExtra(MiuixPlanComposeActivity.EXTRA_TARGETS, picked)
                )
            }
        }
    }
    fun openTaskPicker() {
        if (!activity.storageWritable || userId == null) {
            ToastUtil.show(context, "请先授予「所有文件访问权限」并登录支付宝")
            return
        }
        pickerLauncher.launch(
            Intent(context, MiuixTaskPickerActivity::class.java)
                .putExtra(MiuixTaskPickerActivity.EXTRA_USER_ID, userId)
                .putExtra(MiuixTaskPickerActivity.EXTRA_SELECTED, emptyArray<String>())
        )
    }
    fun setAllEnabled(enabled: Boolean) {
        val all = PlanStore.load(userId ?: "").map { plan ->
            Plan.fromJson(plan.toJson()).apply { setEnabled(enabled) }
        }
        if (!PlanStore.save(userId ?: "", all)) {
            ToastUtil.show(context, "保存失败，请检查文件访问权限")
            return
        }
        context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
        refresh++
        ToastUtil.show(context, if (enabled) "已启用全部计划" else "已暂停全部计划")
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("计划", fontSize = MedusaType.PageTitle, fontWeight = FontWeight.Bold, color = MedusaColors.Ink, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        NewPlanLink(enabled = activity.storageWritable && userId != null) { openTaskPicker() }
        Spacer(Modifier.width(6.dp))
        RunStatusLink { showRunStatus = true }
        Spacer(Modifier.width(6.dp))
        PlanOverflowMenu(
            onSuspendAll = { setAllEnabled(false) },
            onResumeAll = { setAllEnabled(true) },
            onRestoreDefaults = { showRestoreDefaults = true }
        )
    }
    val testModeState = remember(userId, activity.planRevision, refresh) {
        TestModeStore.load(userId ?: "")
    }
    if (testModeState.enabled) {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MedusaColors.Green.copy(alpha = 0.08f))
                .clickable {
                    context.startActivity(
                        Intent(context, MiuixTestModeActivity::class.java)
                            .putExtra(MiuixTestModeActivity.EXTRA_USER_ID, userId)
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "测试模式进行中",
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.GreenDeep
                )
                val done = testModeState.items.count { it.status == TestModeStore.STATUS_DONE }
                Text(
                    text = "默认计划已暂停 · 已完成 " + done + "/" + testModeState.items.size,
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = "打开清单 ›",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.GreenDeep
            )
        }
    }
    Spacer(Modifier.height(14.dp))

    val isRunning = runtime.state == PlanRuntime.RUNNING &&
        now - runtime.updatedAt < 35L * 60L * 1000L
    val currentProgress = remember(runtime.target, ledgerSnapshot, resolvedOverlay) {
        taskProgressFor(ledgerSnapshot, resolvedOverlay, runtime.target)
    }
    PlanRuntimeCard(
        runtime = runtime,
        next = next,
        riskPaused = riskPaused,
        riskReason = riskReason,
        now = now,
        plans = plans,
        history = history,
        progress = currentProgress,
        onPause = { pauseAll() },
        onResume = { resumeRisk() }
    )
    if (isRunning) {
        Spacer(Modifier.height(8.dp))
        CurrentPlanDetail(
            runtime = runtime,
            progress = currentProgress,
            now = now
        )
    }
    Spacer(Modifier.height(12.dp))

    if (!activity.storageWritable) {
        PlanNotice("需要「所有文件访问权限」才能保存计划。", MedusaColors.Warning)
        Spacer(Modifier.height(10.dp))
    }
    if (userId == null) {
        PlanNotice("尚未识别支付宝账号，请先打开并登录支付宝。", MedusaColors.Warning)
        Spacer(Modifier.height(10.dp))
    }
    if (manualIgnored) {
        PlanNotice(
            "支付宝没有接住这次执行请求：请确认支付宝正在运行（未划掉后台），再点一次。",
            MedusaColors.Warning
        )
        Spacer(Modifier.height(10.dp))
    }
    if (planConflicts.isNotEmpty()) {
        PlanConflictNotice(planConflicts)
        Spacer(Modifier.height(10.dp))
    }

    val running = runtime.state == PlanRuntime.RUNNING && now - runtime.updatedAt < 35L * 60L * 1000L
    val manualTarget = next?.plan?.target ?: runtime.lastTarget.takeIf { it.isNotBlank() }
    val manualRemaining = remember(runtime, now, manualTarget) {
        manualCooldownRemaining(runtime, manualTarget, now)
    }
    if (manualRemaining > 0L && !riskPaused && !running) {
        PlanNotice(
            "手动执行建议再等 " + formatRemaining(manualRemaining) + "；计划到点仍会执行，立即执行仍可用。",
            MedusaColors.Cooldown
        )
        Spacer(Modifier.height(10.dp))
    }
    val togglePlan: (Plan, Boolean) -> Unit = { plan, enabled ->
        val all = PlanStore.load(userId ?: "")
        all.firstOrNull { it.id == plan.id }?.setEnabled(enabled)
        PlanStore.save(userId ?: "", all)
        context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
        refresh++
    }
    fun sendTargetRunRaw(target: String, ignoreCooldown: Boolean) {
        val calendar = Calendar.getInstance()
        val time = String.format(
            Locale.CHINA, "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)
        )
        context.sendMedusaBroadcast(
            Intent("com.eg.android.AlipayGphone.sesame.planRun")
                .setPackage("com.eg.android.AlipayGphone")
                .putExtra("target", target)
                .putExtra("roundTime", time)
                .putExtra("ignoreCooldown", ignoreCooldown)
        )
        pendingManual = true
        manualAt = System.currentTimeMillis()
        manualIgnored = false
        ToastUtil.show(
            context,
            if (ignoreCooldown) "已按你的选择发起「" + PlanTargets.labelOf(target) + "」"
            else "已发起「" + PlanTargets.labelOf(target) + "」执行"
        )
        refresh++
    }

    fun sendTargetRun(target: String, ignoreCooldown: Boolean) {
        if (RiskGuard.isGlobalPaused()) {
            ToastUtil.show(
                context,
                if (RiskGuard.isManualPause()) "已暂停全部自动化，请先点右上角恢复"
                else "账号级风控暂停，请先恢复后再执行"
            )
            return
        }
        if (RiskGuard.isTargetPaused(target)) {
            // 3.9.0：1009 现在是域冷却，不再“本域今日暂停”；给用户一次明确的单轮恢复入口。
            pendingRiskRunTarget = target
            pendingRiskRunIgnore = ignoreCooldown
            showRiskResumeConfirm = true
            return
        }
        sendTargetRunRaw(target, ignoreCooldown)
    }

    val runPlanNow: (Plan) -> Unit = { plan ->
        val remaining = manualCooldownRemaining(runtime, plan.target, System.currentTimeMillis())
        if (remaining > 0L) {
            pendingCooldownPlan = plan
            showCooldownConfirm = true
        } else {
            sendTargetRun(plan.target, false)
        }
    }
    val editPlan: (Plan) -> Unit = { plan ->
        context.startActivity(
            Intent(context, MiuixPlanEditActivity::class.java)
                .putExtra(MiuixPlanEditActivity.EXTRA_USER_ID, userId)
                .putExtra(MiuixPlanEditActivity.EXTRA_PLAN_ID, plan.id)
        )
    }
    val deletePlan: (Plan) -> Unit = { plan ->
        AlertDialog.Builder(context)
            .setMessage("删除「" + PlanTargets.labelOf(plan.target) + "」计划？")
            .setPositiveButton("删除") { _, _ ->
                val all = PlanStore.load(userId ?: "").filter { it.id != plan.id }
                PlanStore.save(userId ?: "", all)
                TaskGroupStore.setPlanGroup(userId ?: "", plan.id, null)
                context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
                refresh++
            }
            .setNegativeButton("取消", null)
            .show()
    }
    val editGroup: (String) -> Unit = { groupId ->
        context.startActivity(
            Intent(context, MiuixPlanComposeActivity::class.java)
                .putExtra(MiuixPlanComposeActivity.EXTRA_USER_ID, userId)
                .putExtra(MiuixPlanComposeActivity.EXTRA_GROUP_ID, groupId)
        )
    }
    val toggleGroup: (List<Plan>, Boolean) -> Unit = { members, enabled ->
        if (members.isNotEmpty()) {
            val ids = members.map { it.id }.toSet()
            val all = PlanStore.load(userId ?: "")
            all.forEach { plan -> if (plan.id in ids) plan.setEnabled(enabled) }
            PlanStore.save(userId ?: "", all)
            context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
            refresh++
        }
    }
    val copyGroup: (String, List<Plan>) -> Unit = { groupId, members ->
        val name = groupNames[groupId].orEmpty().ifBlank { "组" }
        val newId = java.util.UUID.randomUUID().toString()
        val all = PlanStore.load(userId ?: "")
        val copies = members.map { member ->
            Plan.fromJson(member.toJson()).apply {
                setId(java.util.UUID.randomUUID().toString())
                setGroupId(newId)
            }
        }
        all.addAll(copies)
        if (PlanStore.save(userId ?: "", all)) {
            TaskGroupStore.saveGroupName(userId ?: "", newId, name + " 副本")
            context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
            ToastUtil.show(context, "已复制「$name」")
            refresh++
        } else {
            ToastUtil.show(context, "保存失败，请检查文件访问权限")
        }
    }
    val deleteGroup: (String, List<Plan>) -> Unit = { groupId, members ->
        val name = groupNames[groupId].orEmpty().ifBlank { "组" }
        AlertDialog.Builder(context)
            .setMessage("删除「$name」整组？组内 ${members.size} 项任务会一并删除。")
            .setPositiveButton("删除") { _, _ ->
                val ids = members.map { it.id }.toSet()
                val all = PlanStore.load(userId ?: "").filterNot { it.id in ids }
                PlanStore.save(userId ?: "", all)
                TaskGroupStore.removeGroupName(userId ?: "", groupId)
                context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
                ToastUtil.show(context, "已删除「$name」")
                refresh++
            }
            .setNegativeButton("取消", null)
            .show()
    }

    val todayHistory = remember(history) {
        val now = java.util.Calendar.getInstance()
        history.filter {
            it.status != PlanHistory.RUNNING &&
            run {
                val c = java.util.Calendar.getInstance().apply { timeInMillis = it.startedAt }
                c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                    c.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
            }
        }
    }
    val completedHistory = remember(todayHistory) {        todayHistory.filter { it.status == PlanHistory.COMPLETED }.sortedByDescending { it.startedAt }
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

    TodaySummaryCard(
        total = todayHistory.size,
        completed = completedHistory.size,
        skipped = skippedHistory.size,
        risk = riskHistory.size
    ) {
        if (userId.isNullOrEmpty()) {
            ToastUtil.show(context, "尚未识别支付宝账号，请先打开并登录支付宝")
        } else {
            context.startActivity(
                Intent(context, MiuixRunHistoryActivity::class.java)
                    .putExtra(MiuixRunHistoryActivity.EXTRA_USER_ID, userId)
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    if (plans.isEmpty()) {
        Spacer(Modifier.height(14.dp))
        EmptyPlansCard()
    } else {
        val buckets = remember(plans, now, running, runtime.target, runtime.roundTime) {
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val nowMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            plans.map { plan ->
                val times = plan.roundTimes.mapNotNull(::timeToMinutes).sorted()
                val isRunning = running && runtime.target == plan.target
                val nextToday = times.firstOrNull { it >= nowMinute }
                val activeToday = plan.isEnabled && (isRunning || nextToday != null)
                val nextMinute = when {
                    isRunning -> timeToMinutes(runtime.roundTime) ?: nextToday ?: times.firstOrNull()
                    activeToday -> nextToday
                    else -> times.firstOrNull()
                }
                PlanBucket(plan, activeToday, nextMinute)
            }
        }
        // 待执行统一按“下一次执行时间”排序：今天剩余在前，明天在后；当前正在跑的计划由子览接管。
        val pendingBuckets = buckets.filterNot { bucket ->
            running && runtime.planId == bucket.plan.id
        }
        val entries = remember(pendingBuckets, groupNames) {
            val grouped = LinkedHashMap<String, MutableList<PlanBucket>>()
            val singles = ArrayList<PlanEntry.Single>()
            pendingBuckets.forEach { bucket ->
                val groupId = bucket.plan.groupId
                if (groupId.isBlank()) singles.add(PlanEntry.Single(bucket))
                else grouped.getOrPut(groupId) { ArrayList() }.add(bucket)
            }
            val groups = grouped.map { (id, list) ->
                val members = list.sortedBy { it.plan.windowStart }
                PlanEntry.Group(
                    id = id,
                    name = groupNames[id].orEmpty().ifBlank {
                        autoGroupName(members.map { it.plan.target }, members.first().plan.windowStart)
                    },
                    members = members
                )
            }
            (groups + singles).sortedWith(
                compareBy<PlanEntry> { entryKey(it) }
                    .thenBy { if (it.isEnabledEntry()) 0 else 1 }
            )
        }
        val futureHints = remember(pendingBuckets) {
            pendingBuckets.filter { !it.today && it.plan.isEnabled }
                .mapNotNull { bucket -> bucket.nextMinute?.let { bucket.plan.id to "明天 " + minuteText(it) } }
                .toMap()
        }

        val todayPlanCount = buckets.count { it.today }
        PlanSection(
            title = "待执行",
            meta = "$todayPlanCount 今日 / ${plans.size} 全部",
            count = entries.size,
            emptyText = "没有待执行计划"
        ) { index ->
            when (val entry = entries[index]) {
                is PlanEntry.Single -> {
                    val plan = entry.bucket.plan
                    val legacyGroupId = planGroupMap[plan.id]
                    val group = TaskGroups.find(legacyGroupId) ?: customGroups.firstOrNull { it.id == legacyGroupId }
                    val rowProgress = remember(plan.id, ledgerSnapshot, resolvedOverlay) {
                        taskProgressFor(ledgerSnapshot, resolvedOverlay, plan.target)
                    }
                    PlanRow(
                        plan = plan,
                        group = group,
                        tasks = planTaskMap[plan.id],
                        last = lastByPlan[plan.id],
                        busy = running,
                        active = running && runtime.planId == plan.id,
                        progress = if (group?.executesFullTarget == true) null else rowProgress,
                        liveTitle = if (running && runtime.planId == plan.id) runtime.activity else null,
                        now = now,
                        runStartedAt = if (running && runtime.target == plan.target) runtime.startedAt else 0L,
                        onToggle = { togglePlan(plan, it) },
                        onRunNow = { runPlanNow(plan) },
                        onEdit = { editPlan(plan) },
                        onDelete = { deletePlan(plan) },
                        nextHint = futureHints[plan.id]?.let { hint ->
                            val last = lastByPlan[plan.id]
                            if (last != null && isToday(last.startedAt)) "今日已执行 · $hint" else hint
                        }
                    )
                }
                is PlanEntry.Group -> {
                    val members = entry.members.map { it.plan }
                    GroupRow(
                        groupId = entry.id,
                        name = entry.name,
                        members = members,
                        lastByPlan = lastByPlan,
                        runningPlanId = if (running) runtime.planId else null,
                        now = now,
                        onToggle = { toggleGroup(members, it) },
                        onEdit = { editGroup(entry.id) },
                        onCopy = { copyGroup(entry.id, members) },
                        onDelete = { deleteGroup(entry.id, members) },
                        onRunMember = { plan -> runPlanNow(plan) },
                        onEditMember = { plan -> editPlan(plan) }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    if (showCooldownConfirm) {
        val plan = pendingCooldownPlan
        val remaining = plan?.let {
            manualCooldownRemaining(runtime, it.target, System.currentTimeMillis())
        } ?: 0L
        val minutesAgo = if (runtime.lastManualAt > 0L) {
            ((System.currentTimeMillis() - runtime.lastManualAt) / 60_000L).coerceAtLeast(0L)
        } else {
            0L
        }
        ConfirmDialog(
            title = "节律建议",
            text = "距上次手动执行约 " + minutesAgo + " 分钟，建议再等 " + formatRemaining(remaining)
                + "。\n仍要执行「" + (plan?.let { PlanTargets.labelOf(it.target) } ?: "") + "」吗？",
            confirmText = "仍要执行",
            dismissText = "取消",
            onConfirm = {
                val target = pendingCooldownPlan
                showCooldownConfirm = false
                pendingCooldownPlan = null
                if (target != null) sendTargetRun(target.target, true)
            },
            onDismiss = {
                showCooldownConfirm = false
                pendingCooldownPlan = null
            }
        )
    }

    if (showRiskResumeConfirm) {
        val target = pendingRiskRunTarget
        val scope = target?.let { RiskGuard.scopeForTarget(it) } ?: ""
        val reason = if (scope.isNotBlank()) RiskGuard.getScopeReason(scope) else ""
        ConfirmDialog(
            title = "本域暂停（1009）",
            text = "本域因服务端 1009 暂停"
                + (if (reason.isNotBlank()) "（" + reason + "）" else "")
                + "；支付宝界面没有可见验证。恢复后只执行这一轮，如果再次 1009 会重新冷却。\n仍要执行「"
                + (target?.let { PlanTargets.labelOf(it) } ?: "") + "」吗？",
            confirmText = "恢复并执行",
            dismissText = "保持暂停",
            onConfirm = {
                showRiskResumeConfirm = false
                val runTarget = pendingRiskRunTarget
                val ignore = pendingRiskRunIgnore
                pendingRiskRunTarget = null
                if (scope.isNotBlank()) {
                    RiskGuard.resumeScope(scope)
                }
                if (runTarget != null) {
                    sendTargetRunRaw(runTarget, ignore)
                }
            },
            onDismiss = {
                showRiskResumeConfirm = false
                pendingRiskRunTarget = null
            }
        )
    }

    if (showRunStatus) {
        RunStatusDialog(
            userId = userId,
            runtime = runtime,
            now = now,
            riskPaused = riskPaused,
            riskGlobal = riskGlobal,
            riskScope = riskScope,
            onDismiss = { showRunStatus = false },
            onStop = {
                context.sendMedusaBroadcast(
                    Intent("com.eg.android.AlipayGphone.sesame.planStop")
                        .setPackage("com.eg.android.AlipayGphone")
                )
                ToastUtil.show(context, "已发送停止请求")
                refresh++
            },
            onResume = {
                resumeRisk()
                refresh++
            },
            onRerun = { target ->
                showRunStatus = false
                sendTargetRun(target, true)
            }
        )
    }

    if (showRestoreDefaults) {
        ConfirmDialog(
            title = "恢复默认编排",
            text = "将用默认编排替换现有计划（当前 ${plans.size} 项会被删除）。默认编排按时间排序，可继续编辑。",
            confirmText = "恢复",
            onConfirm = {
                showRestoreDefaults = false
                if (PlanStore.restoreDefaults(userId ?: "")) {
                    context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
                    refresh++
                    ToastUtil.show(context, "已恢复默认编排")
                } else {
                    ToastUtil.show(context, "恢复失败，请检查文件访问权限")
                }
            },
            onDismiss = { showRestoreDefaults = false }
        )
    }
}

@Composable
private fun NewPlanLink(enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MedusaColors.Green.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 9.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MedusaColors.GreenDeep, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text("新建", fontSize = MedusaType.Secondary, fontWeight = FontWeight.SemiBold, color = MedusaColors.GreenDeep)
    }
}

/** 批量操作收进一个菜单，列表本身留在页面上，不再另开一层“管理计划”。 */
@Composable
private fun PlanOverflowMenu(
    onSuspendAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRestoreDefaults: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val positioner = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(8)
                val below = anchorBounds.bottom + 4
                val y = if (below + popupContentSize.height <= windowSize.height - 8) {
                    below
                } else {
                    (anchorBounds.top - popupContentSize.height - 4).coerceAtLeast(8)
                }
                return IntOffset(x, y)
            }
        }
    }
    Box {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = MedusaColors.Muted,
                modifier = Modifier.size(17.dp)
            )
        }
        if (open) {
            Popup(
                popupPositionProvider = positioner,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    Modifier
                        .width(148.dp)
                        .shadow(18.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, MedusaColors.Line, RoundedCornerShape(14.dp))
                        .padding(vertical = 4.dp)
                ) {
                    PlanMenuItem(Icons.Filled.PlayArrow, "启用全部计划", MedusaColors.Ink) {
                        open = false
                        onResumeAll()
                    }
                    PlanMenuItem(Icons.Filled.Tune, "暂停全部计划", MedusaColors.Ink) {
                        open = false
                        onSuspendAll()
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MedusaColors.Line.copy(alpha = 0.7f))
                    )
                    PlanMenuItem(Icons.Filled.Refresh, "恢复默认编排", MedusaColors.Muted) {
                        open = false
                        onRestoreDefaults()
                    }
                }
            }
        }
    }
}

@Composable
private fun RunStatusLink(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MedusaColors.Muted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("运行状态", fontSize = MedusaType.Secondary, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink)
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MedusaColors.Muted, modifier = Modifier.size(14.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ManualRunCard(onRun: (String) -> Unit) {
    val targets = remember { PlanTargets.all().filterNot { it.endsWith(".external") } }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Text("手动执行任意模块", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = MedusaColors.Ink)
        Text(
            "立刻跑一轮并出结果通知，不占用计划轮次；模块没开启会直接返回原因",
            fontSize = MedusaType.Secondary, color = MedusaColors.Muted, modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            targets.forEach { target ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF2F6FC))
                        .clickable { onRun(target) }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        PlanTargets.labelOf(target),
                        fontSize = MedusaType.Secondary, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentPlanDetail(
    runtime: PlanRuntime,
    progress: PlanTaskProgress?,
    now: Long
) {
    CardColumn(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "当前计划 · 子览",
                    fontSize = MedusaType.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Muted
                )
                Spacer(Modifier.weight(1f))
                Text(
                    PlanTargets.labelOf(runtime.target),
                    fontSize = MedusaType.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.GreenDeep
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = runtime.roundTime + " · 第 " + runtime.roundIndex + "/" + runtime.roundCount + " 轮"
                    + " · 已运行 " + durationText((now - runtime.startedAt).coerceAtLeast(0L)),
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted
            )
            Spacer(Modifier.height(6.dp))
            if (progress != null) {
                TaskProgressLine(progress, runtime.activity)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "剩余 " + (progress.total - progress.done).coerceAtLeast(0) + " 项"
                        + if (progress.title.isNotBlank()) " · 下一项 " + progress.title else "",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "正在做 · " + runtime.activity.ifBlank { "准备任务" },
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "该目标没有任务清单，正在按执行器步骤推进",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun PlanRuntimeCard(
    runtime: PlanRuntime,
    next: NextRun?,
    riskPaused: Boolean,
    riskReason: String = "",
    now: Long,
    plans: List<Plan>,
    history: List<PlanHistory.Record> = emptyList(),
    progress: PlanTaskProgress? = null,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val running = runtime.state == PlanRuntime.RUNNING && now - runtime.updatedAt < 35L * 60L * 1000L
    val manualPaused = riskPaused && RiskGuard.isManualPause()
    val riskStopped = PlanRuntime.RISK_STOPPED == runtime.state
    val stopped = PlanRuntime.STOPPED == runtime.state || riskStopped
    val nowMinute = Calendar.getInstance().apply { timeInMillis = now }.let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }
    // 时段内的计划即使轮次已跑完，也要留在当前卡上，绿色条才能按时间走完整段
    val activePlan = if (running) {
        null
    } else {
        plans.firstOrNull { plan ->
            if (!plan.isEnabled) {
                return@firstOrNull false
            }
            val start = timeToMinutes(plan.windowStart)
            val end = timeToMinutes(plan.windowEnd)
            start != null && end != null && end > start && nowMinute >= start && nowMinute < end
        }
    }
    // 任务类/单轮计划在本时段已经跑完：不能再按“时段进行中 + 时间进度”展示，
    // 否则结果已经是“已完成”，卡片却一直在走到窗口结束。
    val activePlanLast = activePlan?.let { plan ->
        history.firstOrNull { it.planId == plan.id && isToday(it.startedAt) }
    }
    val activePlanDone = activePlan?.let { plan ->
        activePlanLast?.status == PlanHistory.COMPLETED &&
            (plan.roundTimes.size <= 1 || plan.roundTimes.all { roundTime ->
                history.any {
                    it.planId == plan.id && it.roundTime == roundTime &&
                        it.status == PlanHistory.COMPLETED && isToday(it.startedAt)
                }
            })
    } ?: false
    val windowPlan = when {
        running -> plans.firstOrNull { it.id == runtime.planId }
            ?: plans.firstOrNull { it.target == runtime.target && it.roundTimes.contains(runtime.roundTime) }
            ?: plans.firstOrNull { it.target == runtime.target }
        activePlan != null -> activePlan
        else -> next?.plan
    }
    val windowProgress = if (riskPaused || stopped || activePlanDone) {
        null
    } else {
        windowPlan?.let { plan ->
            planRunTimeProgress(
                plan = plan,
                now = now,
                forTomorrow = !running && activePlan == null && next?.tomorrow == true,
                running = running,
                runStartedAt = runtime.startedAt
            )
        }
    }
    val tone = when {
        riskStopped || stopped -> MedusaColors.Stopped
        riskPaused -> MedusaColors.Cooldown
        running -> MedusaColors.Green
        activePlanDone -> MedusaColors.Green
        else -> MedusaColors.Blue
    }
    val elapsedMs = if (running && runtime.startedAt > 0L) {
        (now - runtime.startedAt).coerceAtLeast(0L)
    } else {
        0L
    }
    val roundLabel = if (running && runtime.roundCount > 1) {
        "第 " + runtime.roundIndex + "/" + runtime.roundCount + " 轮"
    } else {
        null
    }
    val runningHint = listOfNotNull(roundLabel, "已运行 " + durationText(elapsedMs))
        .joinToString(" · ")
    val estimateMs = if (running) {
        val durations = history
            .filter { it.target == runtime.target && it.durationMs() > 0L }
            .take(5)
            .map { it.durationMs() }
        if (durations.isNotEmpty()) {
            durations.average().toLong().coerceAtLeast(60_000L)
        } else {
            30L * 60_000L
        }
    } else {
        0L
    }
    val taskNote = progress?.let { "任务 ${it.done}/${it.total}" }
    // 总览口径：有任务流就看任务完成度；没有任务流才看计划窗口时间；手动单轮目标用已用时间
    val progressSpec = when {
        riskPaused || riskStopped || stopped -> null
        activePlanDone && progress != null -> PlanProgressSpec(
            progress.percent / 100f, "任务进度",
            "${progress.done}/${progress.total} · ${progress.percent}%", null)
        activePlanDone -> null
        running && progress != null -> PlanProgressSpec(
            progress.percent / 100f, "任务进度",
            "${progress.done}/${progress.total} · ${progress.percent}%", runningHint)
        running && windowProgress != null -> PlanProgressSpec(
            windowProgress, "时间进度", percentText(windowProgress), runningHint)
        running -> PlanProgressSpec(
            (elapsedMs.toFloat() / estimateMs.toFloat()).coerceIn(0f, 0.95f),
            "已用时间",
            "已用 " + durationText(elapsedMs) + " / 参考 " + durationText(estimateMs),
            roundLabel)
        // 空闲卡片的主题是“下一项”，任务进度属于上次目标，不能挂在这里
        windowProgress != null -> PlanProgressSpec(
            windowProgress, "时间进度", percentText(windowProgress), null)
        else -> null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF101633), Color(0xFF173A52))
                )
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(tone, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                text = when {
                    riskStopped -> "风控已停止"
                    stopped -> "已停止"
                    manualPaused -> "已暂停"
                    riskPaused -> "风控暂停"
                    running -> "正在执行"
                    activePlanDone -> "本时段已完成"
                    activePlan != null -> "时段进行中"
                    else -> "等待执行"
                },
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (riskPaused) "恢复并补跑" else "暂停全部",
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = if (riskPaused) Color(0xFFFFD28A) else Color.White.copy(alpha = 0.78f),
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.13f))
                    .clickable(onClick = if (riskPaused) onResume else onPause)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        if (running) {
            Text(
                text = PlanTargets.labelOf(runtime.target) + " · " + runtime.roundTime,
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            if (runtime.activity.isNotBlank()) {
                AnimatedContent(
                    targetState = runtime.activity,
                    transitionSpec = {
                        (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(160)))
                    },
                    label = "plan-activity"
                ) { current ->
                    Text(
                        text = "正在做 · " + current,
                        fontSize = MedusaType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "正在准备任务…",
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        } else if (riskStopped || stopped) {
            Text(
                text = if (riskStopped) "本轮被风控停止" else "本轮已停止",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = runtime.reason.ifBlank { if (riskStopped) "检测到 1009" else "用户停止" }
                    + " · 后续写请求已终止；可重新执行",
                fontSize = MedusaType.Secondary,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 3.dp)
            )
        } else if (riskPaused) {
            Text(
                text = riskReason.ifBlank { runtime.reason.ifBlank { "检测到账号风险" } },
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (manualPaused) {
                    "暂停期间不会发起任务 RPC；点右上角恢复后继续"
                } else {
                    "该页面暂停期间不会发起任务 RPC；确认风险解除后点右上角恢复"
                },
                fontSize = MedusaType.Secondary,
                color = Color.White.copy(alpha = 0.58f),
                modifier = Modifier.padding(top = 3.dp)
            )
        } else if (activePlan != null) {
            Text(
                text = "本时段 · " + PlanTargets.labelOf(activePlan.target),
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            val endMinute = timeToMinutes(activePlan.windowEnd)
            val remain = (endMinute ?: nowMinute) - nowMinute
            Text(
                text = activePlan.windowStart + "–" + activePlan.windowEnd + " · " + if (activePlanDone) {
                    "已完成"
                } else if (remain <= 1) {
                    "即将结束"
                } else {
                    "剩余 $remain 分钟"
                },
                fontSize = MedusaType.Secondary,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 3.dp)
            )
        } else {
            Text(
                text = next?.let { "下一项 · " + it.label } ?: "暂无启用中的计划",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = next?.let {
                    (if (it.tomorrow) "明天 " else "今天 ") + it.time + " · 到点自动执行"
                } ?: "在下方新增计划，到点由支付宝内的调度器执行",
                fontSize = MedusaType.Secondary,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        // 计划卡只有这一条进度：时间 > 任务数 > 轮次，百分比与条同源
        if (progressSpec != null) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(progressSpec.label, fontSize = MedusaType.Caption, color = Color.White.copy(alpha = 0.5f))
                if (!progressSpec.hint.isNullOrBlank()) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = progressSpec.hint,
                        fontSize = MedusaType.Caption,
                        color = Color.White.copy(alpha = 0.35f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    progressSpec.rightText,
                    fontSize = MedusaType.Caption,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
            Spacer(Modifier.height(6.dp))
            PlanProgressBar(progress = progressSpec.fraction)
        }

        // 上次结果与任务数合并一行，避免卡片被拆成多层
        if (!running) {
            val lastRecord = if (activePlan != null) activePlanLast else null
            val lastStatus = lastRecord?.status ?: runtime.lastStatus
            val lastTarget = lastRecord?.target ?: runtime.lastTarget
            val lastRoundTime = lastRecord?.roundTime ?: runtime.lastRoundTime
            val lastStatusText = when (lastStatus) {
                PlanHistory.COMPLETED -> "已完成"
                PlanHistory.RISK_STOPPED -> "风控停止"
                PlanHistory.STOPPED -> "已停止"
                else -> "未完成"
            }
            val lastLine = if (lastStatus.isNotBlank()) {
                "上次 " + PlanTargets.labelOf(lastTarget) + " " + lastRoundTime +
                    " · " + lastStatusText
            } else {
                null
            }
            val footer = listOfNotNull(lastLine, taskNote).joinToString(" · ").ifBlank { null }
            if (footer != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = footer,
                    fontSize = MedusaType.Caption,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        RuntimeMetricStrip(
            runtime = runtime,
            running = running,
            now = now,
            actionsOverride = if (!running) activePlanLast?.actionCount else null,
            energyOverride = if (!running) activePlanLast?.energyDelta else null,
            durationOverride = if (!running) activePlanLast?.durationMs() else null
        )
        if (running) {
            Text(
                text = "动作与能量在本轮结束后写入",
                fontSize = MedusaType.Caption,
                color = Color.White.copy(alpha = 0.38f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun TaskTimeLine(fraction: Float, window: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "时间进度",
            fontSize = MedusaType.Secondary,
            fontWeight = FontWeight.SemiBold,
            color = MedusaColors.Blue
        )
        Spacer(Modifier.weight(1f))
        Text(percentText(fraction), fontSize = MedusaType.Caption, color = MedusaColors.Muted)
    }
    Spacer(Modifier.height(3.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(Color(0xFFE7EDF6))
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(MedusaColors.Blue, CircleShape)
            )
        }
    }
    Text(
        text = window,
        fontSize = MedusaType.Caption,
        color = MedusaColors.Muted,
        modifier = Modifier.padding(top = 3.dp)
    )
}

@Composable
private fun PlanProgressBar(progress: Float) {
    val target = progress.coerceIn(0f, 1f)
    val fraction by animateFloatAsState(targetValue = target, animationSpec = tween(500), label = "plan-progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.10f))
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(Brush.horizontalGradient(listOf(MedusaColors.Green, Color(0xFF8CF3C0))))
            )
        }
    }
}

@Composable
private fun RuntimeMetricStrip(
    runtime: PlanRuntime,
    running: Boolean,
    now: Long,
    actionsOverride: Int? = null,
    energyOverride: Int? = null,
    durationOverride: Long? = null
) {
    val hasLast = actionsOverride != null || runtime.lastStatus.isNotBlank()
    val actions = when {
        running -> "—"
        actionsOverride != null -> actionsOverride.toString()
        !hasLast -> "—"
        else -> runtime.lastActionCount.toString()
    }
    val energyValue = energyOverride ?: runtime.lastEnergyDelta
    val energy = when {
        running || (energyOverride == null && !hasLast) -> "—"
        else -> "+" + energyValue
    }
    val duration = when {
        running -> if (runtime.startedAt > 0L) {
            durationText((now - runtime.startedAt).coerceAtLeast(0L))
        } else {
            "—"
        }
        durationOverride != null -> durationText(durationOverride)
        hasLast -> durationText(runtime.lastDurationMs)
        else -> "—"
    }
    val energyTone = if (!running && hasLast && energyValue > 0) {
        Color(0xFF8CF3C0)
    } else {
        Color.White
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RuntimeMetric("动作（项）", actions, Modifier.weight(1f))
        RuntimeMetricDivider()
        RuntimeMetric("能量（g）", energy, Modifier.weight(1f), energyTone)
        RuntimeMetricDivider()
        RuntimeMetric("用时", duration, Modifier.weight(1f))
    }
}

@Composable
private fun RuntimeMetric(label: String, value: String, modifier: Modifier, tone: Color = Color.White) {
    Column(modifier.padding(horizontal = 10.dp)) {
        Text(label, fontSize = MedusaType.Caption, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = MedusaType.CardTitle,
            fontWeight = FontWeight.Bold,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RuntimeMetricDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.10f)))
}

private fun targetModuleCode(target: String): String? = when {
    target.startsWith("forest.") -> "AntForestV2"
    target.startsWith("ocean.") -> "AntOcean"
    target.startsWith("farm.") -> "AntFarm"
    target.startsWith("orchard.") -> "AntOrchard"
    target == PlanTargets.DODO || target.startsWith("species.") -> "AntDodo"
    target == PlanTargets.SPORTS || target == PlanTargets.SPORTS_EXTERNAL -> "AntSports"
    target == PlanTargets.PROTECT -> "ProtectEcology"
    target == PlanTargets.GOLDEN -> "goldenbeans"
    else -> null
}

private fun targetModuleEnabled(target: String): Boolean {
    val code = targetModuleCode(target) ?: return true
    val field = ConfigV2.INSTANCE.getModelFields(code)?.get("enable") ?: return true
    return field.value == true
}

@Composable
private fun PlanRow(
    plan: Plan,
    group: TaskGroupSpec? = null,
    tasks: List<String>? = null,
    last: PlanHistory.Record?,
    busy: Boolean,
    active: Boolean = false,
    progress: PlanTaskProgress? = null,
    liveTitle: String? = null,
    now: Long,
    runStartedAt: Long = 0L,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    nextHint: String? = null
) {
    val domainOff = !targetModuleEnabled(plan.target)
    val visual = planVisual(plan.target)
    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            PlanDomainMenu(
                visual = visual,
                enabled = plan.isEnabled,
                onEdit = onEdit,
                onDelete = onDelete
            )
            Spacer(Modifier.width(9.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group?.name ?: PlanTargets.labelOf(plan.target),
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.Bold,
                        color = if (plan.isEnabled) MedusaColors.Ink else Color(0xFFA2ACBC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    RunNowChip(
                        busy = busy,
                        active = active,
                        blocked = domainOff,
                        onClick = onRunNow
                    )
                    Spacer(Modifier.width(8.dp))
                    MedusaPillSwitch(checked = plan.isEnabled, onCheckedChange = onToggle)
                }
                if (group != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).background(group.risk.color, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = group.risk.label,
                            fontSize = MedusaType.Caption,
                            fontWeight = FontWeight.SemiBold,
                            color = group.risk.color
                        )
                        Spacer(Modifier.width(6.dp))
                        val detail = if (!tasks.isNullOrEmpty()) {
                            "已选 " + tasks.size + " 项：" + tasks.joinToString(" · ")
                        } else {
                            group.composition
                        }
                        Text(
                            text = detail + " · 约" + group.estimatedMinutes + " 分钟",
                            fontSize = MedusaType.Caption,
                            color = MedusaColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = plan.windowStart + "–" + plan.windowEnd + " · "
                        + (if (PlanTargetLedgers.hasTaskList(plan.target)) "单轮" else plan.roundTimes.size.toString() + " 轮")
                        + " · " + plan.roundTimes.joinToString("、"),
                    fontSize = MedusaType.Secondary,
                    color = MedusaColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (progress != null) {
                    TaskProgressLine(progress, liveTitle)
                } else {
                    val timeFraction = planRowTimeProgress(
                        plan = plan,
                        now = now,
                        active = active,
                        runStartedAt = runStartedAt
                    )
                    if (timeFraction != null) {
                        TaskTimeLine(
                            fraction = timeFraction,
                            window = planTimeProgressLabel(plan, now, active, runStartedAt)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val completed = last?.status == PlanHistory.COMPLETED
                    val dot = when {
                        active -> MedusaColors.Green
                        !plan.isEnabled -> Color(0xFFB9C2D0)
                        domainOff -> Color(0xFF9AA5B1)
                        nextHint != null -> Color(0xFF8A97AA)
                        last == null -> Color(0xFFB9C2D0)
                        completed -> MedusaColors.Green
                        else -> MedusaColors.Warning
                    }
                    Box(Modifier.size(5.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            active -> "本次执行中"
                            !plan.isEnabled -> "已停用"
                            domainOff -> "本域已关闭 · 暂不执行"
                            nextHint != null -> nextHint
                            last == null -> "尚未执行"
                            else -> "上次 ${last.roundTime} · ${if (completed) "已完成" else "未完成"} · ${durationText(last.durationMs())}"
                        },
                        fontSize = MedusaType.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (nextHint == null && completed) MedusaColors.GreenDeep else MedusaColors.Muted,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (last != null && last.energyDelta > 0) {
                        Text(
                            text = "+${last.energyDelta}g",
                            fontSize = MedusaType.Caption,
                            fontWeight = FontWeight.SemiBold,
                            color = MedusaColors.GreenDeep,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE6F7EE))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFFC3CBD8),
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (!active && last != null && last.reason.isNotBlank()) {
                    Text(
                        text = last.reason,
                        fontSize = MedusaType.Secondary,
                        color = if (last.status == PlanHistory.COMPLETED) MedusaColors.Muted else MedusaColors.Warning,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 11.dp, top = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    groupId: String,
    name: String,
    members: List<Plan>,
    lastByPlan: Map<String, PlanHistory.Record?>,
    runningPlanId: String?,
    now: Long,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onRunMember: (Plan) -> Unit,
    onEditMember: (Plan) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember(groupId) { mutableStateOf(false) }
    val enabled = members.any { it.isEnabled }
    val runningMember = members.firstOrNull { it.id == runningPlanId }
    val firstStart = members.minOfOrNull { it.windowStart } ?: ""
    val lastEnd = members.maxOfOrNull { it.windowEnd } ?: ""
    val totalMinutes = members.sumOf { plan ->
        ((timeToMinutes(plan.windowEnd) ?: 0) - (timeToMinutes(plan.windowStart) ?: 0)).coerceAtLeast(0)
    }
    val lastRecord = members.mapNotNull { lastByPlan[it.id] }.maxByOrNull { it.startedAt }
    val doneCount = members.count { lastByPlan[it.id]?.status == PlanHistory.COMPLETED }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (enabled) MedusaColors.Green.copy(alpha = 0.10f) else Color(0xFFF1F4F8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${members.size}",
                    fontSize = MedusaType.CardTitle,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MedusaColors.GreenDeep else Color(0xFFA2ACBC)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MedusaColors.Ink else Color(0xFFA2ACBC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                AlertDialog.Builder(context)
                                    .setTitle(name)
                                    .setItems(arrayOf("编辑这一组", "复制这一组", "删除整组")) { _, which ->
                                        when (which) {
                                            0 -> onEdit()
                                            1 -> onCopy()
                                            else -> onDelete()
                                        }
                                    }
                                    .setNegativeButton("取消", null)
                                    .show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "组操作",
                            tint = Color(0xFF9AA5B1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    MedusaPillSwitch(checked = enabled, onCheckedChange = onToggle)
                }
                Text(
                    text = members.joinToString(" · ") { PlanTargets.labelOf(it.target) },
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    text = "$firstStart–$lastEnd · ${members.size} 项 · 约 $totalMinutes 分钟",
                    fontSize = MedusaType.Secondary,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val dot = when {
                        runningMember != null -> MedusaColors.Green
                        !enabled -> Color(0xFFB9C2D0)
                        lastRecord == null -> Color(0xFFB9C2D0)
                        doneCount == members.size -> MedusaColors.Green
                        else -> MedusaColors.Warning
                    }
                    Box(Modifier.size(5.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            runningMember != null -> "正在执行 " + PlanTargets.labelOf(runningMember.target)
                            !enabled -> "已停用"
                            lastRecord == null -> "尚未执行"
                            else -> "上次 ${lastRecord.roundTime} · 完成 $doneCount/${members.size}"
                        },
                        fontSize = MedusaType.Secondary,
                        color = if (enabled && doneCount == members.size && lastRecord != null) MedusaColors.GreenDeep else MedusaColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开成员",
                        tint = Color(0xFFC3CBD8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (expanded) {
            Column(Modifier.fillMaxWidth().background(Color(0xFFF8FAFC))) {
                members.forEach { member ->
                    GroupMemberRow(
                        plan = member,
                        last = lastByPlan[member.id],
                        running = runningPlanId == member.id,
                        onRun = { onRunMember(member) },
                        onEdit = { onEditMember(member) }
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MedusaColors.GreenDeep,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "编辑这一组（改名 · 改时间 · 增删成员）",
                        fontSize = MedusaType.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = MedusaColors.GreenDeep
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMemberRow(
    plan: Plan,
    last: PlanHistory.Record?,
    running: Boolean,
    onRun: () -> Unit,
    onEdit: () -> Unit
) {
    val domainOff = !targetModuleEnabled(plan.target)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 18.dp, end = 15.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = PlanTargets.labelOf(plan.target),
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = if (plan.isEnabled) MedusaColors.Ink else Color(0xFFA2ACBC)
            )
            Text(
                text = plan.windowStart + "–" + plan.windowEnd + " · "
                    + (last?.let { if (it.status == PlanHistory.COMPLETED) "上次已完成" else "上次未完成" } ?: "尚未执行")
                    + if (domainOff) " · 本域已关闭" else "",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        RunNowChip(busy = running, active = running, blocked = domainOff, onClick = onRun)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFC3CBD8),
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun TaskProgressLine(progress: PlanTaskProgress, liveTitle: String?) {
    val current = liveTitle?.takeIf { it.isNotBlank() }
    val title = current ?: progress.title
    val prefix = when {
        !current.isNullOrBlank() -> "正在做 · "
        progress.status == "待领取" -> "可领 · "
        title.isNotBlank() -> "下一项 · "
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "任务 ${progress.done}/${progress.total}",
            fontSize = MedusaType.Secondary,
            fontWeight = FontWeight.SemiBold,
            color = MedusaColors.GreenDeep
        )
        Spacer(Modifier.weight(1f))
        Text("${progress.percent}%", fontSize = MedusaType.Caption, color = MedusaColors.Muted)
    }
    Spacer(Modifier.height(3.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(Color(0xFFE7EDF6))
    ) {
        if (progress.percent > 0) {
            Box(
                Modifier
                    .fillMaxWidth(progress.percent / 100f)
                    .height(3.dp)
                    .background(MedusaColors.GreenDeep, CircleShape)
            )
        }
    }
    if (prefix.isNotEmpty()) {
        Text(
            text = prefix + title,
            fontSize = MedusaType.Caption,
            color = MedusaColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private data class PlanVisual(
    @DrawableRes val res: Int,
    val tone: Color,
    /** 旧矢量图标需要彩色底 + 白色字形；新上传的彩色图标直接显示原图 */
    val legacy: Boolean = false
)

private fun planVisual(target: String): PlanVisual = when {
    PlanTargets.isForest(target) -> PlanVisual(R.drawable.ic_plan_forest, Color(0xFF1E9E62))
    target == PlanTargets.FARM || target.startsWith("farm.") -> PlanVisual(R.drawable.ic_plan_farm, Color(0xFFE0A21B))
    target == PlanTargets.OCEAN || target.startsWith("ocean.") -> PlanVisual(R.drawable.ic_plan_ocean, Color(0xFF2F8FE0))
    target == PlanTargets.MEMBER -> PlanVisual(R.drawable.ic_plan_member, Color(0xFF7B61FF))
    target == PlanTargets.SPORTS -> PlanVisual(R.drawable.ic_plan_sports, Color(0xFF2E7FB8))
    target == PlanTargets.ANCIENT -> PlanVisual(R.drawable.ic_plan_ancient, Color(0xFF4C8B2B))
    target == PlanTargets.PROTECT -> PlanVisual(R.drawable.ic_plan_protect, Color(0xFF14584C))
    target == PlanTargets.DODO || target.startsWith("species.") -> PlanVisual(R.drawable.ic_plan_species, Color(0xFF2F6DE0))
    target == PlanTargets.STALL -> PlanVisual(R.drawable.ic_plan_stall, Color(0xFF8B5E2A))
    target == PlanTargets.ORCHARD || target.startsWith("orchard.") ->
        PlanVisual(R.drawable.ic_plan_orchard, Color(0xFF4C8B2B))
    // 上传的图标网格没有「绿色金融」；保留原绿色图标，不擅自重绘
    target == PlanTargets.GREEN -> PlanVisual(R.drawable.ic_plan_green, Color(0xFF1E7A5A), legacy = true)
    target == PlanTargets.GOLDEN -> PlanVisual(R.drawable.ic_plan_golden, Color(0xFFB98B1E))
    target == PlanTargets.ANSWER_AI -> PlanVisual(R.drawable.ic_plan_answer_ai, Color(0xFF4F63D8))
    else -> PlanVisual(R.drawable.ic_plan_unknown, Color(0xFF7B8598), legacy = true)
}

@Composable
private fun PlanDomainMenu(
    visual: PlanVisual,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val positioner = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (anchorBounds.left).coerceAtMost(windowSize.width - popupContentSize.width - 8)
                val below = anchorBounds.bottom + 4
                val y = if (below + popupContentSize.height <= windowSize.height - 8) {
                    below
                } else {
                    (anchorBounds.top - popupContentSize.height - 4).coerceAtLeast(8)
                }
                return IntOffset(x, y)
            }
        }
    }
    Box {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { open = true },
            contentAlignment = Alignment.Center
        ) {
            DomainTile(visual = visual, enabled = enabled, description = "更多操作")
        }
        // 三点收进图标右下角：点图标即出编辑/删除，不再贴着开关放
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, visual.tone.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) {
                    Box(Modifier.size(1.3.dp).background(visual.tone, CircleShape))
                }
            }
        }
        if (open) {
            Popup(
                popupPositionProvider = positioner,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    Modifier
                        .width(132.dp)
                        .shadow(18.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, MedusaColors.Line, RoundedCornerShape(14.dp))
                        .padding(vertical = 4.dp)
                ) {
                    PlanMenuItem(Icons.Filled.Edit, "编辑计划", MedusaColors.Ink) {
                        open = false
                        onEdit()
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MedusaColors.Line.copy(alpha = 0.7f))
                    )
                    PlanMenuItem(Icons.Filled.Delete, "删除计划", MedusaColors.Danger) {
                        open = false
                        onDelete()
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainTile(visual: PlanVisual, enabled: Boolean, description: String? = null) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.42f }
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (visual.legacy) {
            // 仅有绿色金融/未知域没有新版彩色图标，沿用旧矢量图标但不改画风
            Box(
                Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(listOf(visual.tone, visual.tone.copy(alpha = 0.76f))))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(visual.res),
                    contentDescription = description,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Image(
                painter = painterResource(visual.res),
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PlanMenuItem(icon: ImageVector, label: String, tone: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = MedusaType.Body, fontWeight = FontWeight.Medium, color = tone)
    }
}

/** 今日概览：只给数字和入口，完整记录在独立的运行记录页。 */
@Composable
private fun TodaySummaryCard(
    total: Int,
    completed: Int,
    skipped: Int,
    risk: Int,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "今日记录",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = MedusaColors.Ink
            )
            Spacer(Modifier.width(6.dp))
            Text("$total 轮", fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
            Spacer(Modifier.weight(1f))
            Text(
                "查看全部 ›",
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.GreenDeep
            )
        }
        Spacer(Modifier.height(7.dp))
        if (total == 0) {
            Text(
                "今天还没有执行记录，到点执行后这里会有数字",
                fontSize = MedusaType.Secondary,
                color = MedusaColors.Muted
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TodayCount("完成", completed, MedusaColors.GreenDeep)
                TodayCount("跳过", skipped, Color(0xFF9AA5B5))
                TodayCount("风控/未完成", risk, MedusaColors.Stopped)
            }
        }
    }
}

@Composable
private fun TodayCount(label: String, value: Int, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(tone, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            "$label $value",
            fontSize = MedusaType.Secondary,
            fontWeight = FontWeight.SemiBold,
            color = tone
        )
    }
}

@Composable
private fun PlanSection(
    title: String,
    meta: String,
    count: Int,
    emptyText: String? = null,
    row: @Composable (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = MedusaColors.Ink)
        Spacer(Modifier.width(6.dp))
        Text(meta, fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
    }
    if (count == 0) {
        if (emptyText != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(18.dp))
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(emptyText, fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
        ) {
            repeat(count) { index ->
                row(index)
                if (index != count - 1) {
                    Separator()
                }
            }
        }
    }
}

@Composable
internal fun Separator() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp)
            .height(1.dp)
            .background(MedusaColors.Line.copy(alpha = 0.7f))
    )
}

@Composable
private fun RunNowChip(
    busy: Boolean,
    active: Boolean = busy,
    blocked: Boolean = false,
    onClick: () -> Unit
) {
    val disabled = busy || blocked
    val tone = if (disabled) Color(0xFFB9C2D0) else MedusaColors.GreenDeep
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(tone.copy(alpha = if (disabled) 0.10f else 0.12f))
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!disabled) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = tone, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = when {
                blocked -> "本域已关闭"
                active -> "执行中"
                busy -> "等待空闲"
                else -> "立即执行"
            },
            fontSize = MedusaType.Secondary,
            fontWeight = FontWeight.SemiBold,
            color = tone
        )
    }
}

@Composable
private fun PlanConflictNotice(conflicts: List<PlanConflict>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF8E9))
            .border(1.dp, MedusaColors.Warning.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MedusaColors.Warning,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "时间段有重叠 · ${conflicts.size} 组",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Warning
            )
        }
        Spacer(Modifier.height(4.dp))
        conflicts.take(3).forEach { conflict ->
            Text(
                text = PlanTargets.labelOf(conflict.first.target) + " "
                    + conflict.first.windowStart + "–" + conflict.first.windowEnd + " 与 "
                    + PlanTargets.labelOf(conflict.second.target) + " "
                    + conflict.second.windowStart + "–" + conflict.second.windowEnd,
                fontSize = 10.sp,
                color = MedusaColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (conflicts.size > 3) {
            Text("另有 ${conflicts.size - 3} 组…", fontSize = 9.5.sp, color = MedusaColors.Muted)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "同一时段会串行排队；只提醒，不拦截。触发风控请自行调整时间窗。",
            fontSize = 9.5.sp,
            color = MedusaColors.Muted
        )
    }
}

@Composable
private fun PlanNotice(text: String, tone: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = tone, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = MedusaType.Secondary, color = MedusaColors.Ink)
    }
}

@Composable
private fun EmptyPlansCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, MedusaColors.Line.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MedusaColors.Muted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text("暂未安排计划", fontSize = MedusaType.Body, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink)
        Text("先选模块、时间段和轮次，保存后到点自动执行", fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
    }
}

private fun nextRun(plans: List<Plan>): NextRun? {
    val now = Calendar.getInstance()
    val nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    var best: NextRun? = null
    var bestMinute = Int.MAX_VALUE
    plans.filter { it.isEnabled }.forEach { plan ->
        plan.roundTimes.forEach { time ->
            val minute = timeToMinutes(time) ?: return@forEach
            if (minute > nowMinute && minute < bestMinute) {
                bestMinute = minute
                best = NextRun(PlanTargets.labelOf(plan.target), time, false, plan)
            }
        }
    }
    if (best != null) {
        return best
    }
    plans.filter { it.isEnabled }.forEach { plan ->
        plan.roundTimes.forEach { time ->
            val minute = timeToMinutes(time) ?: return@forEach
            if (minute < bestMinute) {
                bestMinute = minute
                best = NextRun(PlanTargets.labelOf(plan.target), time, true, plan)
            }
        }
    }
    return best
}

private fun taskProgressFor(snapshot: JSONObject?, overlay: JSONObject?, target: String): PlanTaskProgress? {
    if (target.isBlank()) return null
    val progress = TaskProgress.from(snapshot, overlay, target)
    if (!progress.hasTasks()) return null
    return PlanTaskProgress(
        done = progress.done,
        total = progress.total,
        title = progress.nextTitle,
        status = progress.nextStatus
    )
}

private fun timeToMinutes(time: String): Int? {
    if (!PlanTargets.isValidTime(time)) {
        return null
    }
    return time.substring(0, 2).toInt() * 60 + time.substring(3, 5).toInt()
}

private fun minuteText(minute: Int): String =
    String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60)

private fun todayAt(minute: Int, now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun nowMinuteOf(now: Long): Int =
    Calendar.getInstance().apply { timeInMillis = now }.let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

/**
 * 总览时间进度：
 * - 计划窗口内按窗口起点走；
 * - 窗口外手动执行时，用本轮开始时间 + 计划默认时长，避免一上来就是 100%。
 */
private fun planRunTimeProgress(
    plan: Plan,
    now: Long,
    forTomorrow: Boolean,
    running: Boolean,
    runStartedAt: Long
): Float? {
    val start = timeToMinutes(plan.windowStart) ?: return null
    val end = timeToMinutes(plan.windowEnd) ?: return null
    if (end <= start) {
        return null
    }
    if (forTomorrow) {
        return 0f
    }
    val durationMs = (end - start) * 60_000L
    val nowMinute = nowMinuteOf(now)
    val inWindow = nowMinute >= start && nowMinute < end
    if (!running) {
        if (!inWindow) {
            return null
        }
        return ((now - todayAt(start, now)).toFloat() / durationMs).coerceIn(0f, 1f)
    }
    val anchor = if (!inWindow && runStartedAt > 0L) runStartedAt else todayAt(start, now)
    return ((now - anchor).toFloat() / durationMs).coerceIn(0f, 1f)
}

private fun planTimeProgressLabel(
    plan: Plan,
    now: Long,
    active: Boolean,
    runStartedAt: Long
): String {
    val start = timeToMinutes(plan.windowStart) ?: return "${plan.windowStart}–${plan.windowEnd}"
    val end = timeToMinutes(plan.windowEnd) ?: return "${plan.windowStart}–${plan.windowEnd}"
    val nowMinute = nowMinuteOf(now)
    val inWindow = nowMinute >= start && nowMinute < end
    if (active && !inWindow && runStartedAt > 0L) {
        val duration = (end - start).coerceAtLeast(0)
        val clock = Calendar.getInstance().apply { timeInMillis = runStartedAt }.let {
            String.format(Locale.CHINA, "%02d:%02d",
                it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
        }
        return "默认 $duration 分钟 · 本轮 $clock 起"
    }
    return "${plan.windowStart}–${plan.windowEnd}"
}

/** 计划卡子进度：窗口内按窗口走；窗口外只有正在执行的那张卡按本轮开始时间走。 */
private fun planRowTimeProgress(
    plan: Plan,
    now: Long,
    active: Boolean,
    runStartedAt: Long
): Float? {
    val start = timeToMinutes(plan.windowStart) ?: return null
    val end = timeToMinutes(plan.windowEnd) ?: return null
    if (end <= start) {
        return null
    }
    val durationMs = (end - start) * 60_000L
    val nowMinute = nowMinuteOf(now)
    val inWindow = nowMinute >= start && nowMinute < end
    if (!inWindow) {
        if (!active || runStartedAt <= 0L) {
            return null
        }
        return ((now - runStartedAt).toFloat() / durationMs).coerceIn(0f, 1f)
    }
    return ((now - todayAt(start, now)).toFloat() / durationMs).coerceIn(0f, 1f)
}

internal fun durationText(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(0L)
    if (seconds < 60) {
        return "${seconds}秒"
    }
    val minutes = seconds / 60
    val rest = seconds % 60
    if (minutes < 60) {
        return "${minutes}分${rest}秒"
    }
    return "${minutes / 60}小时${minutes % 60}分"
}

internal fun dateText(millis: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val time = String.format(Locale.CHINA, "%02d:%02d",
        target.get(Calendar.HOUR_OF_DAY), target.get(Calendar.MINUTE))
    return if (sameDay) "今天 $time" else String.format(Locale.CHINA, "%02d-%02d %s",
        target.get(Calendar.MONTH) + 1, target.get(Calendar.DAY_OF_MONTH), time)
}

private fun isToday(millis: Long): Boolean {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

internal fun triggerText(trigger: String): String = when (trigger) {
    PlanHistory.TRIGGER_CATCHUP -> "补跑"
    PlanHistory.TRIGGER_FALLBACK -> "晚间兜底"
    PlanHistory.TRIGGER_PROBE -> "零点试探"
    PlanHistory.TRIGGER_MANUAL -> "手动"
    PlanHistory.TRIGGER_WINDOW -> "能量窗口"
    else -> "定时"
}

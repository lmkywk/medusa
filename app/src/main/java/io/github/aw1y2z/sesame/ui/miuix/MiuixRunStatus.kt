package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.plan.PlanRuntime
import io.github.aw1y2z.sesame.util.ToastUtil
import android.content.Context
import androidx.compose.ui.graphics.Color

/** 3.0-alpha：首页、计划页、运行状态页共用同一套运行状态语义。 */
internal enum class RunVisualState {
    IDLE, RUNNING, COOLDOWN, RISK_PAUSED, RISK_STOPPED, STOPPED
}

internal data class RunVisual(
    val state: RunVisualState,
    val label: String,
    val tone: Color,
    val soft: Color
)

/** 手动节律建议：只按目标独立计算；各页各算各的，不做全局冷却。 */
internal fun manualCooldownRemaining(runtime: PlanRuntime, target: String?, now: Long): Long {
    val targetMinutes = (AppConfig.INSTANCE.manualCooldownTarget ?: 60).coerceAtLeast(0)
    if (targetMinutes <= 0 || target.isNullOrBlank()) {
        return 0L
    }
    val last = runtime.manualLastByTarget.optLong(target, 0L)
    if (last <= 0L) {
        return 0L
    }
    return (last + targetMinutes * 60_000L - now).coerceAtLeast(0L)
}

internal fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes < 60) {
        String.format(java.util.Locale.CHINA, "%02d:%02d", minutes, seconds)
    } else {
        String.format(java.util.Locale.CHINA, "%d 小时 %02d 分", minutes / 60, minutes % 60)
    }
}

internal fun resolveRunVisual(
    runtime: PlanRuntime,
    now: Long,
    riskPaused: Boolean,
    cooldownRemaining: Long
): RunVisual = when {
    PlanRuntime.STOPPED == runtime.state -> RunVisual(
        RunVisualState.STOPPED, "已停止", MedusaColors.Stopped, MedusaColors.StoppedSoft
    )
    PlanRuntime.RISK_STOPPED == runtime.state -> RunVisual(
        RunVisualState.RISK_STOPPED, "风控已停止", MedusaColors.Stopped, MedusaColors.StoppedSoft
    )
    PlanRuntime.RUNNING == runtime.state && now - runtime.updatedAt < 35L * 60L * 1000L -> RunVisual(
        RunVisualState.RUNNING, "正在执行", MedusaColors.Success, MedusaColors.SuccessSoft
    )
    riskPaused -> RunVisual(
        RunVisualState.RISK_PAUSED, "风控暂停", MedusaColors.Cooldown, MedusaColors.CooldownSoft
    )
    cooldownRemaining > 0L -> RunVisual(
        RunVisualState.COOLDOWN, "建议等待", MedusaColors.Cooldown, MedusaColors.CooldownSoft
    )
    else -> RunVisual(
        RunVisualState.IDLE, "等待执行", MedusaColors.Blue, Color.Transparent
    )
}

/** 一次提示只弹一次；首页和计划页都可能订阅同一条运行说明。 */
internal object RunNoticeUi {
    var lastShownAt: Long = 0L
}

internal fun maybeShowRunNotice(context: Context, runtime: PlanRuntime) {
    val at = runtime.noticeAt
    if (at <= 0L || at <= RunNoticeUi.lastShownAt || runtime.notice.isBlank()) {
        return
    }
    RunNoticeUi.lastShownAt = at
    ToastUtil.show(context, runtime.notice)
}

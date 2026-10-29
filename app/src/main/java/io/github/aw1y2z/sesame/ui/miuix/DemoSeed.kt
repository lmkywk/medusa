package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.BuildConfig
import io.github.aw1y2z.sesame.data.plan.DefaultSchedule
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanStore
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import java.io.File
import java.util.Calendar

/**
 * 分身试验包的演示数据：只在 UI_DEMO 构建里写一次，落在分身自己的共享目录，
 * 正式包和主空间那条运行链路完全不经过这里。
 */
internal object DemoSeed {

    private const val DEMO_UID = "2088000000000001"
    private const val MARKER = ".ui_demo_seeded_v3"

    fun ensure() {
        if (!BuildConfig.UI_PREVIEW) return
        try {
            val marker = File(FileUtil.MAIN_DIRECTORY_FILE, MARKER)
            if (marker.exists()) return
            FileUtil.write2File(DEMO_UID, FileUtil.getCurrentAccountFile())
            if (PlanStore.load(DEMO_UID).isEmpty()) {
                PlanStore.save(DEMO_UID, DefaultSchedule.createPlans())
            }
            PlanHistory.save(DEMO_UID, demoRecords())
            marker.createNewFile()
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    /** 计划取默认编排的前三条，让列表行能显示“上次执行”；另补跳过和风控两类记录。 */
    private fun demoRecords(): List<PlanHistory.Record> {
        val plans = PlanStore.load(DEMO_UID).filter { it.isEnabled }.take(3)
        val rows = mutableListOf<PlanHistory.Record>()
        plans.forEachIndexed { index, plan ->
            val totalMinutes = 7 * 60 + 5 + index * 30
            rows += record(
                planId = plan.id,
                target = plan.target,
                hour = totalMinutes / 60,
                minute = totalMinutes % 60,
                roundTime = String.format("%02d:%02d", totalMinutes / 60, totalMinutes % 60),
                status = PlanHistory.COMPLETED,
                reason = "",
                actions = 12 + index * 5,
                energy = 186 + index * 64,
                trigger = PlanHistory.TRIGGER_SCHEDULED
            )
        }
        rows += record(
            planId = "demo-skip",
            target = "farm.draw",
            hour = 12,
            minute = 30,
            roundTime = "12:30",
            status = PlanHistory.SKIPPED,
            reason = "今日次数已用完",
            actions = 0,
            energy = 0,
            trigger = PlanHistory.TRIGGER_SCHEDULED
        )
        rows += record(
            planId = "demo-risk",
            target = "forest.patrol",
            hour = 18,
            minute = 40,
            roundTime = "18:40",
            status = PlanHistory.RISK_STOPPED,
            reason = "检测到 1009，已停止并等待验证",
            actions = 3,
            energy = 24,
            trigger = PlanHistory.TRIGGER_SCHEDULED
        )
        rows += record(
            planId = "demo-manual",
            target = "ocean.clean",
            hour = 19,
            minute = 20,
            roundTime = "19:20",
            status = PlanHistory.COMPLETED,
            reason = "",
            actions = 20,
            energy = 64,
            trigger = PlanHistory.TRIGGER_MANUAL
        )
        return rows
    }

    private fun record(
        planId: String,
        target: String,
        hour: Int,
        minute: Int,
        roundTime: String,
        status: String,
        reason: String,
        actions: Int,
        energy: Int,
        trigger: String
    ): PlanHistory.Record {
        val startedAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val duration = actions * 8_000L + 20_000L
        return PlanHistory.Record().apply {
            this.runId = "demo-" + planId + "-" + roundTime
            this.planId = planId
            this.target = target
            this.roundTime = roundTime
            this.startedAt = startedAt
            this.endedAt = startedAt + duration
            this.status = status
            this.reason = reason
            this.completedCount = if (status == PlanHistory.COMPLETED) actions else 0
            this.actionCount = actions
            this.energyDelta = energy
            this.roundIndex = 1
            this.roundCount = 1
            this.trigger = trigger
            this.manualOverride = trigger == PlanHistory.TRIGGER_MANUAL
        }
    }
}

package io.github.aw1y2z.sesame.data.home

import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.data.ledger.TaskProgress
import io.github.aw1y2z.sesame.data.plan.Plan
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanRuntime
import io.github.aw1y2z.sesame.data.plan.PlanStore
import io.github.aw1y2z.sesame.util.RiskGuard
import io.github.aw1y2z.sesame.util.Statistics

/**
 * 首页只读快照：一次读取计划、历史、运行态、统计、账本进度和风控状态。
 * UI 只消费这份快照，不再各自直连多个存储。
 */
data class HomeSnapshot(
    val uid: String,
    val stats: Statistics,
    val plans: List<Plan>,
    val history: List<PlanHistory.Record>,
    val runtime: PlanRuntime,
    val taskProgressByModule: Map<String, TaskProgress>,
    val riskPaused: Boolean,
    val verificationRequired: Boolean,
    val loadedAt: Long
)

object HomeSnapshotService {

    fun load(uid: String?, moduleTargets: Map<String, String?>): HomeSnapshot {
        val safeUid = uid.orEmpty()
        return try {
            val stats = if (safeUid.isEmpty()) Statistics() else Statistics.readFrom(safeUid)
            val plans = if (safeUid.isEmpty()) emptyList() else PlanStore.load(safeUid)
            val history = if (safeUid.isEmpty()) emptyList() else PlanHistory.load(safeUid)
            val runtime = if (safeUid.isEmpty()) PlanRuntime() else PlanRuntime.load(safeUid)
            val progress = if (safeUid.isEmpty()) {
                emptyMap()
            } else {
                loadTaskProgress(safeUid, moduleTargets)
            }
            HomeSnapshot(
                uid = safeUid,
                stats = stats,
                plans = plans,
                history = history,
                runtime = runtime,
                taskProgressByModule = progress,
                riskPaused = riskPaused(),
                verificationRequired = verificationRequired(),
                loadedAt = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            HomeSnapshot(
                uid = safeUid,
                stats = Statistics(),
                plans = emptyList(),
                history = emptyList(),
                runtime = PlanRuntime(),
                taskProgressByModule = emptyMap(),
                riskPaused = riskPaused(),
                verificationRequired = verificationRequired(),
                loadedAt = System.currentTimeMillis()
            )
        }
    }

    private fun loadTaskProgress(
        uid: String,
        moduleTargets: Map<String, String?>
    ): Map<String, TaskProgress> {
        val day = TaskLedgerStore.today()
        val snapshot = TaskLedgerStore.loadSnapshot(uid, day)
        val overlay = TaskLedgerStore.loadResolved(uid, day)
        val map = HashMap<String, TaskProgress>()
        for ((key, target) in moduleTargets) {
            if (target.isNullOrBlank()) {
                continue
            }
            val progress = runCatching {
                TaskProgress.from(snapshot, overlay, target)
            }.getOrNull() ?: continue
            if (progress.hasTasks()) {
                map[key] = progress
            }
        }
        return map
    }

    private fun riskPaused(): Boolean =
        runCatching { RiskGuard.isGlobalPaused() }.getOrDefault(false)

    private fun verificationRequired(): Boolean =
        runCatching { RiskGuard.isVerificationRequired() }.getOrDefault(false)
}

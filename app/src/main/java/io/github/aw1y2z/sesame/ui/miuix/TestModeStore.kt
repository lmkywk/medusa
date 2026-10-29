package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.Intent
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanStore
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import io.github.aw1y2z.sesame.util.FileUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TestModeItem(
    var target: String,
    var status: String = "PENDING",
    var reason: String = "",
    var queuedAt: Long = 0L,
    var endedAt: Long = 0L,
    /** 今日已完成、但不是本轮实测：用于区分“实测通过”和“继承”。 */
    var inherited: Boolean = false
)

data class TestModeState(
    var enabled: Boolean = false,
    var day: String = "",
    var startedAt: Long = 0L,
    var buildInstalledAt: Long = 0L,
    val items: MutableList<TestModeItem> = mutableListOf()
)

/**
 * 测试模式：默认计划全部暂停，用一份持久化清单驱动全量测试。
 * 只负责清单/暂停/恢复与排队，不改执行器；执行仍走宿主现有手动队列。
 */
object TestModeStore {

    const val STATUS_PENDING = "PENDING"
    const val STATUS_QUEUED = "QUEUED"
    const val STATUS_DONE = "DONE"
    const val STATUS_SKIPPED = "SKIPPED"
    const val STATUS_RISK = "RISK"
    const val STATUS_STOPPED = "STOPPED"
    const val STATUS_ERROR = "ERROR"
    const val STATUS_INCOMPLETE = "INCOMPLETE"
    const val STATUS_TIMEOUT = "TIMEOUT"

    private const val FILE_NAME = "test_mode.json"
    private const val BACKUP_NAME = "plans.pre_test.json"

    private fun file(userId: String): File =
        File(FileUtil.getUserConfigDirectoryFile(userId), FILE_NAME)

    private fun backupFile(userId: String): File =
        File(FileUtil.getUserConfigDirectoryFile(userId), BACKUP_NAME)

    fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun nowTime(): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date())

    fun load(userId: String): TestModeState {
        val state = TestModeState()
        if (userId.isBlank()) return state
        try {
            val file = file(userId)
            if (!file.exists()) return state
            val root = JSONObject(FileUtil.readFromFile(file) ?: return state)
            state.enabled = root.optBoolean("enabled", false)
            state.day = root.optString("day", "")
            state.startedAt = root.optLong("startedAt", 0L)
            state.buildInstalledAt = root.optLong("buildInstalledAt", 0L)
            val array = root.optJSONArray("items") ?: return state
            for (i in 0 until array.length()) {
                val jo = array.optJSONObject(i) ?: continue
                val target = jo.optString("target", "")
                if (target.isEmpty()) continue
                state.items.add(
                    TestModeItem(
                        target = target,
                        status = jo.optString("status", STATUS_PENDING),
                        reason = jo.optString("reason", ""),
                        queuedAt = jo.optLong("queuedAt", 0L),
                        endedAt = jo.optLong("endedAt", 0L),
                        inherited = jo.optBoolean("inherited", false)
                    )
                )
            }
        } catch (_: Throwable) {
            return TestModeState()
        }
        return state
    }

    private fun save(userId: String, state: TestModeState) {
        if (userId.isBlank()) return
        try {
            val root = JSONObject()
            root.put("enabled", state.enabled)
            root.put("day", state.day)
            root.put("startedAt", state.startedAt)
            root.put("buildInstalledAt", state.buildInstalledAt)
            val array = JSONArray()
            state.items.forEach { item ->
                val jo = JSONObject()
                jo.put("target", item.target)
                jo.put("status", item.status)
                jo.put("reason", item.reason)
                jo.put("queuedAt", item.queuedAt)
                jo.put("endedAt", item.endedAt)
                jo.put("inherited", item.inherited)
                array.put(jo)
            }
            root.put("items", array)
            FileUtil.write2File(root.toString(), file(userId))
        } catch (_: Throwable) {
        }
    }

    fun enable(context: Context, userId: String) {
        if (userId.isBlank()) return
        // 备份当前计划，再把默认计划全部停用；测试模式是唯一调度
        val backup = backupFile(userId)
        if (!backup.exists()) {
            val current = FileUtil.readFromFile(FileUtil.getPlanFile(userId))
            if (current != null) FileUtil.write2File(current, backup)
        }
        val plans = PlanStore.load(userId)
        plans.forEach { it.isEnabled = false }
        PlanStore.save(userId, plans)

        val installedAt = installTime(context)
        val state = TestModeState(
            enabled = true,
            day = today(),
            startedAt = System.currentTimeMillis(),
            buildInstalledAt = installedAt
        )
        // 只继承“当前安装版本”内的完成记录。旧版本的白日结果不能算 3.5.7/3.6.0 的实测，
        // 否则测试页会显示 15 完成，实际主改动根本没跑。
        val completed = completedTargetsToday(userId, installedAt)
        PlanTargets.testTargets().forEach { target ->
            val item = TestModeItem(target)
            if (completed.contains(target)) {
                item.status = STATUS_DONE
                item.reason = "今日已完成（本版本），不重跑"
                item.inherited = true
            }
            state.items.add(item)
        }
        save(userId, state)
        context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
    }

    private fun installTime(context: Context): Long {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        } catch (_: Throwable) {
            0L
        }
    }

    /** 升级/重装后自动把旧清单恢复为未完成；同一版本内继续保留“继承”语义。 */
    fun ensureCurrentBuild(context: Context, userId: String) {
        if (userId.isBlank()) return
        val installedAt = installTime(context)
        if (installedAt <= 0L) return
        val state = load(userId)
        if (!state.enabled || state.buildInstalledAt == installedAt) return
        state.items.forEach { item ->
            item.status = STATUS_PENDING
            item.reason = ""
            item.queuedAt = 0L
            item.endedAt = 0L
            item.inherited = false
        }
        state.startedAt = System.currentTimeMillis()
        state.day = today()
        state.buildInstalledAt = installedAt
        save(userId, state)
    }

    /** 把“今日已完成（本版本）”重新放回待执行，供用户强制全量复测。 */
    fun requeueInherited(userId: String): Int {
        if (userId.isBlank()) return 0
        val state = load(userId)
        var count = 0
        state.items.forEach { item ->
            if (item.status == STATUS_DONE && item.inherited) {
                item.status = STATUS_PENDING
                item.reason = ""
                item.queuedAt = 0L
                item.endedAt = 0L
                item.inherited = false
                count++
            }
        }
        if (count > 0) {
            save(userId, state)
        }
        return count
    }

    fun disable(context: Context, userId: String) {
        if (userId.isBlank()) return
        val backup = backupFile(userId)
        if (backup.exists()) {
            val content = FileUtil.readFromFile(backup)
            if (content != null) {
                FileUtil.write2File(content, FileUtil.getPlanFile(userId))
            }
            backup.delete()
        }
        val state = load(userId)
        state.enabled = false
        save(userId, state)
        context.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.planChanged"))
    }

    /** 把未完成项排进宿主手动队列；已完成/风控/跳过不重跑。 */
    fun queueUnfinished(context: Context, userId: String) {
        if (userId.isBlank()) return
        val state = load(userId)
        if (!state.enabled) return
        val pending = state.items.filter {
            it.status == STATUS_PENDING || it.status == STATUS_ERROR
                    || it.status == STATUS_INCOMPLETE || it.status == STATUS_TIMEOUT
        }
        if (pending.isEmpty()) return
        val roundTime = nowTime()
        val app = context.applicationContext
        Thread {
            pending.forEachIndexed { index, item ->
                item.status = STATUS_QUEUED
                item.queuedAt = System.currentTimeMillis()
                item.reason = ""
                save(userId, state)
                app.sendMedusaBroadcast(
                    Intent("com.eg.android.AlipayGphone.sesame.planRun")
                        .setPackage("com.eg.android.AlipayGphone")
                        .putExtra("target", item.target)
                        .putExtra("roundTime", roundTime)
                        .putExtra("ignoreCooldown", true)
                )
                if (index != pending.lastIndex) {
                    try {
                        Thread.sleep(150L)
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }.start()
    }

    /** 只刷新清单状态，不发起执行。 */
    fun refreshStatuses(userId: String) {
        if (userId.isBlank()) return
        val state = load(userId)
        if (!state.enabled) return
        val history = try {
            PlanHistory.load(userId)
        } catch (_: Throwable) {
            emptyList()
        }
        state.items.forEach { item ->
            val record = history
                .filter { it.target == item.target }
                .filter { it.startedAt >= state.startedAt }
                .maxByOrNull { it.startedAt }
            if (record != null) {
                item.status = when (record.status) {
                    PlanHistory.COMPLETED -> STATUS_DONE
                    PlanHistory.RISK_STOPPED -> STATUS_RISK
                    PlanHistory.SKIPPED -> STATUS_SKIPPED
                    PlanHistory.STOPPED -> STATUS_STOPPED
                    PlanHistory.INCOMPLETE -> {
                        val why = record.reason ?: ""
                        when {
                            why.contains("超时") -> STATUS_TIMEOUT
                            why.contains("异常") || why.contains("无响应") || why.contains("离线") -> STATUS_ERROR
                            else -> STATUS_INCOMPLETE
                        }
                    }
                    else -> STATUS_ERROR
                }
                item.reason = record.reason ?: ""
                item.endedAt = record.endedAt
                item.inherited = false
            }
        }
        save(userId, state)
    }

    private fun completedTargetsToday(userId: String, installedAt: Long): Set<String> {
        return try {
            val cal = java.util.Calendar.getInstance()
            PlanHistory.load(userId).filter { record ->
                if (record.status != PlanHistory.COMPLETED) return@filter false
                if (installedAt > 0L && record.startedAt < installedAt) return@filter false
                val c = java.util.Calendar.getInstance().apply { timeInMillis = record.startedAt }
                c.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR) &&
                    c.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
            }.map { it.target }.toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }
}

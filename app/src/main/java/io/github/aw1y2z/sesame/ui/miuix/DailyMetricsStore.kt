package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.data.ai.AiDailySummary
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 「每天 × 域」指标层：账本给任务完成度，运行记录给耗时与风控。
 * 只读聚合，不新增落盘文件；界面与 AI 摘要共用同一口径。
 */
internal object DailyMetricsStore {

    data class DomainDay(
        val domain: String,
        val label: String,
        val done: Int,
        val total: Int,
        val runs: Int,
        val completed: Int,
        val risk: Int,
        val minutes: Int
    ) {
        val ratio: Float
            get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    data class DayMetrics(
        val day: String,
        val domains: List<DomainDay>
    ) {
        val done: Int get() = domains.sumOf { it.done }
        val total: Int get() = domains.sumOf { it.total }
        val runs: Int get() = domains.sumOf { it.runs }
        val completedRuns: Int get() = domains.sumOf { it.completed }
        val riskRuns: Int get() = domains.sumOf { it.risk }
        val minutes: Int get() = domains.sumOf { it.minutes }
    }

    private val labels = mapOf(
        "forest" to "森林",
        "farm" to "庄园",
        "ocean" to "海洋",
        "orchard" to "果园",
        "species" to "物种",
        "golden" to "金豆",
        "sports" to "运动",
        "dodo" to "待办",
        "member" to "会员"
    )

    private val order = listOf("forest", "farm", "ocean", "orchard", "species", "golden", "sports", "dodo", "member")

    fun labelOf(domain: String): String = labels[domain] ?: domain

    fun domainsInOrder(): List<String> = order

    fun load(userId: String?, days: Int): List<DayMetrics> {
        if (userId.isNullOrEmpty()) return emptyList()
        val dayList = (days - 1 downTo 0).map { offset ->
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        }
        val history = runCatching { PlanHistory.load(userId) }.getOrDefault(emptyList())
        val byDay = history.groupBy { record ->
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(record.startedAt))
        }
        return dayList.mapNotNull { day ->
            val summary = runCatching { TaskLedgerStore.loadSummary(userId, day) }.getOrNull()
            val runs = byDay[day].orEmpty()
            if (summary == null && runs.isEmpty()) {
                return@mapNotNull null
            }
            val domainMap = LinkedHashMap<String, DomainDay>()
            val ledgerDomains = summary?.optJSONObject("domains")
            if (ledgerDomains != null) {
                val keys = ledgerDomains.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val node = ledgerDomains.optJSONObject(key) ?: continue
                    val domain = key.substringBefore('.')
                    val prev = domainMap[domain] ?: DomainDay(domain, labelOf(domain), 0, 0, 0, 0, 0, 0)
                    domainMap[domain] = prev.copy(
                        done = prev.done + node.optInt("claimed", 0),
                        total = prev.total + (node.optInt("total", 0) - node.optInt("blacklisted", 0)).coerceAtLeast(0)
                    )
                }
            }
            runs.forEach { record ->
                val domain = record.target.substringBefore('.')
                val prev = domainMap[domain] ?: DomainDay(domain, labelOf(domain), 0, 0, 0, 0, 0, 0)
                domainMap[domain] = prev.copy(
                    runs = prev.runs + 1,
                    completed = prev.completed + if (record.status == PlanHistory.COMPLETED) 1 else 0,
                    risk = prev.risk + if (record.status == PlanHistory.RISK_STOPPED) 1 else 0,
                    minutes = prev.minutes + ((record.endedAt - record.startedAt).coerceAtLeast(0L) / 60_000L).toInt()
                )
            }
            DayMetrics(day, domainMap.values.sortedBy { order.indexOf(it.domain).let { i -> if (i < 0) 99 else i } })
        }
    }

    /** 近 N 天每个域的完成用时中位数（只统计成功运行，样本 ≥2 才给值）。 */
    fun durationMedians(userId: String?, days: Int): Map<String, Pair<Int, Int>> {
        if (userId.isNullOrEmpty()) return emptyMap()
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val history = runCatching { PlanHistory.load(userId) }.getOrDefault(emptyList())
        return history
            .filter { it.startedAt >= cutoff && it.status == PlanHistory.COMPLETED }
            .filter { it.endedAt > it.startedAt }
            .groupBy { it.target.substringBefore('.') }
            .mapValues { (_, list) ->
                val minutes = list.map { ((it.endedAt - it.startedAt) / 60_000L).toInt().coerceAtLeast(1) }.sorted()
                minutes[minutes.size / 2] to minutes.size
            }
            .filterValues { it.second >= 2 }
    }

    /** AI 每日摘要（近 N 天，新→旧）。 */
    fun aiSummaries(days: Int): List<JSONObject> = runCatching { AiDailySummary.recent(days) }.getOrDefault(emptyList())

    fun suggestionText(summary: JSONObject): String {
        val suggestions: JSONArray = summary.optJSONArray("suggestions") ?: return ""
        val items = ArrayList<String>()
        for (i in 0 until suggestions.length()) {
            val value = suggestions.optString(i, "")
            if (value.isNotEmpty()) items.add(value)
        }
        return items.joinToString("；")
    }
}

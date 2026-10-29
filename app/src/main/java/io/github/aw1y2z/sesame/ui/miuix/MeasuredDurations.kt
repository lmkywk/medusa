package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.data.plan.PlanHistory

/**
 * 用运行记录里的实际用时替代目录里的估算。
 * 样本不足（少于 [MIN_SAMPLES] 次成功记录）时返回 null，调用方回退到估算值。
 */
object MeasuredDurations {

    private const val MIN_SAMPLES = 2
    private const val MAX_SAMPLES = 20
    private const val CACHE_TTL_MS = 60_000L

    private var cachedUser: String = ""
    private var cachedAt: Long = 0L

    /** target -> (中位分钟数, 样本数) */
    private val cache = HashMap<String, Pair<Int, Int>>()

    fun samples(userId: String?, target: String): Pair<Int, Int>? {
        if (userId.isNullOrEmpty() || target.isBlank()) return null
        ensureLoaded(userId)
        return cache[target]
    }

    fun minutesOf(userId: String?, target: String, fallback: Int): Int =
        samples(userId, target)?.first ?: fallback

    private fun ensureLoaded(userId: String) {
        val now = System.currentTimeMillis()
        if (userId == cachedUser && now - cachedAt < CACHE_TTL_MS) return
        cachedUser = userId
        cachedAt = now
        cache.clear()
        val records = runCatching { PlanHistory.load(userId) }.getOrDefault(emptyList())
        records
            .filter { it.status == PlanHistory.COMPLETED && it.durationMs() > 0L && it.target.isNotBlank() }
            .groupBy { it.target }
            .forEach { (target, list) ->
                val durations = list
                    .sortedByDescending { it.startedAt }
                    .take(MAX_SAMPLES)
                    .map { it.durationMs() }
                    .sorted()
                if (durations.size < MIN_SAMPLES) return@forEach
                val median = durations[durations.size / 2]
                cache[target] = ((median + 30_000L) / 60_000L).toInt().coerceAtLeast(1) to durations.size
            }
    }
}

package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.data.plan.Plan
import io.github.aw1y2z.sesame.data.plan.PlanTargets

internal data class PlanConflict(val first: Plan, val second: Plan)

/** 只看启用计划的窗口相交；只用于提醒，不参与调度决策。 */
internal fun collectPlanConflicts(plans: List<Plan>): List<PlanConflict> {
    val enabled = plans.filter { it.isEnabled }
    val result = ArrayList<PlanConflict>()
    for (i in enabled.indices) {
        for (j in i + 1 until enabled.size) {
            // 默认编排本来就把 05:00 一次性块和 07:00 能量窗设计成串行排队，
            // 不把“默认 vs 默认”的重叠当成用户需要处理的冲突。
            if (enabled[i].isDefaultPlan && enabled[j].isDefaultPlan) {
                continue
            }
            if (windowsOverlap(enabled[i], enabled[j])) {
                result.add(PlanConflict(enabled[i], enabled[j]))
            }
        }
    }
    return result
}

/** 编辑保存时用：候选时间段与其它启用计划是否相交；excludeId 排除正在编辑的计划。 */
internal fun overlappingPlans(
    start: String,
    end: String,
    plans: List<Plan>,
    excludeId: String? = null
): List<Plan> {
    val candidateStart = minutes(start) ?: return emptyList()
    val candidateEnd = minutes(end) ?: return emptyList()
    if (candidateEnd <= candidateStart) {
        return emptyList()
    }
    return plans.filter { plan ->
        plan.isEnabled && plan.id != excludeId && overlaps(candidateStart, candidateEnd, plan)
    }
}

private fun windowsOverlap(first: Plan, second: Plan): Boolean {
    val start = minutes(first.windowStart) ?: return false
    val end = minutes(first.windowEnd) ?: return false
    if (end <= start) {
        return false
    }
    return overlaps(start, end, second)
}

private fun overlaps(start: Int, end: Int, other: Plan): Boolean {
    val otherStart = minutes(other.windowStart) ?: return false
    val otherEnd = minutes(other.windowEnd) ?: return false
    if (otherEnd <= otherStart) {
        return false
    }
    return start < otherEnd && otherStart < end
}

private fun minutes(value: String): Int? {
    if (!PlanTargets.isValidTime(value)) {
        return null
    }
    return value.substring(0, 2).toInt() * 60 + value.substring(3, 5).toInt()
}

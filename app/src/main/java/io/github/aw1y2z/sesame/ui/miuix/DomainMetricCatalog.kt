package io.github.aw1y2z.sesame.ui.miuix

import android.content.SharedPreferences

import org.json.JSONObject
import java.util.Locale

internal data class DomainMetricOption(
    val id: String,
    val label: String,
    val unit: String
)

/** 域卡小轮的候选指标；与主显示共用 ID 的指标会被自动避开 */
internal object DomainMetricCatalog {

    val defaultMainIds = setOf(
        "forest.day", "forest.rob",
        "farm.eggs", "farm.drawn",
        "ocean.cleaned", "ocean.total",
        "plan.steps", "plan.completed", "plan.pending"
    )

    /** 卡面默认优先展示的指标；未单独选择过卡面数据时生效。 */
    val defaultCardIds = mapOf(
        "ocean" to listOf("ocean.welcome", "ocean.fish", "ocean.rubbish", "ocean.puzzle"),
        "farm" to listOf("farm.harvestEggs", "farm.food", "farm.eggsStock"),
        "orchard" to listOf("orchard.spreadLeft", "orchard.tree", "orchard.factory"),
        "species" to listOf("species.drawLeft", "species.help", "species.cards"),
        "golden" to listOf("golden.balance", "golden.pending", "golden.draw"),
        "forest" to listOf("forest.bubbles", "forest.watered", "forest.robbed"),
        "sports" to listOf("sports.walk", "sports.balance", "sports.sign"),
        "protect" to listOf("protect.projects", "protect.carbon", "protect.beaches"),
        "greenAction" to listOf("green.today", "green.streak", "green.checkins"),
        "member" to listOf("member.pointClaimable", "member.tasks", "member.sign"),
        "stall" to listOf("stall.coin", "stall.tasks", "stall.shops")
    )

    /** 每个域各自升级；改某个域的默认值时只 bump 该域，不覆盖其他域的自定义。 */
    private val defaultCardVersions = mapOf(
        "ocean" to 1,
        "farm" to 1,
        "orchard" to 2,
        "species" to 1,
        "golden" to 1,
        "forest" to 1,
        "sports" to 2,
        "protect" to 1,
        "greenAction" to 1
    )

    private fun defaultCardVersionOf(key: String): Int = defaultCardVersions[key] ?: 1

    /** 一次性把旧版卡面偏好迁移到新默认；之后用户自定义不会再被覆盖。 */
    fun migrateDefaults(prefs: SharedPreferences): Boolean {
        var changed = false
        val editor = prefs.edit()
        for ((key, ids) in defaultCardIds) {
            val version = defaultCardVersionOf(key)
            if (prefs.getInt("domain_metrics_version_$key", 0) < version) {
                editor.putString("domain_metrics_$key", ids.joinToString(","))
                editor.putInt("domain_metrics_version_$key", version)
                changed = true
            }
        }
        if (changed) {
            editor.apply()
        }
        return changed
    }

    val forest = listOf(
        DomainMetricOption("forest.day", "今日收取", "g"),
        DomainMetricOption("forest.totalCollected", "累计收取", "g"),
        DomainMetricOption("forest.watered", "今日浇水", "g"),
        DomainMetricOption("forest.rob", "收好友", "g"),
        DomainMetricOption("forest.robbed", "被好友收", "g"),
        DomainMetricOption("forest.bubbles", "可收能量球", "个"),
        DomainMetricOption("forest.switches", "已开启", "项"),
        DomainMetricOption("forest.todayRuns", "今日执行", "轮"),
        DomainMetricOption("forest.recent", "近3天", "轮")
    )

    val greenAction = listOf(
        DomainMetricOption("green.today", "今日打卡", ""),
        DomainMetricOption("green.remaining", "待打卡动作", "个"),
        DomainMetricOption("green.streak", "已坚持", "天"),
        DomainMetricOption("green.checkins", "已打卡", "次"),
        DomainMetricOption("green.medals", "勋章", "个")
    )

    val protect = listOf(
        DomainMetricOption("protect.projects", "可保护项目", "个"),
        DomainMetricOption("protect.certs", "已获证书", "个"),
        DomainMetricOption("protect.carbon", "可助力活动", "个"),
        DomainMetricOption("protect.beaches", "可保护海滩", "片"),
        DomainMetricOption("protect.cooperate", "合种", "个"),
        DomainMetricOption("protect.switches", "已开启", "项")
    )

    val ancient = listOf(
        DomainMetricOption("ancient.today", "今日执行", "轮"),
        DomainMetricOption("ancient.switches", "已开启", "项"),
        DomainMetricOption("ancient.recent", "近3天", "轮")
    )

    val farm = listOf(
        DomainMetricOption("farm.eggs", "今日捐蛋", "颗"),
        DomainMetricOption("farm.totalEggs", "累计捐蛋", "颗"),
        DomainMetricOption("farm.drawn", "今日抽奖", "次"),
        DomainMetricOption("farm.harvestEggs", "储备蛋", "颗"),
        DomainMetricOption("farm.food", "饲料", "g"),
        DomainMetricOption("farm.eggsStock", "库存蛋", "颗"),
        DomainMetricOption("farm.manure", "可收肥料", "个"),
        DomainMetricOption("farm.away", "离家小鸡", "只"),
        DomainMetricOption("farm.rewardFriends", "待打赏", "人"),
        DomainMetricOption("farm.parkBox", "乐园宝箱", "个"),
        DomainMetricOption("farm.parkTasks", "乐园可领", "项"),
        DomainMetricOption("farm.kitchen", "可做菜", "次"),
        DomainMetricOption("farm.diary", "日记未读", "篇"),
        DomainMetricOption("farm.switches", "已开启", "项")
    )

    val orchard = listOf(
        DomainMetricOption("orchard.fertilizer", "今日肥料", "g"),
        DomainMetricOption("orchard.spreadLeft", "可施肥", "次"),
        DomainMetricOption("orchard.tree", "果树进度", ""),
        DomainMetricOption("orchard.balance", "肥料余额", "g"),
        DomainMetricOption("orchard.eggs", "可砸金蛋", "个"),
        DomainMetricOption("orchard.factory", "可收肥料罐", "罐"),
        DomainMetricOption("orchard.sign", "签到", ""),
        DomainMetricOption("orchard.switches", "已开启", "项")
    )

    val ocean = listOf(
        DomainMetricOption("ocean.cleaned", "今日清理", "次"),
        DomainMetricOption("ocean.total", "累计清理", "块"),
        DomainMetricOption("ocean.welcome", "可找回", "个"),
        DomainMetricOption("ocean.fish", "可捞鱼", "次"),
        DomainMetricOption("ocean.rubbish", "自家垃圾", "块"),
        DomainMetricOption("ocean.puzzle", "修复海域", ""),
        DomainMetricOption("ocean.switches", "已开启", "项")
    )

    val sports = listOf(
        DomainMetricOption("sports.balance", "健康能量", "g"),
        DomainMetricOption("sports.steps", "今日步数", "步"),
        DomainMetricOption("sports.bubbles", "待收气泡", "个"),
        DomainMetricOption("sports.walk", "可捐步数", "步"),
        DomainMetricOption("sports.sign", "签到", ""),
        DomainMetricOption("sports.switches", "已开启", "项")
    )

    val species = listOf(
        DomainMetricOption("species.drawLeft", "可抽卡", "次"),
        DomainMetricOption("species.help", "可帮抽", "人"),
        DomainMetricOption("species.cards", "图鉴卡片", "张"),
        DomainMetricOption("species.medals", "可合成", "枚"),
        DomainMetricOption("species.props", "道具", "件"),
        DomainMetricOption("species.books", "图鉴册", "本"),
        DomainMetricOption("species.done", "今日抽卡", ""),
        DomainMetricOption("species.switches", "已开启", "项")
    )

    val golden = listOf(
        DomainMetricOption("golden.balance", "金豆余额", "豆"),
        DomainMetricOption("golden.pending", "待领金豆", "颗"),
        DomainMetricOption("golden.draw", "可抽", "次"),
        DomainMetricOption("golden.jars", "金豆罐", "罐"),
        DomainMetricOption("golden.exchange", "今日可换", "豆"),
        DomainMetricOption("golden.capacity", "金豆容量", "豆"),
        DomainMetricOption("golden.sign", "签到", ""),
        DomainMetricOption("golden.switches", "已开启", "项")
    )

    val member = listOf(
        DomainMetricOption("member.pointClaimable", "待领积分", "分"),
        DomainMetricOption("member.tasks", "会员任务", "个"),
        DomainMetricOption("member.sign", "签到", ""),
        DomainMetricOption("member.benefits", "权益", "项"),
        DomainMetricOption("common.switches", "已开启", "项")
    )

    val stall = listOf(
        DomainMetricOption("stall.coin", "新村币", ""),
        DomainMetricOption("stall.tasks", "新村任务", "个"),
        DomainMetricOption("stall.shops", "店铺", "个"),
        DomainMetricOption("stall.manure", "肥料", "份"),
        DomainMetricOption("common.switches", "已开启", "项")
    )

    val generic = listOf(
        DomainMetricOption("common.today", "今日执行", "轮"),
        DomainMetricOption("common.recent", "近3天", "轮"),
        DomainMetricOption("common.switches", "已开启", "项")
    )

    fun forKey(key: String): List<DomainMetricOption> = when (DomainCatalog.metricKeyOf(key)) {
        "forest" -> forest
        "greenAction" -> greenAction
        "ancient" -> ancient
        "farm" -> farm
        "orchard" -> orchard
        "ocean" -> ocean
        "sports" -> sports
        "species" -> species
        "golden" -> golden
        "protect" -> protect
        "member" -> member
        "stall" -> stall
        else -> generic
    }
}

/** 绿色行动卡面文本；状态来自 GreenActionStatus 的当日快照 */
internal object GreenActionStats {

    fun todayText(status: JSONObject?): String {
        val done = status?.optInt("todayDone", -1) ?: -1
        if (done < 0) return "—"
        val goal = status?.optInt("todayGoal", 5)?.coerceAtLeast(1) ?: 5
        return "$done/$goal"
    }

    fun remainingText(status: JSONObject?): String {
        val done = status?.optInt("todayDone", -1) ?: -1
        val total = status?.optInt("todayActions", -1) ?: -1
        if (done < 0 || total < 0) return "—"
        return (total - done).coerceAtLeast(0).toString()
    }

    fun streakText(status: JSONObject?): String {
        val days = status?.optInt("streakDays", -1) ?: -1
        return if (days >= 0) days.toString() else "—"
    }

    fun totalText(status: JSONObject?): String {
        val total = status?.optInt("totalCheckins", -1) ?: -1
        return if (total >= 0) String.format(Locale.CHINA, "%,d", total) else "—"
    }

    fun medalText(status: JSONObject?): String {
        val medals = status?.optInt("medals", -1) ?: -1
        return if (medals >= 0) medals.toString() else "—"
    }
}

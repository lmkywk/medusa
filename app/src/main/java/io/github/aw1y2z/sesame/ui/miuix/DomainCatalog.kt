package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.ui.graphics.Color
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import io.github.aw1y2z.sesame.model.task.antDodo.AntDodo
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm
import io.github.aw1y2z.sesame.model.task.antForest.AntForestV2
import io.github.aw1y2z.sesame.model.task.antMember.AntMember
import io.github.aw1y2z.sesame.model.task.antOcean.AntOcean
import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchard
import io.github.aw1y2z.sesame.model.task.antSports.AntSports
import io.github.aw1y2z.sesame.model.task.antStall.AntStall
import io.github.aw1y2z.sesame.model.task.goldenbeans.goldenbeans
import io.github.aw1y2z.sesame.model.task.protectEcology.ProtectEcology

/** 域身份的单一来源：首页卡、域页 key、指标目录和任务进度都从这里取。 */
internal data class DomainEntry(
    val key: String,
    val title: String,
    val groupCode: String,
    val scope: String,
    val taskTarget: String?,
    val metricKey: String,
    val defaultBg: Int,
    val fallbackBg: List<Color>,
    val modelClass: Class<out Model>?,
    val showRun: Boolean = true,
    val showInHome: Boolean = true,
    val targetPrefixes: List<String> = emptyList(),
    val exactTargets: Set<String> = emptySet()
) {
    fun matchesTarget(target: String): Boolean {
        if (target.isBlank()) {
            return false
        }
        return target in exactTargets || targetPrefixes.any { target.startsWith(it) }
    }
}

internal object DomainCatalog {

    val entries: List<DomainEntry> = listOf(
        DomainEntry(
            key = "forest",
            title = "蚂蚁森林",
            groupCode = "FOREST",
            scope = PlanTargets.FOREST_COLLECT,
            taskTarget = PlanTargets.FOREST_DAILY,
            metricKey = "forest",
            defaultBg = R.drawable.home_forest,
            fallbackBg = listOf(Color(0xFF123A2B), Color(0xFF0B5D58)),
            modelClass = AntForestV2::class.java,
            targetPrefixes = listOf("forest.")
        ),
        DomainEntry(
            key = "sports",
            title = "医疗健康·运动",
            groupCode = "SPORTS",
            scope = PlanTargets.SPORTS,
            taskTarget = PlanTargets.KINETIC,
            metricKey = "sports",
            defaultBg = R.drawable.home_sports,
            fallbackBg = listOf(Color(0xFF0F2F4A), Color(0xFF2E7FB8)),
            modelClass = AntSports::class.java,
            targetPrefixes = listOf("sports.")
        ),
        DomainEntry(
            key = "farm",
            title = "蚂蚁庄园",
            groupCode = "FARM",
            scope = PlanTargets.FARM_DAILY,
            taskTarget = PlanTargets.FARM,
            metricKey = "farm",
            defaultBg = R.drawable.home_farm,
            fallbackBg = listOf(Color(0xFF4A2F10), Color(0xFFF5A623)),
            modelClass = AntFarm::class.java,
            targetPrefixes = listOf("farm.")
        ),
        DomainEntry(
            key = "orchard",
            title = "芭芭农场",
            groupCode = "ORCHARD",
            scope = PlanTargets.ORCHARD,
            taskTarget = PlanTargets.ORCHARD_DAILY,
            metricKey = "orchard",
            defaultBg = R.drawable.home_orchard,
            fallbackBg = listOf(Color(0xFF1E3B14), Color(0xFF4C8B2B)),
            modelClass = AntOrchard::class.java,
            targetPrefixes = listOf("orchard.")
        ),
        DomainEntry(
            key = "ocean",
            title = "神奇海洋",
            groupCode = "OCEAN",
            scope = PlanTargets.OCEAN_REWARD,
            taskTarget = PlanTargets.OCEAN,
            metricKey = "ocean",
            defaultBg = R.drawable.home_ocean,
            fallbackBg = listOf(Color(0xFF0A2A44), Color(0xFF0D3D69)),
            modelClass = AntOcean::class.java,
            targetPrefixes = listOf("ocean.")
        ),
        DomainEntry(
            key = "species",
            title = "神奇物种",
            groupCode = "SPECIES",
            scope = PlanTargets.DODO,
            taskTarget = PlanTargets.DODO,
            metricKey = "species",
            defaultBg = R.drawable.home_species,
            fallbackBg = listOf(Color(0xFF1B2E5A), Color(0xFF2F9BFF)),
            modelClass = AntDodo::class.java,
            targetPrefixes = listOf("species."),
            exactTargets = setOf(PlanTargets.DODO)
        ),
        DomainEntry(
            key = "golden",
            title = "金豆夺宝",
            groupCode = "GOLDENBEANS",
            scope = PlanTargets.GOLDEN,
            taskTarget = PlanTargets.GOLDEN,
            metricKey = "golden",
            defaultBg = R.drawable.home_golden,
            fallbackBg = listOf(Color(0xFF3B2E08), Color(0xFFB98B1E)),
            modelClass = goldenbeans::class.java,
            exactTargets = setOf(PlanTargets.GOLDEN)
        ),
        DomainEntry(
            key = "protect",
            title = "生态保护",
            groupCode = "PROTECT",
            scope = PlanTargets.PROTECT,
            taskTarget = null,
            metricKey = "protect",
            defaultBg = R.drawable.home_eco,
            fallbackBg = listOf(Color(0xFF0E3B33), Color(0xFF14584C)),
            modelClass = ProtectEcology::class.java,
            exactTargets = setOf(PlanTargets.PROTECT)
        ),
        DomainEntry(
            key = "greenAction",
            title = "绿色行动",
            groupCode = "FOREST",
            scope = PlanTargets.FOREST_COLLECT,
            taskTarget = null,
            metricKey = "greenAction",
            defaultBg = R.drawable.home_green,
            fallbackBg = listOf(Color(0xFF0D3B2E), Color(0xFF1E7A5A)),
            modelClass = AntForestV2::class.java,
            showRun = false,
            exactTargets = setOf(PlanTargets.FOREST_COLLECT)
        ),
        DomainEntry(
            key = "member",
            title = "会员",
            groupCode = "MEMBER",
            scope = PlanTargets.MEMBER,
            taskTarget = null,
            metricKey = "member",
            defaultBg = R.drawable.home_member,
            fallbackBg = listOf(Color(0xFF241A4D), Color(0xFF7B61FF)),
            modelClass = AntMember::class.java,
            exactTargets = setOf(PlanTargets.MEMBER)
        ),
        DomainEntry(
            key = "stall",
            title = "蚂蚁新村",
            groupCode = "STALL",
            scope = PlanTargets.STALL,
            taskTarget = null,
            metricKey = "stall",
            defaultBg = R.drawable.home_village,
            fallbackBg = listOf(Color(0xFF3A2410), Color(0xFF8B5E2A)),
            modelClass = AntStall::class.java,
            exactTargets = setOf(PlanTargets.STALL)
        ),
        // 域设置页存在、首页没有独立卡片；保留身份供 DomainRegistry / 指标目录校验。
        DomainEntry(
            key = "ancient",
            title = "古树",
            groupCode = "PROTECT",
            scope = PlanTargets.ANCIENT,
            taskTarget = null,
            metricKey = "ancient",
            defaultBg = R.drawable.home_eco,
            fallbackBg = listOf(Color(0xFF0E3B33), Color(0xFF14584C)),
            modelClass = ProtectEcology::class.java,
            showRun = false,
            showInHome = false,
            exactTargets = setOf(PlanTargets.ANCIENT)
        )
    )

    val homeEntries: List<DomainEntry> = entries.filter { it.showInHome }

    private val index: Map<String, DomainEntry> = entries.associateBy { it.key }

    fun byKey(key: String?): DomainEntry? = if (key == null) null else index[key]

    fun metricKeyOf(key: String?): String = byKey(key)?.metricKey ?: "generic"
}

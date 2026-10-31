package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.data.ledger.FarmCompetitionStatus
import io.github.aw1y2z.sesame.data.ledger.FarmStatus
import io.github.aw1y2z.sesame.data.ledger.FarmSunflowerStatus
import io.github.aw1y2z.sesame.data.ledger.FarmTaskLedger
import io.github.aw1y2z.sesame.data.ledger.ForestBackpackStatus
import io.github.aw1y2z.sesame.data.ledger.ForestIpStatus
import io.github.aw1y2z.sesame.data.ledger.ForestParkTaskLedger
import io.github.aw1y2z.sesame.data.ledger.ForestPatrolTaskLedger
import io.github.aw1y2z.sesame.data.ledger.ForestStatus
import io.github.aw1y2z.sesame.data.ledger.ForestTaskLedger
import io.github.aw1y2z.sesame.data.ledger.GoldenBeansStatus
import io.github.aw1y2z.sesame.data.ledger.GoldenBeansTaskLedger
import io.github.aw1y2z.sesame.data.ledger.GreenActionStatus
import io.github.aw1y2z.sesame.data.ledger.KineticStatus
import io.github.aw1y2z.sesame.data.ledger.KineticTaskLedger
import io.github.aw1y2z.sesame.data.ledger.OceanStatus
import io.github.aw1y2z.sesame.data.ledger.OceanTaskLedger
import io.github.aw1y2z.sesame.data.ledger.OrchardChallengeTaskLedger
import io.github.aw1y2z.sesame.data.ledger.OrchardDrawTaskLedger
import io.github.aw1y2z.sesame.data.ledger.OrchardParkTaskLedger
import io.github.aw1y2z.sesame.data.ledger.OrchardStatus
import io.github.aw1y2z.sesame.data.ledger.OrchardTaskLedger
import io.github.aw1y2z.sesame.data.ledger.ProtectStatus
import io.github.aw1y2z.sesame.data.ledger.SpeciesStatus
import io.github.aw1y2z.sesame.data.ledger.SpeciesTaskLedger
import io.github.aw1y2z.sesame.data.ledger.OceanWelcomeStatus
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.data.ocean.OceanCleanQuota
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanRuntime
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import io.github.aw1y2z.sesame.util.RiskGuard
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.ToastUtil
import java.util.Calendar
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MiuixDomainActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_DOMAIN_KEY = "domainKey"
        const val EXTRA_DOMAIN_TITLE = "domainTitle"
        const val EXTRA_GROUP_CODE = "groupCode"
        const val EXTRA_USER_ID = "userId"
    }

    private var domainKey: String? = null
    private var domainTitle: String? = null
    private var groupCode: String? = null
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domainKey = intent.getStringExtra(EXTRA_DOMAIN_KEY)
        domainTitle = intent.getStringExtra(EXTRA_DOMAIN_TITLE)
        groupCode = intent.getStringExtra(EXTRA_GROUP_CODE)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        ConfigPreload.prepare(userId)
        setAppContent {
            val spec = DomainRegistry.get(domainKey) ?: run {
                val key = domainKey
                val group = ModelGroup.entries.firstOrNull { it.name == groupCode }
                if (key != null && group != null) {
                    DomainRegistry.generic(key, domainTitle ?: group.getName(), group)
                } else {
                    null
                }
            }
            if (spec == null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("域不存在：${domainKey ?: "未指定"}", color = MiuixTheme.colorScheme.error)
                }
            } else {
                DomainContent(this, spec, userId)
            }
        }
    }
}

@Composable
private fun DomainContent(
    activity: MiuixDomainActivity,
    spec: DomainSpec,
    userId: String?
) {
    var version by remember { mutableIntStateOf(0) }
    val prefs = remember { activity.getSharedPreferences("medusa_home_v2", Context.MODE_PRIVATE) }
    val dataOptions = remember(spec.key) { DomainMetricCatalog.forKey(spec.key) }
    var showDataPicker by remember { mutableStateOf(false) }
    var showBackpack by remember { mutableStateOf(false) }
    val modelConfigs = remember(spec.group, version) {
        Model.getGroupModelConfig(spec.group).values.filter { it.fields.isNotEmpty() }
    }
    val allFields = remember(modelConfigs, spec.primaryModelCode) {
        val list = ArrayList<ModelField<*>>()
        // 主模型优先，保证 enable 等同名域级字段取到本域自己的模型
        val ordered = if (spec.primaryModelCode == null) modelConfigs
        else modelConfigs.sortedBy { if (it.code == spec.primaryModelCode) 0 else 1 }
        ordered.forEach { mc ->
            mc.getModelField("enable")?.let { list.add(it) }
            list.addAll(mc.fields.values)
        }
        list.distinctBy { it.code }
    }
    var uiTick by remember { mutableIntStateOf(0) }
    val assignments = remember(spec, allFields, uiTick) {
        val map = LinkedHashMap<String, MutableList<ModelField<*>>>()
        spec.cards.forEach { map[it.id] = ArrayList() }
        val domainFields = allFields.filterNot(spec.exclude)
        if (spec.onlyMatched) {
            val assignedCodes = HashSet<String>()
            spec.cards.forEach { card ->
                val matched = domainFields.filter(card.match)
                    .filter { field -> field.code?.let { assignedCodes.add(it) } ?: false }
                map[card.id] = matched.toMutableList()
            }
            // 未被任何卡片匹配的域字段统一收进置顶“本域设置”，避免 enable 等设置被藏掉
            val settings = spec.cards.firstOrNull { it.id.endsWith(".settings") }
            if (settings != null) {
                val leftovers = domainFields.filter { field ->
                    field.code == null || field.code !in assignedCodes
                }
                map[settings.id]?.addAll(leftovers)
            }
        } else {
            val settings = spec.cards.first()
            val others = spec.cards.drop(1)
            domainFields.forEach { field ->
                val card = others.firstOrNull { it.match(field) }
                map[card?.id ?: settings.id]!!.add(field)
            }
        }
        map
    }

    val history = remember(userId, version) { PlanHistory.load(userId ?: "") }
    val runtime = remember(userId, version) { PlanRuntime.load(userId ?: "") }
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) resumeTick++
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val oceanSummary = remember(userId, resumeTick) {
        if (spec.key == "ocean") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), OceanTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val fishSummary = remember(userId, resumeTick) {
        if (spec.key == "ocean") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), OceanTaskLedger.DOMAIN_FISH)
        } else {
            null
        }
    }
    val welcomeName = remember(userId, resumeTick) {
        if (spec.key == "ocean") OceanWelcomeStatus.loadTodayName(userId ?: "") else ""
    }
    val oceanCleanState = remember(userId, resumeTick) {
        if (spec.key == "ocean") OceanCleanQuota.read() else null
    }
    val oceanStatus = remember(userId, resumeTick) {
        if (spec.key == "ocean") OceanStatus.loadToday(userId ?: "") else null
    }
    val farmSummary = remember(userId, resumeTick) {
        if (spec.key == "farm") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), FarmTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val farmDrawSummary = remember(userId, resumeTick) {
        if (spec.key == "farm") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), FarmTaskLedger.DOMAIN_DRAW)
        } else {
            null
        }
    }
    val farmStatus = remember(userId, resumeTick) {
        if (spec.key == "farm") FarmStatus.loadToday(userId ?: "") else null
    }
    val competitionStatus = remember(userId, resumeTick) {
        if (spec.key == "farm") FarmCompetitionStatus.loadToday(userId ?: "") else null
    }
    val sunflowerStatus = remember(userId, resumeTick) {
        if (spec.key == "farm") FarmSunflowerStatus.loadToday(userId ?: "") else null
    }
    val forestStatus = remember(userId, resumeTick) {
        if (spec.key == "forest") ForestStatus.loadToday(userId ?: "") else null
    }
    val forestSummary = remember(userId, resumeTick) {
        if (spec.key == "forest") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), ForestTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val forestParkSummary = remember(userId, resumeTick) {
        if (spec.key == "forest") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), ForestParkTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val forestPatrolSummary = remember(userId, resumeTick) {
        if (spec.key == "forest") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), ForestPatrolTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val forestBackpack = remember(userId, resumeTick) {
        if (spec.key == "forest") ForestBackpackStatus.loadToday(userId ?: "") else null
    }
    val forestIp = remember(userId, resumeTick) {
        if (spec.key == "forest") ForestIpStatus.loadToday(userId ?: "") else null
    }
    val speciesStatus = remember(userId, resumeTick) {
        if (spec.key == "species") SpeciesStatus.loadToday(userId ?: "") else null
    }
    val speciesTaskSummary = remember(userId, resumeTick) {
        if (spec.key == "species") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), SpeciesTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val kineticSummary = remember(userId, resumeTick) {
        if (spec.key == "sports") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), KineticTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val kineticStatus = remember(userId, resumeTick) {
        if (spec.key == "sports") KineticStatus.loadToday(userId ?: "") else null
    }
    val sportsPaused = remember(userId, resumeTick) {
        spec.key == "sports" && (RiskGuard.isGlobalPaused() || RiskGuard.isPaused("sports"))
    }
    val goldenSummary = remember(userId, resumeTick) {
        if (spec.key == "golden") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), GoldenBeansTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val goldenStatus = remember(userId, resumeTick) {
        if (spec.key == "golden") GoldenBeansStatus.loadToday(userId ?: "") else null
    }
    val orchardSummary = remember(userId, resumeTick) {
        if (spec.key == "orchard") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), OrchardTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val orchardParkSummary = remember(userId, resumeTick) {
        if (spec.key == "orchard") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), OrchardParkTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val orchardDrawSummary = remember(userId, resumeTick) {
        if (spec.key == "orchard") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), OrchardDrawTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val orchardChallengeSummary = remember(userId, resumeTick) {
        if (spec.key == "orchard") {
            TaskLedgerStore.loadDomainSummary(userId ?: "", TaskLedgerStore.today(), OrchardChallengeTaskLedger.DOMAIN)
        } else {
            null
        }
    }
    val orchardStatus = remember(userId, resumeTick) {
        if (spec.key == "orchard") OrchardStatus.loadToday(userId ?: "") else null
    }
    val protectStatus = remember(userId, resumeTick) {
        if (spec.key == "protect") ProtectStatus.loadToday(userId ?: "") else null
    }
    val greenStatus = remember(userId, resumeTick) {
        if (spec.key == "greenAction") GreenActionStatus.loadToday(userId ?: "") else null
    }
    val targetPrefixes = remember(spec) {
        when (spec.key) {
            "greenAction" -> listOf("forest")
            "ancient" -> listOf("ancient")
            "protect" -> listOf("protect")
            "species" -> listOf("species", "dodo")
            "farm" -> listOf("farm")
            "ocean" -> listOf("ocean")
            "stall" -> listOf("stall")
            "orchard" -> listOf("orchard")
            "golden" -> listOf("golden")
            "sports" -> listOf("sports")
            "member" -> listOf("member")
            "forest" -> listOf("forest")
            else -> listOf(spec.group.name.lowercase())
        }
    }
    fun sameDay(millis: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }
    fun belongsToDomain(target: String): Boolean =
        targetPrefixes.any { target == it || target.startsWith("$it.") }
    val domainRunning = runtime.state == PlanRuntime.RUNNING && belongsToDomain(runtime.lastTarget)
    // 错过窗口的计划会写 SKIPPED，不能当作“已运行”；今天没真正启动过就不算。
    val domainRanToday = history.any {
        sameDay(it.startedAt) && belongsToDomain(it.target) && it.status != PlanHistory.SKIPPED
    }
    // 子卡有独立执行目标时，状态只看自己对应的目标，不能借整域账本。
    val cardTargets = remember(spec.key) {
        when (spec.key) {
            "forest" -> mapOf(
                "forest.home" to listOf(PlanTargets.FOREST_COLLECT),
                "forest.friends" to listOf(
                    PlanTargets.FOREST_DAILY, PlanTargets.FOREST_EXTERNAL, PlanTargets.FOREST_HUNT
                ),
                "forest.reward" to listOf(PlanTargets.FOREST_DAILY, PlanTargets.FOREST_EXTERNAL),
                "forest.green" to listOf(PlanTargets.FOREST_DAILY, PlanTargets.FOREST_EXTERNAL),
                "forest.ip" to listOf(PlanTargets.FOREST_IP),
                "forest.park" to listOf(PlanTargets.FOREST_DAILY, PlanTargets.FOREST_EXTERNAL),
                "forest.patrol" to listOf(
                    PlanTargets.FOREST_PATROL, PlanTargets.FOREST_DAILY, PlanTargets.FOREST_EXTERNAL
                ),
                "forest.backpack" to listOf(PlanTargets.FOREST_DAILY, PlanTargets.FOREST_EXTERNAL)
            )
            "ocean" -> mapOf(
                "ocean.reward" to listOf(PlanTargets.OCEAN_REWARD),
                "ocean.clean" to listOf(PlanTargets.OCEAN_CLEAN),
                "ocean.fish" to listOf(PlanTargets.OCEAN_FISH),
                "ocean.welcome" to listOf(PlanTargets.OCEAN_WELCOME)
            )
            "farm" -> mapOf(
                "farm.daily" to listOf(PlanTargets.FARM_DAILY),
                "farm.task" to listOf(PlanTargets.FARM_TASK),
                "farm.donate" to listOf(PlanTargets.FARM_DONATE, PlanTargets.FARM_COMPETITION),
                "farm.park" to listOf(PlanTargets.FARM_PARK),
                "farm.draw" to listOf(PlanTargets.FARM_DRAW),
                "farm.competition" to listOf(PlanTargets.FARM_COMPETITION),
                "farm.sunflower" to listOf(PlanTargets.FARM_SUNFLOWER),
                "farm.family" to listOf(PlanTargets.FARM_FAMILY),
                "farm.friend" to listOf(PlanTargets.FARM_FRIEND),
                "farm.kitchen" to listOf(PlanTargets.FARM_KITCHEN),
                "farm.life" to listOf(PlanTargets.FARM_LIFE)
            )
            "species" -> mapOf(
                "species.task" to listOf(PlanTargets.SPECIES_TASK),
                "species.draw" to listOf(PlanTargets.SPECIES_DRAW),
                "species.friend" to listOf(PlanTargets.SPECIES_FRIEND),
                "species.prop" to listOf(PlanTargets.SPECIES_PROP),
                "species.book" to listOf(PlanTargets.SPECIES_BOOK),
                "species.gift" to listOf(PlanTargets.SPECIES_GIFT)
            )
            "orchard" -> mapOf(
                "orchard.fertilizer" to listOf(PlanTargets.ORCHARD_FERTILIZER, PlanTargets.ORCHARD),
                "orchard.task" to listOf(PlanTargets.ORCHARD_TASK, PlanTargets.ORCHARD_DAILY, PlanTargets.ORCHARD),
                "orchard.sign" to listOf(PlanTargets.ORCHARD_SIGN, PlanTargets.ORCHARD_DAILY, PlanTargets.ORCHARD),
                "orchard.spread" to listOf(PlanTargets.ORCHARD_SPREAD, PlanTargets.ORCHARD),
                "orchard.park" to listOf(PlanTargets.ORCHARD_PARK, PlanTargets.ORCHARD_DAILY, PlanTargets.ORCHARD),
                "orchard.assist" to listOf(PlanTargets.ORCHARD_ASSIST, PlanTargets.ORCHARD_DAILY, PlanTargets.ORCHARD),
                "orchard.draw" to listOf(PlanTargets.ORCHARD_DRAW, PlanTargets.ORCHARD_DAILY, PlanTargets.ORCHARD),
                "orchard.challenge" to listOf(PlanTargets.ORCHARD_CHALLENGE, PlanTargets.ORCHARD_DAILY, PlanTargets.ORCHARD),
                "orchard.factory" to listOf(PlanTargets.ORCHARD_FACTORY, PlanTargets.ORCHARD_DAILY, PlanTargets.ORCHARD)
            )
            "sports" -> mapOf(
                "sports.walk" to listOf(PlanTargets.SPORTS),
                "sports.donate" to listOf(PlanTargets.SPORTS),
                "sports.tiyu" to listOf(PlanTargets.SPORTS),
                "sports.club" to listOf(PlanTargets.SPORTS),
                "sports.health" to listOf(PlanTargets.SPORTS),
                "kinetic.center" to listOf(PlanTargets.SPORTS, PlanTargets.KINETIC),
                "kinetic.sign" to listOf(PlanTargets.SPORTS, PlanTargets.KINETIC),
                "kinetic.task" to listOf(PlanTargets.SPORTS, PlanTargets.KINETIC),
                "kinetic.bubble" to listOf(PlanTargets.SPORTS, PlanTargets.KINETIC)
            )
            "golden" -> mapOf(
                "golden.sign" to listOf(PlanTargets.GOLDEN),
                "golden.task" to listOf(PlanTargets.GOLDEN),
                "golden.sprout" to listOf(PlanTargets.GOLDEN),
                "golden.miner" to listOf(PlanTargets.GOLDEN),
                "golden.game" to listOf(PlanTargets.GOLDEN),
                "golden.exchange" to listOf(PlanTargets.GOLDEN),
                "golden.mall" to listOf(PlanTargets.GOLDEN),
                "golden.overview" to listOf(PlanTargets.GOLDEN)
            )
            else -> emptyMap()
        }
    }
    fun cardRunning(card: DomainCardSpec): Boolean {
        val targets = cardTargets[card.id] ?: return domainRunning
        return runtime.state == PlanRuntime.RUNNING && targets.contains(runtime.lastTarget)
    }
    fun cardRanToday(card: DomainCardSpec): Boolean {
        val targets = cardTargets[card.id] ?: return domainRanToday
        return history.any {
            sameDay(it.startedAt) && targets.contains(it.target) && it.status != PlanHistory.SKIPPED
        }
    }

    val saveHandler = remember { Handler(Looper.getMainLooper()) }
    var pendingSave by remember { mutableStateOf<Runnable?>(null) }

    /**
     * 胶囊点击先只改内存并立即重绘；300ms 内的连续点击合并成一次落盘/广播，
     * 避免每点一次都在主线程写盘 + 重载整组配置造成迟滞。
     */
    fun scheduleSave() {
        uiTick++
        pendingSave?.let { saveHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val result = ConfigSync.saveQuiet(activity, userId)
            if (result.ok) {
                ConfigSync.notifyReload(activity, userId)
            } else {
                ToastUtil.show(activity, ConfigSync.failureMessage(result))
            }
        }
        pendingSave = runnable
        saveHandler.postDelayed(runnable, 300)
    }

    val masterOff = spec.key == "greenAction" && runCatching {
        Model.getGroupModelConfig(spec.group).values
            .firstNotNullOfOrNull { it.getModelField("ecoLife") }
            ?.value != true
    }.getOrDefault(false)

    val domainSettingsCard = spec.cards.firstOrNull {
        it.id in setOf("ocean.settings", "farm.settings", "orchard.settings", "species.settings",
            "sports.settings", "golden.settings")
    }
    val domainOff = domainSettingsCard?.let { card ->
        assignments[card.id].orEmpty()
            .filterIsInstance<BooleanModelField>()
            .firstOrNull { it.code == "enable" }?.value != true
    } ?: false

    fun openTaskList(domainKey: String, titleText: String) {
        activity.startActivity(
            Intent(activity, MiuixTaskListActivity::class.java).apply {
                putExtra(MiuixTaskListActivity.EXTRA_DOMAIN, domainKey)
                putExtra(MiuixTaskListActivity.EXTRA_TITLE, titleText)
                putExtra(MiuixTaskListActivity.EXTRA_USER_ID, userId)
            }
        )
    }

    /**
     * 页面里的字段是模型图副本；配置快照只认 ConfigV2 里的实时字段。
     * 保存前必须写到实时字段，否则会出现“界面变了但保存被跳过”。
     */
    fun liveConfigField(code: String): ModelField<*>? {
        val modelCode = if (code == "enable" && spec.primaryModelCode != null) {
            spec.primaryModelCode
        } else {
            Model.getGroupModelConfig(spec.group).values
                .firstOrNull { it.getModelField(code) != null }
                ?.code
                ?: return null
        }
        return ConfigV2.INSTANCE.getModelFields(modelCode)?.get(code)
    }

    fun openCard(card: DomainCardSpec) {
        if (spec.key == "ocean" && card.id == "ocean.reward") {
            openTaskList(OceanTaskLedger.DOMAIN, "海洋 · 奖励")
            return
        }
        val fields = assignments[card.id].orEmpty()
        if (fields.isEmpty()) {
            ToastUtil.show(activity, "「${card.title}」暂无可配置项")
            return
        }
        activity.startActivity(
            Intent(activity, MiuixGroupFieldsActivity::class.java).apply {
                putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, spec.group.name)
                putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                putExtra(MiuixGroupFieldsActivity.EXTRA_FIELD_CODES, fields.joinToString(",") { it.code })
                putExtra(MiuixGroupFieldsActivity.EXTRA_CARD_ID, card.id)
                putExtra(MiuixGroupFieldsActivity.EXTRA_CARD_TITLE, card.title)
            }
        )
    }

    /** 卡片详情页里的字段：数值、选项、好友名单合并平铺。 */
    fun detailCodes(card: DomainCardSpec): List<String> {
        if (spec.key == "forest") {
            val fields = assignments[card.id].orEmpty()
                .filterNot { it.code == "dressDetailList" || it.code == "dressDetailListClear" }
            if (card.id == "forest.settings") {
                return fields.mapNotNull { it.code }
            }
            // 绿色能量四项都是开关，没有其它可填字段；仍给一个详情入口，用来显示每项到底做什么
            if (card.id == "forest.green") {
                return fields.mapNotNull { it.code }
            }
            return fields.filterNot { it is BooleanModelField }.mapNotNull { it.code }
        }
        if (spec.key == "species") {
            return assignments[card.id].orEmpty()
                .filterNot { it is BooleanModelField }
                .mapNotNull { it.code }
        }
        if (spec.key == "protect") {
            val fields = assignments[card.id].orEmpty()
            if (card.id == "protect.settings") return fields.mapNotNull { it.code }
            return fields.filterNot { it is BooleanModelField }.mapNotNull { it.code }
        }
        if (spec.key == "orchard") {
            val fields = assignments[card.id].orEmpty()
            if (card.id == "orchard.settings") return fields.mapNotNull { it.code }
            return fields.filterNot { it is BooleanModelField }.mapNotNull { it.code }
        }
        if (spec.key == "sports") {
            return assignments[card.id].orEmpty()
                .filterNot { it is BooleanModelField && it.code == card.switchCode }
                .mapNotNull { it.code }
        }
        if (spec.key == "golden") {
            return assignments[card.id].orEmpty()
                .filterNot { it is BooleanModelField && it.code == card.switchCode }
                .mapNotNull { it.code }
        }
        if (spec.key != "farm") return emptyList()
        val codes = assignments[card.id].orEmpty().mapNotNull { it.code }.toSet()
        return (FARM_FRIEND_FIELDS[card.id].orEmpty() + FARM_VALUE_FIELDS[card.id].orEmpty())
            .filter { it in codes }
            .distinct()
    }

    fun openDetail(card: DomainCardSpec) {
        val codes = detailCodes(card)
        if (codes.isEmpty()) return
        activity.startActivity(
            Intent(activity, MiuixGroupFieldsActivity::class.java).apply {
                putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, spec.group.name)
                putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                putExtra(MiuixGroupFieldsActivity.EXTRA_FIELD_CODES, codes.joinToString(","))
                putExtra(MiuixGroupFieldsActivity.EXTRA_INCLUDE_HIDDEN, true)
                putExtra(MiuixGroupFieldsActivity.EXTRA_CARD_TITLE, card.title)
            }
        )
    }

    /** 亲密家庭：7 个选项直接在卡上做胶囊；任意一个开启时自动打开 family 总开关。 */
    fun familyCapsules(card: DomainCardSpec): List<FlatCapsule> {
        if (spec.key != "farm" || card.id != "farm.family") return emptyList()
        val fields = assignments[card.id].orEmpty()
        val options = fields.filterIsInstance<SelectModelField>()
            .firstOrNull { it.code == "familyOptions" } ?: return emptyList()
        val master = fields.filterIsInstance<BooleanModelField>()
            .firstOrNull { it.code == "family" }
        return options.getExpandValue().mapNotNull { item ->
            val id = item.id ?: return@mapNotNull null
            FlatCapsule(item.name ?: id, options.getValue().contains(id)) {
                val liveOptions = liveConfigField("familyOptions") as? SelectModelField ?: options
                val liveMaster = liveConfigField("family") as? BooleanModelField ?: master
                val next = LinkedHashSet(liveOptions.getValue())
                if (!next.remove(id)) {
                    next.add(id)
                }
                // 实时字段负责落盘；页面字段负责立即重绘
                liveOptions.setObjectValue(next)
                if (liveOptions !== options) {
                    options.setObjectValue(LinkedHashSet(next))
                }
                liveMaster?.setObjectValue(next.isNotEmpty())
                if (liveMaster !== master) {
                    master?.setObjectValue(next.isNotEmpty())
                }
                scheduleSave()
            }
        }
    }

    /** 域页手动直达：与首页补跑同一条 planRun 广播，走手动队列而不是只打开页面。 */
    fun sendTargetRun(target: String) {
        val calendar = Calendar.getInstance()
        val time = String.format(
            Locale.CHINA, "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)
        )
        activity.sendMedusaBroadcast(
            Intent("com.eg.android.AlipayGphone.sesame.planRun")
                .setPackage("com.eg.android.AlipayGphone")
                .putExtra("target", target)
                .putExtra("roundTime", time)
                .putExtra("ignoreCooldown", true)
        )
        ToastUtil.show(activity, "已发起执行：" + PlanTargets.labelOf(target))
    }

    fun cardFlatActions(card: DomainCardSpec): List<FlatAction> {
        val out = ArrayList<FlatAction>()
        when (card.id) {
            "ocean.reward" -> out.add(FlatAction("任务列表") {
                openTaskList(OceanTaskLedger.DOMAIN, "海洋 · 奖励")
            })
            "ocean.fish" -> {
                out.add(FlatAction("立即执行") { sendTargetRun(PlanTargets.OCEAN_FISH) })
                out.add(FlatAction("任务列表") {
                    openTaskList(OceanTaskLedger.DOMAIN_FISH, "海洋 · 捞鱼任务")
                })
            }
            "farm.task" -> out.add(FlatAction("任务列表") {
                openTaskList(FarmTaskLedger.DOMAIN, "庄园 · 饲料任务")
            })
            "farm.draw" -> out.add(FlatAction("任务列表") {
                openTaskList(FarmTaskLedger.DOMAIN_DRAW, "庄园 · 装扮抽奖")
            })
            "forest.reward" -> out.add(FlatAction("任务列表") {
                openTaskList(ForestTaskLedger.DOMAIN, "森林 · 领奖励")
            })
            "forest.park" -> out.add(FlatAction("任务列表") {
                openTaskList(ForestParkTaskLedger.DOMAIN, "森林 · 乐园任务")
            })
            "forest.patrol" -> {
                out.add(FlatAction("立即执行") { sendTargetRun(PlanTargets.FOREST_PATROL) })
                out.add(FlatAction("任务列表") {
                    openTaskList(ForestPatrolTaskLedger.DOMAIN, "森林 · 巡护任务")
                })
            }
            "forest.backpack" -> out.add(FlatAction("道具列表") { showBackpack = true })
            "species.task" -> out.add(FlatAction("任务列表") {
                openTaskList(SpeciesTaskLedger.DOMAIN, "神奇物种 · 任务")
            })
            "kinetic.task" -> out.add(FlatAction("任务列表") {
                openTaskList(KineticTaskLedger.DOMAIN, "运动 · 能量任务")
            })
            "kinetic.center" -> out.add(FlatAction("任务列表") {
                openTaskList(KineticTaskLedger.DOMAIN, "运动 · 能量任务")
            })
            "golden.task" -> out.add(FlatAction("任务列表") {
                openTaskList(GoldenBeansTaskLedger.DOMAIN, "金豆夺宝 · 任务")
            })
            "golden.overview" -> out.add(FlatAction("任务列表") {
                openTaskList(GoldenBeansTaskLedger.DOMAIN, "金豆夺宝 · 任务")
            })
            "orchard.task" -> out.add(FlatAction("任务列表") {
                openTaskList(OrchardTaskLedger.DOMAIN, "芭芭农场 · 肥料任务")
            })
            "orchard.park" -> out.add(FlatAction("任务列表") {
                openTaskList(OrchardParkTaskLedger.DOMAIN, "芭芭农场 · 农场乐园")
            })
            "orchard.draw" -> out.add(FlatAction("任务列表") {
                openTaskList(OrchardDrawTaskLedger.DOMAIN, "芭芭农场 · 阿肥寻宝记")
            })
            "orchard.challenge" -> out.add(FlatAction("任务列表") {
                openTaskList(OrchardChallengeTaskLedger.DOMAIN, "芭芭农场 · 限时挑战")
            })
        }
        // 通用规则：卡片有可执行目标就补一个“立即执行”，不再逐张手写。
        // 只认真正有独立执行分支的目标；外部页面目标不提供执行。
        if (out.none { it.label == "立即执行" }) {
            val runTarget = cardTargets[card.id]?.firstOrNull()
            if (runTarget != null
                && runTarget != PlanTargets.FOREST_EXTERNAL
                && PlanTargets.isValidTarget(runTarget)
            ) {
                out.add(0, FlatAction("立即执行") { sendTargetRun(runTarget) })
            }
        }
        return out
    }

    fun cardHasEntries(card: DomainCardSpec): Boolean {
        if (card.id in setOf(
                "ocean.reward", "ocean.fish", "farm.task", "farm.draw",
                "orchard.task", "orchard.park", "orchard.draw", "orchard.challenge",
                "kinetic.task", "kinetic.center", "golden.task", "golden.overview"
            )) return true
        return detailCodes(card).isNotEmpty()
    }

    val context = LocalContext.current
    val domainFieldCodes = remember(assignments) {
        assignments.values.flatten().mapNotNull { it.code }.toSet()
    }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
            }.onSuccess { text ->
                pendingImportText = text
                pendingImportName = uri.lastPathSegment ?: "所选文件"
                showImportConfirm = true
            }.onFailure {
                ToastUtil.show(activity, "读取失败：${it.message ?: "未知错误"}")
            }
        }
    }

    if (showImportConfirm) {
        ConfirmDialog(
            title = "导入「${spec.title}」设置",
            text = "将用「${pendingImportName ?: "所选文件"}」覆盖本域字段，其它域不受影响。\n导入前会先保存当前配置。",
            confirmText = "导入",
            dismissText = "取消",
            onConfirm = {
                showImportConfirm = false
                val text = pendingImportText
                pendingImportText = null
                pendingImportName = null
                if (text != null) {
                    DomainConfigIO.applyJson(text, spec, domainFieldCodes)
                        .onSuccess { count ->
                            val saved = ConfigSync.save(activity, userId, true)
                            if (saved.ok) {
                                ToastUtil.show(activity, "已导入 $count 项「${spec.title}」设置")
                                version++
                            } else {
                                ToastUtil.show(activity, ConfigSync.failureMessage(saved))
                            }
                        }
                        .onFailure {
                            ToastUtil.show(activity, "导入失败：${it.message ?: "未知错误"}")
                        }
                }
            },
            onDismiss = {
                showImportConfirm = false
                pendingImportText = null
                pendingImportName = null
            }
        )
    }

    val mainIds = remember {
        val raw = prefs.getString("main_metric_ids", null)
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: DomainMetricCatalog.defaultMainIds
    }
    val selectedDomainMetrics = remember(showDataPicker) {
        prefs.getString("domain_metrics_${spec.key}", null)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()
    }
    if (showDataPicker && dataOptions.isNotEmpty()) {
        DomainMetricPickerDialog(
            title = "${spec.title} · 卡片数据",
            options = dataOptions,
            mainIds = mainIds,
            selected = selectedDomainMetrics,
            onDismiss = { showDataPicker = false },
            onConfirm = { ids ->
                prefs.edit().putString("domain_metrics_${spec.key}", ids.joinToString(",")).apply()
                showDataPicker = false
                ToastUtil.show(activity, "已保存「${spec.title}」卡面数据")
            }
        )
    }
    if (showBackpack) {
        ForestBackpackDialog(
            items = forestBackpack?.optJSONArray("items"),
            onDismiss = { showBackpack = false }
        )
    }

    fun switchFieldOf(card: DomainCardSpec, booleans: List<BooleanModelField>): BooleanModelField? =
        card.switchCode?.let { wanted -> booleans.firstOrNull { it.code == wanted } }

    fun anySwitchOn(card: DomainCardSpec): Boolean =
        assignments[card.id].orEmpty().filterIsInstance<BooleanModelField>().any { it.value }

    fun taskReport(summary: org.json.JSONObject?): String? {
        if (summary == null) return null
        val pending = summary.optInt("pending", 0)
        val manual = summary.optInt("manual", 0)
        val blacklisted = summary.optInt("blacklisted", 0)
        return "待处理 $pending · 需手动 $manual" + if (blacklisted > 0) " · 未探明 $blacklisted" else ""
    }

    fun taskStatus(summary: org.json.JSONObject?, switchOn: Boolean): Pair<String, Color> {
        if (!switchOn) return "已关闭" to Color(0xFFE57373)
        if (summary == null) return "未同步" to Color(0xFFE57373)
        val actionable = summary.optInt("claimable", 0) + summary.optInt("untouched", 0)
        val inProgress = summary.optInt("inProgress", 0)
        val manual = summary.optInt("manual", 0)
        return when {
            actionable > 0 -> "有任务" to Color(0xFFF4C66F)
            inProgress > 0 -> "进行中" to MedusaColors.Blue
            manual > 0 -> "有需手动" to Color(0xFFE57373)
            else -> "已清" to MedusaColors.Green
        }
    }

    fun cleanQuotaToday(): org.json.JSONObject? {
        val state = oceanCleanState ?: return null
        val cal = Calendar.getInstance()
        val today = String.format(
            Locale.CHINA, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        return if (state.optString("day") == today) state else null
    }

    fun cleanWindowQuota(morning: Boolean, morningOn: Boolean, eveningOn: Boolean): Int =
        if (!(if (morning) morningOn else eveningOn)) {
            0
        } else if (morningOn && eveningOn) {
            OceanCleanQuota.BOTH_WINDOW_QUOTA
        } else {
            OceanCleanQuota.SINGLE_WINDOW_QUOTA
        }

    fun cleanWindowUsed(state: org.json.JSONObject?, morning: Boolean): Int =
        if (state == null) {
            0
        } else if (morning) {
            state.optInt("morningUsed", 0)
        } else {
            state.optInt("eveningUsed", 0)
        }

    fun greenCategoryText(type: String, fallback: String): String {
        val status = greenStatus ?: return fallback
        val categories = status.optJSONArray("categories") ?: return fallback
        for (i in 0 until categories.length()) {
            val category = categories.optJSONObject(i) ?: continue
            if (category.optString("type") != type) continue
            val done = category.optInt("done", 0)
            val total = category.optInt("total", 0)
            val todayDone = status.optInt("todayDone", 0)
            val goal = status.optInt("todayGoal", 5).coerceAtLeast(1)
            return "已打卡 $done/$total · 今日 $todayDone/$goal"
        }
        return fallback
    }

    fun flatSummaryText(card: DomainCardSpec): String? {
        if (card.id in setOf("ocean.settings", "farm.settings", "orchard.settings", "species.settings",
                "sports.settings", "golden.settings", "protect.settings", "green.settings")) {
            val enableCode = if (card.id == "green.settings") "ecoLife" else "enable"
            val enabled = assignments[card.id].orEmpty()
                .filterIsInstance<BooleanModelField>()
                .firstOrNull { it.code == enableCode }?.value == true
            return if (enabled) "本域已启用 · 点右侧进入更多设置"
            else "本域已关闭 · 点胶囊开启后计划才会执行"
        }
        return when (spec.key) {
            "ocean" -> when (card.id) {
                "ocean.reward" -> taskReport(oceanSummary)
                "ocean.fish" -> {
                    val parts = mutableListOf<String>()
                    taskReport(fishSummary)?.let { parts.add(it) }
                    val remain = oceanStatus?.optInt("fishRemainTouchChance", -1) ?: -1
                    if (remain >= 0) parts.add("可捞 $remain 次")
                    parts.joinToString(" · ").ifEmpty { null }
                }
                "ocean.welcome" -> {
                    val parts = mutableListOf<String>()
                    val ready = oceanStatus?.optInt("welcomeReady", -1) ?: -1
                    if (ready >= 0) parts.add("可找回 $ready 个")
                    val fixed = oceanStatus?.optInt("fixedSeaAreaNum", -1) ?: -1
                    val total = oceanStatus?.optInt("seaAreaNum", -1) ?: -1
                    if (fixed >= 0 && total > 0) parts.add("拼图 $fixed/$total")
                    if (welcomeName.isNotBlank()) parts.add("上次 $welcomeName")
                    parts.joinToString(" · ").ifEmpty { null }
                }
                "ocean.clean" -> {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val state = cleanQuotaToday()
                    if (state == null) {
                        if (hour < 7) "07:00 刷新后可清理" else null
                    } else {
                        val booleans = assignments[card.id].orEmpty().filterIsInstance<BooleanModelField>()
                        val morningOn = booleans.firstOrNull { it.code == "cleanMorning" }?.value == true
                        val eveningOn = booleans.firstOrNull { it.code == "cleanEvening" }?.value == true
                        val morningWindow = hour < 17
                        val quota = cleanWindowQuota(morningWindow, morningOn, eveningOn)
                        val used = cleanWindowUsed(state, morningWindow)
                        val daily = state.optInt("dailyUsed", 0)
                        val suffix = if (state.optBoolean("serverLimited", false)) " · 已达服务端上限" else ""
                        val rubbish = oceanStatus?.optInt("rubbishNumber", -1) ?: -1
                        val rubbishSuffix = if (rubbish >= 0) " · 自家垃圾 $rubbish 块" else ""
                        if (quota > 0) {
                            "本时段 $used/$quota · 今日 $daily/${OceanCleanQuota.DAILY_LIMIT}$suffix$rubbishSuffix"
                        } else {
                            "本时段未开启$rubbishSuffix"
                        }
                    }
                }
                else -> null
            }
            "farm" -> when (card.id) {
                "farm.task" -> taskReport(farmSummary)
                "farm.draw" -> taskReport(farmDrawSummary)
                "farm.daily" -> farmStatus?.let { st ->
                    val parts = mutableListOf<String>()
                    val food = st.optInt("foodStock", -1)
                    val limit = st.optInt("foodStockLimit", -1)
                    val trough = st.optInt("foodInTrough", -1)
                    val harvest = st.optDouble("harvestEggs", -1.0)
                    if (food >= 0) parts.add("饲料 $food" + if (limit > 0) "/$limit" else "")
                    if (trough >= 0) parts.add("食槽 $trough")
                    if (harvest >= 0) parts.add("储备蛋 " + String.format(Locale.CHINA, "%.0f", harvest))
                    val away = st.optInt("animalAway", 0)
                    if (away > 0) parts.add("离家 $away 只")
                    parts.joinToString(" · ").ifEmpty { null }
                }
                "farm.donate" -> farmStatus?.let { st ->
                    val parts = mutableListOf<String>()
                    val donationType = assignments[card.id].orEmpty()
                        .filterIsInstance<ChoiceModelField>()
                        .firstOrNull { it.code == "donationType" }?.value
                    val enabled = donationType == null || donationType != 0
                    val eggs = st.optDouble("benevolenceEggs", -1.0)
                    if (!enabled) {
                        parts.add("未启用")
                    } else if (st.optBoolean("charityDonationDoneToday", false)) {
                        parts.add("今日已捐")
                    } else if (eggs >= 1) {
                        parts.add("可捐")
                    } else {
                        parts.add("无蛋可捐")
                    }
                    if (eggs >= 0) parts.add("库存 ${eggs.toInt()} 颗")
                    val projects = st.optInt("donationProjectCount", 0)
                    if (projects > 0) parts.add("$projects 个项目")
                    parts.joinToString(" · ").ifEmpty { null }
                }
                "farm.competition" -> competitionStatus?.let { st ->
                    val love = st.optInt("loveValue", -1)
                    if (love < 0) {
                        null
                    } else {
                        val parts = mutableListOf("爱心值 $love")
                        val rank = st.optInt("rank", 0)
                        if (rank > 0) parts.add("第 $rank 名")
                        val donation = st.optInt("myDonation", 0)
                        if (donation > 0) parts.add("本周捐 $donation")
                        parts.joinToString(" · ")
                    }
                }
                "farm.sunflower" -> sunflowerStatus?.let { st ->
                    "葵花籽 ${st.optInt("seeds", 0)} 粒 · 本月已助 ${st.optInt("myDonation", 0)} 份"
                }
                "farm.park" -> farmStatus?.let { st ->
                    val parts = mutableListOf<String>()
                    val box = st.optInt("parkBoxQuota", -1)
                    if (box >= 0) parts.add("宝箱 $box")
                    val coin = st.optInt("parkCoinBalance", -1)
                    if (coin >= 0) parts.add("乐园币 $coin")
                    val claim = st.optInt("parkTaskClaimable", 0)
                    if (claim > 0) parts.add("可领 $claim")
                    val todo = st.optInt("parkTaskTodo", 0)
                    if (todo > 0) parts.add("待做 $todo")
                    parts.joinToString(" · ").ifEmpty { null }
                }
                "farm.kitchen" -> farmStatus?.let { st ->
                    val parts = mutableListOf<String>()
                    val cook = st.optInt("kitchenCookTimes", -1)
                    if (cook >= 0) parts.add("可做 $cook 次")
                    if (st.optBoolean("kitchenDailyMaterial", false)) {
                        val amount = st.optInt("kitchenDailyAmount", 0)
                        parts.add("今日食材可领" + if (amount > 0) " ${amount}g" else "")
                    }
                    val garbage = st.optInt("kitchenGarbage", -1)
                    if (garbage > 0) parts.add("厨余 $garbage")
                    if (st.optString("kitchenOrchardStatus") == "FINISHED") {
                        parts.add("农场食材可领")
                    }
                    parts.joinToString(" · ").ifEmpty { null }
                }
                "farm.family" -> farmStatus?.let { st ->
                    when {
                        st.optString("spaceType") == "ChickFamily" -> "小鸡在家庭空间"
                        st.optString("familyGroupId").isNotBlank() -> "已加入家庭"
                        else -> "未加入家庭"
                    }
                }
                "farm.friend" -> farmStatus?.let { st ->
                    val count = st.optInt("rewardFriendCount", -1)
                    if (count >= 0) "待打赏 $count 位" else null
                }
                "farm.life" -> farmStatus?.let { st ->
                    val parts = mutableListOf<String>()
                    val interact = st.optString("ownInteractStatus", "")
                    if (interact == "BEHIRING" || interact == "HIRING") {
                        parts.add("小鸡外出打工")
                    } else if (interact == "STEALING") {
                        parts.add("小鸡偷吃中")
                    } else {
                        val feed = st.optString("ownFeedStatus", "")
                        if (feed.isNotBlank()) {
                            val feedText = when (feed) {
                                "HUNGRY" -> "饿了"
                                "EATING" -> "进食中"
                                "SLEEPY" -> "想睡"
                                "SLEEPING" -> "睡觉中"
                                else -> ""
                            }
                            if (feedText.isNotEmpty()) parts.add("小鸡$feedText")
                        }
                    }
                    val countDown = st.optLong("ownSleepCountDown", -1)
                    if (countDown > 0) parts.add("睡眠剩 ${countDown / 60} 分")
                    val unread = st.optInt("diaryUnreadCount", -1)
                    if (unread > 0) parts.add("日记未读 $unread")
                    val latest = st.optString("diaryLatestDate", "")
                    if (latest.isNotBlank()) parts.add("最新 ${latest.takeLast(5)}")
                    parts.joinToString(" · ").ifEmpty { null }
                }
                else -> null
            }
            "protect" -> when (card.id) {
                "protect.ancient" -> protectStatus?.optInt("trees", -1)?.takeIf { it >= 0 }
                    ?.let { "可保护树木 $it 处 · 可设置只在周一三五运行" }
                    ?: "保护古树 · 可设置只在周一三五运行"
                "protect.cooperate" -> protectStatus?.optInt("cooperate", -1)?.takeIf { it >= 0 }
                    ?.let { "合种 $it 个 · 按名单每日浇水" }
                    ?: "每日按名单给合种树浇水"
                "protect.carbon" -> protectStatus?.let { status ->
                    val total = status.optInt("carbon", -1)
                    if (total < 0) null else {
                        val marathon = status.optInt("marathon", 0)
                        val doctor = status.optInt("doctor", 0)
                        if (marathon + doctor > 0) {
                            "可助力 $total 个 · 马拉松 $marathon / 古树医生 $doctor"
                        } else {
                            "可助力 $total 个活动 · 点右侧选择"
                        }
                    }
                } ?: "马拉松与古树医生 · 点右侧选择"
                "protect.forest" -> protectStatus?.let { status ->
                    val trees = status.optInt("trees", -1)
                    if (trees < 0) null else "可保护 $trees 处 · 已获证书 ${status.optInt("certs", 0)}"
                } ?: "植树任务 · 点右侧选择树种"
                "protect.animal" -> protectStatus?.let { status ->
                    val reserves = status.optInt("reserves", -1)
                    val animals = status.optInt("animals", -1)
                    if (reserves < 0 || animals < 0) null
                    else "保护地 $reserves 处 · 可保护动物 $animals 处"
                } ?: "保护地与护林员 · 点右侧配置"
                "protect.ocean" -> protectStatus?.let { status ->
                    val beaches = status.optInt("beaches", -1)
                    if (beaches < 0) null else "可保护 $beaches 片 · 已保护 ${status.optInt("beachCerts", 0)} 次"
                } ?: "海滩保护 · 点右侧选择海滩"
                else -> null
            }
            "greenAction" -> when (card.id) {
                "green.grain" -> greenCategoryText("jieyueliangshi", "2 个动作 · 光盘需拍照")
                "green.travel" -> greenCategoryText("lvsechuxing", "4 个动作")
                "green.electricity" -> greenCategoryText("jieyueyongdian", "5 个动作")
                "green.water" -> greenCategoryText("jieyueyongshui", "6 个动作")
                "green.recycle" -> greenCategoryText("bianfeiweibao", "3 个动作")
                "green.paper" -> greenCategoryText("jianzhijiansu", "5 个动作")
                else -> null
            }
            "forest" -> when (card.id) {
                "forest.settings" -> "时间间隔、阈值等技术项 · 点右侧进入"
                "forest.green" -> {
                    val on = assignments[card.id].orEmpty()
                        .filterIsInstance<BooleanModelField>()
                        .count { it.code in setOf("greenLife", "marketBrowseTask", "greenRent", "readForest") && it.value == true }
                    "4 个能量来源 · 已开启 $on 项"
                }
                "forest.reward" -> taskReport(forestSummary)
                "forest.park" -> taskReport(forestParkSummary)
                "forest.patrol" -> taskReport(forestPatrolSummary)
                "forest.home" -> forestStatus?.let { st ->
                    "今日收取 ${st.optInt("todayObtained", 0)}g · 当前 ${st.optInt("currentEnergy", 0)}g"
                }
                "forest.friends" -> {
                    val fields = assignments[card.id].orEmpty()
                    fun bool(code: String) = fields.filterIsInstance<BooleanModelField>()
                        .firstOrNull { it.code == code }?.value == true
                    fun int(code: String) = fields.filterIsInstance<IntegerModelField>()
                        .firstOrNull { it.code == code }?.value
                    val parts = mutableListOf<String>()
                    if (bool("waterFriendEnable")) {
                        val limit = int("waterDailyLimit") ?: 200
                        val mode = fields.filterIsInstance<ChoiceModelField>()
                            .firstOrNull { it.code == "waterMode" }?.value ?: 0
                        val used = Statistics.getData(Statistics.TimeType.DAY, Statistics.DataType.WATERED)
                        parts.add((if (mode == 1) "浇水·指定" else "浇水·回浇") + " $used/${limit}g")
                    } else {
                        parts.add("浇水 关")
                    }
                    when {
                        bool("partnerteamWater") -> parts.add("合种 ${int("partnerteamWaterNum") ?: 0}g")
                        bool("loveteamWater") -> parts.add("真爱 ${int("loveteamWaterNum") ?: 0}g")
                        else -> parts.add("合种 关")
                    }
                    parts.add(if (bool("ForestHunt")) "寻宝 开" else "寻宝 关")
                    val common = CommonFriendStore.load(userId)
                    if (common.ids.isNotEmpty()) parts.add("常用 ${common.ids.size} 人")
                    parts.joinToString(" · ")
                }
                "forest.backpack" -> forestBackpack?.optJSONArray("items")?.let { items ->
                    if (items.length() == 0) {
                        "背包为空"
                    } else {
                        val top = (0 until minOf(items.length(), 2)).mapNotNull { index ->
                            items.optJSONObject(index)?.let {
                                it.optString("name") + "x" + it.optInt("count")
                            }
                        }
                        "背包 ${items.length()} 种 · " + top.joinToString(" · ")
                    }
                }
                "forest.ip" -> forestIp?.let { st ->
                    "保护罩 ${st.optInt("price", 500)}g · 今日已兑 ${st.optInt("todayExchanged", 0)} 个"
                }
                else -> null
            }
            "orchard" -> when (card.id) {
                "orchard.fertilizer" -> orchardStatus?.optJSONObject("fertilizer")?.let { packet ->
                    val today = packet.optInt("todayNum", 0)
                    val tomorrow = packet.optInt("tomorrowNum", 0)
                    when (packet.optString("status")) {
                        "todayFertilizerFinish" -> if (today > 0) {
                            "今日已领 ${today}g · 明日 ${tomorrow}g"
                        } else {
                            "今日已领 · 明日 ${tomorrow}g"
                        }
                        "todayFertilizerWaitTake" -> "可领 ${today}g · 明日 ${tomorrow}g"
                        "todayFertilizerCanNotTake" -> {
                            val take = packet.optLong("takeTime", 0L)
                            if (take > 0L && System.currentTimeMillis() >= take) {
                                "可领 ${today}g · 明日 ${tomorrow}g"
                            } else {
                                "今日 ${today}g · 明日 ${tomorrow}g"
                            }
                        }
                        else -> "今日 ${today}g"
                    }
                }
                "orchard.task" -> taskReport(orchardSummary)
                "orchard.sign" -> orchardStatus?.optJSONObject("sign")?.let { sign ->
                    val days = sign.optInt("continuousCount", 0)
                    if (sign.optBoolean("signed", false)) {
                        "连续 $days 天 · 今日已签"
                    } else {
                        "连续 $days 天 · 今日 ${sign.optInt("awardCount", 0)}g"
                    }
                }
                "orchard.spread" -> {
                    val count = assignments[card.id].orEmpty()
                        .filterIsInstance<IntegerModelField>()
                        .firstOrNull { it.code == "orchardSpreadManureCount" }?.value ?: 3
                    val mode = assignments[card.id].orEmpty()
                        .filterIsInstance<ChoiceModelField>()
                        .firstOrNull { it.code == "spreadMode" }?.value ?: 0
                    val account = orchardStatus?.optJSONObject("account")
                    val used = account?.optInt("used", 0) ?: 0
                    val happy = account?.optLong("happyPoint", 0L) ?: 0L
                    val modeText = if (mode == 1) "一键5次" else "单次施肥"
                    "今日 $used/$count 次 · $modeText · 肥料余额 ${happy}g"
                }
                "orchard.park" -> {
                    val report = taskReport(orchardParkSummary)
                    val egg = orchardStatus?.optJSONObject("eggs")?.let {
                        "金蛋 ${it.optInt("smashed", 0)}/${it.optInt("limit", 0)}"
                    }
                    listOfNotNull(report, egg).joinToString(" · ").ifBlank { null }
                }
                "orchard.draw" -> {
                    val report = taskReport(orchardDrawSummary)
                    val balance = orchardStatus?.optJSONObject("draw")?.optInt("balance", -1) ?: -1
                    val chance = if (balance >= 0) "机会 $balance" else null
                    listOfNotNull(report, chance).joinToString(" · ").ifBlank { null }
                }
                "orchard.challenge" -> {
                    val round = orchardStatus?.optJSONObject("challenge")?.let { challenge ->
                        val rounds = challenge.optJSONArray("rounds") ?: return@let null
                        val current = challenge.optInt("currentRound", 0)
                        (0 until rounds.length()).mapNotNull { rounds.optJSONObject(it) }
                            .firstOrNull { it.optInt("index", 0) == current }
                    }
                    val roundText = round?.let {
                        "挑战${it.optInt("index", 0)} ${it.optInt("done", 0)}/${it.optInt("total", 0)}" +
                            " · 奖励 ${it.optInt("award", 0)}g"
                    }
                    listOfNotNull(taskReport(orchardChallengeSummary), roundText)
                        .joinToString(" · ").ifBlank { null }
                }
                "orchard.factory" -> orchardStatus?.optJSONObject("factory")?.let { factory ->
                    val collectable = factory.optInt("collectable", 0)
                    val total = factory.optInt("total", 0)
                    if (collectable > 0) "可收 $collectable 罐 · 共 ${total}g" else "暂无可收"
                }
                "orchard.tree" -> orchardStatus?.optJSONObject("tree")?.let { tree ->
                    val name = tree.optString("seedName", "")
                    val subTitle = tree.optString("seedSubTitle", "")
                    val unit = tree.optString("unit", "")
                    val stage = tree.optString("stageText", "")
                    val yield = (name + subTitle.ifBlank { unit }).ifBlank { null }
                    listOfNotNull(
                        yield,
                        stage.ifBlank { null },
                        if (tree.optBoolean("canExchange", false)) "可兑换" else null
                    ).joinToString(" · ").ifBlank { null }
                }
                "orchard.team" -> orchardStatus?.optJSONObject("team")?.let { team ->
                    if (team.optBoolean("inTeam", false)) "已组队 · 可做合种任务" else "未组队 · 合种任务需先组队"
                }
                else -> null
            }
            "golden" -> when (card.id) {
                "golden.overview" -> {
                    if (goldenStatus == null && goldenSummary == null) {
                        "等待首次同步 · 点任务列表刷新"
                    } else {
                        val balance = goldenStatus?.optInt("balance", 0) ?: 0
                        val capacity = goldenStatus?.optInt("capacity", 0) ?: 0
                        val jars = goldenStatus?.optInt("jars", 0) ?: 0
                        listOfNotNull(
                            if (capacity > 0) "金豆 $balance/$capacity" else "金豆 $balance",
                            "金豆罐 $jars",
                            taskReport(goldenSummary),
                            if ((goldenStatus?.optInt("pendingBeans", 0) ?: 0) > 0)
                                "待领 ${goldenStatus?.optInt("pendingBeans", 0)} 颗" else null
                        ).joinToString(" · ")
                    }
                }
                "golden.sign" -> {
                    if (goldenStatus == null) {
                        "等待首次同步"
                    } else if (goldenStatus.optBoolean("signed", false)) {
                        "今日已签 · 连续 ${goldenStatus.optInt("continuous", 0)} 天"
                    } else {
                        "待签到 · +${goldenStatus.optInt("signAward", 0)}豆"
                    }
                }
                "golden.task" -> taskReport(goldenSummary) ?: "等待首次同步"
                "golden.sprout" -> {
                    val pending = goldenStatus?.optInt("pendingBeans", 0) ?: 0
                    if (pending > 0) "待领 $pending 颗金豆" else "暂无待领金豆"
                }
                "golden.miner" -> "金猫矿工 · 自动挖豆"
                "golden.game" -> {
                    val rights = goldenStatus?.optInt("drawRights", 0) ?: 0
                    "可抽 $rights 次"
                }
                "golden.exchange" -> goldenStatus?.let { st ->
                    "肥料 ${st.optInt("manureCurrent", 0)} · 今日可换 ${st.optInt("manureRemain", 0)}豆"
                } ?: "等待首次同步"
                "golden.mall" -> "金豆商城权益兑换"
                else -> null
            }
            "sports" -> when (card.id) {
                "kinetic.center" -> {
                    if (kineticStatus == null && kineticSummary == null) {
                        "等待首次同步 · 点任务列表刷新"
                    } else {
                        val balance = kineticStatus?.optInt("balance", -1) ?: -1
                        val bubbles = kineticStatus?.optInt("bubbleCount", 0) ?: 0
                        val amount = kineticStatus?.optInt("bubbleAmount", 0) ?: 0
                        val earnable = kineticStatus?.optInt("earnable", 0) ?: 0
                        listOfNotNull(
                            if (balance >= 0) "健康能量 ${balance}g" else null,
                            if (bubbles > 0) "待收 $bubbles 个气泡 · ${amount}g" else "气泡已收完",
                            if (earnable > 0) "今日可领 ${earnable}g" else null
                        ).joinToString(" · ")
                    }
                }
                "kinetic.sign" -> {
                    if (kineticStatus == null) {
                        "等待首次同步"
                    } else {
                        val signed = kineticStatus.optBoolean("signed", false)
                        val amount = kineticStatus.optInt("signAmount", 0)
                        if (signed) "今日已签 · ${amount}g" else "可签 ${amount}g"
                    }
                }
                "kinetic.task" -> taskReport(kineticSummary) ?: "等待首次同步"
                "kinetic.bubble" -> {
                    if (kineticStatus == null) {
                        "等待首次同步"
                    } else {
                        val bubbles = kineticStatus.optInt("bubbleCount", 0)
                        val amount = kineticStatus.optInt("bubbleAmount", 0)
                        if (bubbles > 0) "待收 $bubbles 个气泡 · ${amount}g" else "暂无可收气泡"
                    }
                }
                "sports.walk" -> kineticStatus?.let { st ->
                    val steps = st.optInt("steps", -1)
                    if (steps < 0) {
                        "等待首次同步"
                    } else {
                        "今日 $steps 步 · " +
                            if (st.optBoolean("walkSigned", false)) "步数已同步" else "步数未同步"
                    }
                }
                "sports.donate" -> kineticStatus?.let { st ->
                    val steps = if (st.optInt("walkStepsToday", -1) >= 0) {
                        st.optInt("walkStepsToday", 0)
                    } else {
                        st.optInt("steps", -1)
                    }
                    when {
                        st.optBoolean("walkDonationDoneToday", false) -> "今日已捐 · $steps 步"
                        st.optBoolean("walkExchange", false) -> "可捐 · $steps 步"
                        steps >= 0 -> "步数不足 · $steps 步"
                        else -> "等待首次同步"
                    }
                }
                else -> null
            }
            "species" -> when (card.id) {
                "species.task" -> taskReport(speciesTaskSummary)
                "species.draw" -> speciesStatus?.let { st ->
                    if (st.optBoolean("dailyCollected", false)) {
                        "今日抽卡已完成"
                    } else {
                        "今日未抽 · 可抽 ${st.optInt("dailyLeft", 0)} 次"
                    }
                }
                "species.friend" -> speciesStatus?.let { st ->
                    val left = minOf(st.optInt("friendLeft", 0), st.optInt("friendAvailable", 0))
                    if (System.currentTimeMillis() < st.optLong("friendStartTime", 0L)) {
                        "12:00 后可帮抽 $left 人"
                    } else {
                        "可帮抽 $left 人"
                    }
                }
                "species.prop" -> speciesStatus?.let { st ->
                    "道具 ${st.optInt("propKinds", 0)} 种 · 共 ${st.optInt("propHolds", 0)} 个"
                }
                "species.book" -> speciesStatus?.let { st ->
                    "图鉴 ${st.optInt("collectedCards", 0)}/${st.optInt("totalCards", 0)}" +
                        " · 可合成 ${st.optInt("canGenerateMedals", 0)}"
                }
                "species.friends" -> {
                    val selected = assignments[card.id].orEmpty()
                        .filterIsInstance<SelectModelField>()
                        .firstOrNull { it.code == "speciesFriendList" }?.value?.size ?: 0
                    "已选 $selected 人 · 未选=全部好友"
                }
                else -> null
            }
            else -> null
        }
    }

    fun flatStatusOverride(card: DomainCardSpec, switchOn: Boolean): Pair<String, Color>? {
        if (card.id in setOf("ocean.settings", "farm.settings", "orchard.settings", "species.settings",
                "sports.settings", "golden.settings", "protect.settings", "green.settings")) {
            // 绿色行动挂在其专属总开关 ecoLife 下，森林的 enable 打开并不代表它生效
            val enableCode = if (card.id == "green.settings") "ecoLife" else "enable"
            val enabled = assignments[card.id].orEmpty()
                .filterIsInstance<BooleanModelField>()
                .firstOrNull { it.code == enableCode }?.value == true
            return when {
                !enabled -> "已关闭" to Color(0xFF9AA5B1)
                cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                else -> "已启用" to MedusaColors.Green
            }
        }
        if (spec.key == "greenAction") {
            val categoryType = when (card.id) {
                "green.grain" -> "jieyueliangshi"
                "green.travel" -> "lvsechuxing"
                "green.electricity" -> "jieyueyongdian"
                "green.water" -> "jieyueyongshui"
                "green.recycle" -> "bianfeiweibao"
                "green.paper" -> "jianzhijiansu"
                else -> null
            }
            if (categoryType != null) {
                if (!switchOn) return "未开启" to Color(0xFFE57373)
                val status = greenStatus ?: return "未同步" to MedusaColors.Muted
                val categories = status.optJSONArray("categories")
                    ?: return "未同步" to MedusaColors.Muted
                for (i in 0 until categories.length()) {
                    val category = categories.optJSONObject(i) ?: continue
                    if (category.optString("type") != categoryType) continue
                    val done = category.optInt("done", 0)
                    val total = category.optInt("total", 0)
                    return when {
                        total > 0 && done >= total -> "已完成" to MedusaColors.Green
                        done > 0 -> "进行中" to Color(0xFFF4C66F)
                        else -> "未打卡" to MedusaColors.Muted
                    }
                }
                return "未同步" to MedusaColors.Muted
            }
        }
        if (spec.key == "golden") {
            return when (card.id) {
                "golden.overview" -> {
                    val balance = goldenStatus?.optInt("balance", -1) ?: -1
                    if (balance >= 0) "${balance}豆" to MedusaColors.Gold else "未同步" to MedusaColors.Muted
                }
                "golden.sign" -> {
                    if (!switchOn) {
                        "未开启" to Color(0xFFE57373)
                    } else if (goldenStatus == null) {
                        "未同步" to MedusaColors.Muted
                    } else if (goldenStatus.optBoolean("signed", false)) {
                        "今日已签" to MedusaColors.Green
                    } else {
                        "待签到" to MedusaColors.Purple
                    }
                }
                "golden.task" -> taskStatus(goldenSummary, switchOn)
                "golden.sprout" -> {
                    val pending = goldenStatus?.optInt("pendingBeans", 0) ?: 0
                    if (!switchOn) "未开启" to Color(0xFFE57373)
                    else if (pending > 0) "可领 $pending 颗" to MedusaColors.Gold
                    else "已领" to MedusaColors.Green
                }
                else -> null
            }
        }
        if (spec.key == "sports") {
            return when (card.id) {
                "kinetic.task" -> taskStatus(kineticSummary, switchOn)
                "kinetic.sign" -> {
                    if (!switchOn) {
                        "未开启" to Color(0xFFE57373)
                    } else if (kineticStatus == null) {
                        "未同步" to MedusaColors.Muted
                    } else if (kineticStatus.optBoolean("signed", false)) {
                        "今日已签" to MedusaColors.Green
                    } else {
                        "待签到" to MedusaColors.Purple
                    }
                }
                "kinetic.bubble" -> {
                    if (!switchOn) {
                        "未开启" to Color(0xFFE57373)
                    } else if (kineticStatus == null) {
                        "未同步" to MedusaColors.Muted
                    } else {
                        val bubbles = kineticStatus.optInt("bubbleCount", 0)
                        if (bubbles > 0) "可收 ${kineticStatus.optInt("bubbleAmount", 0)}g" to MedusaColors.Gold
                        else "已收完" to MedusaColors.Green
                    }
                }
                "kinetic.center" -> {
                    val balance = kineticStatus?.optInt("balance", -1) ?: -1
                    if (balance >= 0) "${balance}g" to MedusaColors.Gold else "未同步" to MedusaColors.Muted
                }
                "sports.walk" -> {
                    val steps = kineticStatus?.optInt("steps", -1) ?: -1
                    when {
                        !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        kineticStatus == null || steps < 0 -> "未同步" to MedusaColors.Muted
                        kineticStatus?.optBoolean("walkSigned", false) == true ->
                            "已同步" to MedusaColors.Green
                        else -> "待同步" to MedusaColors.Gold
                    }
                }
                "sports.donate" -> {
                    val minCount = assignments[card.id].orEmpty()
                        .filterIsInstance<IntegerModelField>()
                        .firstOrNull { it.code == "minExchangeCount" }?.value ?: 0
                    when {
                        minCount <= 0 -> "未启用" to MedusaColors.Muted
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        kineticStatus == null ||
                            (kineticStatus?.optInt("steps", -1) ?: -1) < 0 ->
                            "未同步" to MedusaColors.Muted
                        kineticStatus?.optBoolean("walkDonationDoneToday", false) == true ->
                            "今日已捐" to MedusaColors.Green
                        kineticStatus?.optBoolean("walkExchange", false) == true ->
                            "可捐" to MedusaColors.Gold
                        else -> "步数不足" to MedusaColors.Muted
                    }
                }
                else -> null
            }
        }
        if (spec.key == "species") {
            return when (card.id) {
                "species.task" -> taskStatus(speciesTaskSummary, switchOn)
                "species.draw" -> {
                    if (speciesStatus?.optBoolean("dailyCollected", false) == true) {
                        "已抽完" to MedusaColors.Green
                    } else {
                        "可抽 ${speciesStatus?.optInt("dailyLeft", 0) ?: 0} 次" to MedusaColors.Gold
                    }
                }
                "species.friend" -> {
                    val type = assignments[card.id].orEmpty()
                        .filterIsInstance<ChoiceModelField>()
                        .firstOrNull { it.code == "collectToFriendType" }?.value ?: 0
                    val start = speciesStatus?.optLong("friendStartTime", 0L) ?: 0L
                    val left = minOf(
                        speciesStatus?.optInt("friendLeft", 0) ?: 0,
                        speciesStatus?.optInt("friendAvailable", 0) ?: 0
                    )
                    when {
                        type == 0 -> "未开启" to Color(0xFFE57373)
                        System.currentTimeMillis() < start -> "12:00 后" to MedusaColors.Muted
                        else -> "可帮抽 $left 人" to MedusaColors.Gold
                    }
                }
                "species.book" -> {
                    val canGenerate = speciesStatus?.optInt("canGenerateMedals", 0) ?: 0
                    if (canGenerate > 0) "可合成 $canGenerate" to MedusaColors.Gold else null
                }
                "species.friends" -> "可选好友" to MedusaColors.Muted
                else -> null
            }
        }
        if (spec.key == "forest") {
            return when (card.id) {
                "forest.settings" -> "设置" to MedusaColors.Muted
                "forest.reward" -> taskStatus(forestSummary, switchOn)
                "forest.park" -> taskStatus(forestParkSummary, switchOn)
                "forest.patrol" -> taskStatus(forestPatrolSummary, switchOn)
                "forest.ip" -> {
                    val target = assignments[card.id].orEmpty()
                        .filterIsInstance<IntegerModelField>()
                        .firstOrNull { it.code == "ipExchangeCount" }?.value ?: 1
                    when {
                        !switchOn -> "已关闭" to Color(0xFFE57373)
                        forestIp == null || forestIp.optString("skuId").isEmpty() ->
                            "未发现" to Color(0xFFE57373)
                        forestIp.optInt("todayExchanged", 0) >= target ->
                            "已兑满" to MedusaColors.Green
                        else -> "可兑换" to MedusaColors.Gold
                    }
                }
                else -> null
            }
        }
        if (spec.key == "farm") {
            return when (card.id) {
                "farm.task" -> taskStatus(farmSummary, switchOn)
                "farm.draw" -> taskStatus(farmDrawSummary, switchOn)
                "farm.daily" -> when {
                    !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                    cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                    farmStatus == null -> "未同步" to Color(0xFFE57373)
                    (farmStatus?.optInt("foodStock", 0) ?: 0) <= 0 -> "缺饲料" to Color(0xFFE57373)
                    (farmStatus?.optInt("manureReady", 0) ?: 0) > 0 ->
                        "可收肥料" to MedusaColors.Gold
                    (farmStatus?.optDouble("harvestEggs", 0.0) ?: 0.0) >= 1 ->
                        "可收蛋" to MedusaColors.Gold
                    else -> "正常" to MedusaColors.Green
                }
                "farm.donate" -> {
                    val donationType = assignments[card.id].orEmpty()
                        .filterIsInstance<ChoiceModelField>()
                        .firstOrNull { it.code == "donationType" }?.value
                    when {
                        donationType != null && donationType == 0 -> "未启用" to MedusaColors.Muted
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        farmStatus == null -> "未同步" to Color(0xFFE57373)
                        (farmStatus?.optBoolean("charityDonationDoneToday", false) ?: false) ->
                            "今日已捐" to MedusaColors.Green
                        (farmStatus?.optDouble("benevolenceEggs", 0.0) ?: 0.0) >= 1 ->
                            "可捐" to MedusaColors.Gold
                        else -> "无蛋可捐" to MedusaColors.Muted
                    }
                }
                "farm.competition" -> when {
                    !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                    cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                    competitionStatus == null -> "未同步" to Color(0xFFE57373)
                    cardRanToday(card) -> "已运行" to MedusaColors.Green
                    else -> "今日未跑" to Color(0xFFE57373)
                }
                "farm.park" -> {
                    val box = farmStatus?.optInt("parkBoxQuota", -1) ?: -1
                    val claim = farmStatus?.optInt("parkTaskClaimable", 0) ?: 0
                    val todo = farmStatus?.optInt("parkTaskTodo", 0) ?: 0
                    when {
                        !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        farmStatus == null -> "未同步" to Color(0xFFE57373)
                        box > 0 -> "可开宝箱 $box" to MedusaColors.Gold
                        claim > 0 -> "可领 $claim 项" to MedusaColors.Gold
                        todo > 0 -> "待做 $todo 项" to MedusaColors.Blue
                        else -> "已清" to MedusaColors.Green
                    }
                }
                "farm.kitchen" -> {
                    val cook = farmStatus?.optInt("kitchenCookTimes", -1) ?: -1
                    when {
                        !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        farmStatus == null -> "未同步" to Color(0xFFE57373)
                        farmStatus?.optBoolean("kitchenDailyMaterial", false) == true ->
                            "可领食材" to MedusaColors.Gold
                        cook > 0 -> "可做 $cook 次" to MedusaColors.Gold
                        else -> "已清" to MedusaColors.Green
                    }
                }
                "farm.sunflower" -> when {
                    !switchOn -> "已关闭" to Color(0xFFE57373)
                    sunflowerStatus == null -> "未同步" to Color(0xFFE57373)
                    cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                    cardRanToday(card) -> "已运行" to MedusaColors.Green
                    sunflowerStatus.optInt("seeds", 0) >=
                        sunflowerStatus.optInt("ratio", 50) -> "可捐" to MedusaColors.Gold
                    else -> "无葵花籽" to MedusaColors.Muted
                }
                "farm.family" -> when {
                    !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                    cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                    farmStatus == null -> "未同步" to Color(0xFFE57373)
                    farmStatus?.optString("spaceType") == "ChickFamily" ->
                        "小鸡在家庭" to MedusaColors.Green
                    !farmStatus?.optString("familyGroupId").isNullOrBlank() ->
                        "已组队" to MedusaColors.Green
                    else -> "未组队" to MedusaColors.Muted
                }
                "farm.friend" -> {
                    val count = farmStatus?.optInt("rewardFriendCount", -1) ?: -1
                    when {
                        !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        count > 0 -> "待打赏 $count" to MedusaColors.Gold
                        cardRanToday(card) -> "已运行" to MedusaColors.Green
                        else -> "今日未跑" to Color(0xFFE57373)
                    }
                }
                "farm.life" -> {
                    val sleep = farmStatus?.optLong("ownSleepCountDown", -1) ?: -1
                    val interact = farmStatus?.optString("ownInteractStatus", "") ?: ""
                    val diary = farmStatus?.optInt("diaryUnreadCount", 0) ?: 0
                    when {
                        !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        farmStatus == null -> "未同步" to Color(0xFFE57373)
                        diary > 0 -> "日记未读 $diary" to MedusaColors.Gold
                        sleep > 0 -> "睡眠中" to MedusaColors.Blue
                        interact == "BEHIRING" || interact == "HIRING" ->
                            "外出打工" to MedusaColors.Blue
                        interact == "STEALING" -> "偷吃中" to MedusaColors.Gold
                        farmStatus?.optString("ownFeedStatus") == "HUNGRY" ->
                            "小鸡饿了" to MedusaColors.Gold
                        else -> "正常" to MedusaColors.Green
                    }
                }
                else -> null
            }
        }
        if (spec.key == "orchard") {
            return when (card.id) {
                "orchard.fertilizer" -> {
                    val packet = orchardStatus?.optJSONObject("fertilizer")
                    when {
                        !switchOn -> "已关闭" to Color(0xFFE57373)
                        packet == null -> "未同步" to Color(0xFFE57373)
                        packet.optString("status") == "todayFertilizerFinish" -> "已领" to MedusaColors.Green
                        packet.optString("status") == "todayFertilizerWaitTake" ->
                            "可领 ${packet.optInt("todayNum", 0)}g" to MedusaColors.Gold
                        packet.optString("status") == "todayFertilizerCanNotTake" -> {
                            val take = packet.optLong("takeTime", 0L)
                            if (take > 0L && System.currentTimeMillis() >= take) {
                                "可领 ${packet.optInt("todayNum", 0)}g" to MedusaColors.Gold
                            } else {
                                "${clockText(take)} 后可领" to MedusaColors.Muted
                            }
                        }
                        else -> "待领取" to MedusaColors.Gold
                    }
                }
                "orchard.task" -> taskStatus(orchardSummary, switchOn)
                "orchard.sign" -> {
                    val sign = orchardStatus?.optJSONObject("sign")
                    when {
                        !switchOn -> "已关闭" to Color(0xFFE57373)
                        sign == null -> "未同步" to Color(0xFFE57373)
                        sign.optBoolean("signed", false) ->
                            "已签 · 连续 ${sign.optInt("continuousCount", 0)} 天" to MedusaColors.Green
                        else -> "可签 ${sign.optInt("awardCount", 0)}g" to MedusaColors.Gold
                    }
                }
                "orchard.spread" -> {
                    val limit = assignments[card.id].orEmpty()
                        .filterIsInstance<IntegerModelField>()
                        .firstOrNull { it.code == "orchardSpreadManureCount" }?.value ?: 3
                    val account = orchardStatus?.optJSONObject("account")
                    val used = account?.optInt("used", 0) ?: 0
                    val cost = account?.optInt("wateringCost", 0) ?: 0
                    val happy = account?.optLong("happyPoint", 0L) ?: 0L
                    when {
                        !switchOn -> "已关闭" to Color(0xFFE57373)
                        account == null -> "未同步" to Color(0xFFE57373)
                        used >= limit -> "已施满" to MedusaColors.Green
                        cost > 0 && happy < cost -> "肥料不足" to Color(0xFFE57373)
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        else -> "可施肥 $used/$limit" to MedusaColors.Gold
                    }
                }
                "orchard.park" -> {
                    val eggs = orchardStatus?.optJSONObject("eggs")
                    val awardOn = assignments[card.id].orEmpty()
                        .filterIsInstance<BooleanModelField>()
                        .firstOrNull { it.code == "drawGameCenterAward" }?.value == true
                    when {
                        !anySwitchOn(card) -> "已关闭" to Color(0xFFE57373)
                        eggs == null && orchardParkSummary == null -> "未同步" to Color(0xFFE57373)
                        awardOn && (eggs?.optInt("unsmashed", 0) ?: 0) > 0 ->
                            "可砸 ${eggs!!.optInt("unsmashed", 0)} 个" to MedusaColors.Gold
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        orchardParkSummary != null -> taskStatus(orchardParkSummary, true)
                        else -> "已运行" to MedusaColors.Green
                    }
                }
                "orchard.draw" -> {
                    val balance = orchardStatus?.optJSONObject("draw")?.optInt("balance", -1) ?: -1
                    when {
                        !switchOn -> "已关闭" to Color(0xFFE57373)
                        orchardDrawSummary == null && balance < 0 -> "未同步" to Color(0xFFE57373)
                        balance > 0 -> "可抽 $balance 次" to MedusaColors.Gold
                        orchardDrawSummary != null -> taskStatus(orchardDrawSummary, true)
                        else -> "已抽完" to MedusaColors.Green
                    }
                }
                "orchard.challenge" -> {
                    val challenge = orchardStatus?.optJSONObject("challenge")
                    when {
                        !switchOn -> "已关闭" to Color(0xFFE57373)
                        challenge == null -> "未同步" to Color(0xFFE57373)
                        !challenge.optBoolean("active", false) -> "未开启" to MedusaColors.Muted
                        cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                        orchardChallengeSummary != null -> taskStatus(orchardChallengeSummary, true)
                        else -> "可挑战" to MedusaColors.Gold
                    }
                }
                "orchard.factory" -> {
                    val factory = orchardStatus?.optJSONObject("factory")
                    when {
                        !switchOn -> "已关闭" to Color(0xFFE57373)
                        factory == null -> "未同步" to Color(0xFFE57373)
                        factory.optInt("collectable", 0) > 0 ->
                            "可收 ${factory.optInt("collectable", 0)} 罐" to MedusaColors.Gold
                        else -> "已收" to MedusaColors.Green
                    }
                }
                "orchard.tree" -> {
                    val tree = orchardStatus?.optJSONObject("tree")
                    when {
                        tree == null -> "未同步" to Color(0xFFE57373)
                        tree.optBoolean("canExchange", false) -> "可兑换" to MedusaColors.Gold
                        else -> "${tree.optInt("stageLevel", 0)}级" to MedusaColors.Green
                    }
                }
                "orchard.team" -> {
                    val team = orchardStatus?.optJSONObject("team")
                    when {
                        team == null -> "未同步" to Color(0xFFE57373)
                        team.optBoolean("inTeam", false) -> "已组队" to MedusaColors.Green
                        else -> "未组队" to MedusaColors.Muted
                    }
                }
                else -> null
            }
        }
        if (spec.key != "ocean") return null
        return when (card.id) {
            "ocean.reward" -> taskStatus(oceanSummary, switchOn)
            "ocean.fish" -> {
                val remain = oceanStatus?.optInt("fishRemainTouchChance", -1) ?: -1
                if (switchOn && remain > 0) {
                    "可捞 $remain" to MedusaColors.Green
                } else {
                    taskStatus(fishSummary, switchOn)
                }
            }
            "ocean.clean" -> {
                val booleans = assignments[card.id].orEmpty().filterIsInstance<BooleanModelField>()
                val morning = booleans.firstOrNull { it.code == "cleanMorning" }?.value == true
                val evening = booleans.firstOrNull { it.code == "cleanEvening" }?.value == true
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val state = cleanQuotaToday()
                val dailyUsed = state?.optInt("dailyUsed", 0) ?: 0
                val serverLimited = state?.optBoolean("serverLimited", false) ?: false
                val morningWindow = hour < 17
                val quota = cleanWindowQuota(morningWindow, morning, evening)
                val used = cleanWindowUsed(state, morningWindow)
                when {
                    hour < 7 -> "未到刷新" to MedusaColors.Muted
                    morningWindow && !morning -> "上午未开启" to MedusaColors.Muted
                    !morningWindow && !evening -> "下午未开启" to MedusaColors.Muted
                    serverLimited || dailyUsed >= OceanCleanQuota.DAILY_LIMIT ->
                        "今日已达${OceanCleanQuota.DAILY_LIMIT}" to Color(0xFFE57373)
                    quota > 0 && used >= quota -> "本时段已完成" to MedusaColors.Green
                    else -> "可清理" to MedusaColors.Green
                }
            }
            "ocean.welcome" -> {
                val ready = oceanStatus?.optInt("welcomeReady", -1) ?: -1
                when {
                    !switchOn -> "已关闭" to Color(0xFFE57373)
                    cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                    ready > 0 -> "可找回 $ready" to MedusaColors.Green
                    welcomeName.isNotBlank() -> "已找回 $welcomeName" to MedusaColors.Green
                    cardRanToday(card) -> "无可找回" to MedusaColors.Muted
                    else -> "今日未跑" to Color(0xFFE57373)
                }
            }
            else -> null
        }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = spec.title,
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                if (spec.key == "forest") {
                    "点卡片或右侧箭头进入设置；任务列表在标题右侧；首页执行按钮只扫一遍能量"
                } else if (spec.key == "ocean") {
                    "开关直接生效；任务列表在标题右侧"
                } else if (spec.key == "farm") {
                    "点卡片或右侧箭头进入设置；任务列表在标题右侧"
                } else if (spec.key == "species") {
                    "点卡片或右侧箭头进入设置；任务列表在标题右侧；帮好友抽卡 12:00 后开放"
                } else if (spec.key == "orchard") {
                    "点卡片或右侧箭头进入设置；任务列表在标题右侧；每日肥料 07:00 后可领"
                } else if (spec.key == "sports") {
                    "签到、能量任务、运动币气泡和步数路线都在本域；外跳浏览类任务标为需手动"
                } else if (spec.key == "golden") {
                    "签到、任务、矿工、乐园和兑换都在本域；外跳游戏/浏览任务标为需手动"
                } else if (spec.key == "protect") {
                    "合种浇水、碳中和、森林/动物/海洋保护和古树都在本域；点卡片进入配置"
                } else if (spec.key == "greenAction") {
                    "六类绿色行动按分类开关；光盘行动需要先手动完成一次"
                } else {
                    "点击卡片进入细节；状态按本域账本 + 本卡开关：绿点已运行 · 黄点运行中 · 红点未运行"
                },
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp)
            )
            if (sportsPaused) {
                Text(
                    "运动域已暂停至明天：今天触发过 1009 风控，系统不会自动请求运动接口；明天再试。",
                    fontSize = MedusaType.Secondary,
                    color = Color(0xFF9A5A12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFFFFF3E5), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
            if (masterOff) {
                Text(
                    "绿色行动总开关未开启，分类开关暂不生效；先进第一张卡开启。",
                    fontSize = MedusaType.Secondary,
                    color = Color(0xFF9A6A12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFFFFF6E5), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
            if (domainOff) {
                Text(
                    "本域已关闭，计划与兜底都不会执行；打开上方「本域设置」里的开关即可恢复。",
                    fontSize = MedusaType.Secondary,
                    color = Color(0xFF9A5A12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(Color(0xFFFFF3E5), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                val flatDomain = spec.key == "ocean" || spec.key == "farm" ||
                    spec.key == "forest" || spec.key == "species" || spec.key == "orchard" ||
                    spec.key == "sports" || spec.key == "golden" ||
                    spec.key == "protect" || spec.key == "greenAction"
                val orderedCards = if (spec.key == "sports" || spec.key == "golden") {
                    // 医疗健康·运动 / 金豆夺宝按固定阅读顺序，不做自动重排
                    spec.cards
                } else if (flatDomain) {
                    // 设置最高级置顶；任务列表卡次之；再是带详情入口的卡；纯开关卡最后
                    spec.cards.sortedBy {
                        when {
                            it.id.endsWith(".settings") -> 0
                            it.id in TASK_LIST_CARDS -> 1
                            cardHasEntries(it) -> 2
                            else -> 3
                        }
                    }
                } else {
                    spec.cards
                }
                val cardRows = if (flatDomain) {
                    orderedCards.map { listOf(it) }
                } else {
                    spec.cards.chunked(2)
                }
                cardRows.forEach { rowCards ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowCards.forEach { card ->
                            val fields = assignments[card.id].orEmpty()
                            val booleans = fields.filterIsInstance<BooleanModelField>()
                            val allOn = booleans.isNotEmpty() && booleans.all { it.value == true }
                            val enabledCount = booleans.count { it.value == true }
                            val switchField = switchFieldOf(card, booleans)
                            val cardSwitchOn = switchField?.value ?: allOn
                            if (flatDomain) {
                                val status = flatStatusOverride(card, cardSwitchOn) ?: when {
                                    enabledCount == 0 -> "已关闭" to Color(0xFFE57373)
                                    cardRunning(card) -> "运行中" to Color(0xFFF4C66F)
                                    cardRanToday(card) -> "已运行" to MedusaColors.Green
                                    else -> "今日未跑" to Color(0xFFE57373)
                                }
                                FlatCardView(
                                    card = card,
                                    fields = fields,
                                    summaryText = flatSummaryText(card),
                                    status = status,
                                    revision = uiTick,
                                    onToggle = { field, value ->
                                        val live = liveConfigField(field.code) as? BooleanModelField ?: field
                                        live.setObjectValue(value)
                                        if (live !== field) {
                                            field.setObjectValue(value)
                                        }
                                        scheduleSave()
                                    },
                                    actions = cardFlatActions(card),
                                    capsules = familyCapsules(card),
                                    onDataClick = if (card.id.endsWith(".settings") && dataOptions.isNotEmpty()) {
                                        { showDataPicker = true }
                                    } else {
                                        null
                                    },
                                    onOpenDetail = if (card.id.endsWith(".settings") || card.id == "forest.friends") {
                                        { openCard(card) }
                                    } else if (
                                        flatDomain && detailCodes(card).isNotEmpty()
                                    ) {
                                        { openDetail(card) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                DomainCardView(
                                    card = card,
                                    enabledCount = enabledCount,
                                    onClick = { openCard(card) },
                                    onDataClick = if (
                                        card.type == DomainCardType.ENTRY &&
                                        card.id.endsWith(".settings") &&
                                        dataOptions.isNotEmpty()
                                    ) {
                                        { showDataPicker = true }
                                    } else {
                                        null
                                    },
                                    forceOff = masterOff && card.type == DomainCardType.SWITCH,
                                    ledgerRunning = cardRunning(card),
                                    ledgerRanToday = cardRanToday(card),
                                    summaryText = flatSummaryText(card),
                                    statusOverride = flatStatusOverride(card, cardSwitchOn),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (rowCards.size == 1 && !flatDomain) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            DomainToolbar(
                onSave = {
                    val result = ConfigSync.saveForced(activity, userId)
                    if (!result.ok) {
                        ToastUtil.show(activity, ConfigSync.failureMessage(result))
                    }
                },
                onExport = {
                    runCatching {
                        val json = DomainConfigIO.exportJson(spec, domainFieldCodes)
                        val file = BackupManager.saveDomainExport(json, spec.key, spec.title)
                        ToastUtil.show(activity, "已导出到 ${file.absolutePath}")
                    }.onFailure {
                        ToastUtil.show(activity, "导出失败：${it.message ?: "未知错误"}")
                    }
                },
                onImport = { importLauncher.launch("*/*") },
                onLog = {
                    val logType = when (spec.group) {
                        ModelGroup.FOREST, ModelGroup.SPECIES -> LogType.FOREST
                        ModelGroup.FARM, ModelGroup.STALL, ModelGroup.ORCHARD -> LogType.FARM
                        ModelGroup.GOLDENBEANS -> LogType.GOLDENBEANS
                        else -> LogType.OTHER
                    }
                    val query = when (spec.key) {
                        "greenAction" -> "绿色"
                        "ancient" -> "古树"
                        else -> null
                    }
                    activity.startActivity(
                        Intent(activity, MiuixLogViewerActivity::class.java).apply {
                            putExtra(LogType.EXTRA_LOG_TYPE, logType.name)
                            if (!query.isNullOrBlank()) {
                                putExtra(LogType.EXTRA_LOG_QUERY, query)
                            }
                        }
                    )
                },
                onSync = {
                    ConfigSync.notifyRestart(activity, userId)
                    val syncDomain = if (spec.key == "sports") "sports.status" else spec.key
                    activity.sendBroadcast(
                        Intent("com.eg.android.AlipayGphone.sesame.ledgerRefresh")
                            .setPackage("com.eg.android.AlipayGphone")
                            .putExtra("domain", syncDomain)
                    )
                    ToastUtil.show(
                        activity,
                        if (spec.key == "sports") "已重载配置并刷新运动状态"
                        else "已重载配置并刷新本域数据"
                    )
                }
            )
        }
    }
}

@Composable
private fun DomainCardView(
    card: DomainCardSpec,
    enabledCount: Int,
    onClick: () -> Unit,
    onDataClick: (() -> Unit)? = null,
    forceOff: Boolean = false,
    ledgerRunning: Boolean = false,
    ledgerRanToday: Boolean = false,
    summaryText: String? = null,
    statusOverride: Pair<String, Color>? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val status = statusOverride ?: when {
        forceOff -> "已关闭" to Color(0xFFE57373)
        card.type == DomainCardType.ENTRY -> "设置" to MedusaColors.Muted
        enabledCount == 0 -> "已关闭" to Color(0xFFE57373)
        ledgerRunning -> "运行中" to Color(0xFFF4C66F)
        ledgerRanToday -> "已运行" to MedusaColors.Green
        else -> "今日未跑" to Color(0xFFE57373)
    }
    Column(
        modifier
            .height(108.dp)
            .border(1.dp, Color(0xFFE7EDF3), shape)
            .background(Color.White, shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .background(card.accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                card.title,
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(status.second, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                status.first,
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.Medium,
                color = MedusaColors.Ink.copy(alpha = 0.82f),
                maxLines = 1
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                summaryText ?: "点击进入",
                fontSize = MedusaType.Caption,
                color = if (card.type == DomainCardType.ENTRY) MedusaColors.GreenDeep else MedusaColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onDataClick != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE6F7EE))
                        .clickable(onClick = onDataClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("数据", fontSize = 9.5.sp, color = MedusaColors.GreenDeep)
                }
            }
        }
    }
}

private data class FlatAction(val label: String, val onClick: () -> Unit)

/** 有独立任务列表的卡片：设置之后优先排列 */
private val TASK_LIST_CARDS = setOf(
    "ocean.reward", "ocean.fish",
    "farm.task", "farm.draw",
    "forest.reward", "forest.park", "forest.patrol",
    "species.task",
    "orchard.task", "orchard.park", "orchard.draw", "orchard.challenge"
)

/** 卡片里需要单独选好友的字段；空列表表示该卡不涉及好友名单 */
private val FARM_FRIEND_FIELDS = mapOf(
    "farm.daily" to listOf("visitFriendList"),
    "farm.friend" to listOf("feedFriendAnimalList", "hireAnimalList", "sendFriendList", "notifyFriendList"),
    "farm.family" to listOf("notInviteList")
)

/** 卡片里需要输入数值/选项的字段（不含好友名单、黑名单与家庭开关） */
private val FARM_VALUE_FIELDS = mapOf(
    "farm.daily" to listOf("useAccelerateToolOptions", "useSpecialFoodCountLimit"),
    "farm.donate" to listOf("donationType", "donationAmount"),
    "farm.park" to listOf("farmGameTime", "gameCenterBuyMallItemList"),
    "farm.competition" to listOf("competitionDonateAmount", "competitionStealMinutes", "competitionStealLimit"),
    "farm.sunflower" to listOf("sunflowerKeep"),
    "farm.friend" to listOf("hireAnimalType", "sendBackAnimalWay", "sendBackAnimalType", "notifyFriendType"),
    "farm.life" to listOf("sleepTime", "sleepMinutes", "ornamentsDressUpList", "ornamentsDressUpDays")
)


private data class FlatCapsule(val label: String, val selected: Boolean, val onToggle: () -> Unit)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlatCardView(
    card: DomainCardSpec,
    fields: List<ModelField<*>>,
    summaryText: String?,
    status: Pair<String, Color>,
    revision: Int,
    onToggle: (BooleanModelField, Boolean) -> Unit,
    actions: List<FlatAction> = emptyList(),
    capsules: List<FlatCapsule> = emptyList(),
    onDataClick: (() -> Unit)? = null,
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val booleans = fields.filterIsInstance<BooleanModelField>()
    val visible = when (card.id) {
        "ocean.settings", "farm.settings", "orchard.settings", "species.settings",
        "protect.settings", "green.settings" -> emptyList()
        "ocean.reward" -> booleans.filter { it.code == "queryTaskList" }
        "ocean.fish" -> booleans.filter { it.code == "antfishEnable" || it.code == "antfishAutoTask" }
        "farm.task" -> booleans.filter { it.code == "receiveFarmTaskAward" }
        "farm.draw" -> booleans.filter { it.code == "drawMachine" || it.code == "IPexchangeBenefit" }
        "farm.family" -> emptyList()
        "farm.competition" -> booleans.filter {
            it.code in setOf("competition", "competitionReceiveTask", "competitionDonate", "competitionStealRank")
        }
        "farm.friend" -> booleans.filter { it.code == "feedFriendAnimal" || it.code == "rewardFriend" }
        "farm.park" -> booleans.filter {
            it.code in setOf("recordFarmGame", "drawGameCenterAward", "gameCenterBuyMallItem")
        }
        "farm.life" -> booleans.filter {
            it.code in setOf("chickenDiary", "enableSleep", "ornamentsDressUp")
        }
        "farm.sunflower" -> booleans.filter { it.code == "sunflowerDonate" }
        "farm.kitchen" -> booleans.filter { it.code == "kitchen" }
        "forest.settings" -> emptyList()
        "forest.home" -> booleans.filter {
            it.code in setOf(
                "collectEnergy", "batchRobEnergy", "collectWateringBubble",
                "collectRobExpandEnergyEnable", "expiredEnergy", "pkEnergy", "energyPvp"
            )
        }
        // 好友卡是入口卡：四个分段全部在详情页里，卡面只留摘要，避免十来个胶囊堆在一起
        "forest.friends" -> emptyList()
        "forest.green" -> booleans.filter {
            it.code in setOf("greenLife", "marketBrowseTask", "greenRent", "readForest")
        }
        "forest.reward" -> booleans.filter { it.code == "receiveForestTaskAward" }
        "forest.park" -> booleans.filter { it.code == "drawGameCenterAward" }
        "forest.patrol" -> booleans.filter {
            it.code in setOf(
                "userPatrol", "monopolyPatrol", "monopolyTasks",
                "monopolyAnimalEnergy", "monopolyDispatchAnimal"
            )
        }
        "forest.backpack" -> booleans.filter {
            it.code in setOf("collectProp", "autoUseShieldCard", "energyRain", "useEnergyRainLimit", "dress")
        }
        "forest.ip" -> booleans.filter { it.code == "ipExchange" }
        "species.task" -> booleans.filter { it.code == "dodoTaskList" }
        "sports.settings" -> emptyList()
        "golden.settings" -> emptyList()
        "golden.sign" -> booleans.filter { it.code == "goldenBeansSign" }
        "golden.task" -> booleans.filter { it.code == "goldenBeansAutoTask" }
        "golden.sprout" -> booleans.filter { it.code == "goldenBeansVisitGarden" }
        "golden.miner" -> booleans.filter { it.code in setOf("goldenBeansAutoMine", "goldenBeansCollectReward") }
        "golden.game" -> booleans.filter { it.code == "goldenBeansGameDraw" }
        "golden.exchange" -> booleans.filter {
            it.code in setOf("goldenBeansAutoManureExchange", "goldenBeansAutoSesameExchange")
        }
        "golden.mall" -> booleans.filter { it.code == "goldenBeansMallExchange" }
        "kinetic.sign" -> booleans.filter { it.code == "kineticSignIn" }
        "kinetic.task" -> booleans.filter { it.code == "sportsTasks" }
        "kinetic.bubble" -> booleans.filter { it.code == "receiveCoinAsset" }
        "sports.walk" -> booleans.filter { it.code == "walk" }
        "sports.club" -> booleans.filter { it.code == "club" }
        "sports.health" -> booleans.filter { it.code == "neverLand" }
        "species.draw" -> booleans.filter { it.code == "scratchCard" }
        "species.friend" -> emptyList()
        "species.friends" -> emptyList()
        "species.prop" -> booleans.filter {
            it.code in setOf("useProp", "useUniversalCardBookCollectedhasCollected")
        }
        "species.book" -> booleans.filter { it.code == "bookMedal" }
        "species.gift" -> booleans.filter { it.code == "giftToFriend" }
        "orchard.fertilizer" -> booleans.filter { it.code == "collectFertilizerPacket" }
        "orchard.task" -> booleans.filter { it.code == "orchardListTask" }
        "orchard.sign" -> booleans.filter { it.code == "orchardSign" }
        "orchard.spread" -> booleans.filter { it.code == "orchardSpreadManure" }
        "orchard.park" -> booleans.filter { it.code in setOf("drawGameCenterAward", "receiveOrchardGameStay") }
        "orchard.assist" -> booleans.filter { it.code == "assistFriend" }
        "orchard.draw" -> booleans.filter { it.code == "orchardChouChouLe" }
        "orchard.challenge" -> booleans.filter { it.code == "orchardChallenge" }
        "orchard.factory" -> booleans.filter { it.code == "collectManure" }
        else -> booleans
    }
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    if (onOpenDetail != null) {
                        Modifier.clickable { onOpenDetail() }
                    } else {
                        Modifier
                    }
                )
                .border(1.dp, Color(0xFFE7EDF3), RoundedCornerShape(18.dp))
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(
                    start = 14.dp,
                    top = 14.dp,
                    bottom = 14.dp,
                    // 预留箭头槽位；所有列表入口共用同一条右侧基准线
                    end = 30.dp
                )
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .background(card.accent, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                card.title,
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).background(status.second, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(
                status.first,
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.Medium,
                color = MedusaColors.Ink.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (actions.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    actions.forEach { action ->
                        Box(
                            Modifier
                                .heightIn(min = 28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE6F7EE))
                                .clickable { action.onClick() }
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(action.label, fontSize = 9.5.sp, color = MedusaColors.GreenDeep)
                        }
                    }
                }
            }
            if (onDataClick != null) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE6F7EE))
                        .clickable(onClick = onDataClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("数据", fontSize = 9.5.sp, color = MedusaColors.GreenDeep)
                }
            }
        }
        if (!summaryText.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                summaryText,
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (visible.isNotEmpty() || capsules.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val items = ArrayList<CapsuleSpec>()
            visible.forEach { field ->
                val label = when {
                    card.id == "orchard.park" && field.code == "drawGameCenterAward" -> "砸蛋与任务"
                    card.id == "orchard.park" && field.code == "receiveOrchardGameStay" -> "游戏试玩"
                    card.id == "kinetic.task" && field.code == "sportsTasks" -> "自动领奖"
                    card.id == "kinetic.sign" && field.code == "kineticSignIn" -> "自动签到"
                    card.id == "kinetic.bubble" && field.code == "receiveCoinAsset" -> "收运动币"
                    card.id == "golden.sprout" && field.code == "goldenBeansVisitGarden" -> "一键收豆"
                    card.id == "golden.task" && field.code == "goldenBeansAutoTask" -> "自动任务"
                    card.id == "golden.exchange" && field.code == "goldenBeansAutoManureExchange" -> "肥料换豆"
                    card.id == "golden.exchange" && field.code == "goldenBeansAutoSesameExchange" -> "芝麻粒换豆"
                    else -> fieldLabel(field)
                }
                items.add(
                    CapsuleSpec(label, field.value == true) {
                        onToggle(field, !(field.value == true))
                    }
                )
            }
            capsules.forEach { capsule ->
                items.add(CapsuleSpec(capsule.label, capsule.selected, capsule.onToggle))
            }
            androidx.compose.runtime.key(revision) {
                if (card.id == "farm.family" && items.size > 1) {
                    FamilyCapsuleLayout(items)
                } else {
                    TrapezoidCapsuleLayout(items)
                }
            }
        }
    }
        if (onOpenDetail != null) {
            Text(
                "›",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB3BCC9),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 9.dp)
            )
        }
    }
}

private fun clockText(millis: Long): String {
    if (millis <= 0L) return "07:00"
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(
        Locale.CHINA,
        "%02d:%02d",
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE)
    )
}

private fun fieldLabel(field: BooleanModelField): String {
    when (field.code) {
        "queryTaskList" -> return "奖励任务"
        "collectFertilizerPacket" -> return "自动领取"
        "orchardListTask" -> return "肥料任务"
        "orchardSign" -> return "自动签到"
        "orchardSpreadManure" -> return "自动施肥"
        "assistFriend" -> return "好友助力"
        "orchardChouChouLe" -> return "阿肥寻宝记"
        "orchardChallenge" -> return "限时挑战"
        "collectManure" -> return "收取肥料"
        "receiveOrchardGameStay" -> return "游戏试玩"
        "replica" -> return "神秘海域"
        "receiveFarmTaskAward" -> return "饲料任务"
        "feedAnimal" -> return "投喂"
        "harvestProduce" -> return "收蛋"
        "useNewEggTool" -> return "新蛋卡"
        "useAccelerateTool" -> return "加速卡"
        "useBigEaterTool" -> return "加饭卡"
        "useSpecialFood" -> return "特殊饲料"
        "acceptGift" -> return "收礼物"
        "receiveFarmToolReward" -> return "领道具"
        "recordFarmGame" -> return "自动游戏"
        "drawGameCenterAward" -> return "游戏宝箱"
        "gameCenterBuyMallItem" -> return "乐园集市"
        "drawMachine" -> return "装扮抽奖"
        "IPexchangeBenefit" -> return "装扮兑换"
        "sunflowerDonate" -> return "自动捐葵花籽"
        "competition" -> return "自动参与"
        "competitionReceiveTask" -> return "自动领奖"
        "competitionDonate" -> return "自动捐蛋"
        "competitionStealRank" -> return "抢榜"
        "family" -> return "家庭空间"
        "feedFriendAnimal" -> return "帮喂小鸡"
        "rewardFriend" -> return "打赏好友"
        "kitchen" -> return "自动做菜"
        "chickenDiary" -> return "小鸡日记"
        "enableSleep" -> return "自动睡觉"
        "ornamentsDressUp" -> return "自动换装"
        "antfishEnable" -> return "自动捞鱼"
        "antfishAutoTask" -> return "自动任务"
        "autoWelcome" -> return "自动找回"
        "exchangeUniversalPiece" -> return "拼图制作"
        "useUniversalPiece" -> return "拼图使用"
        "cleanMorning" -> return "上午清理"
        "cleanEvening" -> return "下午清理"
        "cleanFriends" -> return "清理好友"
        "kineticSignIn" -> return "自动签到"
        "sportsTasks" -> return "自动任务"
        "club" -> return "自动参与"
        "tiyubiz" -> return "自动打卡"
        "neverLand" -> return "启用健康岛"
        "QUERY_SIGN" -> return "每日签到"
        "QUERY_TASK_CENTER" -> return "做任务"
        "QUERY_BUBBLE_TASK" -> return "领能量奖励"
        "WALK_GRID" -> return "能量抽取"
        "MapListSwitch" -> return "自动换岛"
        "awardspecialActivityReceive" -> return "活动岛奖励"
        "walk" -> return "自动走路"
        "receiveCoinAsset" -> return "收运动币"
        "collectEnergy" -> return "收能量"
        "teamPriority" -> return "组队优先"
        "batchRobEnergy" -> return "批量收取"
        "collectWateringBubble" -> return "金色能量球"
        "collectRobExpandEnergyEnable" -> return "多倍卡"
        "expiredEnergy" -> return "补领过期能量"
        "pkEnergy" -> return "榜单"
        "energyPvp" -> return "1V1挑战"
        "collectGiftBox" -> return "好友礼盒"
        "waterFriendEnable" -> return "好友浇水"
        "waterMode" -> return "浇水方式"
        "waterFriendType" -> return "每次克数"
        "waterDailyLimit" -> return "每日上限"
        "returnWater" -> return "回赠好友(旧)"
        "waterFriendEnergySendChat" -> return "留话提醒"
        "waterFriendEnergyFirst" -> return "优先执行"
        "doubleWaterFriendEnergy" -> return "重复检查"
        "partnerteamWater" -> return "组队合种"
        "loveteamWater" -> return "真爱合种"
        "receiveForestTaskAward" -> return "活力值任务"
        "vitalityExchangeBenefit" -> return "活力值兑换"
        "youthPrivilege" -> return "学生特权"
        "medicalHealth" -> return "健康能量"
        "dress" -> return "自动穿戴"
        "ForestHunt" -> return "自动寻宝"
        "ForestHuntDraw" -> return "自动抽奖"
        "ForestHuntHelp" -> return "好友助力"
        "NORMALForestHuntHelp" -> return "强制助力·普通"
        "ACTIVITYForestHuntHelp" -> return "强制助力·活动"
        "AutoAntForestHuntTaskList" -> return "自动黑名单"
        "userPatrol" -> return "旧版地图"
        "monopolyPatrol" -> return "新版地图"
        "monopolyTasks" -> return "自动任务"
        "monopolyAnimalEnergy" -> return "动物能量"
        "monopolyDispatchAnimal" -> return "自动派遣"
        "collectProp" -> return "收好友道具"
        "autoUseShieldCard" -> return "自动保护罩"
        "energyRain" -> return "能量雨"
        "useEnergyRainLimit" -> return "限时能量雨卡"
        "ipExchange" -> return "自动兑换保护罩"
        "dodoTaskList" -> return "自动任务"
        "cooperateWater" -> return "自动浇水"
        "protectTree" -> return "自动植树"
        "protectReserve" -> return "自动保护地"
        "protectReserveMinNum" -> return "限制保护下限"
        "protectAnimal" -> return "自动护林员"
        "protectBeachMinNum" -> return "限制海滩下限"
        "protectBeach" -> return "自动保护海滩"
        "ancientTreeOnlyWeek" -> return "周一三五"
        "ecoLifeGrain", "ecoLifeTravel", "ecoLifeElectricity",
        "ecoLifeWater", "ecoLifeRecycle", "ecoLifePaper" -> return "自动打卡"
        "scratchCard" -> return "自动抽卡"
        "useProp" -> return "自动使用"
        "useUniversalCardBookCollectedhasCollected" -> return "优先新卡"
        "bookMedal" -> return "自动合成"
        "giftToFriend" -> return "自动赠送"
    }
    val name = field.name ?: field.code ?: ""
    return name.substringAfterLast('|').substringAfterLast('｜').trim().ifEmpty { name }
}

private data class CapsuleSpec(
    val label: String,
    val checked: Boolean,
    val onToggle: () -> Unit
)

/** 亲密家庭专用：等宽三列网格，最后一行不足三项时居中。 */
@Composable
private fun FamilyCapsuleLayout(items: List<CapsuleSpec>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val side = (3 - row.size) / 2
                repeat(side) { Spacer(Modifier.weight(1f)) }
                row.forEach { item ->
                    Box(Modifier.weight(1f)) {
                        CapsuleToggle(
                            item.label,
                            item.checked,
                            item.onToggle,
                            Modifier.fillMaxWidth()
                        )
                    }
                }
                repeat(3 - row.size - side) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * 胶囊排布：行数由实际宽度决定，行内数量呈「上窄下宽」的梯形；
 * 两行时 2/3，三行时 2/3/4（数量不足则保持非递减并居中）。
 */
@Composable
private fun TrapezoidCapsuleLayout(items: List<CapsuleSpec>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val maxWidth = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val placeables = items.mapIndexed { index, item ->
            subcompose(index) {
                CapsuleToggle(item.label, item.checked, item.onToggle)
            }.map { it.measure(Constraints(maxWidth = maxWidth)) }.first()
        }
        val widths = placeables.map { it.width }
        val heights = placeables.map { it.height }
        val rows = chooseCapsuleRows(widths, maxWidth, gap)
        val rowHeights = rows.map { row -> row.maxOf { heights[it] } }
        val totalHeight = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(maxWidth, totalHeight) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                // 梯形只体现在行内数量；整体保持左对齐
                var x = 0
                row.forEach { itemIndex ->
                    val placeable = placeables[itemIndex]
                    placeable.placeRelative(x, y + (rowHeights[rowIndex] - placeable.height) / 2)
                    x += placeable.width + gap
                }
                y += rowHeights[rowIndex] + gap
            }
        }
    }
}

private fun chooseCapsuleRows(widths: List<Int>, maxWidth: Int, gap: Int): List<List<Int>> {
    val count = widths.size
    if (count == 0) return emptyList()
    for (rowCount in 1..count) {
        val rowSizes = capsuleRowSizes(count, rowCount)
        val rows = ArrayList<MutableList<Int>>(rowSizes.size)
        var index = 0
        var fits = true
        for (size in rowSizes) {
            val row = ArrayList<Int>(size)
            var width = 0
            repeat(size) {
                if (index >= count) return@repeat
                val extra = if (row.isEmpty()) widths[index] else widths[index] + gap
                width += extra
                row.add(index)
                index++
            }
            if (width > maxWidth) {
                fits = false
            }
            rows.add(row)
        }
        if (fits && index == count) {
            return rows
        }
    }
    return widths.indices.map { listOf(it) }
}

private fun capsuleRowSizes(count: Int, rowCount: Int): List<Int> {
    if (rowCount <= 1) return listOf(count)
    val triangle = rowCount * (rowCount + 3) / 2 // 2 + 3 + ... + (rowCount + 1)
    if (count == triangle) {
        return List(rowCount) { 2 + it }
    }
    val base = count / rowCount
    val remainder = count % rowCount
    return List(rowCount) { index ->
        base + if (index >= rowCount - remainder) 1 else 0
    }
}

@Composable
private fun CapsuleToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val checkedColor = MedusaColors.GreenDeep
    Box(
        modifier
            .heightIn(min = 34.dp)
            .shadow(if (checked) 2.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(if (checked) checkedColor else Color.White)
            .border(1.dp, if (checked) checkedColor else Color(0xFFDDE4EE), shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = MedusaType.Secondary,
            fontWeight = FontWeight.Medium,
            color = if (checked) Color.White else MedusaColors.Ink.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                shadow = if (checked) {
                    Shadow(color = Color(0x40000000), offset = Offset(0f, 1f), blurRadius = 2f)
                } else {
                    Shadow(color = Color.Transparent, offset = Offset.Zero, blurRadius = 0f)
                }
            )
        )
    }
}

@Composable
private fun ForestBackpackDialog(items: org.json.JSONArray?, onDismiss: () -> Unit) {
    val count = items?.length() ?: 0
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("森林背包", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = MedusaColors.Ink)
                Spacer(Modifier.weight(1f))
                Text("$count 种", fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF2F5FA))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MedusaColors.Muted, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "当前背包中的道具；数量为服务端持有数",
                fontSize = MedusaType.Secondary,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                if (items == null || count == 0) {
                    Text("背包为空", fontSize = MedusaType.Body, color = MedusaColors.Muted)
                } else {
                    for (index in 0 until count) {
                        val item = items.optJSONObject(index) ?: continue
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.optString("name"), fontSize = MedusaType.Body, color = MedusaColors.Ink)
                            }
                            Text(
                                "x" + item.optInt("count", 0),
                                fontSize = MedusaType.Body,
                                fontWeight = FontWeight.SemiBold,
                                color = MedusaColors.GreenDeep
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainToolbar(
    onSave: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onLog: () -> Unit,
    onSync: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFE7EDF3), shape)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolItem(Icons.Filled.Check, "保存", onSave, Modifier.weight(1f))
        ToolItem(Icons.Filled.KeyboardArrowUp, "导出", onExport, Modifier.weight(1f))
        ToolItem(Icons.Filled.KeyboardArrowDown, "导入", onImport, Modifier.weight(1f))
        ToolItem(Icons.Filled.List, "本域日志", onLog, Modifier.weight(1f))
        ToolItem(Icons.Filled.Sync, "本域同步", onSync, Modifier.weight(1f))
    }
}

@Composable
private fun ToolItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MedusaColors.GreenDeep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 9.5.sp, color = MedusaColors.Ink.copy(alpha = 0.82f), maxLines = 1)
    }
}

@Composable
private fun DomainMetricPickerDialog(
    title: String,
    options: List<DomainMetricOption>,
    mainIds: Set<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val available = options.filter { it.id !in mainIds }
    var picked by remember {
        mutableStateOf(
            (selected.filter { id -> available.any { it.id == id } } + available.map { it.id })
                .distinct()
                .take(6)
                .toSet()
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = MedusaColors.Ink)
                Spacer(Modifier.weight(1f))
                Text("${picked.size}/6", fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF2F5FA))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = MedusaColors.Muted, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "最少 3 条、最多 6 条；主显示已选的指标不会在卡片上重复出现",
                fontSize = MedusaType.Secondary,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                options.forEach { option ->
                    val inMain = option.id in mainIds
                    val checked = option.id in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !inMain) {
                                if (inMain) return@clickable
                                picked = when {
                                    checked -> if (picked.size > 3) picked - option.id else picked
                                    picked.size < 6 -> picked + option.id
                                    else -> picked
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                option.label,
                                fontSize = MedusaType.Body,
                                color = if (inMain) MedusaColors.Muted else MedusaColors.Ink
                            )
                            Text(
                                when {
                                    inMain -> "已在主显示"
                                    option.unit.isBlank() -> "本域指标"
                                    else -> "单位：${option.unit}"
                                },
                                fontSize = MedusaType.Caption,
                                color = Color(0xFFA2ACBC)
                            )
                        }
                        if (inMain) {
                            Text("主显示", fontSize = MedusaType.Caption, color = MedusaColors.Muted)
                        } else {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (checked) MedusaColors.Green else Color(0xFFF0F3F7)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (checked) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF2F5FA))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("取消", fontSize = MedusaType.Body, color = MedusaColors.Muted)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (picked.isEmpty()) Color(0xFFDDE4EE) else MedusaColors.Green)
                        .clickable(enabled = picked.isNotEmpty()) {
                            onConfirm(options.filter { it.id in picked }.map { it.id })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("完成", fontSize = MedusaType.Body, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

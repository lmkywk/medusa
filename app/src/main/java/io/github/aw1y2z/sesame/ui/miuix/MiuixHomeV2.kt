package io.github.aw1y2z.sesame.ui.miuix

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EggAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.data.home.HomeSnapshotService
import io.github.aw1y2z.sesame.data.ledger.FarmStatus
import io.github.aw1y2z.sesame.data.ledger.ForestStatus
import io.github.aw1y2z.sesame.data.ledger.GoldenBeansStatus
import io.github.aw1y2z.sesame.data.ledger.GreenActionStatus
import io.github.aw1y2z.sesame.data.ledger.KineticStatus
import io.github.aw1y2z.sesame.data.ledger.MemberStatus
import io.github.aw1y2z.sesame.data.ledger.OceanStatus
import io.github.aw1y2z.sesame.data.ledger.OrchardStatus
import io.github.aw1y2z.sesame.data.ledger.ProtectStatus
import io.github.aw1y2z.sesame.data.ledger.SpeciesStatus
import io.github.aw1y2z.sesame.data.ledger.StallStatus
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.data.plan.PlanHistory
import io.github.aw1y2z.sesame.data.plan.PlanRuntime
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import io.github.aw1y2z.sesame.model.normal.base.BaseModel
import io.github.aw1y2z.sesame.model.task.antDodo.AntDodo
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm
import io.github.aw1y2z.sesame.model.task.antForest.AntForestV2
import io.github.aw1y2z.sesame.model.task.antMember.AntMember
import io.github.aw1y2z.sesame.model.task.antOcean.AntOcean
import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchard
import io.github.aw1y2z.sesame.model.task.antSports.AntSports
import io.github.aw1y2z.sesame.model.task.antStall.AntStall
import io.github.aw1y2z.sesame.model.task.goldenbeans.goldenbeans
import io.github.aw1y2z.sesame.model.task.greenFinance.GreenFinance
import io.github.aw1y2z.sesame.model.task.protectEcology.ProtectEcology
import io.github.aw1y2z.sesame.util.RiskGuard
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

private val HomePageBg = MedusaColors.PageBg
private val HomeInk = MedusaColors.Ink
private val HomeMuted = MedusaColors.Muted
private val HomeGreen = MedusaColors.Green
private val HomePurple = MedusaColors.Purple
private val HomeGold = MedusaColors.Gold
private val HomeBlue = MedusaColors.Blue

private data class HomeMetric(
    val label: String,
    val value: String,
    val unit: String,
    val icon: ImageVector,
    val tone: Color,
    val emphasize: Boolean = true,
    /** 最后一页不足 3 个时补的占位格：只占宽度，不画内容，保证始终三列。 */
    val placeholder: Boolean = false
)

/** 首页域卡的任务进度：只显示已处理/总数，进度条只在计划卡上出现。 */
private data class HomeTaskProgress(
    val done: Int,
    val total: Int
)

private fun homeTaskTarget(moduleKey: String): String? =
    DomainCatalog.byKey(moduleKey)?.taskTarget

/** 手动/计划目标经常是域级（farm、ocean…），卡片要按域匹配运行状态，不能只比 scope。 */
private fun homeTargetMatches(module: HomeModule, target: String): Boolean =
    DomainCatalog.byKey(module.key)?.matchesTarget(target) ?: (target == module.scope)

private data class MainMetricSpec(
    val id: String,
    val domain: String,
    val metric: HomeMetric
)

private data class HomeModule(
    val key: String,
    val title: String,
    val groupCode: String,
    val defaultBg: Int,
    /** 没有内置插画时的渐变底色，保证每张卡都能独立自定义背景 */
    val fallbackBg: List<Color>,
    val scope: String,
    val modelClass: Class<out Model>?,
    /** 没有可单独执行的入口时隐藏三角，避免点了整组乱跑 */
    val showRun: Boolean = true
)

private val AllHomeModules = DomainCatalog.homeEntries.map { entry ->
    HomeModule(
        key = entry.key,
        title = entry.title,
        groupCode = entry.groupCode,
        defaultBg = entry.defaultBg,
        fallbackBg = entry.fallbackBg,
        scope = entry.scope,
        modelClass = entry.modelClass,
        showRun = entry.showRun
    )
}

@Composable
fun HomeV2Tab(activity: MiuixMainActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("medusa_home_v2", Context.MODE_PRIVATE) }
    var grid by remember { mutableStateOf(prefs.getBoolean("grid", false)) }
    // 4.0.0：默认背景换成新的蛇灵图；升级后一次性把旧的“庄园/森林/海洋”选择复位，
    // 否则装完看起来“没换”（用户仍停在旧背景上）。复位只做一次，之后用户再选就长期保留。
    var heroBg by remember {
        mutableIntStateOf(
            if (prefs.getInt("hero_bg_version", 0) < 1) {
                prefs.edit().putInt("hero_bg", 0).putInt("hero_bg_version", 1).apply()
                0
            } else {
                prefs.getInt("hero_bg", 0)
            }
        )
    }
    var order by remember { mutableStateOf(loadModuleOrder(prefs)) }
    var hidden by remember { mutableStateOf(loadHiddenModules(prefs)) }
    var showManage by remember { mutableStateOf(false) }
    var bgPickerModule by remember { mutableStateOf<HomeModule?>(null) }
    var pendingRun by remember { mutableStateOf<HomeModule?>(null) }
    var showCooldown by remember { mutableStateOf(false) }
    var bgRevision by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    // “同步”按钮的即时重读信号：只刷新数据，不推进轮播页码，避免点同步时卡片跳页。
    var dataRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (DomainMetricCatalog.migrateDefaults(prefs)) tick++
    }
    // 单列卡片固定三格：数据多的域按同一节拍轮换，数据少的域不动
    var metricPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            metricPage++
        }
    }
    var undoModule by remember { mutableStateOf<HomeModule?>(null) }
    // 同一时间只允许一张主域卡片处于“长按选定”态；滑动结束、点卡片或 5 秒无操作都会退出。
    var selectedModuleKey by remember { mutableStateOf<String?>(null) }
    val uid = activity.currentAccountId
    val moduleTargets = remember {
        AllHomeModules.associate { it.key to homeTaskTarget(it.key) }
    }
    val snapshot = remember(uid, activity.statisticsVersion, activity.planRevision, tick, dataRevision) {
        HomeSnapshotService.load(uid, moduleTargets)
    }
    val oceanStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else OceanStatus.loadToday(uid)
    }
    val farmStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else FarmStatus.loadToday(uid)
    }
    val forestStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else ForestStatus.loadToday(uid)
    }
    val orchardStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else OrchardStatus.loadToday(uid)
    }
    val speciesStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else SpeciesStatus.loadToday(uid)
    }
    val goldenStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else GoldenBeansStatus.loadToday(uid)
    }
    val kineticStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else KineticStatus.loadToday(uid)
    }
    val memberStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else MemberStatus.loadToday(uid)
    }
    val stallStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else StallStatus.loadToday(uid)
    }
    val protectStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else ProtectStatus.loadToday(uid)
    }
    val greenStatus = remember(uid, tick, dataRevision) {
        if (uid == null) null else GreenActionStatus.loadToday(uid)
    }

    fun statusNumber(key: String): String {
        val value = oceanStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun statusPuzzle(): String {
        val fixed = oceanStatus?.optInt("fixedSeaAreaNum", -1) ?: -1
        val total = oceanStatus?.optInt("seaAreaNum", -1) ?: -1
        return if (fixed < 0 || total <= 0) "—" else "$fixed/$total"
    }

    fun farmInt(key: String): String {
        val value = farmStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun farmEggs(key: String): String {
        val value = farmStatus?.optDouble(key, -1.0) ?: -1.0
        return if (value < 0) "—" else number(Math.floor(value).toInt())
    }

    fun kineticInt(key: String): String {
        val value = kineticStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun memberInt(key: String): String {
        val value = memberStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun memberSignText(): String {
        val status = memberStatus ?: return "—"
        return if (status.optBoolean("autoSignInSuccess", false)) "已签" else "未签"
    }

    fun stallInt(key: String): String {
        val value = stallStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun forestInt(key: String): String {
        val value = forestStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun orchardInt(section: String, key: String): String {
        val value = orchardStatus?.optJSONObject(section)?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun orchardTreePercent(): String {
        val tree = orchardStatus?.optJSONObject("tree") ?: return "—"
        val percent = tree.optDouble("percent", -1.0)
        return if (percent < 0) "—" else "${percent.toInt()}%"
    }

    fun orchardSignText(): String {
        val sign = orchardStatus?.optJSONObject("sign") ?: return "—"
        return if (sign.optBoolean("signed", false)) "已签" else "待签"
    }

    fun speciesInt(key: String): String {
        val value = speciesStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun speciesCardsText(): String {
        val status = speciesStatus ?: return "—"
        val collected = status.optInt("collectedCards", -1)
        val total = status.optInt("totalCards", 0)
        return if (collected < 0 || total <= 0) "—" else number(collected)
    }

    fun speciesHelpText(): String {
        val status = speciesStatus ?: return "—"
        return number(minOf(status.optInt("friendLeft", 0), status.optInt("friendAvailable", 0)))
    }

    fun speciesDoneText(): String {
        val status = speciesStatus ?: return "—"
        return if (status.optBoolean("dailyCollected", false)) "已抽" else "未抽"
    }

    fun goldenInt(key: String): String {
        val value = goldenStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun goldenSignText(): String {
        val status = goldenStatus ?: return "—"
        return if (status.optBoolean("signed", false)) "已签" else "待签"
    }

    fun protectInt(key: String): String {
        val value = protectStatus?.optInt(key, -1) ?: -1
        return if (value < 0) "—" else number(value)
    }

    fun kineticSignText(): String =
        if (kineticStatus == null) "—"
        else if (kineticStatus.optBoolean("signed", false)) "已签"
        else "待签"
    var riskPaused by remember { mutableStateOf(snapshot.riskPaused) }
    var verificationRequired by remember { mutableStateOf(snapshot.verificationRequired) }
    LaunchedEffect(snapshot.riskPaused, snapshot.verificationRequired) {
        riskPaused = snapshot.riskPaused
        verificationRequired = snapshot.verificationRequired
    }
    val stats = snapshot.stats
    val plans = snapshot.plans
    val history = snapshot.history
    val runtime = snapshot.runtime
    val taskProgressByModule = remember(snapshot.taskProgressByModule) {
        snapshot.taskProgressByModule.mapValues {
            HomeTaskProgress(done = it.value.done, total = it.value.total)
        }
    }
    LaunchedEffect(runtime.noticeAt) { maybeShowRunNotice(context, runtime) }
    val runningTarget = if (runtime.state == PlanRuntime.RUNNING
        && System.currentTimeMillis() - runtime.updatedAt < 35L * 60L * 1000L
    ) runtime.target else ""
    LaunchedEffect(undoModule) {
        if (undoModule != null) {
            delay(4500)
            undoModule = null
        }
    }

    fun moduleEnabledOf(module: HomeModule): Boolean = module.modelClass?.let {
        runCatching { Model.getModel(it).isEnable == true }.getOrDefault(false)
    } ?: true

    // 首页滑动/撤销共用：只改本域总开关，计划开关保持原样，由执行入口统一派生拦截
    fun setModuleEnabled(module: HomeModule, enabled: Boolean, allowUndo: Boolean = true) {
        val model = module.modelClass?.let { runCatching { Model.getModel(it) }.getOrNull() } ?: return
        val field = model.enableField
        if (field.value == enabled) return
        if (!enabled && homeTargetMatches(module, runningTarget)) {
            context.sendMedusaBroadcast(
                Intent("com.eg.android.AlipayGphone.sesame.planStop")
                    .setPackage("com.eg.android.AlipayGphone")
            )
        }
        field.setObjectValue(enabled)
        val uidNow = activity.currentAccountId
        val ok = uidNow != null && runCatching { ConfigV2.save(uidNow, true) }.getOrDefault(false)
        if (!ok) {
            field.setObjectValue(!enabled)
            ToastUtil.show(context, "保存失败，请检查文件权限")
            return
        }
        ConfigSync.notifyRestart(context, uidNow)
        tick++
        if (enabled) {
            undoModule = null
            ToastUtil.show(context, "已启用 ${module.title}")
        } else {
            if (allowUndo) undoModule = module
        }
    }

    // 卡片上的「执行」：直接对该域发起一轮，不占计划轮次。
    // 节律建议只弹确认，不阻止用户；用户选择继续时带 ignoreCooldown。
    fun sendRun(module: HomeModule, ignoreCooldown: Boolean, message: String? = null,
                forceCollect: Boolean = false) {
        if (PreviewMode.enabled) {
            ToastUtil.show(context, "UI 试验版：执行动作只演示，不会真的下发")
            return
        }
        val calendar = Calendar.getInstance()
        val time = String.format(
            Locale.CHINA, "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)
        )
        context.sendBroadcast(
            Intent("com.eg.android.AlipayGphone.sesame.ledgerRefresh")
                .setPackage("com.eg.android.AlipayGphone")
                .putExtra("domain", if (module.key == "forest") "forest" else module.scope)
        )
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            context.sendMedusaBroadcast(
                Intent("com.eg.android.AlipayGphone.sesame.planRun")
                    .setPackage("com.eg.android.AlipayGphone")
                    .putExtra("target", module.scope)
                    .putExtra("roundTime", time)
                    .putExtra("ignoreCooldown", ignoreCooldown)
                    .putExtra("forceCollect", forceCollect)
            )
        }, 1_200L)
        ToastUtil.show(
            context,
            message ?: if (ignoreCooldown) "已同步任务，按顺序补跑「" + module.title + "」"
            else "已同步任务，补跑「" + module.title + "」未完成项"
        )
        tick++
    }

    fun runModuleNow(module: HomeModule) {
        // 森林右上角是“手动收能量”：单轮收能量 + 60 秒冷却，连点只弹气泡
        if (module.key == "forest") {
            if (!moduleEnabledOf(module)) {
                ToastUtil.show(context, "森林已关闭，点卡片进配置开启后再收能量")
                return
            }
            if (RiskGuard.isGlobalPaused() || RiskGuard.isVerificationRequired()) {
                ToastUtil.show(context, "当前风控暂停，暂不能手动收能量")
                return
            }
            if (RiskGuard.isPaused(module.scope)) {
                ToastUtil.show(context, "森林收能量当前被风控暂停，暂不能手动收")
                return
            }
            if (TestModeStore.load(uid ?: "").enabled) {
                ToastUtil.show(context, "测试模式进行中，请用测试清单执行")
                return
            }
            val now = System.currentTimeMillis()
            val last = prefs.getLong("forest_manual_collect_at", 0L)
            val remaining = 60_000L - (now - last)
            if (remaining > 0L) {
                ToastUtil.show(context, "冷却中，还需 " + ((remaining + 999L) / 1000L) + " 秒")
                return
            }
            prefs.edit().putLong("forest_manual_collect_at", now).apply()
            sendRun(module, true, "已发起「蚂蚁森林·收能量」；正在扫描自己/好友能量", forceCollect = true)
            return
        }
        val remaining = manualCooldownRemaining(runtime, module.scope, System.currentTimeMillis())
        if (remaining > 0L) {
            pendingRun = module
            showCooldown = true
        } else {
            sendRun(module, false)
        }
    }
    var configLoaded by remember { mutableStateOf(false) }

    // 模块 App 进程默认没有账号配置，Model 的 enable 全是默认值，首页会把已开启的模块显示成未开启。
    LaunchedEffect(uid) {
        configLoaded = false
        if (uid != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    ConfigPreload.prepare(uid)
                }
            }
            configLoaded = true
            tick++
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            tick++
        }
    }

    val todayCompleted = remember(plans, history) { countCompletedToday(history) }
    val pendingPlans = remember(plans, history) {
        (plans.filter { it.isEnabled }.sumOf { it.roundTimes.size } - todayCompleted).coerceAtLeast(0)
    }
    val dayCollected = stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.COLLECTED)
    val daySpent = runCatching { Statistics.getDayCollectedSpent() }.getOrDefault(0)
    val dayRob = runCatching { Statistics.getDayCollectedRob() }.getOrDefault(0)
    fun switchesOf(clazz: Class<out Model>): String =
        number(runCatching { Model.countEnabledSwitches(clazz) }.getOrDefault(-1))
    fun forestField(code: String): ModelField<*>? =
        Model.getGroupModelConfig(ModelGroup.FOREST).values.firstNotNullOfOrNull { it.getModelField(code) }
    // 看板按「域」整页轮播：三格同属一个域，3 秒后三格一起换到下一个域
    // 主显示由用户在首页状态行选择；最多 15 条、5 页，先接现有 12 条指标
    val mainMetricCatalog = listOf(
        MainMetricSpec("forest.day", "蚂蚁森林", HomeMetric("今日得能量", number(dayCollected), "g", Icons.Filled.MonetizationOn, HomeGreen)),
        MainMetricSpec("forest.rob", "蚂蚁森林", HomeMetric("收好友", number(dayRob), "g", Icons.Filled.DirectionsWalk, HomeBlue)),
        MainMetricSpec("forest.robbed", "蚂蚁森林", HomeMetric("被好友收", forestInt("todayRobbed"), "g", Icons.Filled.WaterDrop, MedusaColors.Warning)),
        MainMetricSpec("farm.eggs", "蚂蚁庄园", HomeMetric("今日捐蛋", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.EGGS)), "颗", Icons.Filled.EggAlt, HomeGold)),
        MainMetricSpec("farm.drawn", "蚂蚁庄园", HomeMetric("今日抽奖", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.DRAWN)), "次", Icons.Filled.CardGiftcard, HomeGold)),
        MainMetricSpec("farm.harvestEggs", "蚂蚁庄园", HomeMetric("储备蛋", farmEggs("harvestEggs"), "颗", Icons.Filled.EggAlt, HomeGold)),
        MainMetricSpec("farm.food", "蚂蚁庄园", HomeMetric("饲料", farmInt("foodStock"), "g", Icons.Filled.Eco, HomeGreen)),
        MainMetricSpec("farm.eggsStock", "蚂蚁庄园", HomeMetric("库存蛋", farmEggs("benevolenceEggs"), "颗", Icons.Filled.CardGiftcard, HomePurple)),
        MainMetricSpec("farm.parkBox", "蚂蚁庄园", HomeMetric("乐园宝箱", farmInt("parkBoxQuota"), "个", Icons.Filled.CardGiftcard, HomeGold)),
        MainMetricSpec("farm.parkTasks", "蚂蚁庄园", HomeMetric("乐园可领", farmInt("parkTaskClaimable"), "项", Icons.Filled.Tune, HomeBlue)),
        MainMetricSpec("farm.kitchen", "蚂蚁庄园", HomeMetric("可做菜", farmInt("kitchenCookTimes"), "次", Icons.Filled.Eco, HomeGold)),
        MainMetricSpec("farm.diary", "蚂蚁庄园", HomeMetric("日记未读", farmInt("diaryUnreadCount"), "篇", Icons.Filled.List, HomePurple)),
        MainMetricSpec("farm.switches", "蚂蚁庄园", HomeMetric("已开启", switchesOf(AntFarm::class.java), "项", Icons.Filled.Tune, HomeGreen)),
        MainMetricSpec("ocean.cleaned", "神奇海洋", HomeMetric("今日清理", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.CLEANED)), "次", Icons.Filled.Waves, HomeGreen)),
        MainMetricSpec("ocean.total", "神奇海洋", HomeMetric("累计清理", number(stats.dataOf(Statistics.TimeType.YEAR, Statistics.DataType.CLEANED)), "块", Icons.Filled.Waves, HomePurple)),
        MainMetricSpec("ocean.welcome", "神奇海洋", HomeMetric("可找回", statusNumber("welcomeReady"), "个", Icons.Filled.CardGiftcard, HomeGreen)),
        MainMetricSpec("ocean.fish", "神奇海洋", HomeMetric("可捞鱼", statusNumber("fishRemainTouchChance"), "次", Icons.Filled.Waves, HomeBlue)),
        MainMetricSpec("ocean.rubbish", "神奇海洋", HomeMetric("自家垃圾", statusNumber("rubbishNumber"), "块", Icons.Filled.WaterDrop, HomeGold)),
        MainMetricSpec("ocean.puzzle", "神奇海洋", HomeMetric("修复海域", statusPuzzle(), "", Icons.Filled.Tune, HomePurple)),
        MainMetricSpec("ocean.switches", "神奇海洋", HomeMetric("已开启", switchesOf(AntOcean::class.java), "项", Icons.Filled.Tune, HomeBlue)),
        MainMetricSpec("sports.balance", "医疗健康·运动", HomeMetric("健康能量", kineticInt("balance"), "g", Icons.Filled.MonetizationOn, HomeGold)),
        MainMetricSpec("sports.steps", "医疗健康·运动", HomeMetric("今日步数", kineticInt("steps"), "步", Icons.Filled.DirectionsWalk, HomeBlue)),
        MainMetricSpec("sports.bubbles", "医疗健康·运动", HomeMetric("待收气泡", kineticInt("bubbleCount"), "个", Icons.Filled.CardGiftcard, HomePurple)),
        MainMetricSpec("sports.walk", "医疗健康·运动", HomeMetric("可捐步数", kineticInt("walkStepsToday"), "步", Icons.Filled.Eco, HomeGreen)),
        MainMetricSpec("sports.sign", "医疗健康·运动", HomeMetric("签到", kineticSignText(), "", Icons.Filled.CheckCircle, HomeGreen)),
        MainMetricSpec("plan.steps", "计划", HomeMetric("今日步数", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.STEPS)), "步", Icons.Filled.DirectionsWalk, HomeBlue)),
        MainMetricSpec("plan.completed", "计划", HomeMetric("今日完成", number(todayCompleted), "轮", Icons.Filled.CheckCircle, HomeGreen)),
        MainMetricSpec("plan.pending", "计划", HomeMetric("待执行", number(pendingPlans), "轮", Icons.Filled.CalendarMonth, HomePurple))
    )
    val defaultMainMetricIds = mainMetricCatalog.map { it.id }
        .filter { it in DomainMetricCatalog.defaultMainIds }
    var mainMetricIds by remember { mutableStateOf(loadMainMetricIds(prefs, defaultMainMetricIds)) }
    val selectedMainMetrics = mainMetricCatalog.filter { it.id in mainMetricIds }.map { it.metric }
    // 最后一页不足 3 个时补占位格：否则 Row 的 weight 会把 2 个指标拉成两列宽。
    val mainMetricPlaceholder = HomeMetric("", "", "", Icons.Filled.Tune, HomeMuted, emphasize = false, placeholder = true)
    val boardPages = (selectedMainMetrics.chunked(3).ifEmpty {
        mainMetricCatalog.filter { it.id in defaultMainMetricIds }.map { it.metric }.chunked(3)
    }).map { page ->
        if (page.size >= 3) page else page + List(3 - page.size) { mainMetricPlaceholder }
    }
    var showDisplayPicker by remember { mutableStateOf(false) }

    val customBgPainters = remember { mutableStateMapOf<String, ImageBitmap?>() }
    var pendingBgModule by remember { mutableStateOf<HomeModule?>(null) }
    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val module = pendingBgModule
        pendingBgModule = null
        if (uri != null && module != null) {
            runCatching {
                val sourceSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
                if (sourceSize > 3L * 1024L * 1024L) error("图片超过 3MB，请先压小再选")
                val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("无法读取图片")
                val maxSide = maxOf(decoded.width, decoded.height)
                val scaled = if (maxSide > 1600) {
                    val ratio = 1600f / maxSide
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * ratio).toInt().coerceAtLeast(1),
                        (decoded.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    decoded
                }
                val dir = File(context.filesDir, "medusa_bg").apply { mkdirs() }
                val target = File(dir, module.key + ".jpg")
                FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                if (scaled !== decoded) scaled.recycle()
                decoded.recycle()
                customBgPainters.remove(target.absolutePath)
                prefs.edit().putString("bg_path_" + module.key, target.absolutePath).apply()
                customBgPainters[target.absolutePath] =
                    BitmapFactory.decodeFile(target.absolutePath)?.asImageBitmap()
                bgRevision++
                ToastUtil.show(context, "已设为" + module.title + "背景")
            }.onFailure {
                ToastUtil.show(context, "背景设置失败：" + (it.message ?: "图片读取失败"))
            }
        }
    }

    fun customBackgroundFor(module: HomeModule): ImageBitmap? {
        val path = prefs.getString("bg_path_" + module.key, null)
        if (path.isNullOrEmpty()) return null
        if (customBgPainters.containsKey(path)) return customBgPainters[path]
        return decodeScaledBitmap(path, 1440).also { customBgPainters[path] = it }
    }

    @Composable
    fun backgroundFor(module: HomeModule): Painter {
        customBackgroundFor(module)?.let { return BitmapPainter(it) }
        val res = prefs.getInt("bg_" + module.key, module.defaultBg)
        if (res != 0) return painterResource(res)
        return gradientPainter(module.key, module.fallbackBg)
    }

    fun moduleStatus(module: HomeModule): Pair<String, Color> {
        // 只有「全账号暂停」或「本模块自己的域被暂停」才算暂停；
        // 之前写成 || isAnyPaused()，森林被 1004 限流时庄园也会显示「风控暂停」，而它其实照跑
        val targetHit = runtime.lastTarget == module.scope ||
            (module.key == "forest" && runtime.lastTarget.startsWith("forest.")) ||
            (module.key == "ocean" && runtime.lastTarget.startsWith("ocean.")) ||
            (module.key == "farm" && runtime.lastTarget.startsWith("farm.")) ||
            (module.key == "orchard" && runtime.lastTarget.startsWith("orchard."))
        if (PlanRuntime.RISK_STOPPED == runtime.state && targetHit) {
            return "风控已停止" to MedusaColors.Stopped
        }
        val globalPaused = RiskGuard.isGlobalPaused()
        val scopePaused = RiskGuard.isTargetPaused(module.scope)
        if (globalPaused) return "风控暂停" to MedusaColors.Cooldown
        if (scopePaused) return "本域暂停" to MedusaColors.Cooldown
        val cooldown = manualCooldownRemaining(runtime, module.scope, System.currentTimeMillis())
        if (cooldown > 0L) {
            return "建议等待 " + formatRemaining(cooldown) to MedusaColors.Cooldown
        }
        // 只收能量时段里除森林外全部模块都会跳过，界面上必须说出来，不能让用户以为模块坏了
        if (module.key != "forest" && configLoaded &&
            runCatching { BaseModel.isEnergyOnlyNow() }.getOrDefault(false)
        ) {
            return "只收能量时段" to MedusaColors.Blue
        }
        if (!configLoaded) return "读取中" to MedusaColors.DisabledText
        // 运行状态优先于模块开关：手动计划可以在模型关闭时由调度直接执行。
        if (homeTargetMatches(module, runningTarget)) {
            return "运行中" to Color(0xFFF4C66F)
        }
        val ecoReady = module.key != "greenAction" || forestField("ecoLife")?.value == true
        val enabled = ecoReady && try {
            module.modelClass?.let { Model.getModel(it).isEnable } == true
        } catch (_: Throwable) {
            false
        }
        if (!enabled) return "已关闭" to Color(0xFF9AA5B1)
        // 错过窗口的 SKIPPED 记录不算“跑过”，否则整点计划一跳过卡片就误报绿点。
        val last = if (module.key == "greenAction") {
            history.filter {
                it.target.startsWith("forest.") && it.status != PlanHistory.SKIPPED
            }.maxByOrNull { it.startedAt }
        } else {
            history.filter { record ->
                record.status != PlanHistory.SKIPPED && (
                    record.target == module.scope ||
                        (module.key == "forest" && record.target.startsWith("forest.")) ||
                        (module.key == "ocean" && record.target.startsWith("ocean.")) ||
                        (module.key == "farm" && record.target.startsWith("farm.")) ||
                        (module.key == "orchard" && record.target.startsWith("orchard."))
                    )
            }.maxByOrNull { it.startedAt }
        }
        return if (last != null && isSameDayAsNow(last.startedAt)) {
            "已运行" to HomeGreen
        } else {
            "未运行" to Color(0xFFE57373)
        }
    }

    fun moduleEnabledCount(module: HomeModule): Int = when (module.key) {
        "greenAction" -> if (forestField("ecoLife")?.value == true) {
            listOf(
                "ecoLifeGrain", "ecoLifeTravel", "ecoLifeElectricity",
                "ecoLifeWater", "ecoLifeRecycle", "ecoLifePaper"
            ).count { forestField(it)?.value == true }
        } else {
            0
        }
        else -> module.modelClass?.let {
            runCatching { Model.countEnabledSwitches(it) }.getOrDefault(0)
        } ?: 0
    }

    fun moduleLastRun(module: HomeModule): PlanHistory.Record? {
        if (module.key == "greenAction") return null
        return history
            .filter { record ->
                record.target == module.scope ||
                    (module.key == "forest" && record.target.startsWith("forest.")) ||
                    (module.key == "farm" && record.target.startsWith("farm.")) ||
                    (module.key == "orchard" && record.target.startsWith("orchard."))
            }
            .maxByOrNull { it.startedAt }
    }

    fun domainMetrics(module: HomeModule, options: List<DomainMetricOption>, includeMain: Boolean = false): List<HomeMetric> {
        val available = options.filter { includeMain || it.id !in mainMetricIds }
        // 单列统一三格：指标不足三个的域补通用执行数据，避免 1/2/3 格来回跳
        val labels = available.map { it.label }.toSet()
        val extra = if (available.size >= 3) emptyList() else {
            DomainMetricCatalog.generic.filter { opt ->
                available.none { it.id == opt.id } && opt.label !in labels
            }
        }
        val pool = available + extra
        if (pool.isEmpty()) {
            return listOf(HomeMetric("数据在主显示", "—", "", Icons.Filled.Tune, HomeMuted))
        }
        val byId = pool.associate { opt ->
            opt.id to when (opt.id) {
                "forest.day" -> HomeMetric("今日收取", number(dayCollected), "g", Icons.Filled.MonetizationOn, HomeGreen)
                "forest.totalCollected" -> HomeMetric("累计收取", number(stats.dataOf(Statistics.TimeType.YEAR, Statistics.DataType.COLLECTED)), "g", Icons.Filled.MonetizationOn, HomePurple)
                "forest.watered" -> HomeMetric("今日浇水", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.WATERED)), "g", Icons.Filled.WaterDrop, HomeGold)
                "forest.rob" -> HomeMetric("收好友", number(dayRob), "g", Icons.Filled.DirectionsWalk, HomeBlue)
                "forest.robbed" -> HomeMetric("被好友收", forestInt("todayRobbed"), "g", Icons.Filled.WaterDrop, MedusaColors.Warning)
                "forest.bubbles" -> HomeMetric("可收能量球", forestInt("bubbles"), "个", Icons.Filled.MonetizationOn, HomeGold)
                "forest.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGreen)
                "forest.todayRuns" -> HomeMetric(
                    "今日执行",
                    number(history.count {
                        isSameDayAsNow(it.startedAt) && it.status != PlanHistory.SKIPPED &&
                            (it.target == module.scope || it.target.startsWith("forest."))
                    }),
                    "轮", Icons.Filled.CheckCircle, HomeGreen
                )
                "forest.recent" -> HomeMetric(
                    "近3天",
                    number(history.count { it.target == module.scope || it.target.startsWith("forest.") }),
                    "轮", Icons.Filled.CalendarMonth, HomePurple
                )
                "orchard.fertilizer" -> HomeMetric("今日肥料", orchardInt("fertilizer", "todayNum"), "g", Icons.Filled.MonetizationOn, HomeGold)
                "orchard.spreadLeft" -> HomeMetric("可施肥", orchardInt("account", "wateringLeft"), "次", Icons.Filled.Tune, HomeBlue)
                "orchard.tree" -> HomeMetric("果树进度", orchardTreePercent(), "", Icons.Filled.CalendarMonth, HomeGreen)
                "orchard.balance" -> HomeMetric("肥料余额", orchardInt("account", "happyPoint"), "g", Icons.Filled.MonetizationOn, HomeGold)
                "orchard.eggs" -> HomeMetric("可砸金蛋", orchardInt("eggs", "unsmashed"), "个", Icons.Filled.EggAlt, HomeGold)
                "orchard.factory" -> HomeMetric("可收肥料罐", orchardInt("factory", "collectable"), "罐", Icons.Filled.WaterDrop, HomeBlue)
                "orchard.sign" -> HomeMetric("签到", orchardSignText(), "", Icons.Filled.CheckCircle, HomeGreen)
                "orchard.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGold)
                "species.drawLeft" -> HomeMetric("可抽卡", speciesInt("dailyLeft"), "次", Icons.Filled.CardGiftcard, HomePurple)
                "species.help" -> HomeMetric("可帮抽", speciesHelpText(), "人", Icons.Filled.CheckCircle, HomeGreen)
                "species.cards" -> HomeMetric("图鉴卡片", speciesCardsText(), "张", Icons.Filled.CalendarMonth, HomeBlue)
                "species.medals" -> HomeMetric("可合成", speciesInt("canGenerateMedals"), "枚", Icons.Filled.CardGiftcard, HomeGold)
                "species.props" -> HomeMetric("道具", speciesInt("propHolds"), "件", Icons.Filled.Tune, HomeBlue)
                "species.books" -> HomeMetric("图鉴册", speciesInt("books"), "本", Icons.Filled.CalendarMonth, HomePurple)
                "species.done" -> HomeMetric("今日抽卡", speciesDoneText(), "", Icons.Filled.CheckCircle, HomeGreen)
                "species.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGold)
                "golden.balance" -> HomeMetric("金豆余额", goldenInt("balance"), "豆", Icons.Filled.MonetizationOn, HomeGold)
                "golden.pending" -> HomeMetric("待领金豆", goldenInt("pendingBeans"), "颗", Icons.Filled.CardGiftcard, HomeGold)
                "golden.draw" -> HomeMetric("可抽", goldenInt("drawRights"), "次", Icons.Filled.CardGiftcard, HomePurple)
                "golden.jars" -> HomeMetric("金豆罐", goldenInt("jars"), "罐", Icons.Filled.MonetizationOn, HomeBlue)
                "golden.exchange" -> HomeMetric("今日可换", goldenInt("manureRemain"), "豆", Icons.Filled.Tune, HomeGreen)
                "golden.capacity" -> HomeMetric("金豆容量", goldenInt("capacity"), "豆", Icons.Filled.MonetizationOn, HomePurple)
                "golden.sign" -> HomeMetric("签到", goldenSignText(), "", Icons.Filled.CheckCircle, HomeGreen)
                "golden.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGold)
                "green.today" -> HomeMetric("今日打卡", GreenActionStats.todayText(greenStatus), "", Icons.Filled.CheckCircle, HomeGreen)
                "green.remaining" -> HomeMetric("待打卡动作", GreenActionStats.remainingText(greenStatus), "个", Icons.Filled.Tune, HomeBlue)
                "green.streak" -> HomeMetric("已坚持", GreenActionStats.streakText(greenStatus), "天", Icons.Filled.CalendarMonth, HomeBlue)
                "green.checkins" -> HomeMetric("已打卡", GreenActionStats.totalText(greenStatus), "次", Icons.Filled.CheckCircle, HomeGold)
                "green.medals" -> HomeMetric("勋章", GreenActionStats.medalText(greenStatus), "个", Icons.Filled.CardGiftcard, HomePurple)
                "protect.projects" -> HomeMetric("可保护项目", protectInt("projects"), "个", Icons.Filled.Eco, HomeGreen)
                "protect.certs" -> HomeMetric("已获证书", protectInt("certs"), "个", Icons.Filled.CardGiftcard, HomeGold)
                "protect.carbon" -> HomeMetric("可助力活动", protectInt("carbon"), "个", Icons.Filled.Eco, HomeBlue)
                "protect.beaches" -> HomeMetric("可保护海滩", protectInt("beaches"), "片", Icons.Filled.Waves, HomeBlue)
                "protect.cooperate" -> HomeMetric("合种", protectInt("cooperate"), "个", Icons.Filled.Eco, HomePurple)
                "protect.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGold)
                "ancient.today" -> HomeMetric(
                    "今日执行",
                    number(history.count {
                        isSameDayAsNow(it.startedAt) && it.status != PlanHistory.SKIPPED &&
                            it.target == module.scope
                    }),
                    "轮", Icons.Filled.CheckCircle, HomeGreen
                )
                "ancient.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGreen)
                "ancient.recent" -> HomeMetric(
                    "近3天",
                    number(history.count { it.target == module.scope }),
                    "轮", Icons.Filled.CalendarMonth, HomePurple
                )
                "farm.eggs" -> HomeMetric("今日捐蛋", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.EGGS)), "颗", Icons.Filled.EggAlt, HomeGreen)
                "farm.totalEggs" -> HomeMetric("累计捐蛋", number(stats.dataOf(Statistics.TimeType.YEAR, Statistics.DataType.EGGS)), "颗", Icons.Filled.EggAlt, HomePurple)
                "farm.drawn" -> HomeMetric("今日抽奖", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.DRAWN)), "次", Icons.Filled.CardGiftcard, HomeGold)
                "farm.harvestEggs" -> HomeMetric("储备蛋", farmEggs("harvestEggs"), "颗", Icons.Filled.EggAlt, HomeGold)
                "farm.food" -> HomeMetric("饲料", farmInt("foodStock"), "g", Icons.Filled.Eco, HomeGreen)
                "farm.eggsStock" -> HomeMetric("库存蛋", farmEggs("benevolenceEggs"), "颗", Icons.Filled.CardGiftcard, HomePurple)
                "farm.manure" -> HomeMetric("可收肥料", farmInt("manureReady"), "个", Icons.Filled.Eco, HomeGold)
                "farm.away" -> HomeMetric("离家小鸡", farmInt("animalAway"), "只", Icons.Filled.DirectionsWalk, HomeBlue)
                "farm.rewardFriends" -> HomeMetric("待打赏", farmInt("rewardFriendCount"), "人", Icons.Filled.CardGiftcard, HomePurple)
                "farm.parkBox" -> HomeMetric("乐园宝箱", farmInt("parkBoxQuota"), "个", Icons.Filled.CardGiftcard, HomeGold)
                "farm.parkTasks" -> HomeMetric("乐园可领", farmInt("parkTaskClaimable"), "项", Icons.Filled.Tune, HomeBlue)
                "farm.kitchen" -> HomeMetric("可做菜", farmInt("kitchenCookTimes"), "次", Icons.Filled.Eco, HomeGold)
                "farm.diary" -> HomeMetric("日记未读", farmInt("diaryUnreadCount"), "篇", Icons.Filled.List, HomePurple)
                "farm.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGreen)
                "ocean.cleaned" -> HomeMetric("今日清理", number(stats.dataOf(Statistics.TimeType.DAY, Statistics.DataType.CLEANED)), "次", Icons.Filled.Waves, HomeGreen)
                "ocean.total" -> HomeMetric("累计清理", number(stats.dataOf(Statistics.TimeType.YEAR, Statistics.DataType.CLEANED)), "块", Icons.Filled.Waves, HomePurple)
                "ocean.welcome" -> HomeMetric("可找回", statusNumber("welcomeReady"), "个", Icons.Filled.CardGiftcard, HomeGreen)
                "ocean.fish" -> HomeMetric("可捞鱼", statusNumber("fishRemainTouchChance"), "次", Icons.Filled.Waves, HomeBlue)
                "ocean.rubbish" -> HomeMetric("自家垃圾", statusNumber("rubbishNumber"), "块", Icons.Filled.WaterDrop, HomeGold)
                "ocean.puzzle" -> HomeMetric("修复海域", statusPuzzle(), "", Icons.Filled.Tune, HomePurple)
                "ocean.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeBlue)
                "sports.balance" -> HomeMetric("健康能量", kineticInt("balance"), "g", Icons.Filled.MonetizationOn, HomeGold)
                "sports.steps" -> HomeMetric("今日步数", kineticInt("steps"), "步", Icons.Filled.DirectionsWalk, HomeBlue)
                "sports.bubbles" -> HomeMetric("待收气泡", kineticInt("bubbleCount"), "个", Icons.Filled.CardGiftcard, HomePurple)
                "sports.walk" -> HomeMetric("可捐步数", kineticInt("walkStepsToday"), "步", Icons.Filled.Eco, HomeGreen)
                "sports.sign" -> HomeMetric("签到", kineticSignText(), "", Icons.Filled.CheckCircle, HomeGreen)
                "member.pointClaimable" -> HomeMetric("待领积分", memberInt("pointClaimable"), "分", Icons.Filled.MonetizationOn, HomeGold)
                "member.tasks" -> HomeMetric("会员任务", memberInt("taskAvailable"), "个", Icons.Filled.Tune, HomeBlue)
                "member.sign" -> HomeMetric("签到", memberSignText(), "", Icons.Filled.CheckCircle, HomeGreen)
                "member.benefits" -> HomeMetric("权益", memberInt("benefitCount"), "项", Icons.Filled.CardGiftcard, HomePurple)
                "stall.coin" -> HomeMetric("新村币", stallInt("coin"), "", Icons.Filled.MonetizationOn, HomeGold)
                "stall.tasks" -> HomeMetric("新村任务", stallInt("taskCount"), "个", Icons.Filled.Tune, HomeBlue)
                "stall.shops" -> HomeMetric("店铺", stallInt("shopCount"), "个", Icons.Filled.CardGiftcard, HomePurple)
                "stall.manure" -> HomeMetric("肥料", stallInt("manure"), "份", Icons.Filled.Eco, HomeGreen)
                "common.today" -> HomeMetric(
                    "今日执行",
                    number(history.count {
                        isSameDayAsNow(it.startedAt) && it.status != PlanHistory.SKIPPED &&
                            it.target == module.scope
                    }),
                    "轮", Icons.Filled.CheckCircle, HomeGreen
                )
                "common.recent" -> HomeMetric(
                    "近3天",
                    number(history.count { it.target == module.scope }),
                    "轮", Icons.Filled.CalendarMonth, HomePurple
                )
                "common.switches" -> HomeMetric("已开启", number(moduleEnabledCount(module)), "项", Icons.Filled.Tune, HomeGold)
                else -> HomeMetric(opt.label, "—", opt.unit, Icons.Filled.Tune, HomeMuted)
            }
        }
        val stored = prefs.getString("domain_metrics_${module.key}", null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { id -> pool.any { it.id == id } }
            ?: emptyList()
        // 每张域卡最多 6 个数据；未选择时默认取前 6 个，选了就按选择顺序并补足 3 个
        val picked = if (stored.isEmpty()) {
            val preferred = DomainMetricCatalog.defaultCardIds[DomainCatalog.metricKeyOf(module.key)]
                ?.filter { id -> pool.any { it.id == id } }
                .orEmpty()
            (preferred + pool.map { it.id }.filter { id -> id !in preferred })
                .distinct()
                .take(6)
        } else {
            (stored.take(6) + pool.map { it.id }.filter { id -> id !in stored })
                .distinct()
                .take(maxOf(3, stored.size.coerceAtMost(6)))
        }
        return picked.mapNotNull { byId[it] }
    }

    fun moduleOptions(module: HomeModule): List<DomainMetricOption> =
        DomainMetricCatalog.forKey(module.key)

    fun moduleMetrics(module: HomeModule, includeMain: Boolean = false): List<HomeMetric> =
        domainMetrics(module, moduleOptions(module), includeMain)

    // 有任务进度的域，卡片状态行已经说明做过多少；底部数据条不再重复「已开启」。
    fun cardMetrics(module: HomeModule): List<HomeMetric> {
        val all = moduleMetrics(module)
        val withoutSwitches = all.filterNot { it.label == "已开启" }
        val filtered = if (taskProgressByModule.containsKey(module.key) && withoutSwitches.size >= 4) {
            withoutSwitches
        } else {
            all
        }
        if (filtered.isNotEmpty()) {
            return filtered
        }
        // 主显示占用了其余指标时，允许复用主显示数据，避免玻璃条空着。
        return moduleMetrics(module, includeMain = true).filterNot { it.label == "已开启" }
    }

    /** 单列固定三格：3 个不动；4 个只换第三格；5 个以上第一个固定，后两格成对轮换。 */
    fun cardMetrics(module: HomeModule, slots: Int): List<HomeMetric> {
        val pool = cardMetrics(module)
        if (pool.size <= slots) return pool
        if (slots <= 1) return listOf(pool[metricPage % pool.size])
        if (pool.size == 4) {
            return listOf(pool[0], pool[1], if (metricPage % 2 == 0) pool[2] else pool[3])
        }
        val fixed = pool[0]
        val tail = pool.drop(1)
        val windows = (0 until (tail.size - 1).coerceAtLeast(1)).map { i ->
            listOf(tail[i], tail[(i + 1) % tail.size])
        }
        return listOf(fixed) + windows[metricPage % windows.size]
    }

    fun showBackgroundPicker(module: HomeModule) {
        bgPickerModule = module
    }

    var customHeroRevision by remember { mutableIntStateOf(0) }
    val customHeroPath = remember(customHeroRevision) { prefs.getString("hero_bg_path", null) }
    val heroCropResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val path = result.data?.getStringExtra(MiuixImageCropActivity.EXTRA_RESULT_PATH)
        if (result.resultCode == android.app.Activity.RESULT_OK && !path.isNullOrEmpty()) {
            prefs.edit().putString("hero_bg_path", path).putInt("hero_bg", -1).apply()
            heroBg = -1
            customHeroRevision++
            ToastUtil.show(context, "已更新首页背景")
        }
    }
    val heroImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            heroCropResult.launch(
                Intent(context, MiuixImageCropActivity::class.java)
                    .putExtra(MiuixImageCropActivity.EXTRA_URI, uri.toString())
                    .putExtra(MiuixImageCropActivity.EXTRA_ASPECT_W, 2)
                    .putExtra(MiuixImageCropActivity.EXTRA_ASPECT_H, 1)
                    .putExtra(MiuixImageCropActivity.EXTRA_OUT_NAME, "hero_custom.jpg")
            )
        }
    }
    fun pickCustomHero() {
        heroImagePicker.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    val orderedModules = order.mapNotNull { key -> AllHomeModules.firstOrNull { it.key == key } }
    val visibleModules = orderedModules.filterNot { hidden.contains(it.key) }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val statusBarTop = WindowInsets.statusBars.getTop(density)
    Box(Modifier.fillMaxSize().background(MedusaHeroBrush)) {
        // Android 12+ 的拉伸回弹会把整页拉开、露出底色；首页的滚动反馈只保留自绘下拉
        CompositionLocalProvider(LocalOverscrollFactory provides NoHomeOverscrollFactory) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(HomePageBg)
        ) {
            HeroV2(
                activity = activity,
                grid = grid,
                heroBg = heroBg,
                heroCustomPath = if (heroBg == -1) customHeroPath else null,
                heroRevision = customHeroRevision,
                riskPaused = riskPaused,
                verificationRequired = verificationRequired,
                onVerifyDone = {
                    activity.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.verifyDone"))
                    verificationRequired = false
                },
                onGridChange = {
                    grid = it
                    prefs.edit().putBoolean("grid", it).apply()
                },
                onPickBackground = { showManage = true },
                onOpenDisplayPicker = { showDisplayPicker = true },
                onSync = { dataRevision++ }
            )
            DataBoardV2(boardPages, tick)
            ManualTaskBanner(uid, tick, history)
            MissedBatchBanner(history, prefs)
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionHeaderV2(grid, visibleModules.size)
                if (visibleModules.isEmpty()) {
                    EmptyModulesV2()
                } else if (!grid) {
                    visibleModules.forEach { module ->
                        val status = moduleStatus(module)
                        val enabled = moduleEnabledOf(module)
                        ModuleCardV2(
                            module = module,
                            background = backgroundFor(module),
                            metrics = cardMetrics(module, 3),
                            status = status,
                            onClick = {
                                selectedModuleKey = null
                                openDomainSettings(activity, module.key, module.groupCode, module.title)
                            },
                            onRun = {
                                selectedModuleKey = null
                                runModuleNow(module)
                            },
                            showRun = module.key == "forest" && module.showRun,
                            running = homeTargetMatches(module, runningTarget),
                            moduleEnabled = enabled,
                            onEnable = { setModuleEnabled(module, true) },
                            onDisable = { setModuleEnabled(module, false) },
                            taskProgress = taskProgressByModule[module.key],
                            selected = selectedModuleKey == module.key,
                            onSelectChange = { on ->
                                if (on) selectedModuleKey = module.key
                                else if (selectedModuleKey == module.key) selectedModuleKey = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            compact = false
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                } else {
                    visibleModules.chunked(2).forEach { rowModules ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowModules.forEach { module ->
                                val status = moduleStatus(module)
                                val enabled = moduleEnabledOf(module)
                                ModuleCardV2(
                                    module = module,
                                    background = backgroundFor(module),
                                    metrics = cardMetrics(module, if (rowModules.size > 1) 1 else 3),
                                    status = status,
                                    onClick = {
                                        selectedModuleKey = null
                                        openDomainSettings(activity, module.key, module.groupCode, module.title)
                                    },
                                    onRun = {
                                        selectedModuleKey = null
                                        runModuleNow(module)
                                    },
                                    showRun = module.key == "forest" && module.showRun,
                                    running = homeTargetMatches(module, runningTarget),
                                    moduleEnabled = enabled,
                                    onEnable = { setModuleEnabled(module, true) },
                                    onDisable = { setModuleEnabled(module, false) },
                                    taskProgress = taskProgressByModule[module.key],
                                    selected = selectedModuleKey == module.key,
                                    onSelectChange = { on ->
                                        if (on) selectedModuleKey = module.key
                                        else if (selectedModuleKey == module.key) selectedModuleKey = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    // 落单的卡片占满整行，就该和单列模式一样给全信息；
                                    // 谁落单取决于排序，所以按「本行是否只有一张」判定，不按位置写死
                                    compact = rowModules.size > 1,
                                    fullWidth = rowModules.size == 1
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                ManageRowV2(onClick = { showManage = true })
                // 浮岛是覆盖在页面上的，最后一块内容要靠这段留白才能完整滚到浮岛上方
                Spacer(Modifier.height(104.dp))
            }
        }
        }
        // 滚动时内容会经过状态栏；压一层很轻的暗色渐变，保证白色状态栏图标和文字都清楚
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(with(density) { statusBarTop.toDp() } + 2.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC070B22), Color(0x00070B22))
                    )
                )
        )
        undoModule?.let { module ->
            val bannerInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xEE1B2233))
                    .clickable(interactionSource = bannerInteraction, indication = null) {}
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已关闭「${module.title}」",
                    fontSize = MedusaType.Secondary,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "撤销",
                    fontSize = MedusaType.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7BD88F),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            undoModule = null
                            setModuleEnabled(module, true, allowUndo = false)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (showManage) {
        ManageHomeDialog(
            modules = orderedModules,
            hidden = hidden,
            heroBg = heroBg,
            onOrderChange = {
                order = it
                prefs.edit().putString("module_order", it.joinToString(",")).apply()
            },
            onHiddenChange = {
                hidden = it
                prefs.edit().putString("module_hidden", it.joinToString(",")).apply()
            },
            onHeroBg = {
                heroBg = it
                prefs.edit().putInt("hero_bg", it).apply()
            },
            onModuleBackground = { module ->
                showManage = false
                showBackgroundPicker(module)
            },
            heroCustomSelected = heroBg == -1,
            onCustomHero = { pickCustomHero() },
            onDismiss = { showManage = false }
        )
    }
    val bgModule = bgPickerModule
    if (bgModule != null) {
        val hasCustom = !prefs.getString("bg_path_" + bgModule.key, null).isNullOrEmpty()
        val presets = if (bgModule.defaultBg != 0) {
            listOf(
                "默认背景" to bgModule.defaultBg,
                "森林背景" to R.drawable.home_forest,
                "庄园背景" to R.drawable.home_farm,
                "海洋背景" to R.drawable.home_ocean
            )
        } else {
            emptyList()
        }
        BackgroundPickerDialog(
            title = bgModule.title + " · 更换背景",
            presets = presets,
            hasCustom = hasCustom,
            onPreset = { res ->
                prefs.edit().putInt("bg_" + bgModule.key, res).remove("bg_path_" + bgModule.key).apply()
                bgRevision++
                bgPickerModule = null
                ToastUtil.show(context, "已更换" + bgModule.title + "背景")
            },
            onAlbum = {
                bgPickerModule = null
                pendingBgModule = bgModule
                pickBackground.launch(
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                )
            },
            onRemove = {
                prefs.edit().remove("bg_path_" + bgModule.key).apply()
                bgRevision++
                bgPickerModule = null
                ToastUtil.show(context, "已恢复默认背景")
            },
            onDismiss = { bgPickerModule = null }
        )
    }
    if (showCooldown) {
        val module = pendingRun
        val remaining = module?.let {
            manualCooldownRemaining(runtime, it.scope, System.currentTimeMillis())
        } ?: 0L
        val minutesAgo = if (runtime.lastManualAt > 0L) {
            ((System.currentTimeMillis() - runtime.lastManualAt) / 60_000L).coerceAtLeast(0L)
        } else {
            0L
        }
        ConfirmDialog(
            title = "节律建议",
            text = "距上次手动执行约 " + minutesAgo + " 分钟，建议再等 " + formatRemaining(remaining)
                + "。\n仍要执行「" + (module?.title ?: "") + "」吗？",
            confirmText = "仍要执行",
            dismissText = "取消",
            onConfirm = {
                val target = pendingRun
                showCooldown = false
                pendingRun = null
                if (target != null) sendRun(target, true)
            },
            onDismiss = {
                showCooldown = false
                pendingRun = null
            }
        )
    }
    if (showDisplayPicker) {
        MainDisplayPickerDialog(
            catalog = mainMetricCatalog,
            selected = mainMetricIds.toSet(),
            onDismiss = { showDisplayPicker = false },
            onConfirm = { ids ->
                mainMetricIds = ids
                prefs.edit().putString("main_metric_ids", ids.joinToString(",")).apply()
                showDisplayPicker = false
            }
        )
    }
    // 读取背景/顺序状态时触发一次重绘，避免修改后仍显示旧卡片
    bgRevision
}

@Composable
private fun HeroV2(
    activity: MiuixMainActivity,
    grid: Boolean,
    heroBg: Int,
    heroCustomPath: String?,
    heroRevision: Int,
    riskPaused: Boolean,
    verificationRequired: Boolean,
    onVerifyDone: () -> Unit,
    onGridChange: (Boolean) -> Unit,
    onPickBackground: () -> Unit,
    onOpenDisplayPicker: () -> Unit,
    onSync: () -> Unit
) {
    val shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(MedusaHeroBrush)
        ) {
            // 4.0.0：默认背景也换成图片（index 0 = 默认蛇灵图），不再是无图的渐变；
            // 渐变只作为图片解码前的底色。其它背景（森林/庄园/海洋）保持原行为。
            val customBmp = remember(heroCustomPath, heroRevision) {
                heroCustomPath?.takeIf { File(it).exists() }
                    ?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
            }
            if (customBmp != null) {
                Image(
                    bitmap = customBmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                val imageRes = if (heroBg == 0) R.drawable.home_default else heroBg
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xAA070B22), Color(0x66070B22), Color(0x99070B22))
                        )
                    )
            )
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                V2Avatar(activity, onClick = onPickBackground)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f).offset(y = (-2).dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activity.alipayProfile?.name?.takeIf { it.isNotBlank() } ?: "美杜莎",
                            // 例外：英雄昵称与 22sp 行高绑定，放大到 28 会让头像/右侧按钮基线漂移
                            fontSize = 22.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 风控是账号级状态，跟着昵称走比塞进数据看板更合理；字号取昵称一半
                        Spacer(Modifier.width(10.dp))
                        val riskColor = when {
                            verificationRequired -> Color(0xFFFF8A80)
                            riskPaused -> Color(0xFFFFC46B)
                            else -> Color(0xFF7FE3B0)
                        }
                        val riskLabel = when {
                            verificationRequired -> "待验证"
                            riskPaused -> "风控暂停"
                            else -> "风控正常"
                        }
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = riskLabel,
                            fontSize = MedusaType.Secondary,
                            color = if (verificationRequired || riskPaused) riskColor else Color.White.copy(alpha = 0.78f),
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .rotate(45f)
                                .background(Color(0xFFF4C66F), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "MEDUSA · V" + io.github.aw1y2z.sesame.data.ViewAppInfo.getAppVersion(),
                            fontSize = MedusaType.Caption,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                            color = Color(0xFFF4C66F)
                        )
                    }
                    if (verificationRequired) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xCC7A2E2E))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "支付宝要求验证，完成滑块后点右侧恢复",
                                fontSize = MedusaType.Caption,
                                color = Color.White,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable(onClick = onVerifyDone)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("已验证", fontSize = 10.sp, color = Color(0xFFFFE08A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GlassIconButton(
                        icon = Icons.Filled.Tune,
                        active = false,
                        description = "选择主显示数据",
                        onClick = onOpenDisplayPicker
                    )
                    GlassIconButton(
                        icon = Icons.Filled.Apps,
                        active = grid,
                        description = "双列",
                        onClick = { onGridChange(true) }
                    )
                    GlassIconButton(
                        icon = Icons.Filled.List,
                        active = !grid,
                        description = "单列",
                        onClick = { onGridChange(false) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HeroStatusRow(activity, onSync)
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    active: Boolean,
    description: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .size(24.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = if (active) 0.18f else 0.07f))
            .border(1.dp, Color.White.copy(alpha = if (active) 0.30f else 0.16f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (active) 1f else 0.72f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun V2Avatar(activity: MiuixMainActivity, onClick: () -> Unit) {
    val avatarUrl = activity.alipayProfile?.avatarUrl
    var bitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatarUrl) {
        bitmap = if (avatarUrl.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                URL(avatarUrl).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xFF0B1730))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "支付宝头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(R.drawable.medusa_logo),
                contentDescription = "美杜莎",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun HeroStatusRow(activity: MiuixMainActivity, onSync: () -> Unit) {
    val context = LocalContext.current
    val text = when {
        !activity.storageWritable -> "文件权限未授权 · 点击授权"
        activity.moduleEnabled == false -> "美杜莎未启用 · 点击启用"
        activity.uiRunType == io.github.aw1y2z.sesame.data.RunType.DISABLE -> "等待支付宝启动"
        else -> "配置已同步 · 文件权限正常"
    }
    val tone = when {
        !activity.storageWritable || activity.moduleEnabled == false -> Color(0xFFFFC46B)
        activity.uiRunType == io.github.aw1y2z.sesame.data.RunType.DISABLE -> Color(0xFFC9D2E3)
        else -> Color(0xFF78F2B7)
    }
    val onStatusClick = {
        when {
            !activity.storageWritable -> activity.requestStoragePermission()
            activity.moduleEnabled == false -> activity.enableCurrentModule()
            else -> {
                activity.broadcastRestart()
                ToastUtil.show(context, "已通知支付宝重载配置")
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态文字的宽度按最长文案固定，保证右侧「同步」不会随状态文字长短漂移
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onStatusClick)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).background(tone, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                fontSize = MedusaType.Secondary,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(178.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        HeroActionChip(
            icon = Icons.Filled.Sync,
            label = "同步",
            enabled = activity.storageWritable,
            onClick = {
                // 同步按钮：重读配置 + 抓支付宝状态/账本（含运动域）+ 立即重读首页显示。
                activity.broadcastReloadConfig()
                context.sendBroadcast(
                    Intent("com.eg.android.AlipayGphone.sesame.ledgerRefresh")
                        .putExtra("includeSports", true)
                )
                onSync()
                ToastUtil.show(context, "已同步支付宝状态、任务账本和首页显示")
            }
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HeroActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.62f * alpha),
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = MedusaType.Secondary,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.72f * alpha)
        )
    }
}

@Composable
private fun MainDisplayPickerDialog(
    catalog: List<MainMetricSpec>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var picked by remember {
        mutableStateOf(catalog.filter { it.id in selected }.map { it.id }.toSet())
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("主显示数据", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = HomeInk)
                Spacer(Modifier.weight(1f))
                Text(
                    "${picked.size}/15",
                    fontSize = MedusaType.Secondary,
                    fontWeight = FontWeight.SemiBold,
                    color = if (picked.size >= 15) MedusaColors.Warning else HomeMuted
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF2F5FA))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = HomeMuted, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                "最多 15 条，按 3 格 × 5 页轮播；域卡小轮的数据在各自域设置里选",
                fontSize = MedusaType.Secondary,
                color = HomeMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 10.dp)
            ) {
                catalog.groupBy { it.domain }.forEach { (domain, specs) ->
                    Text(
                        domain,
                        fontSize = MedusaType.Secondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = HomeMuted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                    specs.forEach { spec ->
                        val checked = spec.id in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    picked = when {
                                        checked -> picked - spec.id
                                        picked.size < 15 -> picked + spec.id
                                        else -> picked
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(spec.metric.label, fontSize = MedusaType.Body, color = HomeInk)
                                if (spec.metric.unit.isNotEmpty()) {
                                    Text(
                                        "单位：${spec.metric.unit}",
                                        fontSize = MedusaType.Caption,
                                        color = Color(0xFFA2ACBC)
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (checked) MedusaColors.Green else Color(0xFFF0F3F7)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (checked) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
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
                    Text("取消", fontSize = MedusaType.Body, color = HomeMuted)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (picked.isEmpty()) Color(0xFFDDE4EE) else MedusaColors.Green)
                        .clickable(enabled = picked.isNotEmpty()) {
                            onConfirm(catalog.filter { it.id in picked }.map { it.id })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "完成",
                        fontSize = MedusaType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DataBoardV2(pages: List<List<HomeMetric>>, tick: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .offset(y = (-10).dp)
            .height(96.dp)
            .shadow(14.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
    ) {
        val index = if (pages.isEmpty()) 0 else (tick % pages.size)
        AnimatedContent(
            targetState = index,
            transitionSpec = { (slideInVertically(tween(320)) { it } + fadeIn(tween(220))) togetherWith (slideOutVertically(tween(320)) { -it } + fadeOut(tween(180))) },
            label = "home-board"
        ) { current ->
            val metrics = pages.getOrNull(current) ?: emptyList()
            Row(Modifier.fillMaxSize()) {
                metrics.forEachIndexed { i, item ->
                    StatCell(item, Modifier.weight(1f), showDivider = i > 0)
                }
            }
        }
    }
}

private fun loadMainMetricIds(prefs: SharedPreferences, defaults: List<String>): List<String> {
    val raw = prefs.getString("main_metric_ids", null) ?: return defaults
    val ids = raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it in defaults }
        .distinct()
    return if (ids.isEmpty()) defaults else ids.take(15)
}

private fun compactLabel(label: String): String = when (label) {
    "累计收取", "累计捐蛋", "累计清理" -> "累计"
    "已开启" -> "已开"
    "已开启类" -> "已开类"
    else -> label.removePrefix("今日")
}

private fun compactValue(value: String, unit: String): String {
    val raw = value.replace(",", "")
    val n = raw.toLongOrNull()
    return when {
        n == null -> raw + unit
        n >= 10_000 -> String.format(Locale.CHINA, "%.1f万", n / 10_000.0) + unit
        else -> raw + unit
    }
}

/** 单个数据格：三格同属一个域，随整页一起切换 */
@Composable
private fun StatCell(item: HomeMetric, modifier: Modifier, showDivider: Boolean) {
    if (item.placeholder) {
        Box(modifier.fillMaxSize()) {
            if (showDivider) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(1.dp)
                        .height(46.dp)
                        .background(MedusaColors.Ink.copy(alpha = 0.08f))
                )
            }
        }
        return
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showDivider) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .height(46.dp)
                    .background(MedusaColors.Ink.copy(alpha = 0.08f))
            )
        }
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(item.tone.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = item.tone, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.value,
                    fontSize = if (item.emphasize) MedusaType.CardTitle else MedusaType.Body,
                    fontWeight = FontWeight.Bold,
                    color = HomeInk,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (item.unit.isEmpty()) item.label else "${item.label}（${item.unit}）",
                fontSize = MedusaType.Caption, color = HomeMuted, maxLines = 1,
                modifier = Modifier.padding(start = 11.dp, top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionHeaderV2(grid: Boolean, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Eco, contentDescription = null, tint = MedusaColors.GreenDeep, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("我的域", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = HomeInk)
        Text("  $count", fontSize = MedusaType.Secondary, color = MedusaColors.Muted, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.weight(1f))
        Text(
            text = (if (grid) "双列" else "单列") + " · 点击进入配置",
            fontSize = MedusaType.Secondary,
            color = HomeMuted
        )
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = HomeMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun EmptyModulesV2() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.8f))
            .border(1.dp, MedusaColors.Line, RoundedCornerShape(20.dp))
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("还没有显示中的模块", fontSize = MedusaType.Body, color = HomeMuted)
        Spacer(Modifier.height(4.dp))
        Text("点击下方「添加模块 / 调整顺序」恢复", fontSize = MedusaType.Secondary, color = Color(0xFFA2ACBC))
    }
}

@Composable
private fun ModuleCardV2(
    module: HomeModule,
    background: Painter,
    metrics: List<HomeMetric>,
    status: Pair<String, Color>,
    onClick: () -> Unit,
    onRun: () -> Unit,
    running: Boolean,
    moduleEnabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    taskProgress: HomeTaskProgress? = null,
    selected: Boolean = false,
    onSelectChange: (Boolean) -> Unit = {},
    showRun: Boolean = true,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fullWidth: Boolean = false
) {
    val shape = RoundedCornerShape(21.dp)
    val height = if (compact && !fullWidth) 202.dp else 152.dp
    val density = LocalDensity.current
    val swipeThreshold = with(density) { (if (compact && !fullWidth) 64.dp else 92.dp).toPx() }
    val zoneWidthDp = if (compact && !fullWidth) 104.dp else 150.dp
    val maxDrag = with(density) { zoneWidthDp.toPx() }
    var dragX by remember(module.key, moduleEnabled) { mutableFloatStateOf(0f) }
    var dragging by remember(module.key, moduleEnabled) { mutableStateOf(false) }
    var armed by remember(module.key, moduleEnabled) { mutableStateOf(false) }
    var suppressClick by remember(module.key) { mutableStateOf(false) }
    var touching by remember(module.key) { mutableStateOf(false) }
    val selectedState = rememberUpdatedState(selected)
    val selectChangeState = rememberUpdatedState(onSelectChange)
    val haptic = LocalHapticFeedback.current
    val selectionScale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = tween(160),
        label = "moduleSelectionScale"
    )
    val selectionElevation by animateDpAsState(
        targetValue = if (selected) 16.dp else 10.dp,
        animationSpec = tween(160),
        label = "moduleSelectionElevation"
    )
    LaunchedEffect(selected, dragging, touching) {
        if (selected && !dragging && !touching) {
            delay(5_000L)
            selectChangeState.value(false)
        }
    }
    LaunchedEffect(selected) {
        if (!selected) {
            delay(300L)
            suppressClick = false
        }
    }
    val shownDrag by animateFloatAsState(
        targetValue = if (dragging) dragX else 0f,
        animationSpec = tween(if (dragging) 0 else 180),
        label = "moduleSwipe"
    )
    val progress = (abs(shownDrag) / swipeThreshold).coerceIn(0f, 1f)
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = selectionScale
                scaleY = selectionScale
            }
            .shadow(selectionElevation, shape)
            .clip(shape)
            .border(
                1.5.dp,
                if (selected) HomeGreen.copy(alpha = 0.9f) else Color.Transparent,
                shape
            )
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B1016)))
        if (moduleEnabled) {
            SwipeActionZone(
                label = "关闭",
                armedLabel = "松手关闭",
                color = Color(0xFFD64B4B),
                icon = Icons.Filled.Close,
                progress = progress,
                zoneWidth = zoneWidthDp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        } else {
            SwipeActionZone(
                label = "启用",
                armedLabel = "松手启用",
                color = Color(0xFF168A5B),
                icon = Icons.Filled.Check,
                progress = progress,
                zoneWidth = zoneWidthDp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = shownDrag }
        ) {
        Image(
            painter = background,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // 双列卡片保留图片右侧（主体在右），单列完整显示
            alignment = if (compact) Alignment.CenterEnd else Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0x99040C10), Color(0x44040C10), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x44040C10), Color.Transparent, Color(0xAA040C10))
                    )
                )
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (compact) 13.dp else 17.dp, top = if (compact) 13.dp else 15.dp, end = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    fontSize = MedusaType.CardTitle,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(if (compact) 5.dp else 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(status.second, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (taskProgress != null) {
                            status.first + " · 任务 " + taskProgress.done + "/" + taskProgress.total
                        } else {
                            status.first
                        },
                        fontSize = if (compact) MedusaType.Caption else MedusaType.Secondary,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 只留三角：两列排版时不抢视觉，点卡片仍进配置
            if (showRun) {
            Box(
                Modifier
                    .size(if (compact) 26.dp else 30.dp)
                    .background(Color.White.copy(alpha = if (running) 0.10f else 0.20f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.38f), CircleShape)
                    .clickable(enabled = !running) { onRun() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = when {
                        running -> "执行中"
                        module.key == "forest" -> "手动收能量（60秒冷却）"
                        else -> "执行"
                    },
                    tint = Color.White.copy(alpha = if (running) 0.45f else 1f),
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp)
                )
            }
            }
        }
        ModuleGlassStats(
            background = background,
            metrics = if (compact) metrics.take(1) else metrics,
            compact = compact,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 9.dp, end = 9.dp, bottom = 8.dp)
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(120)) + slideInVertically(tween(140)) { it / 4 },
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (compact) 56.dp else 58.dp)
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xD90B1016))
                    .border(1.dp, HomeGreen.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (moduleEnabled) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = HomeGreen,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = if (moduleEnabled) "已选定 · 左滑关闭" else "已选定 · 右滑启用",
                    fontSize = MedusaType.Caption,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
        }
    }
}

@Composable
private fun SwipeActionZone(
    label: String,
    armedLabel: String,
    color: Color,
    icon: ImageVector,
    progress: Float,
    zoneWidth: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .width(zoneWidth)
            .fillMaxHeight()
            .graphicsLayer {
                alpha = progress
                val scale = 0.92f + 0.08f * progress
                scaleX = scale
                scaleY = scale
            }
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (progress >= 1f) armedLabel else label,
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ModuleGlassStats(
    background: Painter,
    metrics: List<HomeMetric>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(if (compact) 44.dp else 46.dp)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
    ) {
        Image(
            painter = background,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(18.dp)
                .background(Color.Black.copy(alpha = 0.1f))
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xAA071019), Color(0x88071019))
                    )
                )
        )
        AnimatedContent(
            targetState = if (compact) metrics.take(1) else metrics,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(180)))
            },
            label = "module-metrics"
        ) { shown ->
        if (compact) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                shown.forEachIndexed { index, metric ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(Color.White.copy(alpha = 0.13f))
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 5.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = metric.value + metric.unit,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(3.dp)
                                    .background(metric.tone, CircleShape)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = metric.label,
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.64f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            shown.forEachIndexed { index, metric ->
                if (index > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(Color.White.copy(alpha = 0.13f))
                    )
                }
                Row(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = if (compact) 5.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        Modifier
                            .size(if (compact) 21.dp else 22.dp)
                            .background(
                                Brush.linearGradient(listOf(metric.tone, metric.tone.copy(alpha = 0.75f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(metric.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                metric.value,
                                fontSize = MedusaType.Body,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (metric.unit.isEmpty()) metric.label else "${metric.label}（${metric.unit}）",
                            fontSize = MedusaType.Caption,
                            color = Color.White.copy(alpha = 0.66f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun ManageRowV2(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.88f))
            .border(1.dp, MedusaColors.Ink.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(23.dp)
                .background(Color(0xFFE6F7EE), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Palette, contentDescription = null, tint = MedusaColors.GreenDeep, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("添加模块 / 调整顺序", fontSize = MedusaType.Body, fontWeight = FontWeight.SemiBold, color = Color(0xFF343F55))
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFA2ACBC), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ManageHomeDialog(
    modules: List<HomeModule>,
    hidden: Set<String>,
    heroBg: Int,
    onOrderChange: (List<String>) -> Unit,
    onHiddenChange: (Set<String>) -> Unit,
    onHeroBg: (Int) -> Unit,
    onModuleBackground: (HomeModule) -> Unit,
    heroCustomSelected: Boolean,
    onCustomHero: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("管理首页", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = HomeInk)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF2F5FA))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = HomeMuted, modifier = Modifier.size(16.dp))
                }
            }
            Text("背景、模块显示和顺序都会立即保存", fontSize = MedusaType.Secondary, color = HomeMuted, modifier = Modifier.padding(top = 4.dp))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 14.dp)
            ) {
                Text("HERO 背景", fontSize = MedusaType.Secondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = HomeMuted)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HeroBackgroundOption(R.drawable.home_default, "默认", heroBg == 0) { onHeroBg(0) }
                    HeroBackgroundOption(R.drawable.home_forest, "森林", heroBg == R.drawable.home_forest) { onHeroBg(R.drawable.home_forest) }
                    HeroBackgroundOption(R.drawable.home_farm, "庄园", heroBg == R.drawable.home_farm) { onHeroBg(R.drawable.home_farm) }
                    HeroBackgroundOption(R.drawable.home_ocean, "海洋", heroBg == R.drawable.home_ocean) { onHeroBg(R.drawable.home_ocean) }
                    HeroCustomOption(heroCustomSelected, onCustomHero)
                }
                Spacer(Modifier.height(16.dp))
                Text("模块显示与顺序", fontSize = MedusaType.Secondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = HomeMuted)
                Text(
                    "点「隐藏」移出首页，点「添加」放回；「背景」可从相册选图；上下箭头调整顺序",
                    fontSize = MedusaType.Caption,
                    color = Color(0xFFA2ACBC),
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.height(4.dp))
                modules.forEachIndexed { index, module ->
                    val visible = !hidden.contains(module.key)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = module.title,
                            fontSize = MedusaType.CardTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = if (visible) HomeInk else Color(0xFFA8B2C2),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (visible) "隐藏" else "添加",
                            fontSize = MedusaType.Secondary,
                            color = if (visible) HomeMuted else MedusaColors.GreenDeep,
                            modifier = Modifier
                                .clickable {
                                    onHiddenChange(
                                        if (visible) hidden + module.key else hidden - module.key
                                    )
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                        // 换背景是高频需求，之前只有一个调色板小图标，没人找得到
                        Text(
                            text = "背景",
                            fontSize = MedusaType.Secondary,
                            fontWeight = FontWeight.SemiBold,
                            color = MedusaColors.GreenDeep,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFE6F7EE))
                                .clickable { onModuleBackground(module) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                        ManageIconButton(Icons.Filled.KeyboardArrowUp, "上移") {
                            if (index > 0) {
                                val next = modules.map { it.key }.toMutableList()
                                val item = next.removeAt(index)
                                next.add(index - 1, item)
                                onOrderChange(next)
                            }
                        }
                        ManageIconButton(Icons.Filled.KeyboardArrowDown, "下移") {
                            if (index < modules.lastIndex) {
                                val next = modules.map { it.key }.toMutableList()
                                val item = next.removeAt(index)
                                next.add(index + 1, item)
                                onOrderChange(next)
                            }
                        }
                    }
                    if (index != modules.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MedusaColors.Line.copy(alpha = 0.75f)))
                    }
                }
                Text(
                    "当前支持森林、庄园、海洋；更多模块随后开放",
                    fontSize = MedusaType.Caption,
                    color = Color(0xFFA2ACBC),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MedusaColors.Ink)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("完成", fontSize = MedusaType.CardTitle, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun HeroBackgroundOption(res: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (res == 0) MedusaHeroBrush else Brush.linearGradient(listOf(MedusaColors.Ink, MedusaColors.Ink)))
                .border(if (selected) 2.dp else 1.dp, if (selected) MedusaColors.Green else MedusaColors.Line, RoundedCornerShape(14.dp))
        ) {
            if (res != 0) {
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color(0x55070B22)))
            } else {
                Text(
                    "MEDUSA",
                    // 例外：背景缩略图里的装饰水印，不是信息文字
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = MedusaType.Caption, color = if (selected) MedusaColors.GreenDeep else HomeMuted)
    }
}

@Composable
private fun BackgroundPickerDialog(
    title: String,
    presets: List<Pair<String, Int>>,
    hasCustom: Boolean,
    onPreset: (Int) -> Unit,
    onAlbum: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(5.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(title, fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = HomeInk)
            Text(
                "建议横向 16:9，单张 ≤ 3MB。卡片按中心裁切，底部约 55dp 被数据条盖住，主体放中上偏左。",
                fontSize = MedusaType.Caption,
                color = HomeMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(14.dp))
            presets.forEach { (label, res) ->
                BackgroundOptionRow(label, danger = false) { onPreset(res) }
            }
            BackgroundOptionRow("从相册选择…", danger = false, onClick = onAlbum)
            if (hasCustom) {
                BackgroundOptionRow("移除相册背景，回到内置/渐变", danger = true, onClick = onRemove)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "取消",
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.GreenDeep,
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun BackgroundOptionRow(label: String, danger: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (danger) Color(0x14D9534F) else Color(0xFFF2F5FA))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            label,
            fontSize = MedusaType.Body,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) Color(0xFFD9534F) else HomeInk
        )
    }
}

@Composable
private fun HeroCustomOption(selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF2F5FA))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MedusaColors.Green else MedusaColors.Line,
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "自定义背景",
                tint = MedusaColors.GreenDeep,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "自定义",
            fontSize = MedusaType.Caption,
            color = if (selected) MedusaColors.GreenDeep else HomeMuted
        )
    }
}

@Composable
private fun ManageIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = HomeMuted, modifier = Modifier.size(15.dp))
    }
}

@Composable
fun MedusaNavBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 34.dp)
            .padding(top = 6.dp, bottom = 16.dp)
    ) {
        val shape = RoundedCornerShape(24.dp)
        val items = listOf(
            Triple("首页", Icons.Filled.Home, 0),
            Triple("计划", Icons.Filled.DateRange, 2),
            Triple("配置", Icons.Filled.Tune, 1),
            Triple("数据", Icons.Filled.List, 3)
        )
        val selectedIndex = items.indexOfFirst { it.third == selectedTab }.coerceAtLeast(0)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(24.dp, shape, clip = false)
                .clip(shape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(hazeState) {
                            blurRadius = 28.dp
                            noiseFactor = 0.05f
                        }
                    } else {
                        Modifier
                    }
                )
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.24f)
                        )
                    )
                )
        ) {
            val itemWidth = (maxWidth - 10.dp) / items.size
            val pillX by animateDpAsState(
                targetValue = 5.dp + itemWidth * selectedIndex,
                animationSpec = tween(260),
                label = "navPill"
            )
            // 底部折射带：光穿过玻璃在底边聚集，形成一条更亮的带
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.22f))
                        )
                    )
            )
            // 顶部高光
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.98f), Color.Transparent)
                        )
                    )
            )
            // 选中胶囊：白玻璃 + 绿边 + 底部淡绿折射
            Box(
                Modifier
                    .offset(x = pillX + 2.dp, y = 5.dp)
                    .width(itemWidth - 4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.97f), Color(0xFFEDF9F3).copy(alpha = 0.93f))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.95f), MedusaColors.Green.copy(alpha = 0.28f))
                        ),
                        RoundedCornerShape(19.dp)
                    )
                    .border(
                        0.8.dp,
                        Brush.sweepGradient(
                            listOf(
                                Color.White.copy(alpha = 0.5f),
                                Color(0xFF8FE3C0).copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.5f),
                                Color(0xFFFFC9E2).copy(alpha = 0.16f),
                                Color.White.copy(alpha = 0.5f)
                            )
                        ),
                        RoundedCornerShape(19.dp)
                    )
            )
            Row(
                Modifier.fillMaxSize().padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (label, icon, index) ->
                    val active = selectedTab == index
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(19.dp))
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = if (active) MedusaColors.GreenDeep else Color(0xFF44506A),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            label,
                            fontSize = MedusaType.Caption,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MedusaColors.GreenDeep else Color(0xFF44506A)
                        )
                    }
                }
            }
            // 外缘色散：极淡的青/粉彩边，模拟玻璃边缘的衍射
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        Brush.sweepGradient(
                            listOf(
                                Color.White.copy(alpha = 0.98f),
                                Color(0xFFA8F0D2).copy(alpha = 0.30f),
                                Color.White.copy(alpha = 0.88f),
                                Color(0xFFB9D4FF).copy(alpha = 0.24f),
                                Color.White.copy(alpha = 0.98f)
                            )
                        ),
                        shape
                    )
            )
        }
    }
}

/** 首次安装的首页顺序：以 3.5.0 实机排布固化，后续新增域自动排到末尾 */
private val DefaultModuleOrder = listOf(
    "forest", "farm", "ocean", "orchard", "species", "golden",
    "sports", "stall", "protect", "greenAction", "member"
)

private fun loadModuleOrder(prefs: android.content.SharedPreferences): List<String> {
    val known = AllHomeModules.map { it.key }.toSet()
    val saved = prefs.getString("module_order", "")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it in known }
        ?: emptyList()
    val defaults = DefaultModuleOrder.filter { it in known } + known
    return (saved + defaults).distinct()
}

private fun loadHiddenModules(prefs: android.content.SharedPreferences): Set<String> {
    return prefs.getString("module_hidden", "")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet()
}

private fun number(value: Int): String = String.format(Locale.CHINA, "%,d", value)

private fun isSameDayAsNow(millis: Long): Boolean {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = millis }
    return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

private data class MissedBatchGroup(
    val target: String,
    val label: String,
    val rounds: Int
)

/**
 * 3.10.0 需关注：把跨域“需手动”任务和今日完成/未完成/风控摘要放到首页；
 * 只做入口，不改变执行。点“去处理”进入统一手动队列。
 */
@Composable
private fun ManualTaskBanner(uid: String?, tick: Int, history: List<PlanHistory.Record>) {
    val context = LocalContext.current
    var manualTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(uid) {
        while (true) {
            delay(20_000L)
            manualTick++
        }
    }
    val tasks = remember(uid, tick, manualTick) {
        if (uid.isNullOrBlank()) emptyList()
        else TaskLedgerStore.loadActionableManualTasks(uid, TaskLedgerStore.today())
    }
    val riskCount = RiskGuard.getPausedScopes().size
    val todayCompleted = history.count {
        isSameDayAsNow(it.startedAt) && it.status == PlanHistory.COMPLETED
    }
    val todayIncomplete = history.count {
        isSameDayAsNow(it.startedAt) &&
                (it.status == PlanHistory.INCOMPLETE || it.status == PlanHistory.RISK_STOPPED)
    }
    if (tasks.isEmpty() && todayIncomplete == 0 && riskCount == 0) {
        return
    }
    val byDomain = tasks.groupingBy { manualDomainLabel(it.domain) }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(3)
        .joinToString(" / ") { it.key + " " + it.value }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MedusaColors.Line, shape)
            .clickable(enabled = tasks.isNotEmpty()) {
                context.startActivity(
                    Intent(context, MiuixTaskListActivity::class.java)
                        .putExtra(MiuixTaskListActivity.EXTRA_DOMAIN, TaskLedgerStore.DOMAIN_MANUAL)
                        .putExtra(MiuixTaskListActivity.EXTRA_TITLE, "待你操作")
                        .putExtra(MiuixTaskListActivity.EXTRA_USER_ID, uid)
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "需关注",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            if (tasks.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "待你操作 ${tasks.size} 项" + if (byDomain.isNotEmpty()) " · $byDomain" else "",
                    fontSize = MedusaType.Secondary,
                    color = MedusaColors.GreenDeep,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "今日完成 $todayCompleted · 未完成 $todayIncomplete · 风控 $riskCount",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (tasks.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "去处理",
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.GreenDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFE6F7EE))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

private fun manualDomainLabel(domain: String): String = when {
    domain.startsWith("ocean") -> "海洋"
    domain.startsWith("farm") -> "庄园"
    domain.startsWith("forest") -> "森林"
    domain.startsWith("orchard") -> "果园"
    domain.startsWith("species") -> "物种"
    domain == "golden" -> "金豆"
    domain == "sports.kinetic" -> "运动"
    else -> domain.ifBlank { "其他" }
}

/**
 * 只提示、手动补跑：系统强停/闹钟丢失导致凌晨批次漏跑时，首页给出一个可关闭的提醒。
 * 不自动执行任何任务；补跑仍走现有 planRun 命令。
 */
@Composable
private fun MissedBatchBanner(history: List<PlanHistory.Record>, prefs: SharedPreferences) {
    val context = LocalContext.current
    val todayKey = remember { dayKey(Calendar.getInstance()) }
    var dismissedNow by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val dismissed = prefs.getString("missed_batch_dismissed_day", "") == todayKey
    val groups = remember(history, todayKey) { missedBatchGroups(history) }
    if (dismissed || dismissedNow || groups.isEmpty()) {
        return
    }

    val rounds = groups.sumOf { it.rounds }
    val summary = groups.take(3).joinToString("、") { it.label } +
        if (groups.size > 3) " 等 ${groups.size} 个域" else ""

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(MedusaColors.CooldownSoft)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "今天有 $rounds 轮未执行",
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Warning
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = summary,
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                maxLines = 2
            )
        }
        Text(
            text = "补跑",
            fontSize = MedusaType.Body,
            fontWeight = FontWeight.SemiBold,
            color = MedusaColors.GreenDeep,
            modifier = Modifier.clickable { showDialog = true }
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "知道了",
            fontSize = MedusaType.Body,
            color = MedusaColors.Muted,
            modifier = Modifier.clickable {
                dismissedNow = true
                prefs.edit().putString("missed_batch_dismissed_day", todayKey).apply()
            }
        )
    }

    if (showDialog) {
        DisposableEffect(Unit) {
            val dialog = AlertDialog.Builder(context)
                .setTitle("补跑未执行（${groups.size} 个域，逐个发起）")
                .setItems(groups.map { "${it.label} · ${it.rounds} 轮" }.toTypedArray()) { _, which ->
                    showDialog = false
                    sendMissedCatchUp(context, groups[which].target)
                }
                .setNegativeButton("取消", null)
                .create()
            dialog.setOnDismissListener { showDialog = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}

private fun missedBatchGroups(history: List<PlanHistory.Record>): List<MissedBatchGroup> {
    val doneTargets = history
        .filter { it.status != PlanHistory.SKIPPED && isSameDayAsNow(it.startedAt) }
        .map { it.target }
        .toHashSet()
    val counts = linkedMapOf<String, Int>()
    for (record in history) {
        if (!isSameDayAsNow(record.startedAt)) {
            continue
        }
        if (record.reason != "窗口已结束，未执行") {
            continue
        }
        val hour = record.roundTime.take(2).toIntOrNull() ?: 0
        if (hour >= 9) {
            continue
        }
        if (record.target.isBlank() || record.target in doneTargets) {
            continue
        }
        counts[record.target] = (counts[record.target] ?: 0) + 1
    }
    return counts.map { (target, rounds) ->
        MissedBatchGroup(target, PlanTargets.labelOf(target), rounds)
    }
}

private fun sendMissedCatchUp(context: Context, target: String) {
    val calendar = Calendar.getInstance()
    val time = String.format(
        Locale.CHINA, "%02d:%02d",
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE)
    )
    context.sendMedusaBroadcast(
        Intent("com.eg.android.AlipayGphone.sesame.planRun")
            .setPackage("com.eg.android.AlipayGphone")
            .putExtra("target", target)
            .putExtra("roundTime", time)
            .putExtra("ignoreCooldown", true)
    )
    ToastUtil.show(context, "已发起补跑：" + PlanTargets.labelOf(target))
}

private fun dayKey(calendar: Calendar): String = String.format(
    Locale.CHINA, "%04d-%02d-%02d",
    calendar.get(Calendar.YEAR),
    calendar.get(Calendar.MONTH) + 1,
    calendar.get(Calendar.DAY_OF_MONTH)
)

private fun countCompletedToday(history: List<PlanHistory.Record>): Int {
    val now = Calendar.getInstance()
    return history.count { record ->
        val calendar = Calendar.getInstance().apply { timeInMillis = record.startedAt }
        record.status == PlanHistory.COMPLETED &&
            calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }
}

/** 用户自选背景可能是几千万像素的原图，直接 decodeFile 会在低内存机上 OOM，先按宽度降采样 */private fun decodeScaledBitmap(path: String, maxWidth: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** 没有内置插画的新模块用渐变兜底，这样每张卡都仍然可以换成相册图 */
private val gradientPainters = HashMap<String, Painter>()

/** 首页不要系统拉伸回弹：滚动到底只停住，不再把内容拉开露出底色 */
private object NoHomeOverscrollEffect : OverscrollEffect {
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset
    ): Offset = performScroll(delta)

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity
    ) {
        performFling(velocity)
    }

    override val isInProgress: Boolean get() = false
}

private object NoHomeOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = NoHomeOverscrollEffect
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun equals(other: Any?): Boolean = other === this
}

private fun gradientPainter(key: String, colors: List<Color>): Painter =
    gradientPainters.getOrPut(key) {
        val argb = colors.map { it.toArgb() }.toIntArray()
        val bitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 64f, 36f, argb, null, android.graphics.Shader.TileMode.CLAMP
            )
        }
        android.graphics.Canvas(bitmap).drawRect(0f, 0f, 64f, 36f, paint)
        BitmapPainter(bitmap.asImageBitmap())
    }

package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelConfig
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.EmptyModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountOneModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectOneModelField
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.StringUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 配置字段页（三级）：显示某个分组下的所有配置字段。
 * 从 MiuixSettingsActivity 跳转进来，通过 Intent 传递 userId 和 groupCode。
 */
class MiuixGroupFieldsActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_GROUP_CODE = "groupCode"

        /** 预填搜索词：从二级页搜索结果跳进来时带上，进入即过滤到该字段 */
        const val EXTRA_SEARCH_QUERY = "searchQuery"

        /** 域页卡片带进来的字段白名单；为空表示显示整组 */
        const val EXTRA_FIELD_CODES = "fieldCodes"

        /** 白名单里的字段即使依赖的父开关未开也显示（域页“编辑数值/选择好友”入口用） */
        const val EXTRA_INCLUDE_HIDDEN = "includeHidden"

        /** 域页卡片标题；用于详情页顶栏 */
        const val EXTRA_CARD_TITLE = "cardTitle"

        /** 域页卡片 id；有分段定义的卡（如森林「好友」）据此在详情页分块显示 */
        const val EXTRA_CARD_ID = "cardId"
    }

    private var userId: String? = null
    internal var groupCode: String? = null
    private var initialQuery: String? = null
    private var fieldFilter: Set<String>? = null
    private var includeHidden = false
    private var cardTitle: String? = null
    private var cardId: String? = null

    internal var showPermissionDialog by mutableStateOf(false)
    internal var permissionMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        groupCode = intent.getStringExtra(EXTRA_GROUP_CODE)
        initialQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY)
        fieldFilter = intent.getStringExtra(EXTRA_FIELD_CODES)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
        includeHidden = intent.getBooleanExtra(EXTRA_INCLUDE_HIDDEN, false)
        cardTitle = intent.getStringExtra(EXTRA_CARD_TITLE)
        cardId = intent.getStringExtra(EXTRA_CARD_ID)
        // 从首页功能矩阵可直达本页,不一定经过配置页:必须在这里完成模型与账号配置加载,
        // 否则 Model.getGroupModelConfig 为空,页面只有搜索框、看不到任何字段
        ConfigPreload.prepare(userId)
        setAppContent {
            groupCode?.let { code ->
                val group = ModelGroup.entries.find { it.name == code }
                if (group != null) {
                    GroupFieldsContent(
                        activity = this,
                        userId = userId,
                        groupCode = code,
                        group = group,
                        initialQuery = initialQuery,
                        fieldFilter = fieldFilter,
                        includeHidden = includeHidden,
                        titleOverride = cardTitle,
                        cardId = cardId
                    )
                } else {
                    top.yukonga.miuix.kmp.basic.Text("分组不存在: $code", color = MiuixTheme.colorScheme.error)
                }
            } ?: run {
                top.yukonga.miuix.kmp.basic.Text("缺少参数", color = MiuixTheme.colorScheme.error)
            }
        }
    }

    override fun onBackPressed() {
        if (save()) {
            super.onBackPressed()
        }
    }

    /** 顶部返回按钮与系统返回统一入口：保存失败（尤其无权限）时留在页面，避免改动静默丢失。 */
    fun saveAndFinish() {
        if (save()) {
            finish()
        }
    }

    /**
     * 统一落盘入口。userId 为空表示「默认模板」，同样允许保存——
     * 之前这里直接拦掉 null，正是「未获取到账号，改动未保存」的来源。
     */
    fun save(showNoChanges: Boolean = false): Boolean {
        val result = if (showNoChanges) {
            ConfigSync.saveForced(this, userId)
        } else {
            ConfigSync.save(this, userId, false)
        }
        if (result.ok) {
            return true
        }
        if (result.outcome == SaveOutcome.NO_PERMISSION) {
            permissionMessage = ConfigSync.failureMessage(result)
            showPermissionDialog = true
        } else {
            ToastUtil.show(this, ConfigSync.failureMessage(result))
        }
        return false
    }
}

/**
 * 扁平化后的列表行：把「模型标题」和「字段」都提升为 LazyColumn 的独立 item，
 * 让虚拟化真正下沉到字段级。
 *
 * 原先每个 ModelConfig 是一个 item、内部用 fields.forEach 组合全部字段，
 * 导致 Forest 组（77 个字段）一旦进入视口就要一次性组合、measure、layout 所有字段。
 */
private sealed interface GroupFieldsRow {
    val key: String

    data class Header(override val key: String, val title: String) : GroupFieldsRow

    data class Field(
        override val key: String,
        val modelCode: String,
        val field: ModelField<*>,
        val depth: Int = 0,
        val first: Boolean,
        val last: Boolean
    ) : GroupFieldsRow
}

@Composable
fun GroupFieldsContent(activity: MiuixGroupFieldsActivity, userId: String?, groupCode: String, group: ModelGroup,
                       initialQuery: String? = null,
                       fieldFilter: Set<String>? = null,
                       includeHidden: Boolean = false,
                       titleOverride: String? = null,
                       cardId: String? = null) {
    // 父字段开关/选项变化后，依赖其显示的子字段需重新计算可见性，
    // 用 depVersion 作为 remember 键触发扁平行列表重建。
    var depVersion by remember { mutableStateOf(0) }
    // 固定一份实例：每次重组都换新 lambda 会让子项无法跳过重组
    val bumpDependency: () -> Unit = remember { { depVersion++ } }
    // 开关改动立即静默落盘并让宿主重读配置。只靠返回时保存，
    // 页面被系统回收/切走时改动会丢失，用户会看到开关又变回关闭。
    val persistSwitch: () -> Unit = remember(activity, userId) {
        {
            val result = ConfigSync.saveQuiet(activity, userId)
            if (result.ok) {
                ConfigSync.notifyReload(activity, userId)
            } else {
                ToastUtil.show(activity, ConfigSync.failureMessage(result))
            }
        }
    }
    // 只有「被其他字段依赖」的父字段变动才可能改变可见性。其余字段（如普通文本框）
    // 每次输入都重建整份扁平行，77 字段的组里就表现为「输入框打一个字就消失」。
    val dependencyCodes = remember(group) {
        Model.getGroupModelConfig(group).values
            .flatMap { it.fields.values }
            .mapNotNull { it.dependsOn?.takeIf { code -> code.isNotEmpty() } }
            .toSet()
    }
    // 字段对象由 ConfigV2 单例持有，引用稳定；仅当分组或依赖版本变化时才重建。
    val sectionTitles = remember(cardId) { DomainRegistry.sectionOrder(cardId.orEmpty()) }
    val rows = remember(group, depVersion, fieldFilter, includeHidden, cardId) {
        val list = ArrayList<GroupFieldsRow>()
        val modelConfigs = Model.getGroupModelConfig(group).values.filter { it.fields.isNotEmpty() }
        // 分组独立后单模型组的小节标题与顶栏组名重复；多模型组才需要标题区分
        val showHeader = modelConfigs.size > 1
        // 先按原顺序摊平 (模型, 字段)，再决定按模型还是按卡片分段归档
        val picked = ArrayList<Pair<String, ModelField<*>>>()
        modelConfigs.forEach { mc ->
            mc.fields.values
                .filter { fieldFilter == null || it.code in fieldFilter }
                .filter { f -> (includeHidden && fieldFilter != null) || f.isVisible(mc) }
                .forEach { picked.add(mc.getCode() to it) }
        }
        fun rowsOf(subset: List<Pair<String, ModelField<*>>>): List<GroupFieldsRow.Field> =
            subset.mapIndexed { index, (modelCode, field) ->
                GroupFieldsRow.Field(
                    key = "field:$modelCode:${field.code}",
                    modelCode = modelCode,
                    field = field,
                    depth = if (field.dependsOn.isNullOrEmpty()) 0 else 1,
                    first = index == 0,
                    last = index == subset.lastIndex
                )
            }
        if (sectionTitles.isEmpty()) {
            modelConfigs.forEach { mc ->
                val subset = picked.filter { it.first == mc.getCode() }
                if (subset.isEmpty()) return@forEach
                if (showHeader) {
                    list.add(GroupFieldsRow.Header(key = "header:${mc.getCode()}", title = mc.name ?: ""))
                }
                list.addAll(rowsOf(subset))
            }
        } else {
            // 卡片分段（如森林「好友」：收能量/浇水/合种/寻宝互助），顺序由卡片定义
            val used = HashSet<String>()
            sectionTitles.forEach { title ->
                val subset = picked.filter { DomainRegistry.sectionOf(cardId.orEmpty(), it.second.code) == title }
                if (subset.isEmpty()) return@forEach
                list.add(GroupFieldsRow.Header(key = "section:$title", title = title))
                list.addAll(rowsOf(subset))
                subset.forEach { used.add(it.second.code ?: "") }
            }
            val rest = picked.filterNot { used.contains(it.second.code) }
            if (rest.isNotEmpty()) list.addAll(rowsOf(rest))
        }
        list
    }

    /**
     * 执行当前分组的任务。
     * 本进程是模块 App 的 UI 进程，没有 libxposed 类（ApplicationHook/hook.Toast/NotificationUtil 一碰
     * 就 NoClassDefFoundError），任务循环也不能压在主线程上，所以只发广播让注入进程去跑。
     * BASE 分组由注入侧解释为"执行全部任务"。
     */
    val onExecute = remember {
        {
            try {
                val intent = Intent("com.eg.android.AlipayGphone.sesame.execute")
                intent.putExtra("group", group.getCode())
                activity.sendBroadcast(intent)
                ToastUtil.show(activity, "已发送执行请求：${group.getName()}")
            } catch (th: Throwable) {
                Log.printStackTrace(th)
                ToastUtil.show(activity, "执行失败: ${th.message}")
            }
            Unit
        }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = titleOverride ?: group.getName(),
                onBack = { activity.saveAndFinish() },
                onExecute = if (fieldFilter == null) onExecute else null,
                actions = {
                    // 显式保存：不再只依赖「返回时顺带保存」，用户能确认改动确实落盘
                    IconButton(onClick = { activity.save(true) }) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "保存",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        // 搜索框：与四级页一致，按字段名/编码/描述过滤，并隐藏无匹配的分区
        // 带 initialQuery 进来时（二级页搜索结果跳转）直接以它为初始值，进页面即过滤
        var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
        // 默认模板只影响「尚无独立配置的新账号」；已登录账号必须编辑自己的配置
        val editingTemplate = userId == null
        val hasAccountConfigs = remember {
            FileUtil.CONFIG_DIRECTORY_FILE.listFiles()?.any { it.isDirectory } == true
        }
        // 任务模型的总开关（enable）默认关闭：它是「组内开关都开了、任务却完全没有日志」的头号原因。
        // 在这里直接点名并给出一键开启，避免用户在几十个开关里找不到真正的总闸。
        val disabledModels = remember(group, depVersion) {
            Model.getGroupModelConfig(group).values.mapNotNull { mc ->
                val field = mc.getModelField("enable")
                if (field is BooleanModelField && field.value == false) mc.name ?: mc.code else null
            }
        }
        val enableDisabledModels = remember(group) {
            {
                var changed = 0
                Model.getGroupModelConfig(group).values.forEach { mc ->
                    val field = mc.getModelField("enable")
                    if (field is BooleanModelField && field.value == false) {
                        // field 是 ModelField<*>：星投影下 setValue 不可用，走统一入口
                        field.setObjectValue(true)
                        changed++
                    }
                }
                if (changed > 0) {
                    depVersion++
                }
                Unit
            }
        }
        // 按分区（Header）归组：一个分组 = 一张 CardColumn（四角 16dp 圆角、行无缝），
        // 与一级页「一张卡里排多行」完全一致；行作为 Card 的子项，背景/裁剪/按压观感都由它负责。
        // 注意：必须在这里算（@Composable 上下文），不能放进 LazyColumn 的 content lambda
        val sections = remember(rows, searchQuery) {
                val query = searchQuery.trim()
                val list = ArrayList<Pair<String?, MutableList<GroupFieldsRow.Field>>>()
                var title: String? = null
                var fields = ArrayList<GroupFieldsRow.Field>()
                rows.forEach { row ->
                    when (row) {
                        is GroupFieldsRow.Header -> {
                            if (fields.isNotEmpty()) list.add(title to fields)
                            title = row.title
                            fields = ArrayList()
                        }
                        is GroupFieldsRow.Field -> {
                            if (query.isBlank() || fieldMatchesSearch(query, row.field)) {
                                fields.add(row)
                            }
                        }
                    }
                }
                if (fields.isNotEmpty()) list.add(title to fields)
            list
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (editingTemplate && hasAccountConfigs) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Text(
                        "正在编辑「默认模板」：它只用于尚无独立配置的新账号，不会改变当前登录账号。请返回配置页选择带「当前」标记的账号。",
                        fontSize = MedusaType.Body,
                        color = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
            if (disabledModels.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFE9EA))
                        .padding(12.dp)
                ) {
                    Text(
                        "「${disabledModels.joinToString("、")}」总开关未开启",
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B1E24)
                    )
                    Text(
                        "下面的功能开关即使打开也不会执行，也不会产生该模块的日志。",
                        fontSize = MedusaType.Body,
                        color = Color(0xFF8B1E24).copy(alpha = 0.82f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    MedusaActionButton(
                        text = "一键开启",
                        onClick = enableDisabledModels,
                        modifier = Modifier.fillMaxWidth(),
                        height = 38.dp
                    )
                }
            }
            // 搜索浮岛：贴在标题栏下方，列表从它下面开始排，不让行被浮岛切一半
            // 域页卡片进来的短列表不再放搜索框：菜单已经分好类，搜索反而占空间
            if (fieldFilter == null) {
                MedusaSearchIsland(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "搜索本组配置项",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (searchQuery.isNotBlank() && sections.isEmpty()) {
                    item {
                        SmallTitle(text = "无匹配字段")
                    }
                }
                items(
                    sections.size,
                    key = { index ->
                        "section:" + (sections[index].first ?: "_") + ":" +
                            (sections[index].second.firstOrNull()?.key ?: "empty")
                    }
                ) { index ->
                    val (title, fields) = sections[index]
                    title?.let { SmallTitle(text = it) }
                    val sortedAll = fields.sortedBy { categoryOrder(it.field.type) }
                    // “高级 |”前缀的字段收进可折叠区：默认不展开，避免把资源支出类细项铺在最前面
                    val advancedRows = sortedAll.filter { it.field.name.orEmpty().startsWith("高级 |") }
                    val sorted = sortedAll.filterNot { it.field.name.orEmpty().startsWith("高级 |") }
                    var advancedOpen by remember(index, fields.size) { mutableStateOf(false) }
                    CardColumn {
                        val emitted = HashSet<String>()
                        sorted.forEach { fieldRow ->
                            if (!emitted.add(fieldRow.key)) return@forEach
                            // 每行必须带稳定 key：依赖父开关一变，行会整批位移，
                            // 没有 key 时上一行的开关状态会被复用到下一行
                            key(fieldRow.key) {
                                GroupFieldRow(
                                    activity = activity,
                                    userId = userId,
                                    groupCode = groupCode,
                                    row = fieldRow,
                                    onDependencyChanged =
                                        if (dependencyCodes.contains(fieldRow.field.code)) bumpDependency else null,
                                    onPersistSwitch = persistSwitch
                                )
                            }
                            // 子项紧跟父项：归属关系优先于分类整齐
                            fields.filter { it.field.dependsOn == fieldRow.field.code }.forEach { child ->
                                if (emitted.add(child.key)) {
                                    key(child.key) {
                                        GroupFieldRow(
                                            activity = activity,
                                            userId = userId,
                                            groupCode = groupCode,
                                            row = child,
                                            onDependencyChanged =
                                                if (dependencyCodes.contains(child.field.code)) bumpDependency else null,
                                            onPersistSwitch = persistSwitch
                                        )
                                    }
                                }
                            }
                        }
                        if (advancedRows.isNotEmpty()) {
                            key("advanced:" + (title ?: "_")) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { advancedOpen = !advancedOpen }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "高级设置（${advancedRows.size} 项）",
                                        color = MiuixTheme.colorScheme.onBackground,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        if (advancedOpen) "收起" else "展开",
                                        color = MiuixTheme.colorScheme.onBackground,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            if (advancedOpen) {
                                advancedRows.forEach { fieldRow ->
                                    if (emitted.add(fieldRow.key)) {
                                        key(fieldRow.key) {
                                            GroupFieldRow(
                                                activity = activity,
                                                userId = userId,
                                                groupCode = groupCode,
                                                row = fieldRow,
                                                onDependencyChanged =
                                                    if (dependencyCodes.contains(fieldRow.field.code)) bumpDependency else null,
                                                onPersistSwitch = persistSwitch
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        StoragePermissionDialog(
            show = activity.showPermissionDialog,
            message = activity.permissionMessage,
            onDismiss = { activity.showPermissionDialog = false }
        )
    }
}

/**
 * 单个字段行。相邻行背景一致、圆角只在一组字段的首尾外露，
 * 因此视觉上仍是一张连续的卡片，但每一行都能被 LazyColumn 独立复用/回收。
 */
@Composable
private fun GroupFieldRow(
    activity: MiuixGroupFieldsActivity,
    userId: String?,
    groupCode: String,
    row: GroupFieldsRow.Field,
    onDependencyChanged: (() -> Unit)?,
    onPersistSwitch: () -> Unit
) {
    // 不再自己画背景：行的容器由外层 CardColumn（= 库的 Card）统一负责，
    // 与一级页一样是「一张卡里排多行」，行的左右缩进交给行自身的 insideMargin
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = if (row.depth > 0) 14.dp else 0.dp)
    ) {
        val field = row.field
        when {
            // 基础分组的「定时计划」入口：时间用系统选择器增删，不再手写逗号列表
            field.type == "EMPTY" && field.code == "scheduler" -> {
                ArrowPreference(
                    title = field.name ?: "定时计划",
                    summary = "设置定时执行、唤醒与模块重载",
                    onClick = {
                        activity.startActivity(
                            Intent(activity, MiuixSchedulerActivity::class.java).apply {
                                putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                            }
                        )
                    }
                )
            }
            field.type in SELECTION_FIELD_TYPES -> {
                ArrowPreference(
                    title = field.name ?: "",
                    summary = field.description,
                    onClick = {
                        activity.startActivity(
                            Intent(activity, MiuixSelectionEditActivity::class.java).apply {
                                putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                                putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, groupCode)
                                putExtra(MiuixSelectionEditActivity.EXTRA_FIELD_CODE, field.code)
                                putExtra(MiuixSelectionEditActivity.EXTRA_MODEL_CODE, row.modelCode)
                            }
                        )
                    }
                )
            }
            else -> {
                // 开关改动立即落盘；文本/数字仍在返回或点保存时统一落盘，避免逐字写盘。
                val onFieldChanged: (() -> Unit)? = if (field.type == "BOOLEAN") {
                    {
                        onDependencyChanged?.invoke()
                        onPersistSwitch()
                    }
                } else {
                    onDependencyChanged
                }
                FieldItem(field = field, onFieldChanged = onFieldChanged)
            }
        }
    }
}



private fun categoryKey(type: String?): String = when (type) {
    "BOOLEAN" -> "switch"
    "SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE" -> "list"
    "INTEGER", "MULTIPLY_INTEGER", "TEXT", "STRING" -> "number"
    else -> "entry"
}

private fun categoryOrder(type: String?): Int = when (categoryKey(type)) {
    "switch" -> 0
    "list" -> 1
    "number" -> 2
    else -> 3
}

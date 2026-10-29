package io.github.aw1y2z.sesame.ui.miuix

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.EmptyModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.JsonUtil
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.StringUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MiuixSettingsActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
    }

    private var userId: String? = null

    internal var storageReady by mutableStateOf(false)
    internal var showPermissionDialog by mutableStateOf(false)
    internal var permissionMessage by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        ConfigPreload.prepare(userId)
        storageReady = PermissionUtil.isSharedStorageWritable(this)
        setAppContent {
            SettingsContent(this, userId)
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统授权页返回后立即重测：只改 hasPermission 不够，目录仍可能不可写
        storageReady = PermissionUtil.isSharedStorageWritable(this)
    }

    override fun onBackPressed() {
        if (save()) {
            super.onBackPressed()
        }
    }

    /** 顶部返回按钮与系统返回统一入口：保存失败时留在页面，避免改动丢失。 */
    fun saveAndFinish() {
        if (save()) {
            finish()
        }
    }

    /** 统一落盘入口；userId 为空时保存「默认模板」，与三级/四级页行为一致。 */
    fun save(showNoChanges: Boolean = false): Boolean {
        val result = ConfigSync.save(this, userId, showNoChanges)
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

@Composable
fun SettingsContent(activity: MiuixSettingsActivity, userId: String?) {
    val context = LocalContext.current
    val targetLabel = if (StringUtil.isEmpty(userId)) "默认模板"
        else (io.github.aw1y2z.sesame.util.idMap.UserIdMap.getAccountLabel(userId) ?: userId!!)
    val displayAccount = if (StringUtil.isEmpty(userId)) "默认模板" else runCatching {
        io.github.aw1y2z.sesame.util.idMap.UserIdMap.loadSelf(userId)
        val entity = io.github.aw1y2z.sesame.util.idMap.UserIdMap.get(userId)
        listOfNotNull(
            entity?.showName?.takeIf { it.isNotBlank() },
            entity?.account?.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { targetLabel }
    }.getOrDefault(targetLabel)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }

    /** 覆盖写配置（导入用）：权限由调用方先检查，这里只负责备份与落盘 */
    fun writeImportedConfig(text: String): Boolean {
        FileUtil.backupConfigV2BeforeWrite(userId ?: "")
        FileUtil.backupConfigV2WithRolling(if (StringUtil.isEmpty(userId)) "默认" else userId!!)
        return FileUtil.write2File(text, ConfigPreload.getConfigFile(userId))
    }

    fun importPending() {
        val text = pendingImportText ?: return
        if (!PermissionUtil.checkFilePermissions(context) || !FileUtil.isMainDirectoryWritable()) {
            activity.permissionMessage = "导入需要写入共享配置目录，但当前没有可用的「所有文件访问权限」。"
            activity.showPermissionDialog = true
            return
        }
        try {
            if (!writeImportedConfig(text)) {
                throw IllegalStateException(FileUtil.getLastWriteError() ?: "写入配置失败")
            }
            ConfigPreload.prepare(userId)
            ConfigSync.notifyRestart(context, userId)
            ToastUtil.show(
                context,
                if (StringUtil.isEmpty(userId)) {
                    "导入成功：已覆盖「默认模板」；已有账号的配置不受影响"
                } else {
                    "导入成功：已覆盖「$targetLabel」的配置，并已通知支付宝重载"
                }
            )
        } catch (e: Exception) {
            ToastUtil.show(context, "导入失败：${e.message ?: "未知错误"}")
        } finally {
            pendingImportText = null
            pendingImportName = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            // 导出内存中的当前配置：即使磁盘文件缺失/不可读，也能导出用户眼前这一份
            val text = ConfigV2.INSTANCE.toSaveStr()
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray(Charsets.UTF_8))
                    os.flush()
                } ?: throw IllegalStateException("无法打开所选目标文件")
                ToastUtil.show(context, "导出成功：「$targetLabel」的配置已写入所选文件")
            } catch (e: Exception) {
                ToastUtil.show(context, "导出失败：${e.message ?: "无法写入所选文件"}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }
                // 必须是合法的 config_v2（顶层含 modelFieldsMap）：只验 JSON 语法挡不住误选其它 JSON
                if (text.isNullOrBlank()) throw IllegalArgumentException("文件为空")
                val node = JsonUtil.toNode(text) as? com.fasterxml.jackson.databind.JsonNode
                if (node?.has("modelFieldsMap") != true) {
                    throw IllegalArgumentException("不是美杜莎配置（缺少 modelFieldsMap）")
                }
                pendingImportText = text
                // SAF 的 lastPathSegment 只是文档 ID(常见为 "2"),必须查 DISPLAY_NAME 才是文件名
                pendingImportName = resolveDisplayName(context, uri)
                // 覆盖前二次确认：导入会整份替换当前账号配置
                showImportConfirm = true
            } catch (e: Exception) {
                ToastUtil.show(context, "导入失败：${e.message ?: "无法读取所选文件"}")
            }
        }
    }

    // ============ 配置文件：导入 / 导出 / 删除 ============
    Scaffold(
        topBar = {
            LogTopBar(
                title = "配置文件",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (!activity.storageReady) {
                CardColumn {
                    ArrowPreference(
                        title = "文件权限未就绪，导入/导出/删除会失败",
                        summary = PermissionUtil.getStorageStatusText(context) + "（点击去授权）",
                        onClick = { PermissionUtil.checkOrRequestFilePermissions(activity) }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            CardColumn {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "当前账号",
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = MedusaColors.Ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(displayAccount, fontSize = MedusaType.Body, color = MedusaColors.Muted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "配置文件",
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = MedusaColors.Ink
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        ConfigPreload.getConfigFile(userId).absolutePath,
                        fontSize = MedusaType.Secondary,
                        color = MedusaColors.Muted
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            CardColumn {
                ArrowPreference(
                    title = "导入配置",
                    summary = "从 JSON 文件整份覆盖当前配置，覆盖前自动备份",
                    onClick = {
                        if (!activity.storageReady) {
                            activity.permissionMessage = PermissionUtil.getStorageStatusText(activity)
                            activity.showPermissionDialog = true
                        } else {
                            importLauncher.launch("*/*")
                        }
                    }
                )
                ArrowPreference(
                    title = "导出配置",
                    summary = "把当前账号的配置保存为 JSON 文件",
                    onClick = {
                        // 先落盘再导出：导出的内容必须与用户眼前看到的一致
                        if (activity.save(false)) {
                            val safeLabel = targetLabel.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            exportLauncher.launch("美杜莎-$safeLabel-config_v2.json")
                        }
                    }
                )
                ArrowPreference(
                    title = "删除配置",
                    summary = "删除当前账号的配置文件，操作前会二次确认",
                    onClick = { showDeleteDialog = true }
                )
            }
            Spacer(Modifier.height(16.dp))

            if (showDeleteDialog) {
                ConfirmDialog(
                    title = "删除配置",
                    text = "确认删除「$targetLabel」的配置？\n删除后无法恢复，历史备份不受影响。",
                    onConfirm = {
                        showDeleteDialog = false
                        if (FileUtil.deleteFile(ConfigPreload.getConfigFile(userId))) {
                            ToastUtil.show(context, "已删除「$targetLabel」的配置")
                            activity.finish()
                        } else {
                            ToastUtil.show(context, "删除失败：文件不存在或没有写入权限")
                        }
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }
            if (showImportConfirm) {
                ConfirmDialog(
                    title = "导入配置",
                    text = "将用所选文件整份覆盖「$targetLabel」的现有配置，覆盖前会自动备份。\n\n文件：${pendingImportName ?: "所选文件"}",
                    onConfirm = {
                        showImportConfirm = false
                        importPending()
                    },
                    onDismiss = {
                        showImportConfirm = false
                        pendingImportText = null
                        pendingImportName = null
                    }
                )
            }
            StoragePermissionDialog(
                show = activity.showPermissionDialog,
                message = activity.permissionMessage,
                onDismiss = { activity.showPermissionDialog = false }
            )
        }
    }
}

/**
 * 配置字段编辑项（原地展开编辑）。
 *
 * 值为「只写内存」：任何变更只调用 setObjectValue() 落在 ConfigV2 单例上，不触发磁盘写入。
 * 统一落盘由所在页面的退出流程负责（MiuixGroupFieldsActivity.saveAndFinish() / onBackPressed()），
 * 避免每次拨开关、每次提交输入都做一次「全量序列化 + 写盘 + 备份检查」。
 */
@Composable
fun FieldItem(field: ModelField<*>, onFieldChanged: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldItemBody(field, onFieldChanged)
        // 字段说明统一在这里渲染；BOOLEAN 的说明由 SwitchPreference(summary) 承载，不重复
        val description = field.description
        if (field.type != "BOOLEAN" && !description.isNullOrBlank()) {
            Text(
                text = description,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun FieldItemBody(field: ModelField<*>, onFieldChanged: (() -> Unit)? = null) {
    // 用字段名唯一标识展开状态，避免 LazyColumn 复用导致错位
    val fieldKey = "${field.type}:${field.name}"
    var expanded by remember { mutableStateOf(false) }
    var expandedFieldKey by remember { mutableStateOf<String?>(null) }
    when {
        field.type == "BOOLEAN" -> {
            // 必须按 field 取 key：LazyColumn 复用槽位时，不带 key 的 remember 会把上一个字段的
            // 开关状态留给这一行——用户关掉一个开关，看起来像连带关掉了其它开关。
            var checked by remember(field) { mutableStateOf(field.value as? Boolean ?: false) }
            MedusaSwitchRow(
                title = field.name ?: "",
                summary = field.description,
                checked = checked,
                onCheckedChange = {
                    checked = it
                    field.setObjectValue(it)
                    onFieldChanged?.invoke()
                }
            )
        }

        field.type in listOf("INTEGER", "MULTIPLY_INTEGER") -> {
            val context = LocalContext.current
            val imf = field as? IntegerModelField
            val maxLimit = imf?.maxLimit
            val lowerLimit = imf?.minLimit
            val current = (field as? IntegerModelField.MultiplyIntegerModelField)?.getConfigValue()?.toInt() ?: (field.value as? Int ?: 0)
            val limitHint = when {
                lowerLimit == null && maxLimit == null -> ""
                lowerLimit != null && lowerLimit < 0 -> "（-1 表示按最大额度）"
                lowerLimit != null && maxLimit != null -> "（${lowerLimit}~${maxLimit}）"
                maxLimit != null -> "（上限 ${maxLimit}）"
                else -> "（下限 ${lowerLimit}）"
            }
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = if (limitHint.isEmpty()) current.toString() else "$current$limitHint",
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var text by remember(field) { mutableStateOf(current.toString()) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).padding(horizontal = 16.dp)) {
                    TextField(
                        value = text,
                        onValueChange = { input ->
                            val filtered = input.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }
                            text = filtered
                            val parsed = filtered.toIntOrNull()
                            val belowMin = lowerLimit != null && (parsed == null || parsed < lowerLimit)
                            val aboveMax = maxLimit != null && (parsed == null || parsed > maxLimit)
                            if (!belowMin && !aboveMax) {
                                field.setConfigValue(parsed.toString())
                                onFieldChanged?.invoke()
                            }
                        },
                        label = "",
                    )
                }
            }
        }

        field.type in listOf("STRING", "TEXT") -> {
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = field.configValue,
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var text by remember(field) { mutableStateOf(field.configValue ?: "") }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).padding(horizontal = 16.dp)) {
                    TextField(
                        value = text,
                        onValueChange = {
                            text = it
                            field.setObjectValue(it)
                            onFieldChanged?.invoke()
                        },
                        label = "",
                    )
                }
            }
        }

        field.type == "READ_TEXT" || field.type == "URL_TEXT" -> {
            ArrowPreference(title = field.name ?: "", summary = field.configValue)
        }

        field.type in listOf("SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE") -> {} // 由 GroupFieldsPage 处理
        field.type == "EMPTY" -> {
            val emf = field as? EmptyModelField
            ArrowPreference(title = field.name ?: "", onClick = { emf?.clickRunner?.run() })
        }

        field.type == "LIST" -> {
            val list = (field.value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = list.joinToString(","),
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var text by remember { mutableStateOf(list.joinToString("\n")) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).padding(horizontal = 16.dp)) {
                    TextField(
                        value = text,
                        onValueChange = { input ->
                            text = input
                            field.setObjectValue(input.lines().map { it.trim() }.filter { it.isNotEmpty() })
                            onFieldChanged?.invoke()
                        },
                        label = "",
                        singleLine = false,
                        maxLines = 8,
                    )
                }
            }
        }

        field.type == "CHOICE" -> {
            val cmf = field as? ChoiceModelField
            val choiceArray = cmf?.expandKey ?: emptyArray()
            val current = field.value as? Int ?: 0
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = choiceArray.getOrNull(current),
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var sel by remember { mutableStateOf(current) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    choiceArray.forEachIndexed { index, opt ->
                        // 点选即生效（不再需要保存按钮）
                        RadioButtonPreference(
                            title = opt,
                            selected = sel == index,
                            onClick = {
                                sel = index
                                field.setObjectValue(index)
                                onFieldChanged?.invoke()
                            }
                        )
                    }
                }
            }
        }

        else -> {
            Text(text = field.name ?: "", color = MiuixTheme.colorScheme.onBackground)
        }
    }
}

/** 读取 SAF 文档的展示名；取不到时返回 null，由调用方回退 */
private fun resolveDisplayName(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
    } catch (t: Throwable) {
        uri.lastPathSegment
    }
}

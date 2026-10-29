package io.github.aw1y2z.sesame.ui.miuix

import android.app.AlertDialog
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.ai.AgentHandoff
import io.github.aw1y2z.sesame.data.ai.AiDailySummary
import io.github.aw1y2z.sesame.data.ai.AiRollup
import io.github.aw1y2z.sesame.data.ai.AiUsage
import io.github.aw1y2z.sesame.data.ai.UnknownTaskSamples
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore
import io.github.aw1y2z.sesame.util.ToastUtil
import io.github.aw1y2z.sesame.util.ai.AiClient
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 4.0.0 AI 分析（只读）：配置 OpenAI 兼容接口、测试连接、保存、查看用量与摘要。
 * 这里只出聚合摘要，不传原始日志、令牌、好友、链接。
 */
class MiuixAiActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { AiContent(this) }
    }
}

private data class AiForm(
    val enabled: Boolean,
    val baseUrl: String,
    val model: String,
    val token: String,
    val maxTokens: String,
    val manualHints: Boolean,
    val summaryHour: Int
) {
    companion object {
        fun fromConfig() = AiForm(
            enabled = AppConfig.INSTANCE.aiSummaryEnabled ?: false,
            baseUrl = AppConfig.INSTANCE.aiBaseUrl ?: "",
            model = AppConfig.INSTANCE.aiModel ?: "",
            token = AppConfig.INSTANCE.aiToken ?: "",
            maxTokens = (AppConfig.INSTANCE.aiMaxTokens ?: 1024).toString(),
            manualHints = AppConfig.INSTANCE.aiManualHints ?: true,
            summaryHour = AppConfig.INSTANCE.aiSummaryHour ?: 22
        )
    }
}

@Composable
private fun AiContent(activity: MiuixAiActivity) {
    val context = LocalContext.current
    var form by remember { mutableStateOf(AiForm.fromConfig()) }
    var saved by remember { mutableStateOf(AiForm.fromConfig()) }
    var savedAt by remember { mutableStateOf("") }
    var testState by remember { mutableStateOf("") }
    var testOk by remember { mutableStateOf<Boolean?>(null) }
    var testing by remember { mutableStateOf(false) }
    var summaryText by remember { mutableStateOf(latestSummaryText()) }
    var busy by remember { mutableStateOf(false) }
    var usageRevision by remember { mutableStateOf(0) }

    val dirty = form != saved

    fun persist() {
        AppConfig.INSTANCE.aiSummaryEnabled = form.enabled
        AppConfig.INSTANCE.aiBaseUrl = form.baseUrl.trim()
        AppConfig.INSTANCE.aiModel = form.model.trim()
        AppConfig.INSTANCE.aiToken = form.token.trim()
        AppConfig.INSTANCE.aiMaxTokens = form.maxTokens.toIntOrNull() ?: 1024
        AppConfig.INSTANCE.aiManualHints = form.manualHints
        AppConfig.INSTANCE.aiSummaryHour = form.summaryHour
        ConfigSync.saveAppConfig(context)
        try {
            context.sendBroadcast(
                android.content.Intent("com.eg.android.AlipayGphone.sesame.reloadConfig")
                    .setPackage("com.eg.android.AlipayGphone")
            )
        } catch (ignored: Throwable) {
        }
        saved = form
        savedAt = SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date())
        ToastUtil.show(context, "AI 配置已保存")
    }

    Scaffold(
        topBar = { LogTopBar(title = "AI 分析（只读）", onBack = { activity.finish() }) },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            SmallTitle(text = "开关")
            CardColumn {
                BooleanSwitch(
                    title = "启用每日摘要与分析",
                    summary = when {
                        !form.enabled -> "关闭时不产生任何 AI 调用"
                        !AiClient.ready() -> AiClient.describeReadiness()
                        else -> "已就绪：${AiClient.hostOf(form.baseUrl)} · ${form.model}"
                    },
                    checked = form.enabled,
                    onCheckedChange = { form = form.copy(enabled = it) }
                )
                BooleanSwitch(
                    title = "手动任务说明",
                    summary = "没有跳转链接的手动任务，生成一句“去支付宝哪里做什么”",
                    checked = form.manualHints,
                    onCheckedChange = { form = form.copy(manualHints = it) }
                )
            }

            SmallTitle(text = "接口（OpenAI 兼容）")
            CardColumn {
                TextField(
                    value = form.baseUrl,
                    onValueChange = { form = form.copy(baseUrl = it) },
                    label = "接口根地址（如 https://api.example.com/v1）",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                )
                TextField(
                    value = form.model,
                    onValueChange = { form = form.copy(model = it) },
                    label = "模型名",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                )
                ArrowPreference(
                    title = "Bearer 令牌",
                    summary = if (form.token.isBlank()) {
                        "未设置 · 点此填写"
                    } else {
                        "已设置 ${AiClient.maskToken(form.token)} · 点此更换（默认不显示全文，避免截图泄露）"
                    },
                    onClick = {
                        val input = android.widget.EditText(context).apply {
                            setText(form.token)
                            setSelection(text.length)
                            hint = "sk-..."
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        }
                        AlertDialog.Builder(context)
                            .setTitle("Bearer 令牌")
                            .setView(input)
                            .setPositiveButton("保存") { _, _ ->
                                form = form.copy(token = input.text.toString())
                            }
                            .setNeutralButton("清除") { _, _ -> form = form.copy(token = "") }
                            .setNegativeButton("取消", null)
                            .show()
                    }
                )
            }

            SmallTitle(text = "回复上限")
            CardColumn {
                ArrowPreference(
                    title = "max tokens（每次回复）",
                    summary = "${form.maxTokens} tokens · ${
                        when (form.maxTokens.toIntOrNull() ?: 2048) {
                            in 0..256 -> "偏短，摘要很可能被截断"
                            in 257..1023 -> "普通模型够用；推理模型建议 2048"
                            in 1024..2047 -> "普通模型够用；推理模型可能被思考占满"
                            in 2048..4095 -> "推荐：推理模型的思考也占额度，留足余量"
                            else -> "很长；只在经常截断时才需要"
                        }
                    }",
                    onClick = {
                        val labels = arrayOf(
                            "512（短）", "1024（普通模型）", "2048（推荐）", "4096（推理模型）", "8192（很长）"
                        )
                        val values = intArrayOf(512, 1024, 2048, 4096, 8192)
                        AlertDialog.Builder(context)
                            .setTitle("每次回复上限")
                            .setItems(labels) { _, which ->
                                form = form.copy(maxTokens = values[which].toString())
                            }
                            .show()
                    }
                )
                ArrowPreference(
                    title = "摘要生成时间",
                    summary = "${form.summaryHour} 点（当天首次收尾后生成）",
                    onClick = {
                        val labels = (18..23).map { "$it 点" }.toTypedArray()
                        val values = (18..23).toList()
                        AlertDialog.Builder(context)
                            .setTitle("摘要生成时间")
                            .setItems(labels) { _, which ->
                                form = form.copy(summaryHour = values[which])
                            }
                            .show()
                    }
                )
                Text(
                    text = "max tokens 只是单次回复的上限，不是总额度，也不限制你发出去的内容；调大不会更贵（按实际用量计费）。" +
                        "\n\n注意：推理模型（如 deepseek-flash）的思考过程也计入这个额度，1024 可能被思考占满、正文为空；" +
                        "本版检测到这种情况会自动用双倍上限重试一次。",
                    fontSize = MedusaType.Body,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            SmallTitle(text = "保存与连接")
            CardColumn {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    MedusaActionButton(
                        text = if (dirty) "保存配置" else "已保存",
                        onClick = { persist() },
                        enabled = dirty,
                        modifier = Modifier.weight(1f)
                    )
                    MedusaActionButton(
                        text = if (testing) "测试中…" else "测试连接",
                        onClick = {
                            if (testing) return@MedusaActionButton
                            testing = true
                            testOk = null
                            testState = "正在测试（若刚保存，最多要等 20 秒限流）…"
                            // 先保存再测，避免测的是旧配置
                            persist()
                            Thread {
                                val result = AiClient.testConnection()
                                testOk = result.ok
                                testState = result.detail
                                testing = false
                            }.start()
                        },
                        tone = MedusaButtonTone.Secondary,
                        enabled = !testing,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = when {
                        testState.isNotEmpty() -> testState
                        dirty -> "有未保存改动（改完点「保存配置」才会生效）"
                        savedAt.isNotEmpty() -> "已保存 $savedAt"
                        else -> "改完点「保存配置」；「测试连接」会先保存再发一条最小请求"
                    },
                    fontSize = MedusaType.Body,
                    color = when (testOk) {
                        true -> MedusaColors.GreenDeep
                        false -> MedusaColors.Stopped
                        null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            SmallTitle(text = "用量（本地估算）")
            CardColumn {
                val todayUsage = remember(usageRevision) { AiUsage.today() }
                val weekUsage = remember(usageRevision) { AiUsage.recent(7) }
                ArrowPreference(
                    title = "今日 ${todayUsage.optInt("calls")} 次调用",
                    summary = "约 ${AiUsage.formatTokens(todayUsage.optLong("estTokens"))} tokens" +
                        " · 输入 ${todayUsage.optLong("inChars")} 字 / 输出 ${todayUsage.optLong("outChars")} 字",
                    onClick = { usageRevision++ }
                )
                ArrowPreference(
                    title = "近 7 天 ${weekUsage.optInt("calls")} 次调用",
                    summary = "约 ${AiUsage.formatTokens(weekUsage.optLong("estTokens"))} tokens（点一下刷新）",
                    onClick = { usageRevision++ }
                )
                Text(
                    text = AiUsage.estimateMonthly(1, 10, 1850L, 160L) +
                        "。\n\n口径：汉字按 1 token、其他字符按 4 字符 1 token 估算，仅用于心里有数，不是账单。",
                    fontSize = MedusaType.Body,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            SmallTitle(text = "生成")
            CardColumn {
                ArrowPreference(
                    title = "立即生成今日摘要",
                    summary = if (busy) "生成中…" else "把当天聚合数字交给模型归因；不影响任何执行",
                    onClick = {
                        if (busy) return@ArrowPreference
                        if (!AiClient.ready()) {
                            testOk = false
                            testState = "配置未就绪：${AiClient.describeReadiness()}"
                            return@ArrowPreference
                        }
                        busy = true
                        Thread {
                            val result = AiDailySummary.generate(null, TaskLedgerStore.today(), true)
                            summaryText = AiDailySummary.buildText(result)
                            usageRevision++
                            busy = false
                        }.start()
                    }
                )
                ArrowPreference(
                    title = "生成期末汇总（7 天）",
                    summary = "已有 ${AiRollup.availableDays()} 天摘要；输出未知任务清单、耗时分布、失败排行",
                    onClick = {
                        if (busy) return@ArrowPreference
                        busy = true
                        Thread {
                            val result = AiRollup.generate(null, 7, true)
                            summaryText = result.optString("text", result.optString("reason", "生成完成"))
                            usageRevision++
                            busy = false
                        }.start()
                    }
                )
                ArrowPreference(
                    title = "未知任务样本",
                    summary = "本地已记录 ${UnknownTaskSamples.totalCount()} 条；只在手机里，不会自动上传",
                    onClick = { summaryText = unknownPreview() }
                )
            }

            SmallTitle(text = "维护交接包（给开发 AI）")
            CardColumn {
                ArrowPreference(
                    title = "生成维护交接包",
                    summary = "读取叶子事件/计划/账本，输出压缩 JSON + Markdown；不调用模型",
                    onClick = {
                        if (busy) return@ArrowPreference
                        busy = true
                        Thread {
                            val packet = AgentHandoff.build(null, TaskLedgerStore.today())
                            summaryText = AgentHandoff.buildText(packet)
                            busy = false
                            ToastUtil.show(context, "已生成：ai/handoff-${packet.optString("day")}.json")
                        }.start()
                    }
                )
                ArrowPreference(
                    title = "复制交接包 JSON",
                    summary = "粘贴给开发 AI；不含账号、runId、原始日志",
                    onClick = {
                        if (busy) return@ArrowPreference
                        busy = true
                        Thread {
                            val packet = AgentHandoff.build(null, TaskLedgerStore.today())
                            activity.runOnUiThread {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as? android.content.ClipboardManager
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("medusa-handoff", packet.toString())
                                )
                                ToastUtil.show(context, "交接包已复制（约 ${packet.optInt("tokenEstimate")} tokens）")
                                busy = false
                            }
                        }.start()
                    }
                )
            }

            SmallTitle(text = "最新摘要")
            Text(
                text = summaryText.ifEmpty { "还没有摘要。填好配置、点「测试连接」确认通路，再点「立即生成今日摘要」。" },
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            SmallTitle(text = "边界（固定，不可调）")
            Text(
                text = "· 只发送聚合数字与每日结论，不发送原始日志、任务标题全文、令牌、好友、URL。\n" +
                    "· 模型只做归因与叙述，数字由本地代码算；它的回复必须通过 JSON 校验，含执行意图一律丢弃。\n" +
                    "· AI 不会执行、不会改计划、不会改队列、不参与风险预算。\n" +
                    "· 令牌存在模块配置里，注意备份与截图泄露。日志中令牌已打码。",
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun latestSummaryText(): String {
    val latest = AiDailySummary.latest() ?: return ""
    return AiDailySummary.buildText(latest)
}

private fun unknownPreview(): String {
    val items = UnknownTaskSamples.topUnknown(20)
    if (items.length() == 0) {
        return "还没有记录到未知任务。跑几轮后，分类不出来的任务会自动留样本。"
    }
    val builder = StringBuilder("未知任务样本（最多 20 条，按出现次数排序）：\n")
    for (i in 0 until items.length()) {
        val item = items.optJSONObject(i) ?: continue
        builder.append("· [").append(item.optString("domain")).append("] ")
            .append(item.optString("title"))
            .append("（timeCount=").append(item.optLong("timeCount"))
            .append("，").append(item.optInt("count")).append(" 次）\n")
    }
    val file: File = UnknownTaskSamples.exportFile()
    builder.append("\n文件：").append(file.absolutePath)
    return builder.toString()
}
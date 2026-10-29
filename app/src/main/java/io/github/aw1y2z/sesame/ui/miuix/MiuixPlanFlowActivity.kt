package io.github.aw1y2z.sesame.ui.miuix

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 计划流阈值的独立说明/调整页：分别设置最小间隔与抖动百分比。 */
class MiuixPlanFlowActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { PlanFlowContent(this) }
    }
}

@Composable
private fun PlanFlowContent(activity: MiuixPlanFlowActivity) {
    val context = LocalContext.current
    var intervalMs by remember {
        mutableIntStateOf(AppConfig.INSTANCE.planFlowMs ?: AppConfig.DEFAULT_PLAN_FLOW_MS)
    }
    var jitterPercent by remember {
        mutableIntStateOf(AppConfig.INSTANCE.planFlowJitterPercent ?: AppConfig.DEFAULT_PLAN_FLOW_JITTER)
    }

    fun apply() {
        AppConfig.INSTANCE.planFlowMs = intervalMs
        AppConfig.INSTANCE.planFlowJitterPercent = jitterPercent
        ConfigSync.saveAppConfig(context)
        context.sendBroadcast(
            Intent("com.eg.android.AlipayGphone.sesame.reloadConfig")
                .setPackage("com.eg.android.AlipayGphone")
        )
        ToastUtil.show(context, "计划流阈值已保存")
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "计划流阈值",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            SmallTitle(text = "最小间隔")
            CardColumn {
                ArrowPreference(
                    title = "相邻写请求间隔",
                    summary = if (intervalMs <= 0) "已关闭（不限速）" else "$intervalMs 毫秒",
                    onClick = {
                        val labels = arrayOf(
                            "关闭", "1000 毫秒", "2000 毫秒", "3000 毫秒",
                            "5000 毫秒（默认）", "8000 毫秒"
                        )
                        val values = intArrayOf(0, 1000, 2000, 3000, 5000, 8000)
                        AlertDialog.Builder(context)
                            .setTitle("相邻写请求间隔")
                            .setItems(labels) { _, which ->
                                intervalMs = values[which]
                                apply()
                            }
                            .show()
                    }
                )
            }

            SmallTitle(text = "抖动")
            CardColumn {
                ArrowPreference(
                    title = "随机抖动比例",
                    summary = if (intervalMs <= 0) {
                        "$jitterPercent%（最小间隔关闭时不生效）"
                    } else {
                        val maxMs = intervalMs + intervalMs * jitterPercent / 100
                        "$jitterPercent%（实际 $intervalMs–$maxMs 毫秒）"
                    },
                    onClick = {
                        val labels = arrayOf("0%", "10%", "20%", "30%", "40%", "50%")
                        val values = intArrayOf(0, 10, 20, 30, 40, 50)
                        AlertDialog.Builder(context)
                            .setTitle("随机抖动比例")
                            .setItems(labels) { _, which ->
                                jitterPercent = values[which]
                                apply()
                            }
                            .show()
                    }
                )
            }

            Text(
                text = "只限制计划运行期间的写请求（完成、领取、清理、收集等）。读请求、探针和模块外的手动操作不受影响。\n\n" +
                    "实际间隔 = 最小间隔 + 0～抖动比例 的随机值。例如 5000 毫秒、抖动 30%，实际会在 5000–6500 毫秒之间，避免固定节奏。\n\n" +
                    "最小间隔选「关闭」后恢复模块原始速度。它不能替代风控处理：账号已被 1009 拦截时，仍需先在支付宝完成验证。",
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

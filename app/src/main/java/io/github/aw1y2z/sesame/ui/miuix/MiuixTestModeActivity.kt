package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MiuixTestModeActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
    }

    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        setAppContent { TestModeContent(this, userId) }
    }
}

@Composable
private fun TestModeContent(activity: MiuixTestModeActivity, userId: String?) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(TestModeStore.load(userId ?: "")) }

    LaunchedEffect(userId, revision) {
        val loaded = TestModeStore.load(userId ?: "")
        if (loaded.enabled && loaded.day != TestModeStore.today()) {
            TestModeStore.disable(context, userId ?: "")
        } else {
            // 升级/重装后把旧版本的清单恢复为未完成，避免“15 完成”里混入旧版本结果。
            TestModeStore.ensureCurrentBuild(context, userId ?: "")
        }
        while (true) {
            TestModeStore.refreshStatuses(userId ?: "")
            state = TestModeStore.load(userId ?: "")
            delay(2000)
        }
    }

    val actualDone = state.items.count { it.status == TestModeStore.STATUS_DONE && !it.inherited }
    val inherited = state.items.count { it.status == TestModeStore.STATUS_DONE && it.inherited }
    val failed = state.items.count {
        it.status == TestModeStore.STATUS_ERROR || it.status == TestModeStore.STATUS_STOPPED
                || it.status == TestModeStore.STATUS_INCOMPLETE
                || it.status == TestModeStore.STATUS_TIMEOUT
    }
    val risk = state.items.count { it.status == TestModeStore.STATUS_RISK }
    val skipped = state.items.count { it.status == TestModeStore.STATUS_SKIPPED }
    val total = state.items.size

    Scaffold(
        topBar = {
            LogTopBar(title = "测试模式", onBack = { activity.finish() })
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (!state.enabled) {
                CardBox {
                    Text(
                        text = "测试模式会暂停默认计划",
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.Bold,
                        color = MedusaColors.Ink
                    )
                    Text(
                        text = "开启后用一份全量清单跑一轮；本版本已完成的任务不重跑，升级版本自动重跑。关闭后恢复默认计划。",
                        fontSize = MedusaType.Body,
                        color = MedusaColors.Muted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                MedusaActionButton(
                    text = "开启测试模式",
                    onClick = {
                        TestModeStore.enable(context, userId ?: "")
                        revision++
                        state = TestModeStore.load(userId ?: "")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "清单顺序：保护/绿色/会员/古树 → 物种任务、海洋清理、果园肥料、庄园抽抽乐、森林寻宝/IP → 其余日常。6秒拼手速已移除。",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted
                )
                return@Column
            }

            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "实测通过 $actualDone / $total",
                            fontSize = MedusaType.CardTitle,
                            fontWeight = FontWeight.Bold,
                            color = MedusaColors.Ink
                        )
                        Text(
                            text = "继承 $inherited · 未完成 $failed · 风控 $risk · 跳过 $skipped",
                            fontSize = MedusaType.Caption,
                            color = MedusaColors.Muted,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    val percent = if (total > 0) actualDone * 100 / total else 0
                    Text(
                        text = "$percent%",
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.Bold,
                        color = MedusaColors.GreenDeep
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MedusaActionButton(
                    text = "继续未完成",
                    onClick = {
                        TestModeStore.queueUnfinished(context, userId ?: "")
                        TestModeStore.refreshStatuses(userId ?: "")
                        state = TestModeStore.load(userId ?: "")
                    },
                    modifier = Modifier.weight(1f),
                    height = 40.dp
                )
                MedusaActionButton(
                    text = "刷新状态",
                    onClick = {
                        TestModeStore.refreshStatuses(userId ?: "")
                        state = TestModeStore.load(userId ?: "")
                    },
                    modifier = Modifier.weight(1f),
                    tone = MedusaButtonTone.Secondary,
                    height = 40.dp
                )
                MedusaActionButton(
                    text = "关闭",
                    onClick = {
                        TestModeStore.disable(context, userId ?: "")
                        revision++
                        state = TestModeStore.load(userId ?: "")
                    },
                    modifier = Modifier.weight(1f),
                    tone = MedusaButtonTone.Danger,
                    height = 40.dp
                )
            }
            if (inherited > 0) {
                Spacer(Modifier.height(10.dp))
                MedusaActionButton(
                    text = "重跑今日继承（$inherited 项）",
                    onClick = {
                        TestModeStore.requeueInherited(userId ?: "")
                        TestModeStore.refreshStatuses(userId ?: "")
                        state = TestModeStore.load(userId ?: "")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    tone = MedusaButtonTone.Secondary,
                    height = 40.dp
                )
            }
            Spacer(Modifier.height(16.dp))
            SmallTitle(text = "测试清单")
            state.items.forEachIndexed { index, item ->
                TestItemRow(index + 1, item)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "说明：实测通过=本次队列跑的；继承=本版本今日已完成、未重跑；风控/跳过不会自动重跑。“刷新状态”只读取历史与账本，不发起任务。",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted
            )
        }
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun TestItemRow(index: Int, item: TestModeItem) {
    val inheritedDone = item.status == TestModeStore.STATUS_DONE && item.inherited
    val tone = if (inheritedDone) {
        MedusaColors.Muted
    } else when (item.status) {
        TestModeStore.STATUS_DONE -> MedusaColors.GreenDeep
        TestModeStore.STATUS_RISK -> MedusaColors.Stopped
        TestModeStore.STATUS_SKIPPED -> MedusaColors.Muted
        TestModeStore.STATUS_ERROR, TestModeStore.STATUS_STOPPED -> MedusaColors.Stopped
        TestModeStore.STATUS_INCOMPLETE, TestModeStore.STATUS_TIMEOUT -> MedusaColors.Cooldown
        TestModeStore.STATUS_QUEUED -> MedusaColors.Blue
        else -> MedusaColors.Cooldown
    }
    val label = if (inheritedDone) {
        "继承"
    } else when (item.status) {
        TestModeStore.STATUS_DONE -> "已完成"
        TestModeStore.STATUS_RISK -> "风控停止"
        TestModeStore.STATUS_SKIPPED -> "已跳过"
        TestModeStore.STATUS_ERROR -> "异常"
        TestModeStore.STATUS_STOPPED -> "已停止"
        TestModeStore.STATUS_INCOMPLETE -> "未完成"
        TestModeStore.STATUS_TIMEOUT -> "超时"
        TestModeStore.STATUS_QUEUED -> "排队中"
        else -> "待执行"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString().padStart(2, '0'),
            fontSize = MedusaType.Caption,
            fontWeight = FontWeight.SemiBold,
            color = MedusaColors.Muted,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = PlanTargets.labelOf(item.target),
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            if (item.reason.isNotBlank()) {
                Text(
                    text = item.reason,
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Text(
            text = label,
            fontSize = MedusaType.Caption,
            fontWeight = FontWeight.SemiBold,
            color = tone,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(tone.copy(alpha = 0.10f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

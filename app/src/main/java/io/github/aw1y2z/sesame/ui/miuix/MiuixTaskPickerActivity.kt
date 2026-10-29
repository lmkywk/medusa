package io.github.aw1y2z.sesame.ui.miuix

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.plan.PlanTargets
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 任务选择器：按域分组列出全部可执行任务，支持多选与域索引。 */
class MiuixTaskPickerActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SINGLE = "single"
        const val EXTRA_RESULT = "result"
        const val EXTRA_USER_ID = "userId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { TaskPickerScreen(this) }
    }
}

private sealed interface PickRow {
    data class Header(val domain: PickDomain, val count: Int) : PickRow
    data class Item(val task: PickTask, val domain: String) : PickRow
}

@Composable
private fun TaskPickerScreen(activity: MiuixTaskPickerActivity) {
    val single = activity.intent.getBooleanExtra(MiuixTaskPickerActivity.EXTRA_SINGLE, false)
    val userId = activity.intent.getStringExtra(MiuixTaskPickerActivity.EXTRA_USER_ID)
    var selected by remember {
        mutableStateOf(activity.intent.getStringArrayExtra(MiuixTaskPickerActivity.EXTRA_SELECTED)?.toSet() ?: emptySet())
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val rows = remember {
        val out = ArrayList<PickRow>()
        val starts = LinkedHashMap<String, Int>()
        TaskCatalog.domains.forEach { domain ->
            starts[domain.key] = out.size
            val selectedCount = domain.tasks.count { selected.contains(it.target) }
            out.add(PickRow.Header(domain, selectedCount))
            domain.tasks.forEach { out.add(PickRow.Item(it, domain.label)) }
        }
        out to starts
    }
    val startIndex = remember(rows) { rows.second }
    val totalMinutes = remember(selected) {
        selected.sumOf { MeasuredDurations.minutesOf(userId, it, TaskCatalog.minutesOf(it)) }
    }

    fun toggle(target: String) {
        selected = when {
            single -> if (selected.contains(target)) emptySet() else setOf(target)
            selected.contains(target) -> selected - target
            else -> selected + target
        }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = activity.intent.getStringExtra(MiuixTaskPickerActivity.EXTRA_TITLE) ?: "选择要做什么",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = if (single) "点一项即可，选完自动返回" else "可多选，确认后按顺序自动编排时间",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskCatalog.domains.forEach { domain ->
                    Text(
                        text = domain.label,
                        fontSize = MedusaType.Caption,
                        fontWeight = FontWeight.SemiBold,
                        color = MedusaColors.GreenDeep,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(MedusaColors.Green.copy(alpha = 0.10f))
                            .clickable {
                                scope.launch {
                                    listState.animateScrollToItem(startIndex[domain.key] ?: 0)
                                }
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
            ) {
                items(rows.first, key = { row ->
                    when (row) {
                        is PickRow.Header -> "h-" + row.domain.key
                        is PickRow.Item -> "i-" + row.task.target
                    }
                }) { row ->
                    when (row) {
                        is PickRow.Header -> DomainHeader(row.domain, row.count)
                        is PickRow.Item -> TaskRow(
                            task = row.task,
                            checked = selected.contains(row.task.target),
                            single = single,
                            measured = MeasuredDurations.samples(userId, row.task.target)
                        ) { toggle(row.task.target) }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "已选 ${selected.size} 项",
                        fontSize = MedusaType.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = MedusaColors.Ink
                    )
                    Text(
                        text = if (selected.isEmpty()) "预计 0 分钟" else "预计约 $totalMinutes 分钟",
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Muted
                    )
                }
                MedusaActionButton(
                    text = "确定",
                    onClick = {
                        val data = Intent().putExtra(
                            MiuixTaskPickerActivity.EXTRA_RESULT,
                            selected.toTypedArray()
                        )
                        activity.setResult(Activity.RESULT_OK, data)
                        activity.finish()
                    },
                    enabled = selected.isNotEmpty(),
                    height = 40.dp
                )
            }
        }
    }
}

@Composable
private fun DomainHeader(domain: PickDomain, selectedCount: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = domain.label,
            fontSize = MedusaType.CardTitle,
            fontWeight = FontWeight.Bold,
            color = MedusaColors.Ink
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${domain.tasks.size} 项",
            fontSize = MedusaType.Caption,
            color = MedusaColors.Muted
        )
        if (selectedCount > 0) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "已选 $selectedCount",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.GreenDeep
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: PickTask,
    checked: Boolean,
    single: Boolean,
    measured: Pair<Int, Int>?,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) MedusaColors.Green.copy(alpha = 0.08f) else Color.White)
            .border(
                1.dp,
                if (checked) MedusaColors.Green.copy(alpha = 0.35f) else MedusaColors.Line.copy(alpha = 0.8f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(if (single) 18.dp else 18.dp)
                .clip(if (single) CircleShape else RoundedCornerShape(6.dp))
                .background(if (checked) MedusaColors.GreenDeep else Color.Transparent)
                .border(
                    1.5.dp,
                    if (checked) MedusaColors.GreenDeep else Color(0xFFC9D2DE),
                    if (single) CircleShape else RoundedCornerShape(6.dp)
                ),
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
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = PlanTargets.labelOf(task.target),
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            if (task.note.isNotBlank()) {
                Text(
                    text = task.note,
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        Text(
            text = if (measured == null) "约 ${task.minutes} 分钟"
            else "约 ${measured.first} 分钟 · 实测 ${measured.second} 次",
            fontSize = MedusaType.Caption,
            color = MedusaColors.Muted
        )
    }
}

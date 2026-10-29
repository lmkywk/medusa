package io.github.aw1y2z.sesame.ui.miuix

import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import io.github.aw1y2z.sesame.entity.FriendWatch
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 好友：真实好友名册、PK 名单、常用名单（跨域调用）。 */
class MiuixFriendStatsActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            FriendStatsScreen(this)
        }
    }
}

private enum class FriendTab(val label: String) {
    FRIENDS("好友"),
    COMMON("常用"),
    PK_FOREST("森林PK"),
    PK_FARM("庄园PK")
}

@Composable
fun FriendStatsScreen(activity: MiuixFriendStatsActivity) {
    val context = LocalContext.current
    val userId = remember { runCatching { FileUtil.getCurrentAccountFile().takeIf { it.exists() }?.readText()?.trim() }.getOrNull() }
    var tab by remember { mutableStateOf(FriendTab.FRIENDS) }
    var query by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var roster by remember { mutableStateOf(FriendRoster(0L, emptyList())) }
    var pkBoards by remember { mutableStateOf<Map<String, PkBoard>>(emptyMap()) }
    var watch by remember { mutableStateOf<Map<String, FriendWatch>>(emptyMap()) }
    var common by remember { mutableStateOf(CommonFriendStore.load(userId)) }

    LaunchedEffect(userId, refresh) {
        loading = true
        withContext(Dispatchers.IO) {
            roster = FriendRosterStore.load()
            pkBoards = PkRosterStore.load(userId)
            watch = try {
                FriendWatch.getList().associateBy { it.id }
            } catch (e: Throwable) {
                Log.printStackTrace(e)
                emptyMap()
            }
            common = CommonFriendStore.load(userId)
        }
        loading = false
    }

    fun persist(next: CommonFriendStore.State) {
        common = next
        CommonFriendStore.save(userId, next)
    }

    fun toggleCommon(id: String) {
        val ids = if (id in common.ids) common.ids - id else common.ids + id
        persist(common.copy(ids = ids))
    }

    val commonIds = common.ids
    val forestBoard = pkBoards[PkRosterStore.FOREST]
    val farmBoard = pkBoards[PkRosterStore.FARM]

    val friends = roster.friends.filter { friend ->
        query.isBlank() ||
            friend.name.contains(query, true) ||
            friend.nick.contains(query, true) ||
            friend.remark.contains(query, true) ||
            friend.id.contains(query)
    }.sortedWith(
        compareByDescending<FriendEntry> { it.id in commonIds }.thenBy { it.name }
    )

    val commonFriends = roster.friends.filter { it.id in commonIds }
    fun pkFiltered(board: PkBoard?): List<PkMember> = board?.members.orEmpty().filter { member ->
        query.isBlank() || member.name.contains(query, true) || member.id.contains(query)
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "好友",
                onBack = { activity.finish() },
                actions = {
                    IconButton(onClick = { refresh++ }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FriendSummaryCard(
                friendCount = roster.friends.size,
                commonCount = commonIds.size,
                syncedAt = roster.at,
                pkCount = forestBoard?.members?.size ?: farmBoard?.members?.size ?: 0
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FriendChip(FriendTab.FRIENDS.label, tab == FriendTab.FRIENDS) { tab = FriendTab.FRIENDS }
                FriendChip(FriendTab.COMMON.label, tab == FriendTab.COMMON) { tab = FriendTab.COMMON }
                if (forestBoard != null) {
                    FriendChip(FriendTab.PK_FOREST.label, tab == FriendTab.PK_FOREST) { tab = FriendTab.PK_FOREST }
                }
                if (farmBoard != null) {
                    FriendChip(FriendTab.PK_FARM.label, tab == FriendTab.PK_FARM) { tab = FriendTab.PK_FARM }
                }
            }
            if (tab == FriendTab.FRIENDS || tab == FriendTab.COMMON) {
                MedusaSearchIsland(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索昵称 / 备注 / ID",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val emptyText = when {
                loading -> "加载中…"
                tab == FriendTab.FRIENDS && roster.friends.isEmpty() ->
                    "还没有好友名册。\n先在支付宝端登录并重启一次美杜莎，注入进程会把好友库导出到这里。"
                tab == FriendTab.COMMON && commonFriends.isEmpty() ->
                    "还没有常用好友。\n在好友列表点右侧星标即可加入，之后各域选好友默认只看这份名单。"
                else -> null
            }

            if (emptyText != null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(emptyText, fontSize = MedusaType.Body, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp, top = 4.dp)
                ) {
                    when (tab) {
                        FriendTab.FRIENDS -> items(friends, key = { it.id }) { friend ->
                            FriendRow(
                                friend = friend,
                                watch = watch[friend.id],
                                starred = friend.id in commonIds,
                                onStar = { toggleCommon(friend.id) }
                            )
                        }
                        FriendTab.COMMON -> items(commonFriends, key = { it.id }) { friend ->
                            FriendRow(
                                friend = friend,
                                watch = watch[friend.id],
                                starred = true,
                                onStar = { toggleCommon(friend.id) }
                            )
                        }
                        FriendTab.PK_FOREST -> items(pkFiltered(forestBoard), key = { it.id }) { member ->
                            PkRow(
                                member = member,
                                starred = member.id in commonIds,
                                onStar = { toggleCommon(member.id) }
                            )
                        }
                        FriendTab.PK_FARM -> items(pkFiltered(farmBoard), key = { it.id }) { member ->
                            PkRow(
                                member = member,
                                starred = member.id in commonIds,
                                onStar = { toggleCommon(member.id) }
                            )
                        }
                    }
                }
                CommonListControls(
                    state = common,
                    onOnlyCommon = { persist(common.copy(onlyCommon = it)) },
                    onClear = {
                        persist(common.copy(ids = emptySet()))
                        ToastUtil.show(context, "已清空常用好友")
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendSummaryCard(friendCount: Int, commonCount: Int, syncedAt: Long, pkCount: Int) {
    val syncText = if (syncedAt > 0L) {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(syncedAt))
    } else {
        "未同步"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FriendMetric("好友", friendCount.toString())
        Spacer(Modifier.width(18.dp))
        FriendMetric("常用", commonCount.toString())
        Spacer(Modifier.width(18.dp))
        FriendMetric("PK", if (pkCount > 0) pkCount.toString() else "—")
        Spacer(Modifier.weight(1f))
        Text(
            "同步 $syncText",
            fontSize = MedusaType.Caption,
            color = MedusaColors.Muted
        )
    }
}

@Composable
private fun FriendMetric(label: String, value: String) {
    Column {
        Text(value, fontSize = MedusaType.PageTitle, fontWeight = FontWeight.Bold, color = MedusaColors.Ink)
        Text(label, fontSize = MedusaType.Caption, color = MedusaColors.Muted)
    }
}

@Composable
private fun FriendChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = MedusaType.Caption,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) MedusaColors.GreenDeep else MedusaColors.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) MedusaColors.Green.copy(alpha = 0.12f) else Color(0xFFF2F4F7))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun FriendRow(
    friend: FriendEntry,
    watch: FriendWatch?,
    starred: Boolean,
    onStar: () -> Unit
) {
    val name = friend.name.ifBlank { friend.nick }.ifBlank { "ID " + friend.id.takeLast(4) }
    val extra = buildList {
        if (friend.remark.isNotBlank() && friend.remark != name) add("备注 ${friend.remark}")
        if (friend.nick.isNotBlank() && friend.nick != name) add(friend.nick)
        if (watch != null) add("周收 ${watch.weekGet}g · 总收 ${watch.allGet}g")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Ink
                )
                if (friend.status == 1) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(5.dp).background(MedusaColors.Green, CircleShape))
                }
            }
            if (extra.isNotEmpty()) {
                Text(
                    extra.joinToString(" · "),
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        IconButton(onClick = onStar) {
            Icon(
                if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (starred) "移出常用" else "加入常用",
                tint = if (starred) MedusaColors.Warning else Color(0xFFC3CBD8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PkRow(member: PkMember, starred: Boolean, onStar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (member.rank in 1..3) MedusaColors.Green.copy(alpha = 0.14f) else Color(0xFFF2F4F7)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${member.rank}",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.Bold,
                color = if (member.rank in 1..3) MedusaColors.GreenDeep else MedusaColors.Muted
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                member.name.ifBlank { "ID " + member.id.takeLast(4) },
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink
            )
            if (member.score > 0) {
                Text(
                    "${member.score}",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        IconButton(onClick = onStar) {
            Icon(
                if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (starred) "移出常用" else "加入常用",
                tint = if (starred) MedusaColors.Warning else Color(0xFFC3CBD8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CommonListControls(
    state: CommonFriendStore.State,
    onOnlyCommon: (Boolean) -> Unit,
    onClear: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "选好友只看常用名单",
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Ink
                )
                Text(
                    "开启后，森林/庄园/海洋等所有选好友的页面默认只列常用 ${state.ids.size} 人，可随时切回全部",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            MedusaPillSwitch(checked = state.onlyCommon, onCheckedChange = onOnlyCommon)
        }
        if (state.ids.isNotEmpty()) {
            Text(
                "清空常用（${state.ids.size}）",
                fontSize = MedusaType.Caption,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Stopped,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

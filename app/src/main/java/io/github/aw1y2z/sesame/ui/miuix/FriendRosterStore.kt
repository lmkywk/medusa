package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.util.FileUtil
import org.json.JSONObject

/** 支付宝好友库快照（注入进程反射导出，UI 只读）。 */
data class FriendEntry(
    val id: String,
    val name: String,
    val nick: String,
    val remark: String,
    val status: Int
)

data class FriendRoster(val at: Long, val friends: List<FriendEntry>)

object FriendRosterStore {

    fun load(): FriendRoster {
        val file = FileUtil.getFriendListSnapshotFile()
        if (!file.exists()) return FriendRoster(0L, emptyList())
        return try {
            val root = JSONObject(FileUtil.readFromFile(file) ?: return FriendRoster(0L, emptyList()))
            val array = root.optJSONArray("friends") ?: return FriendRoster(0L, emptyList())
            val list = ArrayList<FriendEntry>(array.length())
            for (i in 0 until array.length()) {
                val jo = array.optJSONObject(i) ?: continue
                val id = jo.optString("id", "")
                if (id.isEmpty()) continue
                list.add(
                    FriendEntry(
                        id = id,
                        name = jo.optString("name", ""),
                        nick = jo.optString("nick", ""),
                        remark = jo.optString("remark", ""),
                        status = jo.optInt("status", -1)
                    )
                )
            }
            FriendRoster(root.optLong("at", 0L), list)
        } catch (_: Throwable) {
            FriendRoster(0L, emptyList())
        }
    }
}

/** PK 赛名单：森林能量挑战 / 庄园捐蛋榜。 */
data class PkMember(val id: String, val name: String, val rank: Int, val score: Int)

data class PkBoard(val domain: String, val at: Long, val joined: Boolean, val members: List<PkMember>)

object PkRosterStore {

    const val FOREST = "forest"
    const val FARM = "farm"

    fun load(userId: String?): Map<String, PkBoard> {
        if (userId.isNullOrEmpty()) return emptyMap()
        val file = FileUtil.getPkRosterFile(userId)
        if (!file.exists()) return emptyMap()
        return try {
            val root = JSONObject(FileUtil.readFromFile(file) ?: return emptyMap())
            val out = LinkedHashMap<String, PkBoard>()
            listOf(FOREST, FARM).forEach { domain ->
                val node = root.optJSONObject(domain) ?: return@forEach
                val array = node.optJSONArray("members") ?: return@forEach
                val members = ArrayList<PkMember>(array.length())
                for (i in 0 until array.length()) {
                    val jo = array.optJSONObject(i) ?: continue
                    val id = jo.optString("id", "")
                    if (id.isEmpty()) continue
                    members.add(
                        PkMember(
                            id = id,
                            name = jo.optString("name", ""),
                            rank = jo.optInt("rank", i + 1),
                            score = jo.optInt("score", 0)
                        )
                    )
                }
                out[domain] = PkBoard(domain, node.optLong("at", 0L), node.optBoolean("joined", true), members)
            }
            out
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}

/** 常用好友名单：跨域调用的候选集合。 */
object CommonFriendStore {

    data class State(val ids: Set<String>, val onlyCommon: Boolean)

    fun load(userId: String?): State {
        if (userId.isNullOrEmpty()) return State(emptySet(), false)
        val file = FileUtil.getFriendCommonFile(userId)
        if (!file.exists()) return State(emptySet(), false)
        return try {
            val root = JSONObject(FileUtil.readFromFile(file) ?: return State(emptySet(), false))
            val array = root.optJSONArray("ids")
            val ids = LinkedHashSet<String>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val id = array.optString(i, "")
                    if (id.isNotEmpty()) ids.add(id)
                }
            }
            State(ids, root.optBoolean("onlyCommon", false))
        } catch (_: Throwable) {
            State(emptySet(), false)
        }
    }

    fun save(userId: String?, state: State) {
        if (userId.isNullOrEmpty()) return
        try {
            val root = JSONObject()
            val array = org.json.JSONArray()
            state.ids.forEach { array.put(it) }
            root.put("ids", array)
            root.put("onlyCommon", state.onlyCommon)
            root.put("at", System.currentTimeMillis())
            FileUtil.write2File(root.toString(), FileUtil.getFriendCommonFile(userId))
        } catch (_: Throwable) {
        }
    }
}

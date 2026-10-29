package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.util.FileUtil
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 计划与任务组的原型侧车：只保存 planId -> groupId 的展示映射，
 * 不改 plans.json、不改执行器。计划仍然按自己的 target 执行。
 */
object TaskGroupStore {

    private const val FILE_NAME = "task_groups.json"

    private fun file(userId: String): File =
        File(FileUtil.getUserConfigDirectoryFile(userId), FILE_NAME)

    fun loadPlanGroups(userId: String): Map<String, String> {
        if (userId.isBlank()) return emptyMap()
        return try {
            val file = file(userId)
            if (!file.exists()) return emptyMap()
            val root = JSONObject(FileUtil.readFromFile(file) ?: return emptyMap())
            val groups = root.optJSONObject("planGroups") ?: return emptyMap()
            val result = LinkedHashMap<String, String>()
            val keys = groups.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = groups.optString(key, "")
                if (value.isNotEmpty()) result[key] = value
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun setPlanGroup(userId: String, planId: String, groupId: String?) {
        if (userId.isBlank() || planId.isBlank()) return
        try {
            val file = file(userId)
            val root = if (file.exists()) {
                JSONObject(FileUtil.readFromFile(file) ?: "{}")
            } else {
                JSONObject()
            }
            val groups = root.optJSONObject("planGroups") ?: JSONObject()
            if (groupId.isNullOrBlank()) {
                groups.remove(planId)
            } else {
                groups.put(planId, groupId)
            }
            root.put("version", 1)
            root.put("planGroups", groups)
            FileUtil.write2File(root.toString(), file)
        } catch (_: Throwable) {
            // 原型侧车写失败不影响计划本身
        }
    }

    fun loadPlanTasks(userId: String): Map<String, List<String>> {
        if (userId.isBlank()) return emptyMap()
        return try {
            val file = file(userId)
            if (!file.exists()) return emptyMap()
            val root = JSONObject(FileUtil.readFromFile(file) ?: return emptyMap())
            val tasksRoot = root.optJSONObject("planTasks") ?: return emptyMap()
            val result = LinkedHashMap<String, List<String>>()
            val keys = tasksRoot.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val array = tasksRoot.optJSONArray(key) ?: continue
                val tasks = ArrayList<String>()
                for (i in 0 until array.length()) {
                    val value = array.optString(i, "")
                    if (value.isNotEmpty()) tasks.add(value)
                }
                if (tasks.isNotEmpty()) result[key] = tasks
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun setPlanTasks(userId: String, planId: String, tasks: List<String>?) {
        if (userId.isBlank() || planId.isBlank()) return
        try {
            val file = file(userId)
            val root = if (file.exists()) {
                JSONObject(FileUtil.readFromFile(file) ?: "{}")
            } else {
                JSONObject()
            }
            val tasksRoot = root.optJSONObject("planTasks") ?: JSONObject()
            if (tasks.isNullOrEmpty()) {
                tasksRoot.remove(planId)
            } else {
                tasksRoot.put(planId, JSONArray(tasks))
            }
            root.put("version", 1)
            root.put("planTasks", tasksRoot)
            FileUtil.write2File(root.toString(), file)
        } catch (_: Throwable) {
            // 原型侧车写失败不影响计划本身
        }
    }

    fun loadCustomGroups(userId: String): List<TaskGroupSpec> {
        if (userId.isBlank()) return emptyList()
        return try {
            val file = file(userId)
            if (!file.exists()) return emptyList()
            val root = JSONObject(FileUtil.readFromFile(file) ?: return emptyList())
            val array = root.optJSONArray("customGroups") ?: return emptyList()
            val result = ArrayList<TaskGroupSpec>()
            for (i in 0 until array.length()) {
                val jo = array.optJSONObject(i) ?: continue
                val id = jo.optString("id", "")
                val name = jo.optString("name", "")
                val target = jo.optString("target", "")
                if (id.isEmpty() || name.isEmpty() || target.isEmpty()) continue
                val tasks = ArrayList<String>()
                val taskArray = jo.optJSONArray("tasks")
                if (taskArray != null) {
                    for (j in 0 until taskArray.length()) {
                        val task = taskArray.optString(j, "")
                        if (task.isNotEmpty()) tasks.add(task)
                    }
                }
                result.add(
                    TaskGroupSpec(
                        id = id,
                        name = name,
                        target = target,
                        composition = jo.optString("composition", tasks.joinToString(" · ")),
                        tasks = tasks,
                        risk = runCatching {
                            GroupRisk.valueOf(jo.optString("risk", GroupRisk.LOW.name))
                        }.getOrDefault(GroupRisk.LOW),
                        estimatedMinutes = jo.optInt("estimatedMinutes", 3),
                        note = jo.optString("note", ""),
                        executesFullTarget = jo.optBoolean("executesFullTarget", true),
                        mechanisms = readList(jo.optJSONArray("mechanisms")),
                        costTypes = readList(jo.optJSONArray("costTypes"))
                    )
                )
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun loadGroupNames(userId: String): Map<String, String> {
        if (userId.isBlank()) return emptyMap()
        return try {
            val file = file(userId)
            if (!file.exists()) return emptyMap()
            val root = JSONObject(FileUtil.readFromFile(file) ?: return emptyMap())
            val groups = root.optJSONObject("groups") ?: return emptyMap()
            val result = LinkedHashMap<String, String>()
            val keys = groups.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = groups.optString(key, "")
                if (value.isNotEmpty()) result[key] = value
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun saveGroupName(userId: String, groupId: String, name: String) {
        if (userId.isBlank() || groupId.isBlank()) return
        try {
            val file = file(userId)
            val root = if (file.exists()) {
                JSONObject(FileUtil.readFromFile(file) ?: "{}")
            } else {
                JSONObject()
            }
            val groups = root.optJSONObject("groups") ?: JSONObject()
            groups.put(groupId, name)
            root.put("version", 2)
            root.put("groups", groups)
            FileUtil.write2File(root.toString(), file)
        } catch (_: Throwable) {
        }
    }

    fun removeGroupName(userId: String, groupId: String) {
        if (userId.isBlank() || groupId.isBlank()) return
        try {
            val file = file(userId)
            if (!file.exists()) return
            val root = JSONObject(FileUtil.readFromFile(file) ?: return)
            val groups = root.optJSONObject("groups") ?: return
            groups.remove(groupId)
            root.put("groups", groups)
            FileUtil.write2File(root.toString(), file)
        } catch (_: Throwable) {
        }
    }

    private fun readList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "")
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }

    fun saveCustomGroup(userId: String, group: TaskGroupSpec) {
        if (userId.isBlank() || group.id.isBlank()) return
        try {
            val file = file(userId)
            val root = if (file.exists()) {
                JSONObject(FileUtil.readFromFile(file) ?: "{}")
            } else {
                JSONObject()
            }
            val array = root.optJSONArray("customGroups") ?: JSONArray()
            var replaced = false
            for (i in 0 until array.length()) {
                val jo = array.optJSONObject(i)
                if (jo != null && jo.optString("id") == group.id) {
                    array.put(i, customGroupJson(group))
                    replaced = true
                    break
                }
            }
            if (!replaced) array.put(customGroupJson(group))
            root.put("version", 1)
            root.put("customGroups", array)
            FileUtil.write2File(root.toString(), file)
        } catch (_: Throwable) {
            // 原型侧车写失败不影响计划本身
        }
    }

    private fun customGroupJson(group: TaskGroupSpec): JSONObject {
        val jo = JSONObject()
        jo.put("id", group.id)
        jo.put("name", group.name)
        jo.put("target", group.target)
        jo.put("composition", group.composition)
        jo.put("tasks", JSONArray(group.tasks))
        jo.put("risk", group.risk.name)
        jo.put("estimatedMinutes", group.estimatedMinutes)
        jo.put("note", group.note)
        jo.put("executesFullTarget", group.executesFullTarget)
        jo.put("mechanisms", JSONArray(group.mechanisms))
        jo.put("costTypes", JSONArray(group.costTypes))
        return jo
    }
}

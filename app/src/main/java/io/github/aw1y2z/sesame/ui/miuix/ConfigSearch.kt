package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.data.ModelField

/** 可在四级页编辑的字段类型（与三级页的分派保持一致） */
internal val SELECTION_FIELD_TYPES = setOf("SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE")

/**
 * 三级页的字段搜索匹配：字段名、编码或描述包含关键字（忽略大小写）。
 * 与四级页按 name/id 过滤的语义保持一致。
 */
internal fun fieldMatchesSearch(query: String, field: ModelField<*>): Boolean {
    return field.name?.contains(query, ignoreCase = true) == true
        || field.code.contains(query, ignoreCase = true)
        || field.description?.contains(query, ignoreCase = true) == true
}

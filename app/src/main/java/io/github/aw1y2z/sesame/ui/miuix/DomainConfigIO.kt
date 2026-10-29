package io.github.aw1y2z.sesame.ui.miuix

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.util.JsonUtil

/** 只读写当前域的模型字段，避免导出/导入时把别的域一起带走 */
internal object DomainConfigIO {

    private val mapper = ObjectMapper()

    fun exportJson(spec: DomainSpec, fieldCodes: Set<String>): String {
        val root = mapper.createObjectNode()
        root.put("schema", 1)
        root.put("kind", "medusa-domain-config")
        root.put("domainKey", spec.key)
        root.put("domainTitle", spec.title)
        root.put("exportedAt", System.currentTimeMillis())
        val models = mapper.createObjectNode()
        Model.getGroupModelConfig(spec.group).forEach { (modelCode, modelConfig) ->
            val fields = modelConfig.fields.values.filter { it.code in fieldCodes }
            if (fields.isNotEmpty()) {
                val modelNode = mapper.createObjectNode()
                fields.forEach { field ->
                    modelNode.set<JsonNode>(field.code, mapper.valueToTree(field.value))
                }
                models.set<JsonNode>(modelCode, modelNode)
            }
        }
        root.set<JsonNode>("modelFieldsMap", models)
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    fun applyJson(text: String, spec: DomainSpec, fieldCodes: Set<String>): Result<Int> = runCatching {
        val root = mapper.readTree(text)
        if (root.path("kind").asText() != "medusa-domain-config") {
            throw IllegalArgumentException("不是美杜莎域配置文件")
        }
        if (root.path("domainKey").asText() != spec.key) {
            val title = root.path("domainTitle").asText().ifBlank { "其它域" }
            throw IllegalArgumentException("这是「$title」的配置，不能导入「${spec.title}」")
        }
        val models = root.get("modelFieldsMap")
            ?: throw IllegalArgumentException("缺少 modelFieldsMap")
        val configs = Model.getGroupModelConfig(spec.group)
        var applied = 0
        val modelIt = models.fields()
        while (modelIt.hasNext()) {
            val modelEntry = modelIt.next()
            val modelConfig = configs[modelEntry.key] ?: continue
            val fieldIt = modelEntry.value.fields()
            while (fieldIt.hasNext()) {
                val fieldEntry = fieldIt.next()
                if (fieldEntry.key !in fieldCodes) continue
                val field = modelConfig.getModelField(fieldEntry.key) ?: continue
                val value = JsonUtil.parseObject<Any>(fieldEntry.value.toString(), field.valueType)
                field.setObjectValue(value)
                applied++
            }
        }
        if (applied == 0) {
            throw IllegalArgumentException("文件里没有属于「${spec.title}」的可导入设置")
        }
        applied
    }
}

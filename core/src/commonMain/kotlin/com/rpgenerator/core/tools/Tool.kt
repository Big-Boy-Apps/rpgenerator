package com.rpgenerator.core.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterDefinition>
)

@Serializable
internal data class ParameterDefinition(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enum: List<String>? = null
)

@Serializable
internal data class ToolCall(
    val toolName: String,
    val arguments: Map<String, JsonElement>
)

@Serializable
internal data class ToolResult(
    val success: Boolean,
    val data: Map<String, JsonElement>? = null,
    val error: String? = null
)

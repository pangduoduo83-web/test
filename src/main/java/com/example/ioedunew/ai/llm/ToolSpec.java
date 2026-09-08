package com.example.ioedunew.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 暴露给模型的工具定义:{name, description, parameters(JSON Schema)}。
 * 与 OpenAI function calling 的 function 对象、MCP 的 Tool 对象是同一个三元组,可直接互转。
 */
@Data
@AllArgsConstructor
public class ToolSpec {
    private String name;
    private String description;
    private JsonNode parameters;
}

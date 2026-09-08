package com.example.ioedunew.ai.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 模型发起的一次工具调用:arguments 为模型给出的 JSON 字符串 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;
    private String name;
    private String arguments;
}

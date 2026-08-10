package com.example.ioedunew.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * 宽容的 JSON 文本反序列化器:
 * 实体里以 String 存储的 JSON 字段(specs/tags/syllabus 等)在请求体中
 * 既可以传 JSON 字符串,也可以直接传数组/对象(GET 返回值原样回写的场景),
 * 统一转成紧凑 JSON 字符串落库。
 */
public class RawJsonStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }
}

package com.example.ioedunew.ai.llm;

/** 流式输出回调:文本增量逐段推送,工具调用在流结束后整体给出 */
public interface StreamListener {

    void onContent(String delta);

    /** 流结束(正常或被服务端截断),result 汇总了全文、工具调用与 token 用量 */
    void onComplete(ChatResult result);
}

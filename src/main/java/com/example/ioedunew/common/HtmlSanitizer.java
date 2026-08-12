package com.example.ioedunew.common;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 富文本 HTML 消毒:允许常见排版标签与图片/视频,过滤脚本与危险属性。
 * 纯文本(不含标签)原样返回,兼容旧数据。
 */
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("video", "source", "figure", "figcaption", "hr")
            .addAttributes("video", "src", "controls", "poster", "width", "height", "style")
            .addAttributes("source", "src", "type")
            .addAttributes("img", "style", "width", "height", "alt")
            .addAttributes(":all", "style", "class")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("video", "src", "http", "https")
            // 站内上传的图片/视频是 /uploads/... 相对路径,必须保留
            .preserveRelativeLinks(true);

    private HtmlSanitizer() {
    }

    public static String clean(String html) {
        if (html == null || html.isEmpty() || !html.contains("<")) {
            return html;
        }
        // baseUri 仅用于让协议校验放行站内相对路径(/uploads/...),
        // preserveRelativeLinks=true 保证输出仍保留原相对路径
        return Jsoup.clean(html, "http://ioedu.internal", SAFELIST);
    }
}

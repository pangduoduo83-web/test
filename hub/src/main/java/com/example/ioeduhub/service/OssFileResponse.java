package com.example.ioeduhub.service;

import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.stereotype.Component;
import javax.servlet.http.*;
import java.io.*;

/** Streams without a disk cache; a configured OSS CNAME enables signed browser redirects. */
@Component
public class OssFileResponse {
    private final OssObjectStore oss;
    public OssFileResponse(OssObjectStore oss) { this.oss = oss; }

    public void serve(String key, String name, HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean head = "HEAD".equals(request.getMethod());
        String url = oss.browserUrl(key, head);
        if (url != null) {
            response.setStatus(302); response.setHeader("Location", url); response.setHeader("Cache-Control", "no-store"); return;
        }
        ObjectMetadata metadata = oss.metadata(key);
        if (metadata == null) { response.setStatus(404); return; }
        long size = metadata.getContentLength();
        String etag = "\"" + metadata.getETag() + "\"";
        response.setHeader("ETag", etag); response.setHeader("Cache-Control", "private, max-age=3600");
        response.setHeader("Accept-Ranges", "bytes"); response.setHeader("X-Content-Type-Options", "nosniff");
        if (etag.equals(request.getHeader("If-None-Match"))) { response.setStatus(304); return; }
        response.setContentType(AssetContent.contentType(name));
        if (name.endsWith(".svg")) response.setHeader("Content-Security-Policy", "sandbox");
        String range = request.getHeader("Range"), ifRange = request.getHeader("If-Range");
        if (head || (ifRange != null && !etag.equals(ifRange))) range = null;
        long[] bounds;
        try { bounds = range(range, size); }
        catch (IllegalArgumentException e) { response.setStatus(416); response.setHeader("Content-Range", "bytes */" + size); return; }
        boolean partial = range != null;
        response.setStatus(partial ? 206 : 200);
        if (partial) response.setHeader("Content-Range", "bytes " + bounds[0] + "-" + bounds[1] + "/" + size);
        long length = size == 0 ? 0 : bounds[1] - bounds[0] + 1;
        response.setContentLengthLong(length);
        if (head || length == 0) return;
        try (InputStream in = oss.open(key, partial ? bounds[0] : null, partial ? bounds[1] : null)) {
            AssetContent.copy(in, response.getOutputStream(), length);
        }
    }

    static long[] range(String value, long size) {
        if (value == null) return new long[]{0, Math.max(0, size - 1)};
        if (size <= 0 || !value.matches("bytes=\\d*-\\d*")) throw new IllegalArgumentException();
        try {
            String[] parts = value.substring(6).split("-", -1);
            long start, end;
            if (parts[0].isEmpty()) {
                long suffix = Long.parseLong(parts[1]); if (suffix <= 0) throw new IllegalArgumentException();
                start = Math.max(0, size - suffix); end = size - 1;
            } else { start = Long.parseLong(parts[0]); end = parts[1].isEmpty() ? size - 1 : Math.min(size - 1, Long.parseLong(parts[1])); }
            if (start >= size || end < start) throw new IllegalArgumentException();
            return new long[]{start, end};
        } catch (NumberFormatException e) { throw new IllegalArgumentException(); }
    }
}

package com.example.ioeduhub.service;
import java.io.*;
import java.net.URLConnection;
import java.util.Locale;
final class AssetContent {
    static void copy(InputStream in, OutputStream out, long size) throws IOException {
        long copied = 0; byte[] buffer = new byte[65536]; int n;
        while ((n = in.read(buffer)) != -1) {
            copied += n;
            if (copied > size) throw new IOException("文件大小与记录不一致");
            out.write(buffer, 0, n);
        }
        if (copied != size) throw new IOException("文件传输不完整");
    }

    public static String contentType(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if ("webp".equals(ext)) return "image/webp";
        if ("mp4".equals(ext)) return "video/mp4";
        if ("mov".equals(ext)) return "video/quicktime";
        if ("webm".equals(ext)) return "video/webm";
        if ("pdf".equals(ext)) return "application/pdf";
        if ("svg".equals(ext)) return "image/svg+xml";
        String mime = URLConnection.guessContentTypeFromName(name);
        return mime == null ? "application/octet-stream" : mime;
    }

}

package com.example.ioedunew.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Platform-owned configuration. Credentials never appear in tenant APIs. */
@Data
@Component
@ConfigurationProperties(prefix = "ioedu.storage")
public class StorageProperties {
    private String type = "local";
    private Oss oss = new Oss();
    private Cdn cdn = new Cdn();
    private Video video = new Video();

    @Data
    public static class Oss {
        private String endpoint = "";
        private String region = "";
        private String bucket = "";
        private String prefix = "ioedu";
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String securityToken = "";
        // Optional HTTPS CNAME bound to OSS. Empty: stream through the application.
        private String publicDomain = "";
        private int signedUrlSeconds = 3600;
        private boolean directUpload = false;
        // Public regional endpoint for browsers, even when the server endpoint is internal.
        private String uploadEndpoint = "";
        private int uploadSeconds = 900;
    }

    @Data
    public static class Cdn {
        private String domain = "";
        private String authKey = "";
        private int authSeconds = 7200;
    }

    @Data
    public static class Video {
        // OSS + IMM on-demand HLS. Enable only after configuring private CDN origin processing.
        private boolean hls = false;
    }
}

package com.example.ioedunew.service;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.*;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.*;
import com.example.ioedunew.common.BusinessException;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.URI;
import java.util.Date;

/** OSS SDK boundary; no caller-supplied bucket, endpoint or credentials. */
@Component
public class OssObjectStore {
    private final StorageProperties properties;
    private OSS client;
    private OSS publicClient;

    public OssObjectStore(StorageProperties properties) {
        this.properties = properties;
        if (!"local".equals(properties.getType()) && !"oss".equals(properties.getType()))
            throw new IllegalArgumentException("文件存储类型须为 local 或 oss");
        if (enabled()) validate();
    }

    public boolean enabled() { return "oss".equals(properties.getType()); }
    public String bucket() { return properties.getOss().getBucket(); }
    public String prefix() { return properties.getOss().getPrefix(); }

    private void validate() {
        StorageProperties.Oss p = properties.getOss();
        if (!p.getBucket().matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]") || p.getRegion().trim().isEmpty()
                || !p.getPrefix().matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*"))
            throw new IllegalArgumentException("请配置有效的 OSS Bucket、地域和目录前缀");
        validateEndpoint(p.getEndpoint());
        if (!p.getPublicDomain().isEmpty()) validateEndpoint(p.getPublicDomain());
        if (p.getSignedUrlSeconds() < 60 || p.getSignedUrlSeconds() > 86400)
            throw new IllegalArgumentException("OSS 签名有效期须为60至86400秒");
        CdnUrls.validate(properties.getCdn());
        if (properties.getVideo().isHls() && properties.getCdn().getDomain().isEmpty())
            throw new IllegalArgumentException("HLS 需先配置 CDN 域名和私有回源");
    }

    private void validateEndpoint(String value) {
        URI uri;
        try { uri = URI.create(value); } catch (Exception e) { throw new IllegalArgumentException("OSS 地址格式错误"); }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
            throw new IllegalArgumentException("OSS 地址须为 HTTPS 域名，不含路径、账号或查询参数");
    }

    private synchronized OSS client(boolean browser) throws com.aliyuncs.exceptions.ClientException {
        validate();
        if (browser && publicClient != null) return publicClient;
        if (!browser && client != null) return client;
        StorageProperties.Oss p = properties.getOss();
        CredentialsProvider credentials;
        if (!p.getAccessKeyId().isEmpty() && !p.getAccessKeySecret().isEmpty())
            credentials = new DefaultCredentialProvider(p.getAccessKeyId(), p.getAccessKeySecret(), p.getSecurityToken().isEmpty() ? null : p.getSecurityToken());
        else credentials = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        configuration.setConnectionTimeout(15000);
        configuration.setSocketTimeout(120000);
        configuration.setMaxErrorRetry(2);
        configuration.setSupportCname(browser);
        OSS created = OSSClientBuilder.create().endpoint(browser ? p.getPublicDomain() : p.getEndpoint())
                .credentialsProvider(credentials).clientConfiguration(configuration).region(p.getRegion()).build();
        if (browser) publicClient = created; else client = created;
        return created;
    }

    public void put(String key, InputStream input, long size, String mime, String sha256) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(size); metadata.setContentType(mime);
            metadata.setCacheControl("public, max-age=86400, immutable");
            // Explicitly private even when the bucket default is public.
            metadata.setHeader("x-oss-object-acl", "private");
            if (sha256 != null) metadata.addUserMetadata("sha256", sha256);
            client(false).putObject(bucket(), key, input, metadata);
        } catch (Exception e) { throw unavailable(); }
    }

    public ObjectMetadata metadata(String key) {
        try { return client(false).getObjectMetadata(bucket(), key); }
        catch (OSSException e) { if ("NoSuchKey".equals(e.getErrorCode())) return null; throw unavailable(); }
        catch (Exception e) { throw unavailable(); }
    }

    public InputStream open(String key, Long from, Long to) {
        try {
            GetObjectRequest request = new GetObjectRequest(bucket(), key);
            if (from != null) request.setRange(from, to);
            OSSObject object = client(false).getObject(request);
            return new FilterInputStream(object.getObjectContent()) {
                @Override public void close() throws IOException { object.close(); }
            };
        } catch (Exception e) { throw unavailable(); }
    }

    public String browserUrl(String key, boolean head) {
        if (!properties.getCdn().getDomain().isEmpty()) return CdnUrls.sign(properties.getCdn(), key);
        if (properties.getOss().getPublicDomain().isEmpty()) return null;
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket(), key, head ? HttpMethod.HEAD : HttpMethod.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + properties.getOss().getSignedUrlSeconds() * 1000L));
            return client(true).generatePresignedUrl(request).toString();
        } catch (Exception e) { throw unavailable(); }
    }

    /** Promote a staged upload within OSS; bytes never pass through the application server. */
    public void promote(String source, String target, String etag, String mime) {
        try {
            CopyObjectRequest request = new CopyObjectRequest(bucket(), source, bucket(), target);
            request.setMatchingETagConstraints(java.util.Collections.singletonList(etag));
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(mime); metadata.setCacheControl("public, max-age=86400, immutable");
            metadata.setHeader("x-oss-object-acl", "private");
            request.setNewObjectMetadata(metadata);
            request.addHeader("x-oss-object-acl", "private");
            client(false).copyObject(request);
        } catch (Exception e) { throw unavailable(); }
    }

    /** IMM does the transcoding. The SDK only sends a small processing instruction. */
    public void generatePlaylist(String source, String targetPrefix, String style) {
        try {
            java.util.Base64.Encoder encoder = java.util.Base64.getUrlEncoder().withoutPadding();
            String process = style + "|sys/saveas,b_" + encoder.encodeToString(bucket().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + ",o_" + encoder.encodeToString(targetPrefix.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            GenericResult result = client(false).processObject(new ProcessObjectRequest(bucket(), source, process));
            if (result.getResponse() != null) result.getResponse().close();
        } catch (Exception e) { throw unavailable(); }
    }

    public void delete(String key) {
        try { client(false).deleteObject(bucket(), key); } catch (Exception e) { throw unavailable(); }
    }
    private BusinessException unavailable() { return new BusinessException(502, "OSS文件服务暂不可用，请检查平台存储配置或稍后重试"); }
    @PreDestroy public synchronized void close() {
        if (client != null) client.shutdown();
        if (publicClient != null) publicClient.shutdown();
    }
}

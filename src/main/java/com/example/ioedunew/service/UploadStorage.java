package com.example.ioedunew.service;

import com.example.ioedunew.tenant.TenantContext;
import com.example.ioedunew.tenant.TenantProperties;
import com.example.ioedunew.tenant.TenantQuotaService;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.entity.StoredFile;
import com.example.ioedunew.repository.StoredFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.io.*;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 可切换的租户文件存储：新文件写 OSS 或本地，历史本地文件兼容读取。
 * 库里与接口里的 URL 仍是 /uploads/{yyyyMM}/{uuid.ext},租户由请求 Host 决定,
 * 因此同一份项目数据搬到别的租户(项目商店)时不需要改写 URL。
 * OSS目录为 {prefix}/tenants/{租户编码}/{yyyyMM}/{uuid.ext}，实际位置记入租户库。
 * 多租户之前上传的文件位于 {upload-dir}/{yyyyMM}/ 下,只对默认租户做兼容回退。
 */
@Component
public class UploadStorage {

    /** 只接受 yyyyMM/文件名 形式的相对路径,杜绝目录穿越 */
    private static final Pattern RELATIVE = Pattern.compile("^\\d{6}/[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,10}$");

    private final Path root;
    private final TenantProperties tenantProperties;
    private final OssObjectStore oss;
    private final StoredFileRepository files;
    private final StoredFileCatalog catalog;
    private final TenantQuotaService quota;
    private final StorageQuotaLock quotaLock;
    private final Object[] locks = new Object[64];

    public UploadStorage(@Value("${ioedu.upload-dir}") String uploadDir, TenantProperties tenantProperties,
            OssObjectStore oss, StoredFileRepository files, StoredFileCatalog catalog, TenantQuotaService quota, StorageQuotaLock quotaLock) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.tenantProperties = tenantProperties;
        this.oss = oss; this.files = files; this.catalog = catalog; this.quota = quota;
        this.quotaLock = quotaLock;
        Arrays.setAll(locks, i -> new Object());
    }

    /** 当前租户的上传根目录 */
    public Path tenantRoot() {
        String tenant = TenantContext.require();
        if (!tenant.matches("[A-Za-z0-9_-]{1,64}")) throw new BusinessException("站点编码无效");
        return root.resolve(tenant);
    }

    /**
     * 把 URL 相对部分(yyyyMM/uuid.ext)解析成当前租户的文件路径;
     * 非法路径返回 null,默认租户在自身目录找不到时回退到多租户前的旧目录。
     */
    public Path resolveForRead(String relative) {
        if (relative == null || !RELATIVE.matcher(relative).matches()) {
            return null;
        }
        Path tenantRoot = tenantRoot();
        Path file = tenantRoot.resolve(relative).normalize();
        if (!file.startsWith(tenantRoot)) {
            return null;
        }
        if (regularInside(file, tenantRoot)) {
            return file;
        }
        if (tenantProperties.getDefaultCode().equals(TenantContext.require())) {
            Path legacy = root.resolve(relative).normalize();
            if (regularInside(legacy, root)) {
                return legacy;
            }
        }
        return file;
    }

    private boolean regularInside(Path file, Path base) {
        try {
            if (!file.normalize().startsWith(base.normalize())) return false;
            for (Path current = file; current != null && current.startsWith(root); current = current.getParent())
                if (Files.isSymbolicLink(current)) return false;
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.toRealPath().startsWith(base.toRealPath());
        }
        catch (IOException e) { return false; }
    }

    private void requireRelative(String relative) {
        if (relative == null || !RELATIVE.matcher(relative).matches()) throw new BusinessException("文件路径无效");
        tenantRoot();
    }

    public StoredFile remote(String relative) {
        requireRelative(relative);
        StoredFile file = files.findById(relative).orElse(null);
        if (file != null && (!file.getBucket().equals(oss.bucket()) || !file.getObjectKey().equals(objectKey(relative))))
            throw new BusinessException(503, "文件存储位置与平台配置不一致，请联系平台管理员");
        return file;
    }

    public String objectKey(String relative) {
        requireRelative(relative);
        return oss.prefix() + "/tenants/" + TenantContext.require() + "/" + relative;
    }

    public boolean exists(String relative) {
        if (remote(relative) != null) return true;
        Path path = resolveForRead(relative);
        return path != null && regularInside(path, root);
    }

    /** New uploads are streamed to OSS; only the local mode writes a permanent file. */
    public void save(String relative, InputStream input, long size) throws IOException {
        try {
            quotaLock.run(() -> {
                try { saveLocked(relative, input, size); return null; }
                catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (UncheckedIOException e) { throw e.getCause(); }
    }

    private void saveLocked(String relative, InputStream input, long size) throws IOException {
        requireRelative(relative);
        if (size <= 0 || size > 100L * 1024 * 1024) throw new BusinessException("文件须为1字节至100MB");
        synchronized (lock()) {
            quota.checkStorageQuota(size);
            if (exists(relative)) throw new BusinessException("文件已存在，请重新上传");
            if (oss.enabled()) {
                String key = objectKey(relative);
                oss.put(key, input, size, contentType(relative), null);
                StoredFile file = record(relative, size, null);
                try { catalog.record(file); }
                catch (RuntimeException e) {
                    try { oss.delete(key); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
                    throw e;
                }
            } else {
                Path target = tenantRoot().resolve(relative);
                Files.createDirectories(target.getParent());
                if (!target.getParent().toRealPath().startsWith(tenantRoot().toRealPath())) throw new BusinessException("文件路径无效");
                Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
                try {
                    try (OutputStream out = Files.newOutputStream(temporary)) { copy(input, out, size); }
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            }
            quota.invalidateStorage(TenantContext.require());
        }
    }

    private StoredFile record(String relative, long size, String sha) {
        StoredFile file = new StoredFile(); file.setRelativePath(relative); file.setObjectKey(objectKey(relative));
        file.setBucket(oss.bucket()); file.setSizeBytes(size); file.setSha256(sha); return file;
    }
    private Object lock() { return locks[(TenantContext.require().hashCode() & 0x7fffffff) % locks.length]; }

    /** Temporary working copy for FFmpeg/MinerU or project export. Always use try-with-resources. */
    public WorkingFile materialize(String relative, long limit) throws IOException {
        StoredFile remote = remote(relative);
        if (remote == null) {
            Path path = resolveForRead(relative);
            if (path == null || !regularInside(path, root)) throw new BusinessException(404, "附件不存在");
            if (Files.size(path) > limit) throw new BusinessException("文件超过处理上限");
            return new WorkingFile(path, false);
        }
        if (remote.getSizeBytes() > limit) throw new BusinessException("文件超过处理上限");
        String extension = relative.substring(relative.lastIndexOf('.'));
        Path temporary = Files.createTempFile("ioedu-material-", extension);
        try {
            try (InputStream in = oss.open(remote.getObjectKey(), null, null); OutputStream out = Files.newOutputStream(temporary)) {
                copy(in, out, remote.getSizeBytes());
            }
            return new WorkingFile(temporary, true);
        } catch (Exception e) { Files.deleteIfExists(temporary); throw e; }
    }

    public static class WorkingFile implements AutoCloseable {
        private final Path path;
        private final boolean temporary;
        public WorkingFile(Path path, boolean temporary) { this.path = path; this.temporary = temporary; }
        public Path path() { return path; }
        @Override public void close() throws IOException { if (temporary) Files.deleteIfExists(path); }
    }

    /** Copies a bounded, exact object; truncated responses are never published as complete files. */
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

    /** Bounded, resumable migration. Existing URLs and local originals remain intact. */
    public Map<String, Object> migrate(int limit, boolean dryRun) throws IOException {
        if (!oss.enabled()) throw new BusinessException("请先启用 OSS 存储配置");
        if (limit < 1 || limit > 50) throw new BusinessException("每批迁移数量须为1至50");
        synchronized (lock()) {
            Map<String, Path> candidates = new TreeMap<>();
            collect(tenantRoot(), candidates);
            if (tenantProperties.getDefaultCode().equals(TenantContext.require())) collect(root, candidates);
            for (StoredFile stored : files.findAll()) candidates.remove(stored.getRelativePath());
            List<String> completed = new ArrayList<>(); Map<String, String> failures = new LinkedHashMap<>();
            for (Map.Entry<String, Path> entry : candidates.entrySet()) {
                if (completed.size() + failures.size() >= limit) break;
                String relative = entry.getKey(); Path path = entry.getValue();
                if (dryRun) { completed.add(relative); continue; }
                try {
                    long size = Files.size(path);
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (DigestInputStream in = new DigestInputStream(Files.newInputStream(path), digest)) {
                        byte[] bytes = new byte[65536]; while (in.read(bytes) != -1) { }
                    }
                    StringBuilder sha = new StringBuilder(); for (byte b : digest.digest()) sha.append(String.format("%02x", b));
                    String key = objectKey(relative);
                    try (InputStream in = Files.newInputStream(path)) { oss.put(key, in, size, contentType(relative), sha.toString()); }
                    com.aliyun.oss.model.ObjectMetadata metadata = oss.metadata(key);
                    if (metadata == null || metadata.getContentLength() != size || !sha.toString().equals(metadata.getUserMetadata().get("sha256")))
                        throw new IOException("迁移校验失败");
                    catalog.record(record(relative, size, sha.toString())); completed.add(relative);
                } catch (Exception e) { failures.put(relative, "迁移未完成，原文件保留，请检查OSS配置后重试"); }
            }
            quota.invalidateStorage(TenantContext.require());
            Map<String, Object> result = new LinkedHashMap<>(); result.put("dryRun", dryRun); result.put("files", completed);
            result.put("failures", failures); result.put("pending", candidates.size() - (dryRun ? 0 : completed.size()));
            return result;
        }
    }

    private void collect(Path base, Map<String, Path> paths) throws IOException {
        if (!Files.isDirectory(base)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(base, 2)) {
            stream.filter(p -> regularInside(p, base)).forEach(p -> {
                String relative = base.relativize(p).toString().replace('\\', '/');
                if (RELATIVE.matcher(relative).matches()) paths.putIfAbsent(relative, p);
            });
        }
    }
}

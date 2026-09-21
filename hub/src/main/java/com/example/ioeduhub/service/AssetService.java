package com.example.ioeduhub.service;

import com.example.ioeduhub.common.BusinessException;
import com.example.ioeduhub.config.HubProperties;
import com.example.ioeduhub.entity.HubAsset;
import com.example.ioeduhub.repository.AssetRepo;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 附件存储:按内容 sha256 命名 {asset-dir}/{sha前2位}/{sha}.{ext},相同文件只存一份。
 * 客户实例发布条目前把封面/富文本图片/教学资料上传到这里,并把 payload 里的 URL 改写为 /hub-assets/...;
 * 安装时再由客户实例下载回本地。
 */
@Service
public class AssetService {

    private static final List<String> ALLOWED_EXT = Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "svg",
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "md",
            "zip", "rar", "7z", "mp4", "mp3");
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");

    private final AssetRepo assetRepo;
    private final Path root;
    private final OssObjectStore oss;

    public AssetService(AssetRepo assetRepo, HubProperties props, OssObjectStore oss) {
        this.assetRepo = assetRepo;
        this.root = Paths.get(props.getAssetDir()).toAbsolutePath().normalize();
        this.oss = oss;
    }

    public HubAsset store(MultipartFile file, Long tenantId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型: " + ext);
        }
        Path tmp = Files.createTempFile("hub-upload-", ".bin");
        try {
            String sha;
            try (InputStream in = file.getInputStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (DigestInputStream din = new DigestInputStream(in, md)) {
                    Files.copy(din, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                sha = hex(md.digest());
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            HubAsset existing = assetRepo.findBySha256(sha).orElse(null);
            if (existing != null) {
                return existing;
            }
            Path target = pathOf(sha, ext);
            HubAsset asset = new HubAsset();
            asset.setSha256(sha);
            asset.setExt(ext);
            asset.setSize(Files.size(tmp));
            asset.setMime(AssetContent.contentType(original));
            asset.setUploadedByTenantId(tenantId);
            if (oss.enabled()) writeCloud(asset, tmp);
            else {
                Files.createDirectories(target.getParent());
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return assetRepo.save(asset);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** 按 URL 里的 {sha}.{ext} 定位文件,不存在返回 null */
    public Path resolve(String sha, String ext) {
        if (sha == null || !SHA.matcher(sha).matches() || ext == null || !ALLOWED_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
            return null;
        }
        Path p = pathOf(sha, ext.toLowerCase(Locale.ROOT));
        return Files.isRegularFile(p) ? p : null;
    }

    public HubAsset remote(String sha, String ext) {
        if (sha == null || !SHA.matcher(sha).matches() || !ALLOWED_EXT.contains(ext)) return null;
        HubAsset asset = assetRepo.findBySha256(sha).orElse(null);
        if (asset == null || !ext.equals(asset.getExt()) || asset.getObjectKey() == null) return null;
        if (!oss.bucket().equals(asset.getBucket()) || !key(asset).equals(asset.getObjectKey()))
            throw new BusinessException(503, "商店文件存储配置不匹配，请联系平台管理员");
        return asset;
    }

    private String key(HubAsset asset) { return oss.prefix() + "/hub/" + asset.getSha256().substring(0, 2) + "/" + asset.getSha256() + "." + asset.getExt(); }
    private void writeCloud(HubAsset asset, Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) { oss.put(key(asset), in, asset.getSize(), AssetContent.contentType(asset.getSha256() + "." + asset.getExt()), asset.getSha256()); }
        com.aliyun.oss.model.ObjectMetadata metadata = oss.metadata(key(asset));
        if (metadata == null || metadata.getContentLength() != asset.getSize()
                || !asset.getSha256().equals(metadata.getUserMetadata().get("sha256"))) throw new IOException("OSS文件校验失败");
        asset.setObjectKey(key(asset)); asset.setBucket(oss.bucket());
    }

    public java.util.Map<String, Object> migrate(boolean dryRun, int limit) {
        if (!oss.enabled()) throw new BusinessException("请先启用 OSS 存储");
        if (limit < 1 || limit > 50) throw new BusinessException("每批数量须为1至50");
        org.springframework.data.domain.Page<HubAsset> page = assetRepo.findByObjectKeyIsNull(org.springframework.data.domain.PageRequest.of(0, limit));
        java.util.List<String> completed = new java.util.ArrayList<>();
        java.util.Map<String, String> failures = new java.util.LinkedHashMap<>();
        for (HubAsset asset : page) {
            if (dryRun) { completed.add(asset.url()); continue; }
            try {
                Path file = resolve(asset.getSha256(), asset.getExt());
                if (file == null) throw new IOException("原文件不存在");
                writeCloud(asset, file); assetRepo.saveAndFlush(asset); completed.add(asset.url());
            } catch (Exception e) { failures.put(asset.url(), "迁移未完成，请检查OSS及旧文件后重试"); }
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("dryRun", dryRun); result.put("files", completed); result.put("failures", failures);
        result.put("pending", page.getTotalElements() - (dryRun ? 0 : completed.size())); return result;
    }

    private Path pathOf(String sha, String ext) {
        return root.resolve(sha.substring(0, 2)).resolve(sha + "." + ext);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

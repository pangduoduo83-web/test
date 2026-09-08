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

    public AssetService(AssetRepo assetRepo, HubProperties props) {
        this.assetRepo = assetRepo;
        this.root = Paths.get(props.getAssetDir()).toAbsolutePath().normalize();
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
            Files.createDirectories(target.getParent());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            HubAsset asset = new HubAsset();
            asset.setSha256(sha);
            asset.setExt(ext);
            asset.setSize(Files.size(target));
            asset.setMime(file.getContentType());
            asset.setUploadedByTenantId(tenantId);
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

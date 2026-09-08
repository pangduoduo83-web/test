package com.example.ioedunew.service;

import com.example.ioedunew.tenant.TenantContext;
import com.example.ioedunew.tenant.TenantProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 上传文件的磁盘布局:{upload-dir}/{租户编码}/{yyyyMM}/{uuid.ext}。
 * 库里与接口里的 URL 仍是 /uploads/{yyyyMM}/{uuid.ext},租户由请求 Host 决定,
 * 因此同一份项目数据搬到别的租户(项目商店)时不需要改写 URL。
 * 多租户之前上传的文件位于 {upload-dir}/{yyyyMM}/ 下,只对默认租户做兼容回退。
 */
@Component
public class UploadStorage {

    /** 只接受 yyyyMM/文件名 形式的相对路径,杜绝目录穿越 */
    private static final Pattern RELATIVE = Pattern.compile("^\\d{6}/[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,10}$");

    private final Path root;
    private final TenantProperties tenantProperties;

    public UploadStorage(@Value("${ioedu.upload-dir}") String uploadDir, TenantProperties tenantProperties) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.tenantProperties = tenantProperties;
    }

    /** 当前租户的上传根目录 */
    public Path tenantRoot() {
        return root.resolve(TenantContext.require());
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
        if (file.toFile().isFile()) {
            return file;
        }
        if (tenantProperties.getDefaultCode().equals(TenantContext.require())) {
            Path legacy = root.resolve(relative).normalize();
            if (legacy.startsWith(root) && legacy.toFile().isFile()) {
                return legacy;
            }
        }
        return file;
    }
}

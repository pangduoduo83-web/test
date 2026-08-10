package com.example.ioedunew.controller;

import com.example.ioedunew.common.ApiResponse;
import com.example.ioedunew.common.BusinessException;
import com.example.ioedunew.config.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传接口:保存到本地磁盘 {upload-dir}/yyyyMM/uuid.ext,
 * 由 WebConfig 将 /uploads/** 映射为静态资源对外访问。
 * 安全边界:
 * - /api/upload 仅允许常见图片扩展名,登录用户可用(头像/封面);
 * - /api/upload/file 允许教学资料类扩展名,仅教师/管理员可用;
 * - 大小上限由 multipart 配置控制(10MB)。
 */
@RestController
@RequestMapping("/api/upload")
public class FileController {

    private static final List<String> IMAGE_EXT =
            Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "svg");

    private static final List<String> DOC_EXT = Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "svg",
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "md",
            "zip", "rar", "7z", "mp4", "mp3");

    @Value("${ioedu.upload-dir}")
    private String uploadDir;

    @PostMapping
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(saveFile(file, IMAGE_EXT, "仅支持图片格式:" + String.join("/", IMAGE_EXT)));
    }

    /**
     * 教学资料上传(教师/管理员):课件、文档、代码包、视频等。
     */
    @PostMapping("/file")
    public ApiResponse<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file,
                                                       @RequestAttribute(AuthUser.REQUEST_ATTR) AuthUser user)
            throws IOException {
        if (!user.isTeacher() && !user.isAdmin()) {
            throw new BusinessException(403, "仅教师或管理员可上传教学资料");
        }
        return ApiResponse.ok(saveFile(file, DOC_EXT, "不支持的文件类型,允许:" + String.join("/", DOC_EXT)));
    }

    private Map<String, String> saveFile(MultipartFile file, List<String> allowedExt, String typeError)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!allowedExt.contains(ext)) {
            throw new BusinessException(typeError);
        }

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        File dir = new File(uploadDir, month);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(500, "上传目录创建失败");
        }
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        file.transferTo(new File(dir, filename).getAbsoluteFile());

        Map<String, String> result = new HashMap<>();
        result.put("url", "/uploads/" + month + "/" + filename);
        result.put("name", original);
        return result;
    }
}
